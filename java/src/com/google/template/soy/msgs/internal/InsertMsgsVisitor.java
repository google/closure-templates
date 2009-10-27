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

package com.google.template.soy.msgs.internal;

import com.google.common.collect.Lists;
import com.google.template.soy.base.IdGenerator;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.sharedpasses.CombineConsecutiveRawTextNodesVisitor;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

import java.util.List;

import javax.annotation.Nullable;


/**
 * Visitor for inserting translated messages into Soy parse tree. This pass replaces the MsgNodes
 * in the parse tree with sequences of RawTextNodes and other nodes. Also, if the replacement of a
 * MsgNode causes consecutive sibling RawTextNodes to appear, those consecutive nodes will be
 * joined into one combined RawTextNode. After this pass, the parse tree should no longer contain
 * MsgNodes and MsgHtmlTagNodes.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class InsertMsgsVisitor extends AbstractSoyNodeVisitor<Void> {


  /** The bundle of translated messages, or null to use the messages from the Soy source. */
  private final SoyMsgBundle msgBundle;

  /** The node id generator for the parse tree. Retrieved from the root SoyFileSetNode. */
  private IdGenerator nodeIdGen;

  /** The new rebuilt list of children for the current parent node (during a pass). */
  private List<SoyNode> currNewChildren;


  /**
   * @param msgBundle The bundle of translated messages, or null to use the messages from the
   *     Soy source.
   */
  public InsertMsgsVisitor(@Nullable SoyMsgBundle msgBundle) {
    this.msgBundle = msgBundle;
  }


  @Override public Void exec(SoyNode node) {

    // Retrieve the node id generator from the root of the parse tree.
    nodeIdGen = node.getNearestAncestor(SoyFileSetNode.class).getNodeIdGen();

    // Execute the pass.
    super.exec(node);

    // The pass may have created consecutive RawTextNodes, so clean them up.
    (new CombineConsecutiveRawTextNodesVisitor()).exec(node);

    return null;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(MsgNode node) {

    long msgId = MsgUtils.computeMsgId(node);
    SoyMsg soyMsg = (msgBundle == null) ? null : msgBundle.getMsg(msgId);
    if (soyMsg != null) {
      // Case 1: Localized message is provided by the msgBundle.
      for (SoyMsgPart msgPart : soyMsg.getParts()) {

        if (msgPart instanceof SoyMsgRawTextPart) {
          // Append a new RawTextNode to the currNewChildren list.
          String rawText = ((SoyMsgRawTextPart) msgPart).getRawText();
          currNewChildren.add(new RawTextNode(nodeIdGen.genStringId(), rawText));

        } else if (msgPart instanceof SoyMsgPlaceholderPart) {
          // First get the representative placeholder node.
          String placeholderName = ((SoyMsgPlaceholderPart) msgPart).getPlaceholderName();
          MsgPlaceholderNode placeholderNode = node.getPlaceholderNode(placeholderName);
          // If the representative placeholder node is a MsgHtmlTagNode, it needs to be replaced by
          // a number of consecutive siblings. This is done by visiting the MsgHtmlTagNode.
          // Otherwise, we simply add the placeholder node to the currNewChildren list being built.
          if (placeholderNode instanceof MsgHtmlTagNode) {
            visit(placeholderNode);
          } else {
            currNewChildren.add(placeholderNode);
          }

        } else {
          throw new AssertionError();
        }
      }

    } else {
      // Case 2: No msgBundle or message not found. Just use the message from the Soy source.
      for (SoyNode child : node.getChildren()) {
        // If the child is a MsgHtmlTagNode, it needs to be replaced by a number of consecutive
        // siblings. This is done by visiting the MsgHtmlTagNode. Otherwise, we simply add the
        // child to the currNewChildren list being built.
        if (child instanceof MsgHtmlTagNode) {
          visit((MsgHtmlTagNode) child);
        } else {
          currNewChildren.add(child);
        }
      }
    }
  }


  @Override protected void visitInternal(MsgHtmlTagNode node) {

    for (SoyNode child : node.getChildren()) {
      currNewChildren.add(child);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {

    // Loop through children and (a) check whether there are any MsgNode children, (b) recurse on
    // ParentSoyNode children (other than MsgNode).
    boolean hasMsgNodeChild = false;
    for (SoyNode child : node.getChildren()) {
      if (child instanceof MsgNode) {
        hasMsgNodeChild = true;
      } else if (child instanceof ParentSoyNode) {
        visit(child);  // recurse
      }
    }
    // If there aren't any MsgNode children, we're done.
    if (!hasMsgNodeChild) {
      return;
    }

    // If there are MsgNode children, then this node must be a ParentSoyNode<SoyNode>.
    @SuppressWarnings("unchecked")
    ParentSoyNode<SoyNode> nodeCast = (ParentSoyNode<SoyNode>) node;

    // Build the new children list in currNewChildren. All the children stay the same except for
    // MsgNode children. Each MsgNode child is replaced by a number of consecutive siblings.
    currNewChildren = Lists.newArrayList();
    for (SoyNode child : nodeCast.getChildren()) {
      if (child instanceof MsgNode) {
        visit((MsgNode) child);
      } else {
        currNewChildren.add(child);
      }
    }

    // Set the currNewChildren as the new children of this node.
    nodeCast.clearChildren();
    nodeCast.addChildren(currNewChildren);
  }

}
