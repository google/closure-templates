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

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/**
 * Rewrites print directives that are callable as functions.
 *
 * <p>For compatibility and migration, we support migrating a {@code SoyPrintDirective} to be
 * implementedby a {@link SoySourceFunction} as long as it sets {@link
 * SoyFunctionSignature#callableAsDeprecatedPrintDirective} to {@code true}. This pass identifies
 * such print directives still using print directive syntax and rewrites them to use function call
 * syntax. For example, {@code {$foo|formatNum}} -> {@code {formatNum($foo)}}.
 *
 * <p>Print directives in this status should be temporary, so there is also a Fixer that inlines
 * this compiler pass.
 */
@RunAfter(ResolvePluginsPass.class)
@RunBefore(ResolveExpressionTypesPass.class)
final class RewriteDirectivesCallableAsFunctionsPass implements CompilerFilePass {

  private static final SoyErrorKind NOT_FIRST_PRINT_DIRECTIVE =
      SoyErrorKind.of(
          "Function ''{0}'' cannot be called as a print directive when preceded by print directive"
              + " ''{1}''.");

  private final ErrorReporter errorReporter;

  RewriteDirectivesCallableAsFunctionsPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (PrintDirectiveNode directiveNode :
        SoyTreeUtils.getAllNodesOfType(file, PrintDirectiveNode.class)) {
      SoySourceFunction function = directiveNode.getPrintDirectiveFunction();
      if (function != null) {
        rewritePrintDirectiveAsFunction(directiveNode, function);
      }
    }
  }

  private void rewritePrintDirectiveAsFunction(
      PrintDirectiveNode directiveNode, SoySourceFunction function) {
    PrintNode printNode = (PrintNode) directiveNode.getParent();
    // printNode.
    String functionName = function.getClass().getAnnotation(SoyFunctionSignature.class).name();

    // Only rewrite the print directive if it is the first in the chain. This avoids having to
    // create new let nodes.
    int directiveIndex = printNode.getChildIndex(directiveNode);
    if (directiveIndex != 0) {
      errorReporter.report(
          directiveNode.getSourceLocation(),
          NOT_FIRST_PRINT_DIRECTIVE,
          functionName,
          printNode.getChild(directiveIndex - 1).getName());
      return;
    }

    ExprRootNode originalExprRoot = printNode.getExpr();
    ExprNode originalExpr = originalExprRoot.getRoot();
    FunctionNode newExpr =
        FunctionNode.newPositional(
            Identifier.create(functionName, directiveNode.getNameLocation()),
            function,
            originalExpr.getSourceLocation().extend(directiveNode.getSourceLocation()));
    // Add the original expression of the print directive as the first argument to the function.
    newExpr.addChild(originalExpr);
    // Add the 0-n arguments to the print directive as the 1-(n+1) arguments of the function.
    newExpr.addChildren(directiveNode.getArgs());
    originalExprRoot.addChild(newExpr);

    // Remove the print directive.
    printNode.removeChild(directiveIndex);
  }
}
