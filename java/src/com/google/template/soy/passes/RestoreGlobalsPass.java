/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.passes;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.Point;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.exprtree.CallableExprBuilder;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.passes.LocalVariablesNodeVisitor.ExprVisitor;
import com.google.template.soy.soytree.SoyFileNode;

/**
 * Converts any VARREF node whose name does not exist in variable scope back into a global node.
 * This simulates earlier versions of the parser, which distinguished between dollar idents and
 * non-dollar idents.
 */
public final class RestoreGlobalsPass implements CompilerFilePass {

  private final LocalVariablesNodeVisitor nodeVisitor;

  public RestoreGlobalsPass() {
    Visitor exprVisitor = new Visitor();
    nodeVisitor =
        new LocalVariablesNodeVisitor(
            new LocalVariablesNodeVisitor.NodeVisitor() {
              @Override
              protected ExprVisitor getExprVisitor() {
                return exprVisitor;
              }
            });
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    nodeVisitor.exec(file);
  }

  private static class Visitor extends LocalVariablesNodeVisitor.ExprVisitor {
    @Override
    protected void visitMethodCallNode(MethodCallNode node) {
      visitChildrenAllowingConcurrentModification(node);
    }

    @Override
    protected void visitFieldAccessNode(FieldAccessNode node) {
      visitChildrenAllowingConcurrentModification(node);
    }

    @Override
    protected void visitVarRefNode(VarRefNode varRef) {
      if (varRef.getDefnDecl() != null) {
        return;
      }

      boolean isOriginalVar = varRef.getName().startsWith("$");
      if (isOriginalVar) {
        return;
      }

      if (getLocalVariables().lookup(varRef.getName()) != null) {
        return;
      }

      // Turn all unresolved VAR_REF + (FIELD_ACCESS)* back into global nodes.
      ExprNode node = varRef;
      while (node.getParent() != null) {
        if (node.getParent().getKind() == Kind.FIELD_ACCESS_NODE
            && !((FieldAccessNode) node.getParent()).isNullSafe()) {
          node = node.getParent();
        } else {
          break;
        }
      }

      if (node.getParent().getKind() == Kind.METHOD_CALL_NODE
          && !((MethodCallNode) node.getParent()).isNullSafe()
          && node.getParent().getChildIndex(node) == 0) {
        // This method call is actually a function call with a dotted identifier.
        MethodCallNode methodNode = (MethodCallNode) node.getParent();
        String fullName = node.toSourceString() + "." + methodNode.getMethodName().identifier();
        FunctionNode functionNode =
            CallableExprBuilder.builder(methodNode)
                .setTarget(null)
                .setIdentifier(
                    Identifier.create(
                        fullName,
                        union(varRef.getSourceLocation(), methodNode.getMethodName().location())))
                .setSourceLocation(
                    union(varRef.getSourceLocation(), methodNode.getSourceLocation()))
                .buildFunction();
        methodNode.getParent().replaceChild(methodNode, functionNode);
      } else {
        GlobalNode globalNode =
            new GlobalNode(
                Identifier.create(
                    node.toSourceString(),
                    union(varRef.getSourceLocation(), node.getSourceLocation())));
        node.getParent().replaceChild(node, globalNode);
      }
    }
  }

  private static SourceLocation union(SourceLocation l1, SourceLocation l2) {
    Point b1 = l1.getBeginPoint();
    Point b2 = l2.getBeginPoint();
    Point e1 = l1.getEndPoint();
    Point e2 = l2.getEndPoint();
    return new SourceLocation(
        l1.getFilePath(), b1.isBefore(b2) ? b1 : b2, e1.isBefore(e2) ? e2 : e1);
  }
}
