// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.template.soy.soytree.jssrc;

import com.google.template.soy.soytree.AbstractParentSoyNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.LocalVarInlineNode;


/**
 * Node representing a {@code goog.getMsg} definition.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author kai@google.com (Kai Huang)
 */
public class GoogMsgNode extends AbstractParentSoyNode<SoyNode> implements LocalVarInlineNode {


  /** The original MsgNode that this node is based off. */
  private final MsgNode origMsgNode;

  /** The name of the Closure message variable (defined by goog.getMsg). */
  private final String googMsgName;


  /**
   * @param id The id for this node.
   * @param origMsgNode The original MsgNode that this node is based off.
   */
  public GoogMsgNode(String id, MsgNode origMsgNode) {
    super(id);
    this.googMsgName = "MSG_UNNAMED_" + getId();

    this.origMsgNode = origMsgNode;
    this.addChildren(origMsgNode.getChildren());
  }


  /** Returns the name of the Closure message variable (defined by goog.getMsg). */
  public String getGoogMsgName() {
    return googMsgName;
  }

  /** Returns the meaning string if set, otherwise null (usually null). */
  public String getMeaning() {
    return origMsgNode.getMeaning();
  }

  /** Returns the description string for translators. */
  public String getDesc() {
    return origMsgNode.getDesc();
  }

  /** Returns whether the message should be added as 'hidden' in the TC. */
  public boolean isHidden() {
    return origMsgNode.isHidden();
  }


  /**
   * Gets the representative placeholder node for a given placeholder name.
   * @param placeholderName The placeholder name.
   * @return The representative placeholder node for the given placeholder name.
   */
  public MsgPlaceholderNode getPlaceholderNode(String placeholderName) {
    return origMsgNode.getPlaceholderNode(placeholderName);
  }


  /**
   * Gets the placeholder name for a given placeholder node.
   * @param placeholderNode The placeholder node.
   * @return The placeholder name for the given placeholder node.
   */
  public String getPlaceholderName(MsgPlaceholderNode placeholderNode) {
    return origMsgNode.getPlaceholderName(placeholderNode);
  }


  @Override public String getLocalVarName() {
    return googMsgName;
  }


  @Override public String toSourceString() {
    return "[GoogMsgNode " + getLocalVarName() + " " + origMsgNode.toSourceString() + "]";
  }

}
