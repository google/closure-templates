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
import com.google.inject.Inject;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.internal.NonpluginFunction;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

import java.util.Map;
import java.util.Set;


/**
 * Checks the signatures of functions.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Mike Samuel
 */
public class CheckFunctionCallsVisitor extends AbstractSoyNodeVisitor<Void> {


  /** Injected Soy function definitions. */
  private final Map<String, SoyFunction> soyFunctionsByName;

  /** Determines whether we allow external function definitions. */
  private boolean allowExternallyDefinedFunctions;

  @Inject public CheckFunctionCallsVisitor(
      Map<String, SoyFunction> soyFunctionsByName) {
    this.soyFunctionsByName = ImmutableMap.copyOf(soyFunctionsByName);
  }


  /**
   * Specifies whether we allow external function definitions.
   * If true, then we ignore calls to functions that are not defined in the
   * map passed to the constructor.
   * Otherwise, we fail with an exception.
   *
   * @param allow true to allow external function definitions.
   */
  public void setAllowExternallyDefinedFunctions(boolean allow) {
    this.allowExternallyDefinedFunctions = allow;
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
          // Add line number info based on node.
          SourceLocation location = node.getLocation();
          if (location.isKnown() && !ex.getSourceLocation().isKnown()) {
            ex.setSourceLocation(location);
          }
          throw ex;
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
        // Check arity.
        if (numArgs != nonpluginFn.getNumArgs()) {
          throw new SoySyntaxException(
              "Function '" + fnName + "' called with the wrong number of arguments" +
              " (function call \"" + node.toSourceString() + "\").");
        }
        // Check argument types.
        switch (nonpluginFn) {
          case INDEX:
          case IS_FIRST:
          case IS_LAST:
            requireLoopVariableInScope(node, node.getChild(0));
            return;
          case HAS_DATA:
            // No arguments to check.
            return;
        }
        throw new AssertionError("Unrecognized nonplugin fn " + nonpluginFn);
      } else {
        // Check pure functions.
        SoyFunction signature = soyFunctionsByName.get(fnName);
        if (signature != null) {
          Set<Integer> arities = signature.getValidArgsSizes();
          // Check arity.
          if (!arities.contains(numArgs)) {
            throw new SoySyntaxException(
                "Function '" + fnName + "' called with the wrong number of arguments" +
                " (function call \"" + node.toSourceString() + "\").");
          }
        } else if (!allowExternallyDefinedFunctions) {
          // In version 2 and later, all functions must be available as SoyFunctions
          // at compile time.
          throw new SoySyntaxException(
              "Unrecognized function '" + fnName + "'" +
              " (function call \"" + node.toSourceString() + "\").");
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
        throw new SoySyntaxException(
            "Error in function call \"" + fn.toSourceString() +
            "\".  It takes a foreach loop variable.");
      }
    }


    private boolean isLoopVariableInScope(ExprNode loopVariable) {
      if (!(loopVariable instanceof DataRefNode)) {
        return false;
      }
      DataRefNode loopVariableRef = (DataRefNode) loopVariable;
      if (loopVariableRef.isIjDataRef()) {
        return false;
      }
      if (loopVariableRef.numChildren() != 1) {
        return false;
      }
      String loopVariableName = loopVariableRef.getFirstKey();
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
