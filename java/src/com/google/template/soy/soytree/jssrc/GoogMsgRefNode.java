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

package com.google.template.soy.soytree.jssrc;

import com.google.template.soy.soytree.AbstractSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;


/**
 * Node representing a reference of a message variable (defined by {@code goog.getMsg}).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class GoogMsgRefNode extends AbstractSoyNode implements StandaloneNode {


  /** The name of the Closure message variable (defined by goog.getMsg). */
  private final String renderedGoogMsgVarName;


  /**
   * @param id The id for this node.
   * @param renderedGoogMsgVarName The name of the Closure message variable
   * (defined by goog.getMsg).
   */
  public GoogMsgRefNode(int id, String renderedGoogMsgVarName) {
    super(id);
    this.renderedGoogMsgVarName = renderedGoogMsgVarName;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected GoogMsgRefNode(GoogMsgRefNode orig) {
    super(orig);
    this.renderedGoogMsgVarName = orig.renderedGoogMsgVarName;
  }


  @Override public Kind getKind() {
    return Kind.GOOG_MSG_REF_NODE;
  }


  /** Returns the name of the Closure message variable (defined by goog.getMsg). */
  public String getRenderedGoogMsgVarName() {
    return renderedGoogMsgVarName;
  }


  @Override public String toSourceString() {
    return "[GoogMsgRefNode " + renderedGoogMsgVarName + "]";
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public GoogMsgRefNode clone() {
    return new GoogMsgRefNode(this);
  }

}
