/*
 * Copyright 2025 Google Inc.
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

package com.google.template.soy.sharedpasses.render;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyAbstractCachingValueProvider;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.shared.internal.SharedRuntime;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AssignmentNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.JavaImplNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.ReturnNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.defn.FunctionParam;
import java.util.Iterator;
import java.util.function.Consumer;

/** Executes a Soy function block, e.g. a {@link JavaImplNode}. */
class FunctionVisitor extends AbstractSoyNodeVisitor<Void> {

  @FunctionalInterface
  interface EvalVisitorFactory {
    EvalVisitor build(Environment env);
  }

  private final JavaImplNode java;
  private final Environment env;
  private final EvalVisitorFactory evalVisitorFactory;

  private EvalVisitor exprVisitor;
  private SoyValue result;

  public FunctionVisitor(
      JavaImplNode java,
      Environment fileScopeEnv,
      ImmutableList<SoyValue> args,
      EvalVisitorFactory evalVisitorFactory) {
    this.java = java;
    this.env = fileScopeEnv.fork();
    this.evalVisitorFactory = evalVisitorFactory;

    ImmutableList<FunctionParam> externParams = java.getParent().getParamVars();
    Preconditions.checkArgument(externParams.size() == args.size());
    for (int i = 0; i < externParams.size(); i++) {
      this.env.bind(externParams.get(i), args.get(i));
    }
  }

  public SoyValue eval() {
    visit(java);
    return result;
  }

  @Override
  protected void visitLetValueNode(LetValueNode node) {
    env.bind(node.getVar(), lazyEval(node.getExpr(), node));
  }

  @Override
  protected void visitAssignmentNode(AssignmentNode node) {
    VarRefNode varRefNode = (VarRefNode) node.getLhs().getRoot();
    env.bind(varRefNode.getDefnDecl(), eval(node.getRhs(), node));
  }

  @Override
  protected void visitReturnNode(ReturnNode node) {
    Preconditions.checkState(result == null);
    result = eval(node.getExpr(), node);
  }

  @Override
  protected void visitIfNode(IfNode node) {
    for (SoyNode child : node.getChildren()) {
      if (result != null) {
        return;
      }
      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;
        if (eval(icn.getExpr(), node).coerceToBoolean()) {
          visit(icn);
          return;
        }
      } else if (child instanceof IfElseNode) {
        visit(child);
        return;
      } else {
        throw new AssertionError();
      }
    }
  }

  @Override
  protected void visitForNode(ForNode node) {
    SoyValue dataRefValue = eval(node.getExpr(), node);
    Iterator<? extends SoyValueProvider> it = dataRefValue.javaIterator();
    ForNonemptyNode child = (ForNonemptyNode) node.getChild(0);
    while (it.hasNext()) {
      SoyValueProvider value = it.next();
      env.bindLoopPosition(child.getVar(), value);
      visitChildren(child);
    }
  }

  @Override
  protected void visitSwitchNode(SwitchNode node) {
    SoyValue switchValue = eval(node.getExpr(), node);

    for (SoyNode child : node.getChildren()) {
      if (result != null) {
        return;
      }
      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode scn = (SwitchCaseNode) child;
        for (ExprNode caseExpr : scn.getExprList()) {
          if (SharedRuntime.switchCaseEqual(switchValue, eval(caseExpr, scn))) {
            visit(scn);
            return;
          }
        }
      } else if (child instanceof SwitchDefaultNode) {
        visit(child);
        return;
      } else {
        throw new AssertionError();
      }
    }
  }

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

  @Override
  protected void visitChildren(ParentSoyNode<?> node) {
    visitChildren(node, this::visit);
  }

  private <T extends SoyNode> void visitChildren(ParentSoyNode<T> parent, Consumer<T> work) {
    for (T child : parent.getChildren()) {
      if (result != null) {
        return;
      }
      work.accept(child);
    }
  }

  private SoyValueProvider lazyEval(ExprNode expr, SoyNode node) {
    return new SoyAbstractCachingValueProvider() {
      @Override
      protected SoyValue compute() {
        return eval(expr, node);
      }

      @Override
      public RenderResult status() {
        return RenderResult.done();
      }
    };
  }

  private SoyValue eval(ExprNode expr, SoyNode node) {
    // Lazily initialize evalVisitor.
    if (exprVisitor == null) {
      exprVisitor = evalVisitorFactory.build(env);
    }

    try {
      return exprVisitor.exec(expr);
    } catch (RenderException e) {
      // RenderExceptions can be thrown when evaluating lazy transclusions.
      throw RenderException.createFromRenderException(
          "When evaluating \"" + expr.toSourceString() + "\": " + e.getMessage(), e, node);
    } catch (Exception e) {
      throw RenderException.createWithSource(
          "When evaluating \"" + expr.toSourceString() + "\": " + e.getMessage(), e, node);
    }
  }
}
