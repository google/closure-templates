/*
 * Copyright 2018 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.MethodNode;
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.Optional;

/**
 * Populates the {@link FunctionNode} and {@link PrintDirectiveNode} with their plugin instances and
 * rewrites some ambiguous function nodes to {@link ProtoInitNode}.
 */
final class ResolvePluginsPass implements CompilerFilePass {

  private static final SoyErrorKind NOT_FIRST_PRINT_DIRECTIVE =
      SoyErrorKind.of(
          "Function ''{0}'' cannot be called as a print directive when preceded by print directive"
              + " ''{1}''.");

  private final PluginResolver resolver;
  private final SoyTypeRegistry typeRegistry;
  private final ErrorReporter errorReporter;

  ResolvePluginsPass(
      PluginResolver resolver, SoyTypeRegistry typeRegistry, ErrorReporter errorReporter) {
    this.resolver = resolver;
    this.typeRegistry = typeRegistry;
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (FunctionNode function : SoyTreeUtils.getAllNodesOfType(file, FunctionNode.class)) {
      // Functions with 0 arguments are ambiguous with proto init nodes with no arguments, check the
      // type registry first to see if this is such a case
      if (function.numChildren() == 0) {
        String name = function.getFunctionName();
        String resolvedName = file.resolveAlias(name);
        SoyType type = typeRegistry.getType(resolvedName);
        if (type != null && type.getKind() == SoyType.Kind.PROTO) {
          ProtoInitNode protoInit =
              new ProtoInitNode(resolvedName, ImmutableList.of(), function.getSourceLocation());
          function.getParent().replaceChild(function, protoInit);
          continue;
        }
      }

      function.setSoyFunction(
          resolver.lookupSoyFunction(
              function.getFunctionName(), function.numChildren(), function.getSourceLocation()));
    }

    for (PrintDirectiveNode directiveNode :
        SoyTreeUtils.getAllNodesOfType(file, PrintDirectiveNode.class)) {
      String name = directiveNode.getName();

      // If a template uses a print directive that doesn't exist, check if a function with the same
      // name does exist. This is likely a print directive being migrated with
      // SoyFunctionSignature#callableAsDeprecatedPrintDirective.
      Optional<SoySourceFunction> aliasedFunction =
          resolver.getFunctionCallableAsPrintDirective(name, directiveNode.getSourceLocation());
      if (aliasedFunction.isPresent()) {
        rewritePrintDirectiveAsFunction(directiveNode, aliasedFunction.get());
      } else {
        directiveNode.setPrintDirective(
            resolver.lookupPrintDirective(
                name, directiveNode.getExprList().size(), directiveNode.getSourceLocation()));
      }
    }

    for (MethodNode methodNode : SoyTreeUtils.getAllNodesOfType(file, MethodNode.class)) {
      methodNode.setSoyMethods(
          resolver.lookupSoyMethod(
              methodNode.getMethodName().identifier(), methodNode.getSourceLocation()));
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
        new FunctionNode(
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
