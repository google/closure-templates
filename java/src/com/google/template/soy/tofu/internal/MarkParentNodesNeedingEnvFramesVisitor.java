/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.tofu.internal;

import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.LocalVarBlockNode;
import com.google.template.soy.soytree.SoyNode.LocalVarInlineNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;


/**
 * Visitor for marking which parent nodes need env frames during interpretation (using
 * {@code ParentNode.setNeedsEnvFrameDuringInterp()}. This should slightly improve rendering speed.
 *
 * @author Kai Huang
 */
class MarkParentNodesNeedingEnvFramesVisitor extends AbstractSoyNodeVisitor<Void> {


  @Override protected void visitSoyNode(SoyNode node) {

    if (! (node instanceof ParentSoyNode<?>)) {
      return;
    }
    ParentSoyNode<?> nodeAsParent = (ParentSoyNode<?>) node;

    visitChildren(nodeAsParent);

    if (nodeAsParent instanceof LocalVarBlockNode) {
      nodeAsParent.setNeedsEnvFrameDuringInterp(true);

    } else if (nodeAsParent instanceof BlockNode) {
      // For a BlockNode that's not a LocalVarBlockNode, it only needs an env frame during
      // interpretation if it has a LocalVarInlineNode child.
      boolean needsEnvFrameDuringInterp = false;
      for (SoyNode child : nodeAsParent.getChildren()) {
        if (child instanceof LocalVarInlineNode) {
          needsEnvFrameDuringInterp = true;
          break;
        }
      }
      nodeAsParent.setNeedsEnvFrameDuringInterp(needsEnvFrameDuringInterp);

    } else {
      // Non-BlockNodes cannot have LocalVarInlineNode children, thus never need env frames.
      nodeAsParent.setNeedsEnvFrameDuringInterp(false);
    }
  }

}
