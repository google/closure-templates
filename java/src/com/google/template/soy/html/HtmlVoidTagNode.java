/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.html;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.AbstractParentSoyNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

/**
 * A node representing an HTML Element with no children. This is equivalent to
 * an {@link HtmlOpenTagNode} followed immediately by an
 * {@link HtmlCloseTagNode}.
 */
public final class HtmlVoidTagNode
    extends AbstractParentSoyNode<StandaloneNode> implements StandaloneNode, BlockNode {

  private final String tagName;

  /**
   * @param id The id for this node.
   * @param tagName The tagName for this tag.
   * @param sourceLocation The node's source location.
   */
  public HtmlVoidTagNode(int id, String tagName, SourceLocation sourceLocation) {
    super(id, sourceLocation);
    this.tagName = tagName;
  }

  private HtmlVoidTagNode(HtmlVoidTagNode orig, CopyState copyState) {
    super(orig, copyState);
    tagName = orig.tagName;
  }

  @Override public Kind getKind() {
    return Kind.HTML_VOID_TAG;
  }

  /**
   * @return The tagName for this tag.
   */
  public String getTagName() {
    return tagName;
  }

  @Override public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append("<" + tagName);

    for (SoyNode child : getChildren()) {
      sb.append(child.toSourceString());
    }

    return sb.append("></" + tagName + ">").toString();
  }

  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public SoyNode copy(CopyState copyState) {
    return new HtmlVoidTagNode(this, copyState);
  }

}
