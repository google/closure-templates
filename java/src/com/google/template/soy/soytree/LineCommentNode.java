/*
 * Copyright 2019 Google Inc.
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

import static com.google.common.base.CharMatcher.whitespace;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

/**
 * Node representing // line comment$.
 *
 */
public final class LineCommentNode extends AbstractSoyNode
    implements StandaloneNode, StatementNode {

  private final String comment;

  public LineCommentNode(int id, String comment, SourceLocation sourceLocation) {
    super(id, sourceLocation);
    String trimmed = whitespace().trimLeadingFrom(comment);
    checkArgument(
        trimmed.length() < comment.length() && trimmed.startsWith("//"),
        "Line comment must start with ' //': %s",
        comment);
    this.comment = whitespace().trimFrom(trimmed.substring(2));
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private LineCommentNode(LineCommentNode orig, CopyState copyState) {
    super(orig, copyState);
    this.comment = orig.comment;
  }

  public String getCommentText() {
    return comment;
  }

  /** Escapes `*\/` in the commment text. */
  public String getEscapedCommentText() {
    return comment.replace("*/", "*&#47;");
  }

  @Override
  public Kind getKind() {
    return Kind.LINE_COMMENT_NODE;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  @Override
  public LineCommentNode copy(CopyState copyState) {
    return new LineCommentNode(this, copyState);
  }

  @Override
  public String toSourceString() {
    return " // " + comment;
  }
}
