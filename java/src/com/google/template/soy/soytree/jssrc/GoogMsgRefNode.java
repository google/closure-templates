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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.AbstractSoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

/**
 * Node representing a reference of a message variable (defined by {@code goog.getMsg}).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class GoogMsgRefNode extends AbstractSoyNode implements StandaloneNode {


  /** The JS var name of the rendered goog msg.. */
  private final String renderedGoogMsgVarName;

  // TODO(gboyer): Consider switching out all references to escaping directive names to
  // the EscapingMode enum, wherever custom print directives are not needed.
  /**
   * Escaping directives names (including the vertical bar) to apply to the return value. With
   * strict autoescape, the result of each call site is escaped, which is potentially a no-op if
   * the template's return value is the correct SanitizedContent object.
   */
  private final ImmutableList<String> escapingDirectiveNames;


  /**
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   * @param renderedGoogMsgVarName The JS var name of the rendered goog msg.
   */
  public GoogMsgRefNode(
      int id,
      SourceLocation sourceLocation,
      String renderedGoogMsgVarName,
      ImmutableList<String> escapingDirectiveNames) {
    super(id, sourceLocation);
    this.renderedGoogMsgVarName = renderedGoogMsgVarName;
    this.escapingDirectiveNames = escapingDirectiveNames;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  private GoogMsgRefNode(GoogMsgRefNode orig, CopyState copyState) {
    super(orig, copyState);
    this.renderedGoogMsgVarName = orig.renderedGoogMsgVarName;
    this.escapingDirectiveNames = orig.escapingDirectiveNames;
  }


  @Override public Kind getKind() {
    return Kind.GOOG_MSG_REF_NODE;
  }


  /** Returns the JS var name of the rendered goog msg. */
  public String getRenderedGoogMsgVarName() {
    return renderedGoogMsgVarName;
  }


  @Override public String toSourceString() {
    return "[GoogMsgRefNode " + renderedGoogMsgVarName + "]";
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public GoogMsgRefNode copy(CopyState copyState) {
    return new GoogMsgRefNode(this, copyState);
  }


  /**
   * Returns the escaping directives, applied from left to right.
   */
  public ImmutableList<String> getEscapingDirectiveNames() {
    return escapingDirectiveNames;
  }
}
