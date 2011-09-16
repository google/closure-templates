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

package com.google.template.soy.soytree;

import com.google.template.soy.soytree.SoyNode.StandaloneNode;

import javax.annotation.Nullable;


/**
 * A node that is the direct child of a MsgBlockNode and will turn into a placeholder.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class MsgPlaceholderNode extends AbstractBlockNode implements StandaloneNode {


  /** The base placeholder name for this node. */
  private final String basePlaceholderName;

  /** The initial child's SoyNode kind. */
  private final SoyNode.Kind initialContentKind;

  /** Key object for determining whether this node and another node should be represented by the
   *  same placeholder. */
  @Nullable private final Object samenessKey;


  /**
   * @param id The id for this node.
   * @param initialContent Will be set as the only child of this node being created. The child is
   *     the node that represents the content of this placeholder. It is called the initial content
   *     because it may be modified/replaced later by compiler passes.
   */
  public MsgPlaceholderNode(int id, MsgPlaceholderInitialContentNode initialContent) {
    super(id);
    this.basePlaceholderName = initialContent.genBasePlaceholderName();
    this.initialContentKind = initialContent.getKind();
    this.samenessKey = initialContent.genSamenessKey();
    this.addChild(initialContent);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected MsgPlaceholderNode(MsgPlaceholderNode orig) {
    super(orig);
    this.basePlaceholderName = orig.basePlaceholderName;
    this.initialContentKind = orig.initialContentKind;
    this.samenessKey = orig.samenessKey;
  }


  @Override public Kind getKind() {
    return Kind.MSG_PLACEHOLDER_NODE;
  }


  /**
   * Gets the base placeholder name for this node.
   * @return The base placeholder name for this node.
   */
  public String genBasePlaceholderName() {
    return basePlaceholderName;
  }


  /**
   * Determines whether this node and the given other node are the same, such that they should be
   * represented by the same placeholder.
   * @param other The other MsgPlaceholderNode to compare to.
   * @return True if this and the other node should be represented by the same placeholder.
   */
  public boolean isSamePlaceholderAs(MsgPlaceholderNode other) {
    return
        (this.initialContentKind == other.initialContentKind) &&
        ((this.samenessKey == null) ?
            other.samenessKey == null : this.samenessKey.equals(other.samenessKey));
  }


  @Override public String toSourceString() {
    return getChild(0).toSourceString();
  }


  @Override public MsgBlockNode getParent() {
    return (MsgBlockNode) super.getParent();
  }


  @Override public MsgPlaceholderNode clone() {
    return new MsgPlaceholderNode(this);
  }

}
