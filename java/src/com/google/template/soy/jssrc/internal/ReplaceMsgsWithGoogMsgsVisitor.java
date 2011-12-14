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
import com.google.inject.Inject;
import com.google.template.soy.base.IdGenerator;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.jssrc.GoogMsgNode;
import com.google.template.soy.soytree.jssrc.GoogMsgRefNode;

import java.util.List;


/**
 * Visitor for replacing {@code MsgNode}s with corresponding pairs of {@code GoogMsgNode}s and
 * {@code GoogMsgRefNode}s.
 *
 * <p> {@link #exec} must be called on a full parse tree.
 *
 */
class ReplaceMsgsWithGoogMsgsVisitor extends AbstractSoyNodeVisitor<Void> {


  /** The options for generating JS source code. */
  private final SoyJsSrcOptions jsSrcOptions;

  /** The list of MsgNodes found in the given node's subtree. */
  private List<MsgNode> msgNodes;


  @Inject
  public ReplaceMsgsWithGoogMsgsVisitor(SoyJsSrcOptions jsSrcOptions) {
    this.jsSrcOptions = jsSrcOptions;
  }


  @Override public Void exec(SoyNode node) {
    msgNodes = Lists.newArrayList();
    visit(node);
    return null;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {

    // We find all the MsgNodes before replacing them because we don't want the modifications to
    // interfere with the traversal.

    // This pass simply finds all the MsgNodes.
    visitChildren(node);

    // Perform the replacments.
    IdGenerator nodeIdGen = node.getNearestAncestor(SoyFileSetNode.class).getNodeIdGenerator();
    for (MsgNode msgNode : msgNodes) {
      replaceMsgNodeHelper(msgNode, nodeIdGen);
    }
  }


  @Override protected void visitMsgNode(MsgNode node) {
    msgNodes.add(node);
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


  private void replaceMsgNodeHelper(MsgNode msgNode, IdGenerator nodeIdGen) {

    int googMsgNodeId = nodeIdGen.genId();
    String googMsgVarName = jsSrcOptions.googMsgsAreExternal() ?
        "MSG_EXTERNAL_" + MsgUtils.computeMsgId(msgNode) : "MSG_UNNAMED_" + googMsgNodeId;

    GoogMsgNode googMsgNode = new GoogMsgNode(googMsgNodeId, msgNode, googMsgVarName);
    GoogMsgRefNode googMsgRefNode = new GoogMsgRefNode(nodeIdGen.genId(),
        googMsgNode.getRenderedGoogMsgVarName());

    BlockNode parent = msgNode.getParent();
    int msgNodeIndex = parent.getChildIndex(msgNode);
    parent.replaceChild(msgNodeIndex, googMsgNode);
    parent.addChild(msgNodeIndex + 1, googMsgRefNode);
  }

}
