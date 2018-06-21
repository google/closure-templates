/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.SoyNode;

/**
 * Visitor for determining whther the code generated from a given node's subtree can be made to also
 * initialize the current variable (if not already initialized).
 *
 * <p>Precondition: MsgNode should not exist in the tree.
 *
 */
public final class CanInitOutputVarVisitor extends AbstractReturningSoyNodeVisitor<Boolean> {

  /** The IsComputableAsJsExprsVisitor used by this instance (when needed). */
  private final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;

  /**
   * @param isComputableAsJsExprsVisitor The IsComputableAsJsExprsVisitor used by this instance
   *     (when needed).
   */
  public CanInitOutputVarVisitor(IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor) {
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected Boolean visitCallNode(CallNode node) {
    // The call is a JS expression that returns its output as a string.
    return true;
  }

  @Override
  protected Boolean visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    // the msg is a variable that is a string (possibly with some escaping directives)
    return true;
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected Boolean visitSoyNode(SoyNode node) {
    // For the vast majority of nodes, the return value of this visitor should be the same as the
    // return value of IsComputableAsJsExprsVisitor.
    return isComputableAsJsExprsVisitor.exec(node);
  }
}
