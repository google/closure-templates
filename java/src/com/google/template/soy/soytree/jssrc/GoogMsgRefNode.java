// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.template.soy.soytree.jssrc;

import com.google.template.soy.soytree.AbstractSoyNode;


/**
 * Node representing a reference of a message variable (defined by {@code goog.getMsg}).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author kai@google.com (Kai Huang)
 */
public class GoogMsgRefNode extends AbstractSoyNode {


  /** The name of the Closure message variable (defined by goog.getMsg). */
  private final String googMsgName;


  /**
   * @param id The id for this node.
   * @param googMsgName The name of the Closure message variable (defined by goog.getMsg).
   */
  public GoogMsgRefNode(String id, String googMsgName) {
    super(id);
    this.googMsgName = googMsgName;
  }


  /** Returns the name of the Closure message variable (defined by goog.getMsg). */
  public String getGoogMsgName() {
    return googMsgName;
  }


  @Override public String toSourceString() {
    return "[GoogMsgRefNode " + googMsgName + "]";
  }

}
