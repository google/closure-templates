/*
 * Copyright 2021 Google Inc.
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
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.HashMap;
import java.util.Map;

/**
 * Visitor to determine whether the output string for the subtree rooted at a given node is
 * computable as a single lit-html template. If this is false, it means the node is not yet
 * supported in the lit backend.
 */
public class IsComputableAsLitTemplateVisitor extends AbstractReturningSoyNodeVisitor<Boolean> {

  /** The memoized results of past visits to nodes. */
  private final Map<SoyNode, Boolean> memoizedResults;

  protected IsComputableAsLitTemplateVisitor() {
    memoizedResults = new HashMap<>();
  }

  @Override
  protected Boolean visit(SoyNode node) {

    if (memoizedResults.containsKey(node)) {
      return memoizedResults.get(node);

    } else {
      Boolean result = super.visit(node);
      memoizedResults.put(node, result);
      return result;
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected Boolean visitTemplateNode(TemplateNode node) {
    return node instanceof TemplateBasicNode && areChildrenComputableAsJsExprs(node);
  }

  @Override
  protected Boolean visitRawTextNode(RawTextNode node) {
    return true;
  }

  @Override
  protected Boolean visitPrintNode(PrintNode node) {
    return true;
  }

  @Override
  protected Boolean visitLetValueNode(LetValueNode node) {
    return true;
  }

  @Override
  protected Boolean visitSoyNode(SoyNode node) {
    // Fallback value
    return false;
  }

  // -----------------------------------------------------------------------------------------------
  // Private helpers.

  /**
   * Private helper to check whether all children of a given parent node satisfy
   * IsComputableAsJsExprsVisitor.
   *
   * @param node The parent node whose children to check.
   * @return True if all children satisfy IsComputableAsJsExprsVisitor.
   */
  private boolean areChildrenComputableAsJsExprs(ParentSoyNode<?> node) {

    for (SoyNode child : node.getChildren()) {
      if (!visit(child)) {
        return false;
      }
    }

    return true;
  }
}
