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

package com.google.template.soy.soytree.jssrc;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.soytree.AbstractParentSoyNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.SoyNode.LocalVarInlineNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

import java.util.List;


/**
 * Represents a {@code goog.getMsg*} definition of a group of one or more messages. If more than one
 * message, then they form a fallback list (first message that has a translation will be shown).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> All children are {@code MsgNode}s.
 *
 */
public class GoogMsgDefNode extends AbstractParentSoyNode<MsgNode>
    implements StandaloneNode, SplitLevelTopNode<MsgNode>, LocalVarInlineNode {


  /** Map from child MsgNode to its msg id. */
  private final ImmutableMap<MsgNode, Long> childToMsgIdMap;

  /** The JS var name of the rendered goog msg. */
  private final String renderedGoogMsgVarName;

  /** The (fake) source string we return. */
  private final String sourceString;


  /**
   * Regular constructor. Note that this new node is meant to replace origMsgFbGrpNode, so this
   * constructor will simply move the children from origMsgFbGrpNode to this new node (i.e.
   * origMsgFbGrpNode is destructively modified).
   *
   * @param id The id for this node.
   * @param origMsgFbGrpNode The original MsgFallbackGroupNode that this node is based off. Note
   *     this original node will be destructively modified.
   * @param childMsgIds The list of child msg ids (must correspond to children of origMsgFbGrpNode
   *     by index).
   */
  public GoogMsgDefNode(int id, MsgFallbackGroupNode origMsgFbGrpNode, List<Long> childMsgIds) {
    super(id);

    int numChildren = origMsgFbGrpNode.numChildren();
    Preconditions.checkArgument(childMsgIds.size() == numChildren);

    // Move origMsgFbGrpNode's children under this node.
    this.addChildren(origMsgFbGrpNode.getChildren());

    ImmutableMap.Builder<MsgNode, Long> childToMsgIdMapBuilder = ImmutableMap.builder();
    for (int i = 0; i < numChildren; i++) {
      childToMsgIdMapBuilder.put(getChild(i), childMsgIds.get(i));
    }
    this.childToMsgIdMap = childToMsgIdMapBuilder.build();

    this.renderedGoogMsgVarName = "msg_s" + id;
    this.sourceString =
        "[GoogMsgDefNode " + renderedGoogMsgVarName + " " + origMsgFbGrpNode.toSourceString() + "]";
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected GoogMsgDefNode(GoogMsgDefNode orig) {
    super(orig);
    this.childToMsgIdMap = orig.childToMsgIdMap;  // immutable
    this.renderedGoogMsgVarName = orig.renderedGoogMsgVarName;
    this.sourceString = orig.sourceString;
  }


  @Override public Kind getKind() {
    return Kind.GOOG_MSG_DEF_NODE;
  }


  /** Returns the msg id for the given child. */
  public long getChildMsgId(MsgNode child) {
    return childToMsgIdMap.get(child);
  }


  /** Returns the JS var name of the rendered goog msg. */
  public String getRenderedGoogMsgVarName() {
    return renderedGoogMsgVarName;
  }


  @Override public String getVarName() {
    return getRenderedGoogMsgVarName();
  }


  @Override public String toSourceString() {
    return sourceString;
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public GoogMsgDefNode clone() {
    return new GoogMsgDefNode(this);
  }

}
