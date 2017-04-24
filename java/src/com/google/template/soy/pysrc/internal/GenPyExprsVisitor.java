/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.pysrc.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.LegacyInternalSyntaxException;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.pysrc.internal.MsgFuncGenerator.MsgFuncGeneratorFactory;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.XidNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Visitor for generating Python expressions for parse tree nodes.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class GenPyExprsVisitor extends AbstractSoyNodeVisitor<List<PyExpr>> {

  /** Injectable factory for creating an instance of this class. */
  public static interface GenPyExprsVisitorFactory {
    public GenPyExprsVisitor create(LocalVariableStack localVarExprs, ErrorReporter errorReporter);
  }

  /** Map of all SoyPySrcPrintDirectives (name to directive). */
  private Map<String, SoyPySrcPrintDirective> soyPySrcDirectivesMap;

  private final IsComputableAsPyExprVisitor isComputableAsPyExprVisitor;

  private final GenPyExprsVisitorFactory genPyExprsVisitorFactory;

  private final GenPyCallExprVisitor genPyCallExprVisitor;

  private final MsgFuncGeneratorFactory msgFuncGeneratorFactory;

  private final LocalVariableStack localVarExprs;

  /** List to collect the results. */
  private List<PyExpr> pyExprs;

  private final ErrorReporter errorReporter;

  /** @param soyPySrcDirectivesMap Map of all SoyPySrcPrintDirectives (name to directive). */
  @AssistedInject
  GenPyExprsVisitor(
      ImmutableMap<String, SoyPySrcPrintDirective> soyPySrcDirectivesMap,
      IsComputableAsPyExprVisitor isComputableAsPyExprVisitor,
      GenPyExprsVisitorFactory genPyExprsVisitorFactory,
      MsgFuncGeneratorFactory msgFuncGeneratorFactory,
      GenPyCallExprVisitor genPyCallExprVisitor,
      @Assisted LocalVariableStack localVarExprs,
      @Assisted ErrorReporter errorReporter) {
    this.soyPySrcDirectivesMap = soyPySrcDirectivesMap;
    this.isComputableAsPyExprVisitor = isComputableAsPyExprVisitor;
    this.genPyExprsVisitorFactory = genPyExprsVisitorFactory;
    this.genPyCallExprVisitor = genPyCallExprVisitor;
    this.msgFuncGeneratorFactory = msgFuncGeneratorFactory;
    this.localVarExprs = localVarExprs;
    this.errorReporter = errorReporter;
  }

  @Override
  public List<PyExpr> exec(SoyNode node) {
    Preconditions.checkArgument(isComputableAsPyExprVisitor.exec(node));
    pyExprs = new ArrayList<>();
    visit(node);
    return pyExprs;
  }

  /**
   * Executes this visitor on the children of the given node, without visiting the given node
   * itself.
   */
  List<PyExpr> execOnChildren(ParentSoyNode<?> node) {
    Preconditions.checkArgument(isComputableAsPyExprVisitor.execOnChildren(node));
    pyExprs = new ArrayList<>();
    visitChildren(node);
    return pyExprs;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  /**
   * Example:
   *
   * <pre>
   *   I'm feeling lucky!
   * </pre>
   *
   * generates
   *
   * <pre>
   *   'I\'m feeling lucky!'
   * </pre>
   */
  @Override
  protected void visitRawTextNode(RawTextNode node) {
    // Escape special characters in the text before writing as a string.
    String exprText = BaseUtils.escapeToSoyString(node.getRawText(), false);
    pyExprs.add(new PyStringExpr(exprText));
  }

  /**
   * Visiting a print node accomplishes 3 basic tasks. It loads data, it performs any operations
   * needed, and it executes the appropriate print directives.
   *
   * <p>TODO(dcphillips): Add support for local variables once LetNode are supported.
   *
   * <p>Example:
   *
   * <pre>
   *   {$boo |changeNewlineToBr}
   *   {$goo + 5}
   * </pre>
   *
   * might generate
   *
   * <pre>
   *   sanitize.change_newline_to_br(data.get('boo'))
   *   data.get('goo') + 5
   * </pre>
   */
  @Override
  protected void visitPrintNode(PrintNode node) {
    TranslateToPyExprVisitor translator =
        new TranslateToPyExprVisitor(localVarExprs, errorReporter);

    PyExpr pyExpr = translator.exec(node.getExpr());

    // Process directives.
    for (PrintDirectiveNode directiveNode : node.getChildren()) {

      // Get directive.
      SoyPySrcPrintDirective directive = soyPySrcDirectivesMap.get(directiveNode.getName());
      if (directive == null) {
        throw LegacyInternalSyntaxException.createWithMetaInfo(
            "Failed to find SoyPySrcPrintDirective with name '"
                + directiveNode.getName()
                + "'"
                + " (tag "
                + node.toSourceString()
                + ")",
            directiveNode.getSourceLocation());
      }

      // Get directive args.
      List<ExprRootNode> args = directiveNode.getArgs();
      if (!directive.getValidArgsSizes().contains(args.size())) {
        throw LegacyInternalSyntaxException.createWithMetaInfo(
            "Print directive '"
                + directiveNode.getName()
                + "' used with the wrong number of"
                + " arguments (tag "
                + node.toSourceString()
                + ").",
            directiveNode.getSourceLocation());
      }

      // Translate directive args.
      List<PyExpr> argsPyExprs = new ArrayList<>(args.size());
      for (ExprRootNode arg : args) {
        argsPyExprs.add(translator.exec(arg));
      }

      // Apply directive.
      pyExpr = directive.applyForPySrc(pyExpr, argsPyExprs);
    }

    pyExprs.add(pyExpr);
  }

  @Override
  protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    PyExpr msg =
        msgFuncGeneratorFactory.create(node.getMsg(), localVarExprs, errorReporter).getPyExpr();

    // MsgFallbackGroupNode could only have one child or two children. See MsgFallbackGroupNode.
    if (node.hasFallbackMsg()) {
      StringBuilder pyExprTextSb = new StringBuilder();
      PyExpr fallbackMsg =
          msgFuncGeneratorFactory
              .create(node.getFallbackMsg(), localVarExprs, errorReporter)
              .getPyExpr();

      // Build Python ternary expression: a if cond else c
      pyExprTextSb.append(msg.getText()).append(" if ");

      // The fallback message is only used if the first message is not available, but the fallback
      // is. So availability of both messages must be tested.
      long firstId = MsgUtils.computeMsgIdForDualFormat(node.getMsg());
      long secondId = MsgUtils.computeMsgIdForDualFormat(node.getFallbackMsg());
      pyExprTextSb
          .append(PyExprUtils.TRANSLATOR_NAME)
          .append(".is_msg_available(")
          .append(firstId)
          .append(")")
          .append(" or not ")
          .append(PyExprUtils.TRANSLATOR_NAME)
          .append(".is_msg_available(")
          .append(secondId)
          .append(")");

      pyExprTextSb.append(" else ").append(fallbackMsg.getText());
      msg =
          new PyStringExpr(
              pyExprTextSb.toString(), PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL));
    }

    // Escaping directives apply to messages, especially in attribute context.
    for (String directiveName : node.getEscapingDirectiveNames()) {
      SoyPySrcPrintDirective directive = soyPySrcDirectivesMap.get(directiveName);
      Preconditions.checkNotNull(
          directive, "Contextual autoescaping produced a bogus directive: %s", directiveName);
      msg = directive.applyForPySrc(msg, ImmutableList.<PyExpr>of());
    }
    pyExprs.add(msg);
  }

  @Override
  protected void visitXidNode(XidNode node) {
    StringBuilder sb =
        new StringBuilder("runtime.get_xid_name('").append(node.getText()).append("')");
    pyExprs.add(new PyExpr(sb.toString(), Integer.MAX_VALUE));
  }

  @Override
  protected void visitCssNode(CssNode node) {
    StringBuilder sb = new StringBuilder("runtime.get_css_name(");

    ExprRootNode componentNameExpr = node.getComponentNameExpr();
    if (componentNameExpr != null) {
      TranslateToPyExprVisitor translator =
          new TranslateToPyExprVisitor(localVarExprs, errorReporter);
      PyExpr basePyExpr = translator.exec(componentNameExpr);
      sb.append(basePyExpr.getText()).append(", ");
    }

    sb.append("'").append(node.getSelectorText()).append("')");
    pyExprs.add(new PyExpr(sb.toString(), Integer.MAX_VALUE));
  }

  /**
   * If all the children are computable as expressions, the IfNode can be written as a ternary
   * conditional expression.
   */
  @Override
  protected void visitIfNode(IfNode node) {
    // Create another instance of this visitor for generating Python expressions from children.
    GenPyExprsVisitor genPyExprsVisitor =
        genPyExprsVisitorFactory.create(localVarExprs, errorReporter);
    TranslateToPyExprVisitor translator =
        new TranslateToPyExprVisitor(localVarExprs, errorReporter);

    StringBuilder pyExprTextSb = new StringBuilder();

    boolean hasElse = false;
    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;

        // Python ternary conditional expressions modify the order of the conditional from
        // <conditional> ? <true> : <false> to
        // <true> if <conditional> else <false>
        PyExpr condBlock = PyExprUtils.concatPyExprs(genPyExprsVisitor.exec(icn)).toPyString();
        condBlock =
            PyExprUtils.maybeProtect(
                condBlock, PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL));
        pyExprTextSb.append(condBlock.getText());

        // Append the conditional and if/else syntax.
        PyExpr condPyExpr = translator.exec(icn.getExpr());
        pyExprTextSb.append(" if ").append(condPyExpr.getText()).append(" else ");

      } else if (child instanceof IfElseNode) {
        hasElse = true;
        IfElseNode ien = (IfElseNode) child;

        PyExpr elseBlock = PyExprUtils.concatPyExprs(genPyExprsVisitor.exec(ien)).toPyString();
        pyExprTextSb.append(elseBlock.getText());
      } else {
        throw new AssertionError("Unexpected if child node type. Child: " + child);
      }
    }

    if (!hasElse) {
      pyExprTextSb.append("''");
    }

    // By their nature, inline'd conditionals can only contain output strings, so they can be
    // treated as a string type with a conditional precedence.
    pyExprs.add(
        new PyStringExpr(
            pyExprTextSb.toString(), PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  @Override
  protected void visitIfCondNode(IfCondNode node) {
    visitChildren(node);
  }

  @Override
  protected void visitIfElseNode(IfElseNode node) {
    visitChildren(node);
  }

  @Override
  protected void visitCallNode(CallNode node) {
    pyExprs.add(genPyCallExprVisitor.exec(node, localVarExprs, errorReporter));
  }

  @Override
  protected void visitCallParamContentNode(CallParamContentNode node) {
    visitChildren(node);
  }
}
