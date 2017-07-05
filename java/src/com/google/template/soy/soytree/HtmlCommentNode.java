/*
 * Copyright 2017 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

/**
 * Node representing a HTML comment. This is basically a {@code RawTextNode} with given
 * prefix/suffix.
 */
public final class HtmlCommentNode extends AbstractParentSoyNode<StandaloneNode>
    implements StandaloneNode {

  /**
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   */
  public HtmlCommentNode(int id, SourceLocation sourceLocation) {
    super(id, sourceLocation);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private HtmlCommentNode(HtmlCommentNode orig, CopyState copyState) {
    super(orig, copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.HTML_COMMENT_NODE;
  }

  @Override
  public HtmlCommentNode copy(CopyState copyState) {
    return new HtmlCommentNode(this, copyState);
  }

  @SuppressWarnings("unchecked")
  @Override
  public final ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append("<!--");
    for (StandaloneNode node : this.getChildren()) {
      sb.append(node.toSourceString());
    }
    sb.append("-->");
    return sb.toString();
  }
}
