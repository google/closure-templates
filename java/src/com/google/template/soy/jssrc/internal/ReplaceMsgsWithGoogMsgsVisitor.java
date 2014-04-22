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
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.jssrc.GoogMsgDefNode;
import com.google.template.soy.soytree.jssrc.GoogMsgRefNode;

import java.util.List;


/**
 * Visitor for replacing {@code MsgFallbackGroupNode}s with corresponding pairs of
 * {@code GoogMsgDefNode}s and {@code GoogMsgRefNode}s.
 *
 * <p> {@link #exec} must be called on a full parse tree.
 *
 */
class ReplaceMsgsWithGoogMsgsVisitor extends AbstractSoyNodeVisitor<Void> {


  /** The list of MsgFallbackGroupNodes found in the given node's subtree. */
  private List<MsgFallbackGroupNode> msgFbGrpNodes;


  @Override public Void exec(SoyNode node) {
    msgFbGrpNodes = Lists.newArrayList();
    visit(node);
    return null;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {

    // We find all the MsgFallbackGroupNodes before replacing them because we don't want the
    // modifications to interfere with the traversal.

    // This pass simply finds all the MsgFallbackGroupNodes.
    visitChildren(node);

    // Perform the replacements.
    IdGenerator nodeIdGen = node.getNearestAncestor(SoyFileSetNode.class).getNodeIdGenerator();
    for (MsgFallbackGroupNode msgFbGrpNode : msgFbGrpNodes) {
      replaceMsgFallbackGroupNodeHelper(msgFbGrpNode, nodeIdGen);
    }
  }


  @Override protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    msgFbGrpNodes.add(node);
    visitChildren(node);
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


  private void replaceMsgFallbackGroupNodeHelper(
      MsgFallbackGroupNode msgFbGrpNode, IdGenerator nodeIdGen) {

    List<Long> childMsgIds = Lists.newArrayListWithCapacity(msgFbGrpNode.numChildren());
    for (MsgNode msgNode : msgFbGrpNode.getChildren()) {
      childMsgIds.add(MsgUtils.computeMsgIdForDualFormat(msgNode));
    }
    GoogMsgDefNode googMsgDefNode =
        new GoogMsgDefNode(nodeIdGen.genId(), msgFbGrpNode, childMsgIds);
    GoogMsgRefNode googMsgRefNode =
        new GoogMsgRefNode(nodeIdGen.genId(), googMsgDefNode.getRenderedGoogMsgVarName());

    BlockNode parent = msgFbGrpNode.getParent();
    int index = parent.getChildIndex(msgFbGrpNode);
    parent.replaceChild(index, googMsgDefNode);
    parent.addChild(index + 1, googMsgRefNode);
  }

}
