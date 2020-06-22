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

package com.google.template.soy.jssrc.internal;

import static com.google.template.soy.jssrc.dsl.Expression.LITERAL_EMPTY_STRING;
import static com.google.template.soy.jssrc.dsl.Expression.stringLiteral;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunkUtils;
import com.google.template.soy.jssrc.dsl.ConditionalExpressionBuilder;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.SoyJsPluginUtils;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Visitor for generating JS expressions for parse tree nodes.
 *
 * <p>Precondition: MsgNode should not exist in the tree.
 *
 */
public class GenJsExprsVisitor extends AbstractSoyNodeVisitor<List<Expression>> {

  /** Injectable factory for creating an instance of this class. */
  public static class GenJsExprsVisitorFactory {
    // We are using a provider to resolve a circular dependency between GenCallCodeUtils and this
    // factory.
    protected final JavaScriptValueFactoryImpl javaScriptValueFactory;
    protected final Supplier<GenCallCodeUtils> genCallCodeUtils;
    protected final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;

    protected GenJsExprsVisitorFactory(
        JavaScriptValueFactoryImpl javaScriptValueFactory,
        Supplier<GenCallCodeUtils> genCallCodeUtils,
        IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor) {
      this.javaScriptValueFactory = javaScriptValueFactory;
      this.genCallCodeUtils = genCallCodeUtils;
      this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    }

    /**
     * @param templateAliases A mapping for looking up the function name for a given fully qualified
     *     name.
     */
    public GenJsExprsVisitor create(
        TranslationContext translationContext,
        TemplateAliases templateAliases,
        ErrorReporter errorReporter) {
      return new GenJsExprsVisitor(
          javaScriptValueFactory,
          genCallCodeUtils.get(),
          isComputableAsJsExprsVisitor,
          this,
          translationContext,
          errorReporter,
          templateAliases);
    }
  }

  private static final SoyErrorKind UNKNOWN_SOY_JS_SRC_PRINT_DIRECTIVE =
      SoyErrorKind.of("Unknown SoyJsSrcPrintDirective ''{0}''.");

  protected final JavaScriptValueFactoryImpl javaScriptValueFactory;
  private final GenCallCodeUtils genCallCodeUtils;
  protected final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;
  private final GenJsExprsVisitorFactory genJsExprsVisitorFactory;

  protected final TranslationContext translationContext;
  protected final ErrorReporter errorReporter;

  /** List to collect the results. */
  protected List<Expression> chunks;

  /**
   * Used for looking up the local name for a given template call to a fully qualified template
   * name.
   */
  protected final TemplateAliases templateAliases;

  /**
   * @param jsExprTranslator Instance of JsExprTranslator to use.
   * @param genCallCodeUtils Instance of GenCallCodeUtils to use.
   * @param isComputableAsJsExprsVisitor The IsComputableAsJsExprsVisitor used by this instance
   *     (when needed).
   * @param genJsExprsVisitorFactory Factory for creating an instance of GenJsExprsVisitor.
   * @param templateAliases A mapping for looking up the function name for a given fully qualified
   *     name.
   */
  protected GenJsExprsVisitor(
      JavaScriptValueFactoryImpl javaScriptValueFactory,
      GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      GenJsExprsVisitorFactory genJsExprsVisitorFactory,
      TranslationContext translationContext,
      ErrorReporter errorReporter,
      TemplateAliases templateAliases) {
    this.javaScriptValueFactory = javaScriptValueFactory;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    this.genJsExprsVisitorFactory = genJsExprsVisitorFactory;

    this.translationContext = translationContext;
    this.errorReporter = errorReporter;
    this.templateAliases = templateAliases;
  }

  @Override
  public List<Expression> exec(SoyNode node) {
    Preconditions.checkArgument(isComputableAsJsExprsVisitor.exec(node));
    chunks = new ArrayList<>();
    visit(node);
    return chunks;
  }

  /**
   * Executes this visitor on the children of the given node, without visiting the given node
   * itself.
   */
  public List<Expression> execOnChildren(ParentSoyNode<?> node) {
    Preconditions.checkArgument(isComputableAsJsExprsVisitor.execOnChildren(node));
    chunks = new ArrayList<>();
    visitChildren(node);
    return chunks;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override protected void visitTemplateNode(TemplateNode node) {
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
  @Override protected void visitRawTextNode(RawTextNode node) {
    chunks.add(stringLiteral(node.getRawText()));
  }

  @Override protected void visitMsgPlaceholderNode(MsgPlaceholderNode node) {
    visitChildren(node);
  }

  @Override
  protected void visitHtmlAttributeValueNode(HtmlAttributeValueNode node) {
    visitChildren(node);
  }

  /**
   * Example:
   * <pre>
   *   &lt;a href="{$url}"&gt;
   * </pre>
   * might generate
   * <pre>
   *   '&lt;a href="' + opt_data.url + '"&gt;'
   * </pre>
   */
  @Override protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    visitChildren(node);
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
  @Override protected void visitPrintNode(PrintNode node) {
    Expression expr = translateExpr(node.getExpr());

    // Process directives.
    for (PrintDirectiveNode directiveNode : node.getChildren()) {

      // Get directive.
      SoyPrintDirective directive = directiveNode.getPrintDirective();
      if (!(directive instanceof SoyJsSrcPrintDirective)) {
        errorReporter.report(
            node.getSourceLocation(), UNKNOWN_SOY_JS_SRC_PRINT_DIRECTIVE, directiveNode.getName());
        return;
      }

      // Get directive args.
      List<ExprRootNode> argNodes = directiveNode.getArgs();

      // Convert args to CodeChunks.
      List<Expression> argChunks = new ArrayList<>(argNodes.size());
      for (ExprRootNode argNode : argNodes) {
        argChunks.add(translateExpr(argNode));
      }

      // Apply directive.
      expr =
          SoyJsPluginUtils.applyDirective(
              expr,
              (SoyJsSrcPrintDirective) directive,
              argChunks,
              node.getSourceLocation(),
              errorReporter);
    }

    chunks.add(expr);
  }

  protected TranslateExprNodeVisitor getExprTranslator() {
    return new TranslateExprNodeVisitor(
        javaScriptValueFactory, translationContext, templateAliases, errorReporter);
  }

  private Expression translateExpr(ExprRootNode argNode) {
    return getExprTranslator().exec(argNode);
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
  @Override protected void visitIfNode(IfNode node) {

    // Create another instance of this visitor class for generating JS expressions from children.
    GenJsExprsVisitor genJsExprsVisitor =
        genJsExprsVisitorFactory.create(translationContext, templateAliases, errorReporter);
    CodeChunk.Generator generator = translationContext.codeGenerator();

    List<Expression> ifs = new ArrayList<>();
    List<Expression> thens = new ArrayList<>();
    Expression trailingElse = null;

    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode ifCond = (IfCondNode) child;

        ifs.add(
            getExprTranslator()
                .maybeCoerceToBoolean(
                    ifCond.getExpr().getType(), translateExpr(ifCond.getExpr()), false));
        thens.add(CodeChunkUtils.concatChunks(genJsExprsVisitor.exec(ifCond)));
      } else if (child instanceof IfElseNode) {
        trailingElse = CodeChunkUtils.concatChunks(genJsExprsVisitor.exec(child));
      } else {
        throw new AssertionError();
      }
    }

    Preconditions.checkState(ifs.size() == thens.size());

    ConditionalExpressionBuilder builder = Expression.ifExpression(ifs.get(0), thens.get(0));

    for (int i = 1; i < ifs.size(); i++) {
      builder.addElseIf(ifs.get(i), thens.get(i));
    }

    Expression ifChunk =
        trailingElse != null
            ? builder.setElse(trailingElse).build(generator)
            : builder.setElse(LITERAL_EMPTY_STRING).build(generator);

    chunks.add(ifChunk);
  }

  @Override protected void visitIfCondNode(IfCondNode node) {
    visitChildren(node);
  }

  @Override protected void visitIfElseNode(IfElseNode node) {
    visitChildren(node);
  }

  /**
   * Example:
   * <pre>
   *   {call some.func data="all" /}
   *   {call some.func data="$boo.foo" /}
   *   {call some.func}
   *     {param key="goo" value="$moo" /}
   *   {/call}
   *   {call some.func data="$boo"}
   *     {param key="goo"}Blah{/param}
   *   {/call}
   * </pre>
   * might generate
   * <pre>
   *   some.func(opt_data)
   *   some.func(opt_data.boo.foo)
   *   some.func({goo: opt_data.moo})
   *   some.func(soy.$$assignDefaults({goo: 'Blah'}, opt_data.boo))
   * </pre>
   */
  @Override protected void visitCallNode(CallNode node) {
    Expression call =
        genCallCodeUtils.gen(
            node, templateAliases, translationContext, errorReporter, getExprTranslator());
    chunks.add(call);
  }

  @Override protected void visitCallParamContentNode(CallParamContentNode node) {
    visitChildren(node);
  }
}
