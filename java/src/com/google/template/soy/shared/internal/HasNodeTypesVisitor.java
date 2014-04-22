/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.shared.internal;

import com.google.common.base.Preconditions;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;


/**
 * Visitor for searching for nodes of any of the given types within a template file.
 *
 * <p> {@link #exec} should be called on a {@code SoyFileNode}. It returns whether the file
 * has at least one template containing at least one node whose type is one
 * of the types given in the constructor.
 *
 *
 */
public class HasNodeTypesVisitor extends AbstractSoyNodeVisitor<Boolean> {


  /**
   * Indicates whether a file has at least one template containing nodes of
   * the types we're looking for.
   */
  private boolean found;


  /** List of classes we're searching for. */
  private final Class<? extends SoyNode>[] nodeTypes;


  public HasNodeTypesVisitor(Class<? extends SoyNode>[] nodeTypes) {
    this.nodeTypes = nodeTypes;
  }


  @Override public Boolean exec(SoyNode soyNode) {
    Preconditions.checkArgument(soyNode instanceof SoyFileNode);

    found = false;
    visit(soyNode);
    return found;
  }


  @Override protected void visitSoyNode(SoyNode node) {
    for (Class<? extends SoyNode> cls : nodeTypes) {
      if (cls.isInstance(node)) {
        found = true;
        return;
      }
    }

    if (node instanceof ParentSoyNode<?>) {
      for (SoyNode child : ((ParentSoyNode<?>) node).getChildren()) {
        // If the node was found, there is no need to continue.
        if (found) {
          return;
        }
        visit(child);
      }
    }
  }
}
