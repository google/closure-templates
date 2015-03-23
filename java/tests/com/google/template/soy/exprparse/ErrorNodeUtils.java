/*
 * Copyright 2015 Google Inc.
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


package com.google.template.soy.exprparse;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.VarNode;
import com.google.template.soy.exprtree.VarRefNode;

/**
 * Test-only utilities for detecting error nodes in expression trees.
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class ErrorNodeUtils {

  private static final ImmutableSet<? extends ExprNode> ERROR_NODES = ImmutableSet.of(
      GlobalNode.ERROR,
      VarNode.ERROR,
      VarRefNode.ERROR);

  /**
   * Returns true if the tree rooted at {@code root} contains an error node and is thus invalid.
   */
  static boolean containsErrors(ExprNode root) {
    if (ERROR_NODES.contains(root)) {
      return true;
    }

    if (root instanceof ParentExprNode) {
      for (ExprNode child : ((ParentExprNode) root).getChildren()) {
        if (containsErrors(child)) {
          return true;
        }
      }
    }
    return false;
  }
}
