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
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Visitor for generating Python expressions for parse tree nodes.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class GenPyExprsVisitor extends AbstractSoyNodeVisitor<List<PyExpr>> {
  private static final SoyErrorKind UNKNOWN_SOY_PY_SRC_PRINT_DIRECTIVE =
      SoyErrorKind.of("Unknown SoyPySrcPrintDirective ''{0}''.");

  /** Injectable factory for creating an instance of this class. */
  public static final class GenPyExprsVisitorFactory {
    private final IsComputableAsPyExprVisitor isComputableAsPyExprVisitor;
    private final PythonValueFactoryImpl pluginValueFactory;
    // depend on a Supplier since there is a circular dependency between GenPyExprsVisitorFactory
    // and GenPyCallExprVisitor
    private final Supplier<GenPyCallExprVisitor> genPyCallExprVisitor;

    GenPyExprsVisitorFactory(
        IsComputableAsPyExprVisitor isComputableAsPyExprVisitor,
        PythonValueFactoryImpl pluginValueFactory,
        Supplier<GenPyCallExprVisitor> genPyCallExprVisitor) {
      this.isComputableAsPyExprVisitor = isComputableAsPyExprVisitor;
      this.pluginValueFactory = pluginValueFactory;
      this.genPyCallExprVisitor = genPyCallExprVisitor;
    }

    public GenPyExprsVisitor create(LocalVariableStack localVarExprs, ErrorReporter errorReporter) {
      return new GenPyExprsVisitor(
          isComputableAsPyExprVisitor,
          this,
          genPyCallExprVisitor.get(),
          pluginValueFactory,
          localVarExprs,
          errorReporter);
    }
  }

  private final IsComputableAsPyExprVisitor isComputableAsPyExprVisitor;

  private final GenPyExprsVisitorFactory genPyExprsVisitorFactory;

  private final PythonValueFactoryImpl pluginValueFactory;

  private final GenPyCallExprVisitor genPyCallExprVisitor;

  private final LocalVariableStack localVarExprs;

  /** List to collect the results. */
  private List<PyExpr> pyExprs;

  private final ErrorReporter errorReporter;

  GenPyExprsVisitor(
      IsComputableAsPyExprVisitor isComputableAsPyExprVisitor,
      GenPyExprsVisitorFactory genPyExprsVisitorFactory,
      GenPyCallExprVisitor genPyCallExprVisitor,
      PythonValueFactoryImpl pluginValueFactory,
      LocalVariableStack localVarExprs,
      ErrorReporter errorReporter) {
    this.isComputableAsPyExprVisitor = isComputableAsPyExprVisitor;
    this.genPyExprsVisitorFactory = genPyExprsVisitorFactory;
    this.genPyCallExprVisitor = genPyCallExprVisitor;
    this.pluginValueFactory = pluginValueFactory;
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
    String exprText = BaseUtils.escapeToSoyString(node.getRawText(), false, QuoteStyle.SINGLE);
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
        new TranslateToPyExprVisitor(localVarExprs, pluginValueFactory, errorReporter);

    PyExpr pyExpr = translator.exec(node.getExpr());

    // Process directives.
    for (PrintDirectiveNode directiveNode : node.getChildren()) {

      // Get directive.
      SoyPrintDirective directive = directiveNode.getPrintDirective();
      if (!(directive instanceof SoyPySrcPrintDirective)) {
        errorReporter.report(
            directiveNode.getSourceLocation(),
            UNKNOWN_SOY_PY_SRC_PRINT_DIRECTIVE,
            directiveNode.getName());
        continue;
      }

      // Get directive args.
      List<ExprRootNode> args = directiveNode.getArgs();
      // Translate directive args.
      List<PyExpr> argsPyExprs = new ArrayList<>(args.size());
      for (ExprRootNode arg : args) {
        argsPyExprs.add(translator.exec(arg));
      }

      // Apply directive.
      pyExpr = ((SoyPySrcPrintDirective) directive).applyForPySrc(pyExpr, argsPyExprs);
    }

    pyExprs.add(pyExpr);
  }

  @Override
  protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    PyExpr msg = generateMsgFunc(node.getMsg());

    // MsgFallbackGroupNode could only have one child or two children. See MsgFallbackGroupNode.
    if (node.hasFallbackMsg()) {
      StringBuilder pyExprTextSb = new StringBuilder();
      PyExpr fallbackMsg = generateMsgFunc(node.getFallbackMsg());

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
    for (SoyPrintDirective directive : node.getEscapingDirectives()) {
      Preconditions.checkState(
          directive instanceof SoyPySrcPrintDirective,
          "Contextual autoescaping produced a bogus directive: %s",
          directive.getName());
      msg = ((SoyPySrcPrintDirective) directive).applyForPySrc(msg, ImmutableList.<PyExpr>of());
    }
    pyExprs.add(msg);
  }

  private PyStringExpr generateMsgFunc(MsgNode msg) {
    return new MsgFuncGenerator(
            genPyExprsVisitorFactory, pluginValueFactory, msg, localVarExprs, errorReporter)
        .getPyExpr();
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
        new TranslateToPyExprVisitor(localVarExprs, pluginValueFactory, errorReporter);

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
