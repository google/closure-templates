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

package com.google.template.soy.jssrc.internal;

import com.google.inject.Inject;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.IsUsingIjData;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;

import java.util.Deque;
import java.util.List;
import java.util.Map;


/**
 * Utilities for generating JS code for calls.
 *
 * @author Kai Huang
 */
class GenCallCodeUtils {


  /** The options for generating JS source code. */
  private final SoyJsSrcOptions jsSrcOptions;

  /** Whether any of the Soy code uses injected data. */
  private final boolean isUsingIjData;

  /** Instance of JsExprTranslator to use. */
  private final JsExprTranslator jsExprTranslator;

  /** The IsComputableAsJsExprsVisitor used by this instance. */
  private final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;

  /** Factory for creating an instance of GenJsExprsVisitor. */
  private final GenJsExprsVisitorFactory genJsExprsVisitorFactory;


  /**
   * @param jsSrcOptions The options for generating JS source code.
   * @param isUsingIjData Whether any of the Soy code uses injected data.
   * @param jsExprTranslator Instance of JsExprTranslator to use.
   * @param isComputableAsJsExprsVisitor The IsComputableAsJsExprsVisitor to be used.
   * @param genJsExprsVisitorFactory Factory for creating an instance of GenJsExprsVisitor.
   */
  @Inject
  GenCallCodeUtils(
      SoyJsSrcOptions jsSrcOptions, @IsUsingIjData boolean isUsingIjData,
      JsExprTranslator jsExprTranslator, IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      GenJsExprsVisitorFactory genJsExprsVisitorFactory) {
    this.jsSrcOptions = jsSrcOptions;
    this.isUsingIjData = isUsingIjData;
    this.jsExprTranslator = jsExprTranslator;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    this.genJsExprsVisitorFactory = genJsExprsVisitorFactory;
  }


  /**
   * Generates the JS expression for a given call (the version that doesn't pass a StringBuilder).
   *
   * <p> Important: If there are CallParamContentNode children whose contents are not computable as
   * JS expressions, then this function assumes that, elsewhere, code has been generated to define
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
   * Note that in the last case, the param content is not computable as JS expressions, so we assume
   * that code has been generated to define the temporary variable 'param<n>'.
   *
   * @param callNode The call to generate code for.
   * @param localVarTranslations The current stack of replacement JS expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   * @return The JS expression for the call (the version that doesn't pass a StringBuilder).
   */
  public JsExpr genCallExpr(CallNode callNode, Deque<Map<String, JsExpr>> localVarTranslations) {

    String calleeExprText = (callNode instanceof CallBasicNode) ?
        ((CallBasicNode) callNode).getCalleeName() :
        "soy.$$getDelegateFn(soy.$$getDelegateId('" +
            ((CallDelegateNode) callNode).getDelCalleeName() + "'))";
    JsExpr objToPass = genObjToPass(callNode, localVarTranslations);
    return new JsExpr(
        calleeExprText + "(" + objToPass.getText() +
            (isUsingIjData ? ", null, opt_ijData" : "") + ")",
        Integer.MAX_VALUE);
  }


  /**
   * Generates the JS expression for the object to pass in a given call.
   *
   * <p> Important: If there are CallParamContentNode children whose contents are not computable as
   * JS expressions, then this function assumes that, elsewhere, code has been generated to define
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
   * Note that in the last case, the param content is not computable as JS expressions, so we assume
   * that code has been generated to define the temporary variable 'param<n>'.
   *
   * @param callNode The call to generate code for.
   * @param localVarTranslations The current stack of replacement JS expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   * @return The JS expression for the object to pass in the call.
   */
  public JsExpr genObjToPass(CallNode callNode, Deque<Map<String, JsExpr>> localVarTranslations) {

    // ------ Generate the expression for the original data to pass ------
    JsExpr dataToPass;
    if (callNode.isPassingAllData()) {
      dataToPass = new JsExpr("opt_data", Integer.MAX_VALUE);
    } else if (callNode.isPassingData()) {
      dataToPass = jsExprTranslator.translateToJsExpr(
          callNode.getExpr(), callNode.getExprText(), localVarTranslations);
    } else {
      dataToPass = new JsExpr("null", Integer.MAX_VALUE);
    }

    // ------ Case 1: No additional params ------
    if (callNode.numChildren() == 0) {
      return dataToPass;
    }

    // ------ Build an object literal containing the additional params ------
    StringBuilder paramsObjSb = new StringBuilder();
    paramsObjSb.append("{");

    boolean isFirst = true;
    for (CallParamNode child : callNode.getChildren()) {

      if (isFirst) {
        isFirst = false;
      } else {
        paramsObjSb.append(", ");
      }

      String key = child.getKey();
      paramsObjSb.append(key).append(": ");

      if (child instanceof CallParamValueNode) {
        CallParamValueNode cpvn = (CallParamValueNode) child;
        JsExpr valueJsExpr = jsExprTranslator.translateToJsExpr(
            cpvn.getValueExprUnion().getExpr(), cpvn.getValueExprText(), localVarTranslations);
        paramsObjSb.append(valueJsExpr.getText());

      } else {
        CallParamContentNode cpcn = (CallParamContentNode) child;

        if (isComputableAsJsExprsVisitor.exec(cpcn)) {
          List<JsExpr> cpcnJsExprs =
              genJsExprsVisitorFactory.create(localVarTranslations).exec(cpcn);
          JsExpr valueJsExpr = JsExprUtils.concatJsExprs(cpcnJsExprs);
          paramsObjSb.append(valueJsExpr.getText());

        } else {
          // This is a param with content that cannot be represented as JS expressions, so we assume
          // that code has been generated to define the temporary variable 'param<n>'.
          if (jsSrcOptions.getCodeStyle() == SoyJsSrcOptions.CodeStyle.STRINGBUILDER) {
            paramsObjSb.append("param").append(cpcn.getId()).append(".toString()");
          } else {
            paramsObjSb.append("param").append(cpcn.getId());
          }
        }
      }
    }

    paramsObjSb.append("}");

    // ------ Cases 2 and 3: Additional params with and without original data to pass ------
    if (callNode.isPassingData()) {
      return new JsExpr(
          "soy.$$augmentData(" + dataToPass.getText() + ", " + paramsObjSb.toString() + ")",
          Integer.MAX_VALUE);
    } else {
      return new JsExpr(paramsObjSb.toString(), Integer.MAX_VALUE);
    }
  }

}
