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

import static com.google.template.soy.jbcsrc.SyntheticVarName.foreachLoopIndex;
import static com.google.template.soy.jbcsrc.SyntheticVarName.foreachLoopLength;

import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.internal.NonpluginFunction;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateParam;

/**
 * An abstract base class that adds extra visitor methods for unpacking varrefs and functions based
 * on their subtypes.
 */
abstract class EnhancedAbstractExprNodeVisitor<T> extends AbstractReturningExprNodeVisitor<T> {

  @Override protected final T visitVarRefNode(VarRefNode node) {
    VarDefn defn = node.getDefnDecl();
    switch (defn.kind()) {
      case LOCAL_VAR:
        LocalVar local = (LocalVar) defn;
        if (local.declaringNode().getKind() == SoyNode.Kind.FOR_NODE) {
          return visitForLoopIndex(node, local);
        }
        if (local.declaringNode().getKind() == SoyNode.Kind.FOREACH_NONEMPTY_NODE) {
          return visitForeachLoopVar(node, local);
        }
        throw new UnsupportedOperationException("lets and foreach loops aren't supported yet");
      case PARAM:
        TemplateParam param = (TemplateParam) defn;
        if (param.declLoc() != TemplateParam.DeclLoc.HEADER) {
          throw new RuntimeException(
              "header doc params are not supported by jbcsrc, use {@param..} instead");
        }
        return visitParam(node, param);
      case IJ_PARAM:
        throw new RuntimeException("$ij are not supported by jbcsrc, use {@inject..} instead");
      case UNDECLARED:
        throw new RuntimeException("undeclared params are not supported by jbcsrc");
      default:
        throw new AssertionError();
    }
  }

  @Override protected final T visitFunctionNode(FunctionNode node) {
    NonpluginFunction nonpluginFn = NonpluginFunction.forFunctionName(node.getFunctionName());
    if (nonpluginFn != null) {
      if (nonpluginFn == NonpluginFunction.QUOTE_KEYS_IF_JS) {
        // this function is a no-op in non JS backends, the CheckFunctionCallsVisitor ensures that
        // there is only one child and it is a MapLiteralNode
        return visitMapLiteralNode((MapLiteralNode) node.getChild(0));
      }
      // the rest of the builtins all deal with indexing operations on foreach variables.
      VarRefNode varRef = (VarRefNode) node.getChild(0);
      ForeachNonemptyNode declaringNode =
          (ForeachNonemptyNode) ((LocalVar) varRef.getDefnDecl()).declaringNode();
      switch (nonpluginFn) {
        case IS_FIRST:
          return visitIsFirstFunction(node, foreachLoopIndex(declaringNode));
        case IS_LAST:
          return visitIsLastFunction(
              node, foreachLoopIndex(declaringNode), foreachLoopLength(declaringNode));
        case INDEX:
          return visitIndexFunction(node, foreachLoopIndex(declaringNode));
        case QUOTE_KEYS_IF_JS:  // handled before the switch above
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

  T visitParam(VarRefNode varRef, TemplateParam param) {
    return visitExprNode(varRef);
  }

  T visitIsFirstFunction(FunctionNode node, SyntheticVarName indexVar)  {
    return visitExprNode(node);
  }

  T visitIsLastFunction(FunctionNode node, SyntheticVarName indexVar, SyntheticVarName lengthVar) {
    return visitExprNode(node);
  }

  T visitIndexFunction(FunctionNode node, SyntheticVarName indexVar) {
    return visitExprNode(node);
  }

  T visitPluginFunction(FunctionNode node) {
    return visitExprNode(node);
  }

}
