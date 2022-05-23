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
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/**
 * Removes redundant non-null assertion operators ({@code !}) from data access chains.
 *
 * <p>A non-null assertion immediately followed by a dereference, like {@code $foo!.bar!.baz}, is
 * unnecessary, since the dereference will throw an exception on its own if the base is null. This
 * helps with code size (especially in JS) and doesn't require complicated code generation,
 * especially when non-null assertion operators are used with the null safe access operator.
 *
 * <p>This is separate from the {@link OptimizationPass} because this must always happen. The code
 * generation backends do not have support for non-null assertion operators in data access chains,
 * so will not generate correct code (or will fail) if they encounter non-null assertion operators
 * in data access chains.
 */
final class SimplifyAssertNonNullPass implements CompilerFilePass {

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (AssertNonNullOpNode node :
        SoyTreeUtils.getAllNodesOfType(file, AssertNonNullOpNode.class)) {
      if (node.getParent() instanceof DataAccessNode) {
        node.getParent().replaceChild(node, node.getChild(0));
      }
    }
  }
}
