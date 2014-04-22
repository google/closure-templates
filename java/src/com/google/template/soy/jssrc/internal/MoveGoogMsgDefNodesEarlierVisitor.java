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
import com.google.template.soy.sharedpasses.BuildAllDependeesMapVisitor;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.LocalVarInlineNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.jssrc.GoogMsgDefNode;

import java.util.List;
import java.util.Map;


/**
 * Visitor for moving {@code GoogMsgDefNode}s to earlier locations in the template. This allows for
 * more efficient code generation because the associated {@code GoogMsgRefNode}s can then be
 * combined with neighboring nodes for output code generation.
 *
 * <p> {@link #exec} must be called on a full parse tree.
 *
 */
class MoveGoogMsgDefNodesEarlierVisitor extends AbstractSoyNodeVisitor<Void> {


  /** The list of GoogMsgDefNodes found. */
  private List<GoogMsgDefNode> googMsgDefNodes;


  @Override public Void exec(SoyNode node) {
    googMsgDefNodes = Lists.newArrayList();
    visit(node);
    return null;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {

    // We find all the GoogMsgDefNodes before moving them because we don't want the
    // modifications to interfere with the traversal.

    // This pass finds all the GoogMsgDefNodes.
    visitChildren(node);

    // Move each GoogMsgDefNode to the earliest point it can go.
    Map<SoyNode, List<SoyNode>> allDependeesMap = (new BuildAllDependeesMapVisitor()).exec(node);
    for (GoogMsgDefNode googMsgDefNode : googMsgDefNodes) {
      moveGoogMsgDefNodeEarlierHelper(
          googMsgDefNode, allDependeesMap.get(googMsgDefNode));
    }
  }


  @Override protected void visitGoogMsgDefNode(GoogMsgDefNode node) {
    googMsgDefNodes.add(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  private void moveGoogMsgDefNodeEarlierHelper(
      GoogMsgDefNode googMsgDefNode, List<SoyNode> allDependees) {

    BlockNode newParent;
    int indexUnderNewParent;

    SoyNode nearestDependee = allDependees.get(0);
    if (nearestDependee instanceof LocalVarInlineNode) {
      newParent = (BlockNode) nearestDependee.getParent();
      indexUnderNewParent = newParent.getChildIndex((LocalVarInlineNode) nearestDependee) + 1;
    } else if (nearestDependee instanceof BlockNode) {
      newParent = (BlockNode) nearestDependee;
      indexUnderNewParent = 0;
    } else {
      throw new AssertionError();
    }

    // Advance the index under the new parent past any GoogMsgDefNodes already at that location.
    // Also, if we end up finding the exact GoogMsgDefNode that we're currently trying to move, then
    // we're done because it's actually not moving (new location same as old location).
    List<StandaloneNode> siblings = newParent.getChildren();
    while (indexUnderNewParent < siblings.size() &&
           siblings.get(indexUnderNewParent) instanceof GoogMsgDefNode) {
      if (googMsgDefNode == siblings.get(indexUnderNewParent)) {
        // Same exact node, so we're done.
        return;
      }
      indexUnderNewParent++;
    }

    // Move the node.
    googMsgDefNode.getParent().removeChild(googMsgDefNode);
    newParent.addChild(indexUnderNewParent, googMsgDefNode);
  }

}
