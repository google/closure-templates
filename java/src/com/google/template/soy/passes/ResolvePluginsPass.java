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

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TypeRegistries;
import java.util.Optional;

/**
 * Populates the {@link FunctionNode} and {@link PrintDirectiveNode} with their plugin instances and
 * identifies some ambiguous function nodes as {@link BuiltinFunction#PROTO_INIT}.
 */
final class ResolvePluginsPass implements CompilerFilePass {
  private final PluginResolver resolver;
  // Proto FQN will be warned in ResolveExpressionTypesPass.
  private final ErrorReporter ignoreFqnWarnings = ErrorReporter.create(ImmutableMap.of());

  ResolvePluginsPass(PluginResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (FunctionNode function :
        SoyTreeUtils.getAllMatchingNodesOfType(file, FunctionNode.class, fn -> !fn.isResolved())) {

      Identifier resolvedName = function.getIdentifier();
      SoyType type =
          TypeRegistries.getTypeOrProtoFqn(
              file.getSoyTypeRegistry(), ignoreFqnWarnings, resolvedName);
      if (type == null) {
        resolvedName = file.resolveAlias(resolvedName);
        type =
            TypeRegistries.getTypeOrProtoFqn(
                file.getSoyTypeRegistry(), ignoreFqnWarnings, resolvedName);
      }
      if (type != null && type.getKind() == SoyType.Kind.PROTO) {
        function.setSoyFunction(BuiltinFunction.PROTO_INIT);
        continue;
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
        directiveNode.setPrintDirectiveFunction(aliasedFunction.get());
      } else {
        directiveNode.setPrintDirective(
            resolver.lookupPrintDirective(
                name, directiveNode.getExprList().size(), directiveNode.getSourceLocation()));
      }
    }
  }
}
