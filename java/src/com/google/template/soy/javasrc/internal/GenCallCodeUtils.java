/*
 * Copyright 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.javasrc.internal;

import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.UTILS_LIB;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genFunctionCall;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genMaybeCast;

import com.google.inject.Inject;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.javasrc.SoyJavaSrcOptions;
import com.google.template.soy.javasrc.internal.GenJavaExprsVisitor.GenJavaExprsVisitorFactory;
import com.google.template.soy.javasrc.internal.TranslateToJavaExprVisitor.TranslateToJavaExprVisitorFactory;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.JavaExprUtils;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;

import java.util.Deque;
import java.util.List;
import java.util.Map;


/**
 * Utilities for generating Java code for calls.
 *
 * @author Kai Huang
 */
class GenCallCodeUtils {


  /** The options for generating Java source code. */
  private final SoyJavaSrcOptions javaSrcOptions;

  /** The IsComputableAsJavaExprsVisitor used by this instance. */
  private final IsComputableAsJavaExprsVisitor isComputableAsJavaExprsVisitor;

  /** Factory for creating an instance of GenJavaExprsVisitor. */
  private final GenJavaExprsVisitorFactory genJavaExprsVisitorFactory;

  /** Factory for creating an instance of TranslateToJavaExprVisitor. */
  private final TranslateToJavaExprVisitorFactory translateToJavaExprVisitorFactory;


  /**
   * @param javaSrcOptions The options for generating Java source code.
   * @param isComputableAsJavaExprsVisitor The IsComputableAsJavaExprsVisitor to be used.
   * @param genJavaExprsVisitorFactory Factory for creating an instance of GenJavaExprsVisitor.
   * @param translateToJavaExprVisitorFactory Factory for creating an instance of
   *     TranslateToJavaExprVisitor.
   */
  @Inject
  GenCallCodeUtils(SoyJavaSrcOptions javaSrcOptions,
                   IsComputableAsJavaExprsVisitor isComputableAsJavaExprsVisitor,
                   GenJavaExprsVisitorFactory genJavaExprsVisitorFactory,
                   TranslateToJavaExprVisitorFactory translateToJavaExprVisitorFactory) {
    this.javaSrcOptions = javaSrcOptions;
    this.isComputableAsJavaExprsVisitor = isComputableAsJavaExprsVisitor;
    this.genJavaExprsVisitorFactory = genJavaExprsVisitorFactory;
    this.translateToJavaExprVisitorFactory = translateToJavaExprVisitorFactory;
  }


  /**
   * Generates the Java expression for a given call (the version that doesn't pass a StringBuilder).
   *
   * <p> Important: If there are CallParamContentNode children whose contents are not computable as
   * Java expressions, then this function assumes that, elsewhere, code has been generated to define
   * their respective 'param<n>' temporary variables.
   *
   * <p> Here are five example calls:
   * <pre>
   *   {call some.func data="all" /}
   *   {call some.func data="$boo.foo" /}
   *   {call some.func}
   *     {param goo = $moo /}
   *   {/call}
   *   {call some.func data="$boo"}
   *     {param goo}Blah{/param}
   *   {/call}
   *   {call some.func}
   *     {param goo}
   *       {for $i in range(3)}{$i}{/for}
   *     {/param}
   *   {/call}
   * </pre>
   * Their respective generated calls might be the following:
   * <pre>
   *   some.func(opt_data)
   *   some.func(opt_data.boo.foo)
   *   some.func({goo: opt_data.moo})
   *   some.func(soy.$$augmentData(opt_data.boo, {goo: 'Blah'}))
   *   some.func({goo: param65})
   * </pre>
   * Note that in the last case, the param content is not computable as Java expressions, so we
   * assume that code has been generated to define the temporary variable 'param<n>'.
   *
   * @param callNode The call to generate code for.
   * @param localVarTranslations The current stack of replacement Java expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   * @return The Java expression for the call (the version that doesn't pass a StringBuilder).
   */
  public JavaExpr genCallExpr(
      CallNode callNode, Deque<Map<String, JavaExpr>> localVarTranslations) {

    if (! (callNode instanceof CallBasicNode)) {
      throw new UnsupportedOperationException("Delegates are not supported in JavaSrc backend.");
    }

    JavaExpr objToPass = genObjToPass(callNode, localVarTranslations);
    return new JavaExpr(
        ((CallBasicNode) callNode).getCalleeName().replace('.', '$') +
            "(" + objToPass.getText() + ")",
        String.class, Integer.MAX_VALUE);
  }


  /**
   * Generates the Java expression for the object to pass in a given call.
   *
   * <p> Important: If there are CallParamContentNode children whose contents are not computable as
   * Java expressions, then this function assumes that, elsewhere, code has been generated to define
   * their respective 'param<n>' temporary variables.
   *
   * <p> Here are five example calls:
   * <pre>
   *   {call some.func data="all" /}
   *   {call some.func data="$boo.foo" /}
   *   {call some.func}
   *     {param goo = $moo /}
   *   {/call}
   *   {call some.func data="$boo"}
   *     {param goo}Blah{/param}
   *   {/call}
   *   {call some.func}
   *     {param goo}
   *       {for $i in range(3)}{$i}{/for}
   *     {/param}
   *   {/call}
   * </pre>
   * Their respective objects to pass might be the following:
   * <pre>
   *   opt_data
   *   opt_data.boo.foo
   *   {goo: opt_data.moo}
   *   soy.$$augmentData(opt_data.boo, {goo: 'Blah'})
   *   {goo: param65}
   * </pre>
   * Note that in the last case, the param content is not computable as Java expressions, so we
   * assume that code has been generated to define the temporary variable 'param<n>'.
   *
   * @param callNode The call to generate code for.
   * @param localVarTranslations The current stack of replacement Java expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   * @return The Java expression for the object to pass in the call.
   */
  public JavaExpr genObjToPass(
      CallNode callNode, Deque<Map<String, JavaExpr>> localVarTranslations) {

    TranslateToJavaExprVisitor ttjev =
        translateToJavaExprVisitorFactory.create(localVarTranslations);

    // ------ Generate the expression for the original data to pass ------
    JavaExpr dataToPass;
    if (callNode.isPassingAllData()) {
      dataToPass = new JavaExpr("data", SoyMapData.class, Integer.MAX_VALUE);
    } else if (callNode.isPassingData()) {
      dataToPass = new JavaExpr(
          genMaybeCast(ttjev.exec(callNode.getExpr()), SoyMapData.class),
          SoyMapData.class, Integer.MAX_VALUE);
    } else {
      dataToPass = new JavaExpr("null", SoyMapData.class, Integer.MAX_VALUE);
    }

    // ------ Case 1: No additional params ------
    if (callNode.numChildren() == 0) {
      return dataToPass;
    }

    // ------ Build an object literal containing the additional params ------
    StringBuilder paramsObjSb = new StringBuilder();
    paramsObjSb.append("new com.google.template.soy.data.SoyMapData(");

    boolean isFirst = true;
    for (CallParamNode child : callNode.getChildren()) {

      if (isFirst) {
        isFirst = false;
      } else {
        paramsObjSb.append(", ");
      }

      String key = child.getKey();
      paramsObjSb.append("\"").append(key).append("\", ");

      if (child instanceof CallParamValueNode) {
        CallParamValueNode cpvn = (CallParamValueNode) child;
        JavaExpr valueJavaExpr = ttjev.exec(cpvn.getValueExprUnion().getExpr());
        paramsObjSb.append(valueJavaExpr.getText());

      } else {
        CallParamContentNode cpcn = (CallParamContentNode) child;

        if (isComputableAsJavaExprsVisitor.exec(cpcn)) {
          List<JavaExpr> cpcnJavaExprs =
              genJavaExprsVisitorFactory.create(localVarTranslations).exec(cpcn);
          JavaExpr valueJavaExpr = JavaExprUtils.concatJavaExprs(cpcnJavaExprs);
          paramsObjSb.append(valueJavaExpr.getText());

        } else {
          // This is a param with content that cannot be represented as Java expressions, so we
          // assume that code has been generated to define the temporary variable 'param<n>'.
          if (javaSrcOptions.getCodeStyle() == SoyJavaSrcOptions.CodeStyle.STRINGBUILDER) {
            paramsObjSb.append("param").append(cpcn.getId()).append(".toString()");
          } else {
            paramsObjSb.append("param").append(cpcn.getId());
          }
        }
      }
    }

    paramsObjSb.append(")");

    // ------ Cases 2 and 3: Additional params with and without original data to pass ------
    if (callNode.isPassingData()) {
      return new JavaExpr(
          genFunctionCall(
              UTILS_LIB + ".$$augmentData", dataToPass.getText(), paramsObjSb.toString()),
          SoyMapData.class, Integer.MAX_VALUE);
    } else {
      return new JavaExpr(paramsObjSb.toString(), SoyMapData.class, Integer.MAX_VALUE);
    }
  }

}
