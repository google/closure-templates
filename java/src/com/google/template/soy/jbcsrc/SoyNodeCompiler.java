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

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.template.soy.jbcsrc.BytecodeUtils.compareSoyEquals;
import static com.google.template.soy.jbcsrc.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.Statement.NULL_STATEMENT;
import static com.google.template.soy.jbcsrc.Statement.concat;

import com.google.common.base.Optional;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.jbcsrc.ControlFlow.IfBlock;
import com.google.template.soy.jbcsrc.SoyExpression.LocalVariableBinding;
import com.google.template.soy.jbcsrc.VariableSet.Scope;
import com.google.template.soy.jbcsrc.VariableSet.Variable;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.XidNode;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Comiles {@link SoyNode soy nodes} into {@link Statement statements}.
 */
final class SoyNodeCompiler extends AbstractReturningSoyNodeVisitor<Statement> {
  // TODO(lukes): Add support for calling the softLimitReached method (and detaching) after writing
  // to the appendable
  private final VariableSet variables;
  private final Expression appendableExpression;
  private final Expression contextExpression;
  private final ExpressionCompiler exprCompiler;

  SoyNodeCompiler(VariableSet variables, Expression appendableExpression, 
      Expression contextExpression, ExpressionCompiler exprCompiler) {
    appendableExpression.checkType(Type.getType(AdvisingAppendable.class));
    contextExpression.checkType(Type.getType(RenderContext.class));
    this.variables = variables;
    this.appendableExpression = appendableExpression;
    this.contextExpression = contextExpression;
    this.exprCompiler = checkNotNull(exprCompiler);
  }

  Statement compile(TemplateBasicNode node) {
    return visit(node);
  }

  @Override protected Statement visitTemplateBasicNode(TemplateNode node) {
    // TODO(lukes): the start of a template should include a jump table for reattaching
    return childrenAsStatement(node);
  }

  private Statement childrenAsStatement(ParentSoyNode<? extends SoyNode> node) {
    return Statement.concat(visitChildren(node)).withSourceLocation(node.getSourceLocation());
  }

  @Override protected Statement visitIfNode(IfNode node) {
    List<IfBlock> ifs = new ArrayList<>();
    Optional<Statement> elseBlock = Optional.absent();
    for (SoyNode child : node.getChildren()) {
      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;
        SoyExpression cond = 
            exprCompiler.compile(icn.getExprUnion().getExpr()).convert(boolean.class);
        Statement block = childrenAsStatement(icn);
        ifs.add(IfBlock.create(cond, block));
      } else {
        IfElseNode ien = (IfElseNode) child;
        elseBlock = Optional.of(childrenAsStatement(ien));
      }
    }
    return ControlFlow.ifElseChain(ifs, elseBlock).withSourceLocation(node.getSourceLocation());
  }
  
  @Override protected Statement visitSwitchNode(SwitchNode node) {
    SoyExpression expression = exprCompiler.compile(node.getExpr());
    Label start = new Label();
    Label end = new Label();
    Statement init;
    List<IfBlock> cases = new ArrayList<>();
    Optional<Statement> defaultBlock = Optional.absent();
    Scope scope = variables.enterScope();
    Variable variable = scope.createSynthetic("switchVar", expression.resultType(), start, end);
    LocalVariableBinding binding = expression.createBinding(variable.local(), start);
    init = binding.initializer();
    expression = binding.accessor();

    for (SoyNode child : node.getChildren()) {
      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode caseNode = (SwitchCaseNode) child;
        List<Expression> comparisons = new ArrayList<>();
        for (ExprRootNode<?> caseExpr : caseNode.getExprList()) {
          comparisons.add(compareSoyEquals(expression, exprCompiler.compile(caseExpr)));
        }
        Statement block = childrenAsStatement(caseNode);
        cases.add(IfBlock.create(BytecodeUtils.logicalOr(comparisons), block));
      } else {
        SwitchDefaultNode defaultNode = (SwitchDefaultNode) child;
        defaultBlock = Optional.of(childrenAsStatement(defaultNode));
      }
    }
    Statement exitScope = scope.exitScope();

    // Soy allows arbitrary expressions to appear in {case} statements within a {switch}.
    // Java/C, by contrast, only allow some constant expressions in cases.
    // TODO(lukes): in practice the case statements are often constant strings/ints.  If everything
    // is typed to int/string we should consider implementing via the tableswitch/lookupswitch
    // instruction which would be way way way faster.  cglib has some helpers for string switch
    // generation that we could maybe use
    return Statement.concat(init, ControlFlow.ifElseChain(cases, defaultBlock), exitScope)
        .withSourceLocation(node.getSourceLocation());
  }

  @Override protected Statement visitPrintNode(PrintNode node) {
    if (!node.getChildren().isEmpty()) {
      throw new UnsupportedOperationException(
          "The jbcsrc implementation does not support print directives (yet!): "
              + node.toSourceString());
    }
    SoyExpression printExpr = exprCompiler.compile(node.getExprUnion().getExpr());
    return MethodRef.SOY_VALUE_RENDER
        .invokeVoid(printExpr.box(), appendableExpression)
        .withSourceLocation(node.getSourceLocation());
  }

  @Override protected Statement visitRawTextNode(RawTextNode node) {
    return MethodRef.ADVISING_APPENDABLE_APPEND
        .invoke(appendableExpression, constant(node.getRawText()))
        .toStatement()
        .withSourceLocation(node.getSourceLocation());
  }

  @Override protected Statement visitDebuggerNode(DebuggerNode node) {
    // intentional no-op.  java has no 'breakpoint' equivalent.  But we can add a label + line
    // number.  Which may be useful for debugging :)
    return NULL_STATEMENT.withSourceLocation(node.getSourceLocation());
  }

  @Override protected Statement visitXidNode(XidNode node) {
    Expression rename = MethodRef.RENDER_CONTEXT_RENAME_XID
        .invoke(contextExpression, constant(node.getText()));
    return MethodRef.ADVISING_APPENDABLE_APPEND.invoke(appendableExpression, rename)
        .toStatement()
        .withSourceLocation(node.getSourceLocation());
  }

  // TODO(lukes):  The RenderVisitor optimizes css/xid renaming by stashing a one element cache in
  // the CSS node itself (keyed off the identity of the renaming map).  We could easily add such
  // an optimization via a static field in the Template class. Though im not sure it makes sense
  // as an optimization... this should just be an immutable map lookup keyed off of a constant
  // string. If we cared a lot, we could employ a simpler (and more compact) optimization by
  // assigning each selector a unique integer id and then instead of hashing we can just reference
  // an array (aka perfect hashing).  This could be part of our runtime library and ids could be
  // assigned at startup.

  @Override protected Statement visitCssNode(CssNode node) {
    Expression renameSelector = MethodRef.RENDER_CONTEXT_RENAME_CSS_SELECTOR
        .invoke(contextExpression, constant(node.getSelectorText()));
    Statement selectorStatement = MethodRef.ADVISING_APPENDABLE_APPEND
        .invoke(appendableExpression, renameSelector)
        .toStatement();

    if (node.getComponentNameExpr() != null) {
      return concat(
          MethodRef.SOY_VALUE_RENDER.invokeVoid(
              exprCompiler.compile(node.getComponentNameExpr()).box(), 
              appendableExpression), 
         MethodRef.ADVISING_APPENDABLE_APPEND_CHAR
            .invoke(appendableExpression, constant('-'))
            .toStatement(), 
         selectorStatement);
    }
    return selectorStatement;
  }

  @Override protected Statement visitLogNode(LogNode node) {
    SoyNodeCompiler loggerCompiler =
        new SoyNodeCompiler(variables, 
            MethodRef.RUNTIME_LOGGER.invoke(),
            contextExpression, 
            exprCompiler);
    return concat(loggerCompiler.visitChildren(node)).withSourceLocation(node.getSourceLocation());
  }

  @Override protected Statement visitSoyNode(SoyNode node) {
    throw new UnsupportedOperationException(
        "The jbcsrc backend doesn't support: " + node.getKind() + " nodes yet.");
  }
}
