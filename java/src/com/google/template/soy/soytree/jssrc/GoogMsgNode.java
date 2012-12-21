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

import com.google.template.soy.soytree.AbstractMsgNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.SoyNode.LocalVarInlineNode;


/**
 * Node representing a {@code goog.getMsg} definition.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class GoogMsgNode extends AbstractMsgNode implements LocalVarInlineNode {


  /** The (fake) source string we return. */
  private final String sourceString;

  /** The name of the Closure message variable ("MSG_UNNAMED_..." or "MSG_EXTERNAL..."). */
  private final String googMsgVarName;

  /** The JS var name of the rendered goog msg (usually same as googMsgVarName, except when there's
   *  plural/select postprocessing). */
  private final String renderedGoogMsgVarName;


  /**
   * Regular constructor. Note that this new node is meant to replace origMsgNode, so this
   * constructor will simply move the children from origMsgNode to this new node (i.e. origMsgNode
   * is destructively modified).
   *
   * @param id The id for this node.
   * @param origMsgNode The original MsgNode that this node is based off. Note origMsgNode will be
   *     destructively modified.
   * @param googMsgVarName The name of the Closure message variable defined by goog.getMsg, e.g.
   */
  public GoogMsgNode(int id, MsgNode origMsgNode, String googMsgVarName) {
    super(id, origMsgNode);

    // Move origMsgNode's children to be this node's children.
    this.addChildren(origMsgNode.getChildren());

    this.sourceString =
        "[GoogMsgNode " + getVarName() + " " + origMsgNode.toSourceString() + "]";

    this.googMsgVarName = googMsgVarName;
    this.renderedGoogMsgVarName =
        origMsgNode.isPlrselMsg() ? "rendered_" + googMsgVarName : googMsgVarName;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected GoogMsgNode(GoogMsgNode orig) {
    super(orig);
    this.sourceString = orig.sourceString;
    this.googMsgVarName = orig.googMsgVarName;
    this.renderedGoogMsgVarName = orig.renderedGoogMsgVarName;
  }


  @Override public Kind getKind() {
    return Kind.GOOG_MSG_NODE;
  }


  /** Returns the name of the Closure message variable ("MSG_UNNAMED_..." or "MSG_EXTERNAL..."). */
  public String getGoogMsgVarName() {
    return googMsgVarName;
  }


  /**
   * Returns the JS var name of the rendered goog msg (usually same as {@code getGoogMsgVarName()},
   * except when there's plural/select postprocessing).
   */
  public String getRenderedGoogMsgVarName() {
    return renderedGoogMsgVarName;
  }


  @Override public String getVarName() {
    return renderedGoogMsgVarName;
  }


  @Override public String toSourceString() {
    return sourceString;
  }


  @Override public GoogMsgNode clone() {
    return new GoogMsgNode(this);
  }

}
