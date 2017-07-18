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

import static com.google.template.soy.jssrc.dsl.CodeChunk.LITERAL_EMPTY_STRING;
import static com.google.template.soy.jssrc.dsl.CodeChunk.stringLiteral;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_GET_CSS_NAME;
import static com.google.template.soy.jssrc.internal.JsRuntime.XID;

import com.google.common.base.Preconditions;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunkUtils;
import com.google.template.soy.jssrc.dsl.ConditionalExpressionBuilder;
import com.google.template.soy.jssrc.dsl.SoyJsPluginUtils;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CssNode;
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
import com.google.template.soy.soytree.XidNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Visitor for generating JS expressions for parse tree nodes.
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 */
public class GenJsExprsVisitor extends AbstractSoyNodeVisitor<List<CodeChunk.WithValue>> {

  /**
   * Injectable factory for creating an instance of this class.
   */
  public interface GenJsExprsVisitorFactory {

    /**
     * @param templateAliases A mapping for looking up the function name for a given fully
     *     qualified name.
     */
    GenJsExprsVisitor create(
        TranslationContext translationContext,
        TemplateAliases templateAliases,
        ErrorReporter errorReporter);
  }

  private static final SoyErrorKind ARITY_MISMATCH =
      SoyErrorKind.of("Print directive ''{0}'' called with {1} arguments, expected {2}.");
  private static final SoyErrorKind UNKNOWN_SOY_JS_SRC_PRINT_DIRECTIVE =
      SoyErrorKind.of("Unknown SoyJsSrcPrintDirective ''{0}''.");

  private final Map<String, SoyJsSrcPrintDirective> soyJsSrcDirectivesMap;
  private final JsExprTranslator jsExprTranslator;
  private final GenCallCodeUtils genCallCodeUtils;
  protected final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;
  private final GenJsExprsVisitorFactory genJsExprsVisitorFactory;

  private final TranslationContext translationContext;
  private final ErrorReporter errorReporter;

  /** List to collect the results. */
  protected List<CodeChunk.WithValue> chunks;

  /**
   * Used for looking up the local name for a given template call to a fully qualified template
   * name.
   */
  private final TemplateAliases templateAliases;

  /**
   * @param soyJsSrcDirectivesMap Map of all SoyJsSrcPrintDirectives (name to directive).
   * @param jsExprTranslator Instance of JsExprTranslator to use.
   * @param genCallCodeUtils Instance of GenCallCodeUtils to use.
   * @param isComputableAsJsExprsVisitor The IsComputableAsJsExprsVisitor used by this instance
   *     (when needed).
   * @param genJsExprsVisitorFactory Factory for creating an instance of GenJsExprsVisitor.
   * @param templateAliases A mapping for looking up the function name for a given fully
   *     qualified name.
   */
  @AssistedInject
  public GenJsExprsVisitor(
      Map<String, SoyJsSrcPrintDirective> soyJsSrcDirectivesMap,
      JsExprTranslator jsExprTranslator,
      GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      GenJsExprsVisitorFactory genJsExprsVisitorFactory,
      @Assisted TranslationContext translationContext,
      @Assisted ErrorReporter errorReporter,
      @Assisted TemplateAliases templateAliases) {
    this.soyJsSrcDirectivesMap = soyJsSrcDirectivesMap;
    this.jsExprTranslator = jsExprTranslator;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    this.genJsExprsVisitorFactory = genJsExprsVisitorFactory;

    this.translationContext = translationContext;
    this.errorReporter = errorReporter;
    this.templateAliases = templateAliases;
  }

  @Override public List<CodeChunk.WithValue> exec(SoyNode node) {
    Preconditions.checkArgument(isComputableAsJsExprsVisitor.exec(node));
    chunks = new ArrayList<>();
    visit(node);
    return chunks;
  }

  /**
   * Executes this visitor on the children of the given node, without visiting the given node
   * itself.
   */
  public List<CodeChunk.WithValue> execOnChildren(ParentSoyNode<?> node) {
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
    CodeChunk.WithValue expr =
        jsExprTranslator.translateToCodeChunk(node.getExpr(), translationContext, errorReporter);

    // Process directives.
    for (PrintDirectiveNode directiveNode : node.getChildren()) {

      // Get directive.
      SoyJsSrcPrintDirective directive = soyJsSrcDirectivesMap.get(directiveNode.getName());
      if (directive == null) {
        // TODO(lukes): this should be dead, delete it
        errorReporter.report(
            node.getSourceLocation(), UNKNOWN_SOY_JS_SRC_PRINT_DIRECTIVE, directiveNode.getName());
        return;
      }

      // Get directive args.
      List<ExprRootNode> argNodes = directiveNode.getArgs();
      if (!directive.getValidArgsSizes().contains(argNodes.size())) {
        // TODO(lukes): this should be dead, delete it
        errorReporter.report(
            node.getSourceLocation(),
            ARITY_MISMATCH,
            directiveNode.getName(),
            argNodes.size(),
            directive.getValidArgsSizes());
        return;
      }

      // Convert args to CodeChunks.
      List<CodeChunk.WithValue> argChunks = new ArrayList<>(argNodes.size());
      for (ExprRootNode argNode : argNodes) {
        argChunks.add(
            jsExprTranslator.translateToCodeChunk(argNode, translationContext, errorReporter));
      }

      // Apply directive.
      expr = SoyJsPluginUtils.applyDirective(
          translationContext.codeGenerator(),
          expr,
          directive,
          argChunks);
    }

    chunks.add(expr);
  }

  /**
   * Example:
   * <pre>
   *   {xid selected-option}
   * </pre>
   * might generate
   * <pre>
   *   xid('selected-option')
   * </pre>
   */
  @Override protected void visitXidNode(XidNode node) {
    chunks.add(XID.call(stringLiteral(node.getText())));
  }

  /**
   * Note: We would only see a CssNode if the css-handling scheme is BACKEND_SPECIFIC.
   * <p>
   * Example:
   * <pre>
   *   {css selected-option}
   * </pre>
   * might generate
   * <pre>
   *   goog.getCssName('selected-option')
   * </pre>
   * </p>
   */
  @Override protected void visitCssNode(CssNode node) {
    chunks.add(GOOG_GET_CSS_NAME.call(stringLiteral(node.getSelectorText())));
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

    List<CodeChunk.WithValue> ifs = new ArrayList<>();
    List<CodeChunk.WithValue> thens = new ArrayList<>();
    CodeChunk.WithValue trailingElse = null;

    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode ifCond = (IfCondNode) child;

        ifs.add(
            jsExprTranslator.translateToCodeChunk(
                ifCond.getExpr(), translationContext, errorReporter));
        thens.add(CodeChunkUtils.concatChunks(genJsExprsVisitor.exec(ifCond)));
      } else if (child instanceof IfElseNode) {
        trailingElse = CodeChunkUtils.concatChunks(genJsExprsVisitor.exec(child));
      } else {
        throw new AssertionError();
      }
    }

    Preconditions.checkState(ifs.size() == thens.size());

    ConditionalExpressionBuilder builder = CodeChunk.ifExpression(ifs.get(0), thens.get(0));

    for (int i = 1; i < ifs.size(); i++) {
      builder.elseif_(ifs.get(i), thens.get(i));
    }

    CodeChunk.WithValue ifChunk =
        trailingElse != null
            ? builder.else_(trailingElse).build(generator)
            : builder.else_(LITERAL_EMPTY_STRING).build(generator);

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
    CodeChunk.WithValue call =
        genCallCodeUtils.gen(node, templateAliases, translationContext, errorReporter);
    chunks.add(call);
  }

  @Override protected void visitCallParamContentNode(CallParamContentNode node) {
    visitChildren(node);
  }
}
