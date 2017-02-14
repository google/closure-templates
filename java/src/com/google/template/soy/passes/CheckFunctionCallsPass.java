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

package com.google.template.soy.passes;

import com.google.common.base.Joiner;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.basicfunctions.LengthFunction;
import com.google.template.soy.basicfunctions.ParseFloatFunction;
import com.google.template.soy.basicfunctions.ParseIntFunction;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.defn.LoopVar;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.primitive.AnyType;
import com.google.template.soy.types.primitive.StringType;
import java.util.Set;

/**
 * Checks the signatures of functions.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
final class CheckFunctionCallsPass extends CompilerFilePass {

  private static final SoyErrorKind INCORRECT_NUM_ARGS =
      SoyErrorKind.of("Function ''{0}'' called with {1} arguments (expected {2}).");
  private static final SoyErrorKind INCORRECT_ARG_TYPE =
      SoyErrorKind.of("Function ''{0}'' called with incorrect arg type {1} (expected {2}).");
  private static final SoyErrorKind LOOP_VARIABLE_NOT_IN_SCOPE =
      SoyErrorKind.of("Function ''{0}'' must have a foreach loop variable as its argument.");
  private static final SoyErrorKind QUOTE_KEYS_IF_JS_REQUIRES_MAP_LITERAL_ARG =
      SoyErrorKind.of(
          "Function ''quoteKeysIfJs'' called with argument of type {0} (expected map literal).");
  private static final SoyErrorKind V1_EXPRESSION_REQUIRES_STRING_ARG =
      SoyErrorKind.of(
          "Function ''v1Expression'' called with argument of type {0} (expected string literal).");
  private static final SoyErrorKind UNKNOWN_FUNCTION = SoyErrorKind.of("Unknown function ''{0}''.");

  private final ErrorReporter errorReporter;
  private final boolean allowUnknownFunction;
  private final CheckFunctionCallsExprVisitor exprNodeVisitor = new CheckFunctionCallsExprVisitor();

  /** User-declared syntax version. */
  private SyntaxVersion declaredSyntaxVersion;

  CheckFunctionCallsPass(
      boolean allowUnknownFunctions,
      SyntaxVersion declaredSyntaxVersion,
      ErrorReporter errorReporter) {
    this.allowUnknownFunction = allowUnknownFunctions;
    this.errorReporter = errorReporter;
    this.declaredSyntaxVersion = declaredSyntaxVersion;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.execOnAllV2Exprs(file, exprNodeVisitor);
  }

  /**
   * Used to visit expr nodes to find nonplugin and basic functions and check their signatures and
   * arg types.
   */
  private final class CheckFunctionCallsExprVisitor extends AbstractExprNodeVisitor<Void> {

    /** Recurse to children. */
    @Override
    protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }

    /** Check the function signature. */
    @Override
    protected void visitFunctionNode(FunctionNode node) {
      String fnName = node.getFunctionName();
      SoyFunction function = node.getSoyFunction();
      if (function == null) {
        if (declaredSyntaxVersion != SyntaxVersion.V1_0 && !allowUnknownFunction) {
          // In Soy V2, all functions must be available as SoyFunctions at compile time.
          errorReporter.report(node.getSourceLocation(), UNKNOWN_FUNCTION, fnName);
        }
        return;
      }

      Checkpoint checkpoint = errorReporter.checkpoint();
      checkNumArgs(function, node);

      // If there were arity errors, don't run further function visits
      if (!errorReporter.errorsSince(checkpoint)) {
        if (function instanceof BuiltinFunction) {
          visitNonpluginFunction((BuiltinFunction) function, node);
        } else {
          visitFunction(function, node);
        }
      }

      // Recurse to operands.
      visitChildren(node);
    }

    private void visitNonpluginFunction(BuiltinFunction nonpluginFn, FunctionNode node) {
      // All non-plugin functions so far have exactly 1 arg
      ExprNode arg = node.getChild(0);

      // Check argument types.
      switch (nonpluginFn) {
        case INDEX:
        case IS_FIRST:
        case IS_LAST:
          requireLoopVariableInScope(node, arg);
          break;
        case QUOTE_KEYS_IF_JS:
          if (!(arg instanceof MapLiteralNode)) {
            errorReporter.report(
                node.getSourceLocation(),
                QUOTE_KEYS_IF_JS_REQUIRES_MAP_LITERAL_ARG,
                arg.getType().toString());
          }
          break;
        case CHECK_NOT_NULL:
          // Do nothing.  All types are valid.
          break;
        case V1_EXPRESSION:
          if (!(arg instanceof StringNode)) {
            errorReporter.report(
                node.getSourceLocation(),
                V1_EXPRESSION_REQUIRES_STRING_ARG,
                node.getChild(0).getType().toString());
          }
          break;
        default:
          throw new AssertionError("Unrecognized nonplugin fn " + nonpluginFn.getName());
      }
    }

    private void visitFunction(SoyFunction fn, FunctionNode node) {
      // TODO(user): This is hacky and incomplete. Come up with a better solution.
      if (fn instanceof LengthFunction) {
        checkArgType(node.getChild(0), ListType.of(AnyType.getInstance()), node);
      } else if (fn instanceof ParseIntFunction) {
        checkArgType(node.getChild(0), StringType.getInstance(), node);
      } else if (fn instanceof ParseFloatFunction) {
        checkArgType(node.getChild(0), StringType.getInstance(), node);
      }
    }

    private void checkNumArgs(SoyFunction function, FunctionNode node) {
      int numArgs = node.numChildren();
      Set<Integer> arities = function.getValidArgsSizes();
      if (!arities.contains(numArgs)) {
        errorReporter.report(
            node.getSourceLocation(),
            INCORRECT_NUM_ARGS,
            function.getName(),
            numArgs,
            Joiner.on(" or ").join(arities));
      }
    }

    /** @param fn The function that must take a loop variable. */
    private void requireLoopVariableInScope(FunctionNode fn, ExprNode loopVariable) {
      if (!(loopVariable instanceof VarRefNode
          && ((VarRefNode) loopVariable).getDefnDecl() instanceof LoopVar)) {
        errorReporter.report(
            fn.getSourceLocation(), LOOP_VARIABLE_NOT_IN_SCOPE, fn.getFunctionName());
      }
    }

    private void checkArgType(ExprNode arg, SoyType expectedType, FunctionNode node) {
      SoyType.Kind argTypeKind = arg.getType().getKind();
      if (argTypeKind == SoyType.Kind.UNKNOWN || argTypeKind == SoyType.Kind.ERROR) {
        return;
      }

      if (!expectedType.isAssignableFrom(arg.getType())) {
        errorReporter.report(
            arg.getSourceLocation(),
            INCORRECT_ARG_TYPE,
            node.getSoyFunction().getName(),
            arg.getType(),
            expectedType);
      }
    }
  }
}
