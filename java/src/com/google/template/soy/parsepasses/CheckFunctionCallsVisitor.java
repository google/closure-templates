/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.parsepasses;

import com.google.common.collect.ImmutableMap;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.internal.NonpluginFunction;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;

import java.util.Map;
import java.util.Set;


/**
 * Checks the signatures of functions.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class CheckFunctionCallsVisitor extends AbstractSoyNodeVisitor<Void> {


  /**
   * Injectable factory for creating an instance of this class.
   */
  public static interface CheckFunctionCallsVisitorFactory {

    /**
     * @param declaredSyntaxVersion User-declared syntax version.
     */
    public CheckFunctionCallsVisitor create(SyntaxVersion declaredSyntaxVersion);
  }


  /** Injected Soy function definitions. */
  private final Map<String, SoyFunction> soyFunctionsByName;

  /** User-declared syntax version. */
  private SyntaxVersion declaredSyntaxVersion;


  @AssistedInject
  public CheckFunctionCallsVisitor(
      Map<String, SoyFunction> soyFunctionsByName, @Assisted SyntaxVersion declaredSyntaxVersion) {
    this.soyFunctionsByName = ImmutableMap.copyOf(soyFunctionsByName);
    this.declaredSyntaxVersion = declaredSyntaxVersion;
  }


  /**
   * Recursively identifies all Soy nodes that contain expressions, and recurse to
   * each expression.
   */
  @Override protected void visitSoyNode(SoyNode node) {

    if (node instanceof ExprHolderNode) {

      for (ExprUnion exprUnion : ((ExprHolderNode) node).getAllExprUnions()) {
        if (exprUnion.getExpr() == null) {
          continue;
        }

        try {
          (new CheckFunctionCallsExprVisitor((ExprHolderNode) node)).exec(exprUnion.getExpr());
        } catch (SoySyntaxException ex) {
          // Add meta info based on node.
          throw SoySyntaxExceptionUtils.associateNode(ex, node);
        }
      }
    }

    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }


  /**
   * Used to visit expr nodes to find nonplugin functions and check their signatures.
   */
  private final class CheckFunctionCallsExprVisitor extends AbstractExprNodeVisitor<Void> {

    private final ExprHolderNode container;


    CheckFunctionCallsExprVisitor(ExprHolderNode container) {
      this.container = container;
    }


    /**
     * Recurse to children.
     */
    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }


    /**
     * Check the function signature.
     *
     * @exception SoySyntaxException When a signature is violated.
     */
    @Override protected void visitFunctionNode(FunctionNode node) {

      String fnName = node.getFunctionName();
      int numArgs = node.numChildren();

      NonpluginFunction nonpluginFn = NonpluginFunction.forFunctionName(fnName);
      if (nonpluginFn != null) {
        // --- Case 1: nonplugin function. ---
        // Check arity.
        if (numArgs != nonpluginFn.getNumArgs()) {
          throw SoySyntaxException.createWithoutMetaInfo(
              "Function '" + fnName + "' called with the wrong number of arguments" +
                  " (function call \"" + node.toSourceString() + "\").");
        }
        // Check argument types.
        switch (nonpluginFn) {
          case INDEX:
          case IS_FIRST:
          case IS_LAST:
            requireLoopVariableInScope(node, node.getChild(0));
            break;
          case QUOTE_KEYS_IF_JS:
            if (! (node.getChild(0) instanceof MapLiteralNode)) {
              throw SoySyntaxException.createWithoutMetaInfo(
                  "Function quoteKeysIfJs() must have a map literal as its arg (encountered \"" +
                      node.toSourceString() + "\").");
            }
            break;
          default:
            throw new AssertionError("Unrecognized nonplugin fn " + nonpluginFn);
        }

      } else {
        SoyFunction pluginFn = soyFunctionsByName.get(fnName);
        if (pluginFn != null) {
          // --- Case 2: Plugin function. ---
          Set<Integer> arities = pluginFn.getValidArgsSizes();
          // Check arity.
          if (!arities.contains(numArgs)) {
            throw SoySyntaxException.createWithoutMetaInfo(
                "Function '" + fnName + "' called with the wrong number of arguments" +
                    " (function call \"" + node.toSourceString() + "\").");
          }

        } else {
          // --- Case 3: Unrecognized function. ---
          // In Soy V2, all functions must be available as SoyFunctions at compile time.
          if (declaredSyntaxVersion != SyntaxVersion.V1_0) {
            throw SoySyntaxException.createWithoutMetaInfo(
                "Unrecognized function '" + fnName + "' (encountered function call \"" +
                    node.toSourceString() + "\").");
          }
        }
      }

      // Recurse to operands.
      visitChildren(node);
    }


    /**
     * @param fn The function that must take a loop variable.
     */
    private void requireLoopVariableInScope(FunctionNode fn, ExprNode loopVariable) {
      if (!isLoopVariableInScope(loopVariable)) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Function '" + fn.getFunctionName() + "' must have a foreach loop variable as its" +
                " argument (encountered \"" + fn.toSourceString() + "\").");
      }
    }


    private boolean isLoopVariableInScope(ExprNode loopVariable) {
      if (!(loopVariable instanceof VarRefNode)) {
        return false;
      }
      VarRefNode loopVariableRef = (VarRefNode) loopVariable;
      if (loopVariableRef.isInjected()) {
        return false;
      }
      String loopVariableName = loopVariableRef.getName();
      for (ParentSoyNode<?> ancestor = container.getParent(); ancestor != null;
           ancestor = ancestor.getParent()) {
        if (ancestor instanceof ForeachNonemptyNode) {
          String iteratorVariableName = ((ForeachNonemptyNode) ancestor).getVarName();
          if (loopVariableName.equals(iteratorVariableName)) {
            return true;
          }
        }
      }
      return false;
    }

  }

}
