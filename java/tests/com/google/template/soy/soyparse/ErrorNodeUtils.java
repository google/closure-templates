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

package com.google.template.soy.soyparse;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.XidNode;

import java.util.List;

/**
 * Test-only utilities for {@link ErrorNodes}.
 *
 * <p>A parse tree containing any of the nodes in {@link ErrorNodes} is invalid and must not be used
 * for further compilation. {@link #containsErrors} is provided as a sanity check for this
 * condition. Because it performs a full tree walk, it can only be used in test code.
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class ErrorNodeUtils {
  // Keep in sync with ErrorNodes.
  private static final ImmutableSet<? extends SoyNode> ERROR_NODES = ImmutableSet.of(
      CallBasicNode.Builder.ERROR,
      CallDelegateNode.Builder.ERROR,
      CallParamContentNode.Builder.ERROR,
      CallParamValueNode.Builder.ERROR,
      LetContentNode.Builder.ERROR,
      LetValueNode.Builder.ERROR,
      MsgHtmlTagNode.Builder.ERROR,
      MsgPluralNode.Builder.ERROR,
      XidNode.Builder.ERROR);

  /**
   * Returns true if the tree rooted at {@code root} contains an error node and is thus invalid.
   *
   * <p>Note that the inverse is not true: if a tree does not contain an error node, it could
   * still be invalid. This is because not all validity errors insert error nodes into the tree.
   */
  static boolean containsErrors(SoyNode root) {
    if (ERROR_NODES.contains(root)) {
      return true;
    }

    if (root instanceof ParentSoyNode) {
      for (SoyNode child : ((ParentSoyNode<? extends SoyNode>) root).getChildren()) {
        if (containsErrors(child)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Returns true if any of the trees rooted at {@code roots} contain an error node and are
   * therefore invalid.
   *
   * <p>Note that the inverse is not true: if none of the trees contain an error node, they could
   * still be invalid. This is because not all validity errors insert error nodes into the tree.
   */
  static boolean containsErrors(List<? extends SoyNode> roots) {
    for (SoyNode root : roots) {
      if (containsErrors(root)) {
        return true;
      }
    }
    return false;
  }
}
