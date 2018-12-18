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
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;

/**
 * Populates the {@link FunctionNode} and {@link PrintDirectiveNode} with their plugin instances and
 * rewrites some ambiguous function nodes to {@link ProtoInitNode}.
 */
final class ResolvePluginsPass extends CompilerFilePass {
  private final PluginResolver resolver;
  private final SoyTypeRegistry typeRegistry;

  ResolvePluginsPass(PluginResolver resolver, SoyTypeRegistry typeRegistry) {
    this.resolver = resolver;
    this.typeRegistry = typeRegistry;
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

    for (PrintDirectiveNode directive :
        SoyTreeUtils.getAllNodesOfType(file, PrintDirectiveNode.class)) {
      directive.setPrintDirective(
          resolver.lookupPrintDirective(
              directive.getName(), directive.getExprList().size(), directive.getSourceLocation()));
    }
  }
}
