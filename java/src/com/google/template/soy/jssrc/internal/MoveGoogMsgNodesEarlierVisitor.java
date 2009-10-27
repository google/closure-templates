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

package com.google.template.soy.jssrc.internal;

import com.google.common.collect.Lists;
import com.google.template.soy.sharedpasses.BuildNearestDependeeMapVisitor;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.LocalVarInlineNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.jssrc.GoogMsgNode;

import java.util.List;
import java.util.Map;


/**
 * Visitor for moving {@code GoogMsgNode}s to earlier locations in the template. This allows for
 * more efficient code generation because the associated {@code GoogMsgRefNode}s can then be
 * combined with neighboring nodes for output code generation. 
 *
 * <p> {@link #exec} must be called on a full parse tree.
 *
 * @author Kai Huang
 */
class MoveGoogMsgNodesEarlierVisitor extends AbstractSoyNodeVisitor<Void> {


  /** The list of GoogMsgNodes found. */
  private List<GoogMsgNode> googMsgNodes;


  @Override protected void setup() {
    googMsgNodes = Lists.newArrayList();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(SoyFileSetNode node) {

    // We find all the GoogMsgNodes before moving them because we don't want the modifications to
    // interfere with the traversal.

    // This pass finds all the GoogMsgNodes.
    visitChildren(node);

    // Move each GoogMsgNode to the earliest point it can go.
    Map<SoyNode, SoyNode> nearestDependeeMap = (new BuildNearestDependeeMapVisitor()).exec(node);
    for (GoogMsgNode googMsgNode : googMsgNodes) {
      moveGoogMsgNodeEarlierHelper(googMsgNode, nearestDependeeMap.get(googMsgNode));
    }
  }


  @Override protected void visitInternal(GoogMsgNode node) {
    googMsgNodes.add(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(SoyNode node) {
    // Nothing to do for other nodes.
  }


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
    visitChildren(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  @SuppressWarnings("unchecked")  // casts with generics
  private void moveGoogMsgNodeEarlierHelper(GoogMsgNode googMsgNode, SoyNode nearestDependee) {

    ParentSoyNode<SoyNode> newParent;
    int indexUnderNewParent;

    if (nearestDependee instanceof LocalVarInlineNode) {
      newParent = (ParentSoyNode<SoyNode>) nearestDependee.getParent();
      indexUnderNewParent = newParent.getChildIndex(nearestDependee) + 1;
    } else if (nearestDependee instanceof ParentSoyNode) {
      newParent = (ParentSoyNode<SoyNode>) nearestDependee;
      indexUnderNewParent = 0;
    } else {
      throw new AssertionError();
    }

    // Advance the index under the new parent past any GoogMsgNodes already at that location. Also,
    // if we end up finding the exact GoogMsgNode that we're currently trying to move, then we're
    // done because it's already at the location we want to move it to.
    List<SoyNode> siblings = newParent.getChildren();
    while (indexUnderNewParent < siblings.size() &&
           siblings.get(indexUnderNewParent) instanceof GoogMsgNode) {
      if (googMsgNode == siblings.get(indexUnderNewParent)) {
        // Same exact node, so we're done.
        return;
      }
      indexUnderNewParent++;
    }

    // Move the node.
    ((ParentSoyNode<SoyNode>) googMsgNode.getParent()).removeChild(googMsgNode);
    newParent.addChild(indexUnderNewParent, googMsgNode);
  }

}
