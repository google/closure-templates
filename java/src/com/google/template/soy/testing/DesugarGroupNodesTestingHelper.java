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
package com.google.template.soy.testing;

import com.google.template.soy.basetree.Node;
import com.google.template.soy.exprtree.GroupNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/** Helper for tests to remove group nodes from a soy tree. A group node is "(Expr)". */
public final class DesugarGroupNodesTestingHelper {

  private DesugarGroupNodesTestingHelper() {}

  /**
   * Removes all group nodes from the node's subtree. If the top-level node IS a group node, this
   * returns the inner expr as the new top-level node (e.g. "(Expr)" -> "Expr").
   */
  public static Node stripGroupNodesForTest(Node node) {
    for (GroupNode gn : SoyTreeUtils.getAllNodesOfType(node, GroupNode.class)) {
      if (gn.getParent() != null) {
        gn.getParent().replaceChild(gn, gn.getChild(0));
      }
    }
    // If the top level node passed in was a GroupNode, return the inner expr.
    if (node instanceof GroupNode) {
      return ((GroupNode) node).getChild(0);
    }
    return node;
  }
}
