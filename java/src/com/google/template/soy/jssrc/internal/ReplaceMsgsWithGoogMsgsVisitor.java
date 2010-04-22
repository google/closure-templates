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
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.jssrc.GoogMsgNode;
import com.google.template.soy.soytree.jssrc.GoogMsgRefNode;

import java.util.List;


/**
 * Visitor for replacing {@code MsgNode}s with corresponding pairs of {@code GoogMsgNode}s and
 * {@code PrintGoogMsgNode}s.
 *
 * <p> {@link #exec} must be called on a full parse tree.
 *
 * @author Kai Huang
 */
class ReplaceMsgsWithGoogMsgsVisitor extends AbstractSoyNodeVisitor<Void> {


  /** The options for generating JS source code. */
  private final SoyJsSrcOptions jsSrcOptions;

  /** The list of MsgNodes found in the given node's subtree. */
  private List<MsgNode> msgNodes;


  @Inject
  ReplaceMsgsWithGoogMsgsVisitor(SoyJsSrcOptions jsSrcOptions) {
    this.jsSrcOptions = jsSrcOptions;
  }


  @Override protected void setup() {
    msgNodes = Lists.newArrayList();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(SoyFileSetNode node) {

    // We find all the MsgNodes before replacing them because we don't want the modifications to
    // interfere with the traversal.

    // This pass simply finds all the MsgNodes.
    visitChildren(node);

    // Perform the replacments.
    IdGenerator nodeIdGen = node.getNearestAncestor(SoyFileSetNode.class).getNodeIdGen();
    for (MsgNode msgNode : msgNodes) {
      replaceMsgNodeHelper(msgNode, nodeIdGen);
    }
  }


  @Override protected void visitInternal(MsgNode node) {
    msgNodes.add(node);
    visitChildren(node);
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


  private void replaceMsgNodeHelper(MsgNode msgNode, IdGenerator nodeIdGen) {

    String googMsgNodeId = nodeIdGen.genStringId();
    String googMsgName = jsSrcOptions.googMsgsAreExternal() ?
        "MSG_EXTERNAL_" + MsgUtils.computeMsgId(msgNode) : "MSG_UNNAMED_" + googMsgNodeId;

    GoogMsgNode googMsgNode = new GoogMsgNode(googMsgNodeId, msgNode, googMsgName);
    GoogMsgRefNode googMsgRefNode = new GoogMsgRefNode(nodeIdGen.genStringId(), googMsgName);

    @SuppressWarnings("unchecked")  // cast with generics
    ParentSoyNode<SoyNode> parent = (ParentSoyNode<SoyNode>) msgNode.getParent();
    int msgNodeIndex = parent.getChildIndex(msgNode);
    parent.setChild(msgNodeIndex, googMsgNode);
    parent.addChild(msgNodeIndex + 1, googMsgRefNode);
  }

}
