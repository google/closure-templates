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
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode.AccessChainComponentNode;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.NullSafeAccessNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/** Translates {@link DataAccessNode}s with null safe accesses into {@link NullSafeAccessNode}s. */
public class NullSafeAccessPass implements CompilerFilePass {

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    ImmutableList<DataAccessNode> accesses =
        SoyTreeUtils.getAllNodesOfType(file, DataAccessNode.class);
    for (DataAccessNode access : accesses.reverse()) {
      if (access.isNullSafe() && access.getParent() != null) {
        AccessChainComponentNode accessChainRoot = findRoot(access);
        NullSafeAccessNode.createAndInsert(access, accessChainRoot);
      }
    }
  }

  private static AccessChainComponentNode findRoot(DataAccessNode access) {
    AccessChainComponentNode node = access;
    while ((node.getParent() instanceof DataAccessNode
            // Make sure to only traverse base nodes up the tree.
            && ((DataAccessNode) node.getParent()).getBaseExprChild() == node)
        || node.getParent().getKind() == Kind.ASSERT_NON_NULL_OP_NODE) {
      node = (AccessChainComponentNode) node.getParent();
    }
    return node;
  }
}
