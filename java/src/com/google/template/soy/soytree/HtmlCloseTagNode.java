/*
 * Copyright 2016 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

/**
 * An HtmlCloseTagNode represents a closing html tag.
 *
 * <p>For example, <code> </{$tag}> </code>.
 *
 * <p>TODO(lukes): change the grammar to ensure there is only a single child and it is guaranteed to
 * be the tag name.
 */
public final class HtmlCloseTagNode extends AbstractParentSoyNode<SoyNode.StandaloneNode>
    implements SoyNode.StandaloneNode, SoyNode.BlockNode {

  private final TagName tagName;

  public HtmlCloseTagNode(int id, TagName tagName, SourceLocation sourceLocation) {
    super(id, sourceLocation);
    this.tagName = checkNotNull(tagName);
  }

  private HtmlCloseTagNode(HtmlCloseTagNode orig, CopyState copyState) {
    super(orig, copyState);
    this.tagName = orig.tagName;
  }

  @Override
  public Kind getKind() {
    return Kind.HTML_CLOSE_TAG_NODE;
  }

  public TagName getTagName() {
    return tagName;
  }

  @Override
  public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public HtmlCloseTagNode copy(CopyState copyState) {
    return new HtmlCloseTagNode(this, copyState);
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append("</").append(getTagName());
    for (StandaloneNode child : getChildren()) {
      sb.append(" ").append(child.toSourceString());
    }
    sb.append(">");
    return sb.toString();
  }
}
