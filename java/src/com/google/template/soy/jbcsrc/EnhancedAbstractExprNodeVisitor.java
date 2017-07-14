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

import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.soytree.defn.InjectedParam;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateParam;

/**
 * An abstract base class that adds extra visitor methods for unpacking varrefs and functions based
 * on their subtypes.
 */
abstract class EnhancedAbstractExprNodeVisitor<T> extends AbstractReturningExprNodeVisitor<T> {

  @Override
  protected T visit(ExprNode node) {
    try {
      return super.visit(node);
    } catch (UnexpectedCompilerFailureException e) {
      e.addLocation(node);
      throw e;
    } catch (Throwable t) {
      throw new UnexpectedCompilerFailureException(node, t);
    }
  }

  @Override
  protected final T visitVarRefNode(VarRefNode node) {
    VarDefn defn = node.getDefnDecl();
    switch (defn.kind()) {
      case LOCAL_VAR:
        LocalVar local = (LocalVar) defn;
        LocalVarNode declaringNode = local.declaringNode();
        switch (declaringNode.getKind()) {
          case FOR_NODE:
            return visitForLoopIndex(node, local);
          case FOREACH_NONEMPTY_NODE:
            return visitForeachLoopVar(node, local);
          case LET_CONTENT_NODE:
          case LET_VALUE_NODE:
            return visitLetNodeVar(node, local);
          default:
            throw new AssertionError("Unexpected local variable: " + local);
        }
      case PARAM:
        return visitParam(node, (TemplateParam) defn);
      case IJ_PARAM:
        return visitIjParam(node, (InjectedParam) defn);
      case UNDECLARED:
        throw new RuntimeException("undeclared params are not supported by jbcsrc");
      default:
        throw new AssertionError();
    }
  }

  @Override
  protected final T visitFunctionNode(FunctionNode node) {
    SoyFunction function = node.getSoyFunction();

    if (function instanceof BuiltinFunction) {
      BuiltinFunction builtinFn = (BuiltinFunction) function;
      switch (builtinFn) {
        case IS_FIRST:
          return visitIsFirstFunction(node);
        case IS_LAST:
          return visitIsLastFunction(node);
        case INDEX:
          return visitIndexFunction(node);
        case QUOTE_KEYS_IF_JS:
          // this function is a no-op in non JS backends, the CheckFunctionCallsVisitor ensures that
          // there is only one child and it is a MapLiteralNode
          return visitMapLiteralNode((MapLiteralNode) node.getChild(0));
        case CHECK_NOT_NULL:
          return visitCheckNotNullFunction(node);
        case CSS:
          return visitCssFunction(node);
        case XID:
          return visitXidFunction(node);
        case V1_EXPRESSION:
          // V1 expressions should not exist in jbcsrc
          throw new AssertionError();
        default:
          throw new AssertionError();
      }
    }

    return visitPluginFunction(node);
  }

  T visitForLoopIndex(VarRefNode varRef, LocalVar local) {
    return visitExprNode(varRef);
  }

  T visitForeachLoopVar(VarRefNode varRef, LocalVar local) {
    return visitExprNode(varRef);
  }

  T visitLetNodeVar(VarRefNode node, LocalVar local) {
    return visitExprNode(node);
  }

  T visitParam(VarRefNode varRef, TemplateParam param) {
    return visitExprNode(varRef);
  }

  T visitIjParam(VarRefNode varRef, InjectedParam ij) {
    return visitExprNode(varRef);
  }

  T visitIsFirstFunction(FunctionNode node) {
    return visitExprNode(node);
  }

  T visitIsLastFunction(FunctionNode node) {
    return visitExprNode(node);
  }

  T visitIndexFunction(FunctionNode node) {
    return visitExprNode(node);
  }

  T visitCheckNotNullFunction(FunctionNode node) {
    return visitExprNode(node);
  }

  T visitCssFunction(FunctionNode node) {
    return visitExprNode(node);
  }

  T visitXidFunction(FunctionNode node) {
    return visitExprNode(node);
  }

  T visitPluginFunction(FunctionNode node) {
    return visitExprNode(node);
  }
}
