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
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyType;
import java.util.Optional;

/**
 * Populates the {@link FunctionNode} and {@link PrintDirectiveNode} with their plugin instances and
 * rewrites some ambiguous function nodes to {@link ProtoInitNode}.
 */
final class ResolvePluginsPass implements CompilerFilePass {
  private final PluginResolver resolver;

  ResolvePluginsPass(PluginResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (FunctionNode function : SoyTreeUtils.getAllNodesOfType(file, FunctionNode.class)) {
      // Functions with 0 arguments are ambiguous with proto init nodes with no arguments, check the
      // type registry first to see if this is such a case
      if (function.numChildren() == 0) {
        Identifier resolvedName = function.getIdentifier();
        SoyType type = file.getSoyTypeRegistry().getType(resolvedName.identifier());
        if (type == null) {
          resolvedName = file.resolveAlias(resolvedName);
          type = file.getSoyTypeRegistry().getType(resolvedName.identifier());
        }
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
        directiveNode.setPrintDirectiveFunction(aliasedFunction.get());
      } else {
        directiveNode.setPrintDirective(
            resolver.lookupPrintDirective(
                name, directiveNode.getExprList().size(), directiveNode.getSourceLocation()));
      }
    }
  }
}
