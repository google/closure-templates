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

import com.google.common.base.Preconditions;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
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
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Visitor for generating JS expressions for parse tree nodes.
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 */
public class GenJsExprsVisitor extends AbstractSoyNodeVisitor<List<JsExpr>> {

  /**
   * Injectable factory for creating an instance of this class.
   */
  public interface GenJsExprsVisitorFactory {

    /**
     * @param localVarTranslations The current stack of replacement JS expressions for the local
     *     variables (and foreach-loop special functions) current in scope.
     * @param templateAliases A mapping for looking up the function name for a given fully
     *     qualified name.
     */
    GenJsExprsVisitor create(
        Deque<Map<String, JsExpr>> localVarTranslations,
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
  private final ErrorReporter errorReporter;

  /**
   * The current stack of replacement JS expressions for the local variables (and foreach-loop
   * special functions) current in scope.
   */
  private final Deque<Map<String, JsExpr>> localVarTranslations;

  /** List to collect the results. */
  protected List<JsExpr> jsExprs;

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
   * @param errorReporter For reporting errors.
   * @param localVarTranslations The current stack of replacement JS expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
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
      @Assisted ErrorReporter errorReporter,
      @Assisted Deque<Map<String, JsExpr>> localVarTranslations,
      @Assisted TemplateAliases templateAliases) {
    this.errorReporter = errorReporter;
    this.soyJsSrcDirectivesMap = soyJsSrcDirectivesMap;
    this.jsExprTranslator = jsExprTranslator;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    this.genJsExprsVisitorFactory = genJsExprsVisitorFactory;
    this.localVarTranslations = localVarTranslations;
    this.templateAliases = templateAliases;
  }

  @Override public List<JsExpr> exec(SoyNode node) {
    Preconditions.checkArgument(isComputableAsJsExprsVisitor.exec(node));
    jsExprs = new ArrayList<>();
    visit(node);
    return jsExprs;
  }

  /**
   * Executes this visitor on the children of the given node, without visiting the given node
   * itself.
   */
  public List<JsExpr> execOnChildren(ParentSoyNode<?> node) {
    Preconditions.checkArgument(isComputableAsJsExprsVisitor.execOnChildren(node));
    jsExprs = new ArrayList<>();
    visitChildren(node);
    return jsExprs;
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

    // Escape non-ASCII characters since browsers are inconsistent in how they interpret utf-8 in
    // JS source files.
    String exprText = BaseUtils.escapeToSoyString(node.getRawText(), true);
    // Note: </script> in a JavaScript string will end the current script tag
    // in most browsers.  Escape the forward slash in the string to get around
    // this issue.
    exprText = exprText.replace("</script>", "<\\/script>");
    jsExprs.add(new JsExpr(exprText, Integer.MAX_VALUE));
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

    JsExpr jsExpr =
        jsExprTranslator.translateToJsExpr(
            node.getExprUnion(), localVarTranslations, errorReporter);

    // Process directives.
    for (PrintDirectiveNode directiveNode : node.getChildren()) {

      // Get directive.
      SoyJsSrcPrintDirective directive = soyJsSrcDirectivesMap.get(directiveNode.getName());
      if (directive == null) {
        errorReporter.report(
            node.getSourceLocation(), UNKNOWN_SOY_JS_SRC_PRINT_DIRECTIVE, directiveNode.getName());
        return;
      }

      // Get directive args.
      List<ExprRootNode> args = directiveNode.getArgs();
      if (!directive.getValidArgsSizes().contains(args.size())) {
        errorReporter.report(
            node.getSourceLocation(),
            ARITY_MISMATCH,
            directiveNode.getName(),
            args.size(),
            directive.getValidArgsSizes());
        return;
      }

      // Translate directive args.
      List<JsExpr> argsJsExprs = new ArrayList<>(args.size());
      for (ExprRootNode arg : args) {
        argsJsExprs.add(
            jsExprTranslator.translateToJsExpr(arg, localVarTranslations, errorReporter));
      }

      // Apply directive.
      jsExpr = directive.applyForJsSrc(jsExpr, argsJsExprs);
    }

    jsExprs.add(jsExpr);
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
    String xid = node.getText();
    String js = "xid('" + xid + "')";
    jsExprs.add(new JsExpr(js, Integer.MAX_VALUE));
  }

  /**
   * Note: We would only see a CssNode if the css-handling scheme is BACKEND_SPECIFIC.
   * <p>
   * Example:
   * <pre>
   *   {css selected-option}
   *   {css $foo, bar}
   * </pre>
   * might generate
   * <pre>
   *   goog.getCssName('selected-option')
   *   goog.getCssName(opt_data.foo, 'bar')
   * </pre>
   * </p>
   */
  @Override protected void visitCssNode(CssNode node) {

    StringBuilder sb = new StringBuilder();
    sb.append("goog.getCssName(");

    ExprRootNode componentNameExpr = node.getComponentNameExpr();
    if (componentNameExpr != null) {
      JsExpr baseJsExpr =
          jsExprTranslator.translateToJsExpr(
              componentNameExpr, localVarTranslations, errorReporter);
      sb.append(baseJsExpr.getText()).append(", ");
    }

    sb.append('\'').append(node.getSelectorText()).append("')");

    jsExprs.add(new JsExpr(sb.toString(), Integer.MAX_VALUE));
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
        genJsExprsVisitorFactory.create(localVarTranslations, templateAliases, errorReporter);

    StringBuilder jsExprTextSb = new StringBuilder();

    boolean hasElse = false;
    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;

        JsExpr condJsExpr =
            jsExprTranslator.translateToJsExpr(
                icn.getExprUnion(), localVarTranslations, errorReporter);
        jsExprTextSb.append('(').append(condJsExpr.getText()).append(") ? ");

        List<JsExpr> condBlockJsExprs = genJsExprsVisitor.exec(icn);
        jsExprTextSb.append(JsExprUtils.concatJsExprs(condBlockJsExprs).getText());

        jsExprTextSb.append(" : ");

      } else if (child instanceof IfElseNode) {
        hasElse = true;
        IfElseNode ien = (IfElseNode) child;

        List<JsExpr> elseBlockJsExprs = genJsExprsVisitor.exec(ien);
        jsExprTextSb.append(JsExprUtils.concatJsExprs(elseBlockJsExprs).getText());

      } else {
        throw new AssertionError();
      }
    }

    if (!hasElse) {
      jsExprTextSb.append("''");
    }

    jsExprs.add(new JsExpr(jsExprTextSb.toString(), Operator.CONDITIONAL.getPrecedence()));
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
    jsExprs.add(
        genCallCodeUtils.genCallExpr(node, localVarTranslations, templateAliases, errorReporter));
  }

  @Override protected void visitCallParamContentNode(CallParamContentNode node) {
    visitChildren(node);
  }
}
