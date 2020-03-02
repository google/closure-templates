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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;

/**
 * Pass which checks that all expressions of type "Named Template" have been already removed. These
 * types should have been "upgraded" to the regular "Template" type in the prior pass, {@link
 * UpgradeTemplateTypesPass}.
 */
final class CheckNoNamedTemplateTypesPass implements CompilerFileSetPass {

  @Override
  public Result run(
      ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator, TemplateRegistry registry) {
    for (SoyFileNode file : sourceFiles) {
      for (ExprNode exprNode : SoyTreeUtils.getAllNodesOfType(file, ExprNode.class)) {
        if (SoyTypes.transitivelyContainsKind(exprNode.getType(), SoyType.Kind.NAMED_TEMPLATE)) {
          throw new IllegalStateException(
              "Found non-upgraded Named Template type after they should have all been removed."
                  + " This is most likely a parsing error; please file a go/soy-bug. Problem"
                  + " expression: "
                  + exprNode
                  + " at "
                  + exprNode.getSourceLocation());
        }
      }
    }
    return Result.CONTINUE;
  }
}
