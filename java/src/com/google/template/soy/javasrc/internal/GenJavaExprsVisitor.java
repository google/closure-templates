/*
 * Copyright 2008 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.base.CharEscapers;
import com.google.template.soy.javasrc.internal.TranslateToJavaExprVisitor.TranslateToJavaExprVisitorFactory;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genCoerceBoolean;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genMaybeProtect;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.JavaExprUtils;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcPrintDirective;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.Deque;
import java.util.List;
import java.util.Map;


/**
 * Visitor for generating Java expressions for parse tree nodes.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 * @author Kai Huang
 */
public class GenJavaExprsVisitor extends AbstractSoyNodeVisitor<List<JavaExpr>> {


  /**
   * Injectable factory for creating an instance of this class.
   */
  public static interface GenJavaExprsVisitorFactory {

    /**
     * @param localVarTranslations The current stack of replacement Java expressions for the local
     *     variables (and foreach-loop special functions) current in scope.
     */
    public GenJavaExprsVisitor create(Deque<Map<String, JavaExpr>> localVarTranslations);
  }


  /** Map of all SoyJavaSrcPrintDirectives (name to directive). */
  Map<String, SoyJavaSrcPrintDirective> soyJavaSrcDirectivesMap;

  /** Instance of GenCallCodeUtils to use. */
  private final GenCallCodeUtils genCallCodeUtils;

  /** The IsComputableAsJavaExprsVisitor used by this instance (when needed). */
  private final IsComputableAsJavaExprsVisitor isComputableAsJavaExprsVisitor;

  /** Factory for creating an instance of GenJavaExprsVisitor. */
  private final GenJavaExprsVisitorFactory genJavaExprsVisitorFactory;

  /** Factory for creating an instance of TranslateToJavaExprVisitor. */
  private final TranslateToJavaExprVisitorFactory translateToJavaExprVisitorFactory;

  /** The current stack of replacement Java expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  private final Deque<Map<String, JavaExpr>> localVarTranslations;

  /** List to collect the results. */
  private List<JavaExpr> javaExprs;


  /**
   * @param soyJavaSrcDirectivesMap Map of all SoyJavaSrcPrintDirectives (name to directive).
   * @param genCallCodeUtils Instance of GenCallCodeUtils to use.
   * @param isComputableAsJavaExprsVisitor The IsComputableAsJavaExprsVisitor used by this instance
   *     (when needed).
   * @param genJavaExprsVisitorFactory Factory for creating an instance of GenJavaExprsVisitor.
   * @param translateToJavaExprVisitorFactory Factory for creating an instance of
   *     TranslateToJavaExprVisitor.
   * @param localVarTranslations The current stack of replacement Java expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   */
  @AssistedInject
  GenJavaExprsVisitor(
      Map<String, SoyJavaSrcPrintDirective> soyJavaSrcDirectivesMap,
      GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJavaExprsVisitor isComputableAsJavaExprsVisitor,
      GenJavaExprsVisitorFactory genJavaExprsVisitorFactory,
      TranslateToJavaExprVisitorFactory translateToJavaExprVisitorFactory,
      @Assisted Deque<Map<String, JavaExpr>> localVarTranslations) {
    this.soyJavaSrcDirectivesMap = soyJavaSrcDirectivesMap;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsJavaExprsVisitor = isComputableAsJavaExprsVisitor;
    this.genJavaExprsVisitorFactory = genJavaExprsVisitorFactory;
    this.translateToJavaExprVisitorFactory = translateToJavaExprVisitorFactory;
    this.localVarTranslations = localVarTranslations;
  }


  @Override public List<JavaExpr> exec(SoyNode node) {
    Preconditions.checkArgument(isComputableAsJavaExprsVisitor.exec(node));
    return super.exec(node);
  }


  @Override protected void setup() {
    javaExprs = Lists.newArrayList();
  }


  @Override protected List<JavaExpr> getResult() {
    return javaExprs;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(TemplateNode node) {
    visitChildren(node);
  }


  /**
   * Example:
   * <pre>
   *   I'm feeling lucky!
   * </pre>
   * generates
   * <pre>
   *   'I\'m feeling lucky!'
   * </pre>
   */
  @Override protected void visitInternal(RawTextNode node) {

    javaExprs.add(new JavaExpr(
        '"' + CharEscapers.javaStringEscaper().escape(node.getRawText()) + '"',
        String.class, Integer.MAX_VALUE));
  }


  /**
   * Example:
   * <pre>
   *   {$boo.foo}
   *   {$goo.moo + 5}
   * </pre>
   * might generate
   * <pre>
   *   opt_data.boo.foo
   *   gooData4.moo + 5
   * </pre>
   */
  @Override protected void visitInternal(PrintNode node) {

    TranslateToJavaExprVisitor ttjev =
        translateToJavaExprVisitorFactory.create(localVarTranslations);

    JavaExpr javaExpr = ttjev.exec(node.getExpr());

    // Process directives.
    for (PrintDirectiveNode directiveNode : node.getChildren()) {

      // Get directive.
      SoyJavaSrcPrintDirective directive = soyJavaSrcDirectivesMap.get(directiveNode.getName());
      if (directive == null) {
        throw new SoySyntaxException(
            "Failed to find SoyJavaSrcPrintDirective with name '" + directiveNode.getName() + "'" +
            " (tag " + node.toSourceString() +")");
      }

      // Get directive args.
      List<ExprRootNode<ExprNode>> args = directiveNode.getArgs();
      if (! directive.getValidArgsSizes().contains(args.size())) {
        throw new SoySyntaxException(
            "Print directive '" + directiveNode.getName() + "' used with the wrong number of" +
            " arguments (tag " + node.toSourceString() + ").");
      }

      // Translate directive args.
      List<JavaExpr> argsJavaExprs = Lists.newArrayListWithCapacity(args.size());
      for (ExprRootNode<ExprNode> arg : args) {
        argsJavaExprs.add(ttjev.exec(arg));
      }

      // Apply directive.
      javaExpr = directive.applyForJavaSrc(javaExpr, argsJavaExprs);
    }

    javaExprs.add(javaExpr);
  }


  /**
   * Example:
   * <pre>
   *   {if $boo}
   *     AAA
   *   {elseif $foo}
   *     BBB
   *   {else}
   *     CCC
   *   {/if}
   * </pre>
   * might generate
   * <pre>
   *   (opt_data.boo) ? AAA : (opt_data.foo) ? BBB : CCC
   * </pre>
   */
  @Override protected void visitInternal(IfNode node) {

    // Create another instance of this visitor class for generating Java expressions from children.
    GenJavaExprsVisitor genJavaExprsVisitor =
        genJavaExprsVisitorFactory.create(localVarTranslations);

    StringBuilder javaExprTextSb = new StringBuilder();

    boolean hasElse = false;
    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;

        JavaExpr condJavaExpr =
            translateToJavaExprVisitorFactory.create(localVarTranslations).exec(icn.getExpr());
        javaExprTextSb.append("(").append(genCoerceBoolean(condJavaExpr)).append(") ? ");

        List<JavaExpr> condBlockJavaExprs = genJavaExprsVisitor.exec(icn);
        javaExprTextSb.append(
            genMaybeProtect(JavaExprUtils.concatJavaExprs(condBlockJavaExprs),
                            Operator.CONDITIONAL.getPrecedence() + 1));

        javaExprTextSb.append(" : ");

      } else if (child instanceof IfElseNode) {
        hasElse = true;
        IfElseNode ien = (IfElseNode) child;

        List<JavaExpr> elseBlockJavaExprs = genJavaExprsVisitor.exec(ien);
        javaExprTextSb.append(
            genMaybeProtect(JavaExprUtils.concatJavaExprs(elseBlockJavaExprs),
                            Operator.CONDITIONAL.getPrecedence() + 1));

      } else {
        throw new AssertionError();
      }
    }

    if (!hasElse) {
      javaExprTextSb.append("\"\"");
    }

    javaExprs.add(new JavaExpr(
        javaExprTextSb.toString(), String.class, Operator.CONDITIONAL.getPrecedence()));
  }


  @Override protected void visitInternal(IfCondNode node) {
    visitChildren(node);
  }


  @Override protected void visitInternal(IfElseNode node) {
    visitChildren(node);
  }


  /**
   * Example:
   * <pre>
   *   {call name="some.func" data="all" /}
   *   {call name="some.func" data="$boo.foo" /}
   *   {call name="some.func"}
   *     {param key="goo" value="$moo" /}
   *   {/call}
   *   {call name="some.func" data="$boo"}
   *     {param key="goo"}Blah{/param}
   *   {/call}
   * </pre>
   * might generate
   * <pre>
   *   some.func(opt_data)
   *   some.func(opt_data.boo.foo)
   *   some.func({goo: opt_data.moo})
   *   some.func(soy.$$augmentData(opt_data.boo, {goo: 'Blah'}))
   * </pre>
   */
  @Override protected void visitInternal(CallNode node) {
    javaExprs.add(genCallCodeUtils.genCallExpr(node, localVarTranslations));
  }


  @Override protected void visitInternal(CallParamContentNode node) {
    visitChildren(node);
  }

}
