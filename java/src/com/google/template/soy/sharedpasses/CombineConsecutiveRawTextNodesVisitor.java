/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.sharedpasses;

import com.google.common.collect.Lists;
import com.google.template.soy.base.IdGenerator;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

import java.util.List;


/**
 * Visitor for combining any consecutive sequences of {@code RawTextNode}s into one equivalent
 * {@code RawTextNode}.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class CombineConsecutiveRawTextNodesVisitor extends AbstractSoyNodeVisitor<Void> {


  /** The node id generator for the parse tree. Retrieved from the root SoyFileSetNode. */
  private IdGenerator nodeIdGen;


  @Override public Void exec(SoyNode node) {

    // Retrieve the node id generator from the root of the parse tree.
    nodeIdGen = node.getNearestAncestor(SoyFileSetNode.class).getNodeIdGen();

    // Execute the pass.
    return super.exec(node);
  }


  @Override protected void visitInternal(SoyNode node) {
    // Nothing to do for other nodes.
  }


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {

    visitChildren(node);

    // Check whether there are any consecutive RawTextNode children.
    boolean hasConsecRawTextNodes = false;
    for (int i = 0; i <= node.numChildren() - 2; i++) {
      if (node.getChild(i) instanceof RawTextNode && node.getChild(i+1) instanceof RawTextNode) {
        hasConsecRawTextNodes = true;
        break;
      }
    }
    // If there aren't any consecutive RawTextNode children, we're done.
    if (!hasConsecRawTextNodes) {
      return;
    }

    // If there are RawTextNode children, then this node must be a ParentSoyNode<SoyNode>.
    @SuppressWarnings("unchecked")
    ParentSoyNode<SoyNode> nodeCast = (ParentSoyNode<SoyNode>) node;

    // Rebuild the list of children, combining consecutive RawTextNodes into one.
    List<SoyNode> children = nodeCast.getChildren();
    nodeCast.clearChildren();

    List<RawTextNode> consecutiveRawTextNodes = Lists.newArrayList();
    for (SoyNode child : children) {

      if (child instanceof RawTextNode) {
        consecutiveRawTextNodes.add((RawTextNode) child);

      } else {
        // First add the preceding consecutive RawTextNodes, if any.
        addConsecutiveRawTextNodesAsOneNodeHelper(nodeCast, consecutiveRawTextNodes);
        consecutiveRawTextNodes.clear();
        // Then add the current new child.
        nodeCast.addChild(child);
      }
    }

    // Add the final group of consecutive RawTextNodes, if any.
    addConsecutiveRawTextNodesAsOneNodeHelper(nodeCast, consecutiveRawTextNodes);
    consecutiveRawTextNodes.clear();
  }


  /**
   * Helper to add consecutive RawTextNodes as one child node (the raw text will be joined).
   * If the consecutive RawTextNodes list actually only has one item, then adds that node instead
   * of creating a new RawTextNode.
   *
   * Note: This function works closely with the above code. In particular, it assumes we're
   * rebuilding the whole list (thus adding to the end of the parent) instead of fixing the old
   * list in-place.
   *
   * @param parent The parent to add the new child to.
   * @param consecutiveRawTextNodes The list of consecutive RawTextNodes.
   */
  private void addConsecutiveRawTextNodesAsOneNodeHelper(
      ParentSoyNode<? super RawTextNode> parent, List<RawTextNode> consecutiveRawTextNodes) {

    if (consecutiveRawTextNodes.size() == 0) {
      return;
    } else if (consecutiveRawTextNodes.size() == 1) {
      // Simply add the one RawTextNode.
      parent.addChild(consecutiveRawTextNodes.get(0));
    } else {
      // Create a new combined RawTextNode.
      StringBuilder rawText = new StringBuilder();
      for (RawTextNode rtn : consecutiveRawTextNodes) {
        rawText.append(rtn.getRawText());
      }
      parent.addChild(new RawTextNode(nodeIdGen.genStringId(), rawText.toString()));
    }
  }

}
