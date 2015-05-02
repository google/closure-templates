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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
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
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.defn.LoopVar;

import java.util.Map;
import java.util.Set;

/**
 * Checks the signatures of functions.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class CheckFunctionCallsVisitor extends AbstractSoyNodeVisitor<Void> {

  private static final SoyError INCORRECT_NUM_ARGS = SoyError.of(
      "Function ''{0}'' called with {1} arguments (expected {2}).");
  private static final SoyError LOOP_VARIABLE_NOT_IN_SCOPE = SoyError.of(
      "Function ''{0}'' must have a foreach loop variable as its argument.");
  private static final SoyError QUOTE_KEYS_IF_JS_REQUIRES_MAP_LITERAL_ARG = SoyError.of(
      "Function ''quoteKeysIfJs'' called with argument of type {0} (expected map literal).");
  private static final SoyError UNKNOWN_FUNCTION = SoyError.of(
      "Unknown function ''{0}''.");

  /**
   * Injectable factory for creating an instance of this class.
   */
  public static interface CheckFunctionCallsVisitorFactory {

    /**
     * @param declaredSyntaxVersion User-declared syntax version.
     * @param errorReporter For reporting errors during the visit.
     */
    public CheckFunctionCallsVisitor create(
        SyntaxVersion declaredSyntaxVersion, ErrorReporter errorReporter);
  }


  /** Injected Soy function definitions. */
  private final Map<String, SoyFunction> soyFunctionsByName;

  /** User-declared syntax version. */
  private SyntaxVersion declaredSyntaxVersion;


  @AssistedInject
  public CheckFunctionCallsVisitor(
      Map<String, SoyFunction> soyFunctionsByName,
      @Assisted SyntaxVersion declaredSyntaxVersion,
      @Assisted ErrorReporter errorReporter) {
    super(errorReporter);
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
        new CheckFunctionCallsExprVisitor().exec(exprUnion.getExpr());
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

    CheckFunctionCallsExprVisitor() {
      super(CheckFunctionCallsVisitor.this.errorReporter);
    }

    /**
     * Recurse to children.
     */
    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }

    /** Check the function signature. */
    @Override protected void visitFunctionNode(FunctionNode node) {
      String fnName = node.getFunctionName();
      NonpluginFunction nonpluginFn;
      SoyFunction pluginFn;

      if ((nonpluginFn = NonpluginFunction.forFunctionName(fnName)) != null) {
        visitNonpluginFunction(nonpluginFn, node);
      } else if ((pluginFn = soyFunctionsByName.get(fnName)) != null) {
        visitPluginFunction(pluginFn, node);
      } else if (declaredSyntaxVersion != SyntaxVersion.V1_0) {
        // In Soy V2, all functions must be available as SoyFunctions at compile time.
        errorReporter.report(node.getSourceLocation(), UNKNOWN_FUNCTION, fnName);
      }

      // Recurse to operands.
      visitChildren(node);
    }

    private void visitNonpluginFunction(NonpluginFunction nonpluginFn, FunctionNode node) {
      int numArgs = node.numChildren();
      String fnName = nonpluginFn.getFunctionName();
      // Check arity.
      if (numArgs != nonpluginFn.getNumArgs()) {
        errorReporter.report(
            node.getSourceLocation(),
            INCORRECT_NUM_ARGS,
            fnName,
            numArgs,
            nonpluginFn.getNumArgs());
        // Return early to avoid NPEs when dereferencing the children below.
        return;
      }
      // Check argument types.
      ExprNode firstChild = Iterables.getFirst(node.getChildren(), null);
      switch (nonpluginFn) {
        case INDEX:
        case IS_FIRST:
        case IS_LAST:
          requireLoopVariableInScope(node, firstChild);
          break;
        case QUOTE_KEYS_IF_JS:
          if (!(firstChild instanceof MapLiteralNode)) {
            errorReporter.report(
                node.getSourceLocation(),
                QUOTE_KEYS_IF_JS_REQUIRES_MAP_LITERAL_ARG,
                node.getChild(0).getType().toString());
          }
          break;
        case CHECK_NOT_NULL:
          // Do nothing.  All types are valid.
          break;
        default:
          throw new AssertionError("Unrecognized nonplugin fn " + fnName);
      }
    }

    private void visitPluginFunction(SoyFunction pluginFn, FunctionNode node) {
      int numArgs = node.numChildren();
      String fnName = pluginFn.getName();
      Set<Integer> arities = pluginFn.getValidArgsSizes();
      // Check arity.
      if (!arities.contains(numArgs)) {
        errorReporter.report(
            node.getSourceLocation(),
            INCORRECT_NUM_ARGS,
            fnName,
            numArgs,
            Joiner.on(" or ").join(arities));
      }
    }

    /**
     * @param fn The function that must take a loop variable.
     */
    private void requireLoopVariableInScope(FunctionNode fn, ExprNode loopVariable) {
      if (!(loopVariable instanceof VarRefNode
          && ((VarRefNode) loopVariable).getDefnDecl() instanceof LoopVar)) {
        errorReporter.report(
            fn.getSourceLocation(), LOOP_VARIABLE_NOT_IN_SCOPE, fn.getFunctionName());
      }
    }
  }
}
