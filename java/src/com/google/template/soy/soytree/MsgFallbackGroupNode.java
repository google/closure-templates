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

package com.google.template.soy.soytree;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

/**
 * Represents one message or a pair of message and fallback message.
 *
 * <p>Only one {@code fallbackmsg} is allowed by the parser.
 * {@link com.google.template.soy.soyparse.TemplateParserTest.java#testRecognizeCommands}
 * TODO(user): fix the grammar.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> All children are {@code MsgNode}s. And conversely, all {@code MsgNode}s must be children of
 * {@code MsgFallbackGroupNode}s through parsing and middleend passes (backends may have their own
 * special structure for messages).
 *
 */
public final class MsgFallbackGroupNode extends AbstractParentSoyNode<MsgNode>
    implements StandaloneNode, SplitLevelTopNode<MsgNode>, StatementNode {

  /**
   * Escaping directives names (including the vertical bar) to apply to the return value. With
   * strict autoescape, the result of each call site is escaped, which is potentially a no-op if
   * the template's return value is the correct SanitizedContent object.
   */
  private ImmutableList<String> escapingDirectiveNames = ImmutableList.of();

  /**
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   */
  public MsgFallbackGroupNode(int id, SourceLocation sourceLocation) {
    super(id, sourceLocation);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  private MsgFallbackGroupNode(MsgFallbackGroupNode orig) {
    super(orig);
    this.escapingDirectiveNames = orig.escapingDirectiveNames;
  }


  @Override public Kind getKind() {
    return Kind.MSG_FALLBACK_GROUP_NODE;
  }


  @Override public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    // Note: The first MsgNode takes care of generating the 'msg' tag.
    appendSourceStringForChildren(sb);
    sb.append("{/msg}");
    return sb.toString();
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public MsgFallbackGroupNode clone() {
    return new MsgFallbackGroupNode(this);
  }

  /**
   * Sets the inferred escaping directives from the contextual engine.
   */
  public void setEscapingDirectiveNames(ImmutableList<String> escapingDirectiveNames) {
    this.escapingDirectiveNames = escapingDirectiveNames;
  }

  /**
   * Returns the escaping directives, applied from left to right.
   *
   * <p>It is an error to call this before the contextual rewriter has been run.
   */
  public ImmutableList<String> getEscapingDirectiveNames() {
    return escapingDirectiveNames;
  }
}
