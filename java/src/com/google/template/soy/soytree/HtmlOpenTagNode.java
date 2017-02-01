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

/**
 * An HtmlOpenTagNode represents an opening html tag.
 *
 * <p>For example, <code> <{$tag} {$attr1} foo="{$attrvalue}" /> </code>.
 *
 * <p>The first child is guaranteed to be the tag name, any after that are guaranteed to be in
 * attribute context. There is always at least one child.
 *
 * <p>TODO(lukes): Change the parser to remove leading/trailing whitepace from children
 */
public final class HtmlOpenTagNode extends AbstractParentSoyNode<SoyNode.StandaloneNode>
    implements SoyNode.StandaloneNode, SoyNode.BlockNode {
  /**
   * Whether or not the node is a self closing tag because it ends with {@code />} instead of {@code
   * >}.
   */
  private final boolean selfClosing;

  private final TagName tagName;

  // TODO(lukes): consider adding a pointer to the closing tag, if known, or annotating it as a
  // void tag if we know that it is one (e.g. for <br>, <hr>)

  public HtmlOpenTagNode(
      int id, TagName tagName, SourceLocation sourceLocation, boolean selfClosing) {
    super(id, sourceLocation);
    this.tagName = checkNotNull(tagName);
    this.selfClosing = selfClosing;
  }

  private HtmlOpenTagNode(HtmlOpenTagNode orig, CopyState copyState) {
    super(orig, copyState);
    this.selfClosing = orig.selfClosing;
    this.tagName = orig.tagName;
  }

  @Override
  public Kind getKind() {
    return Kind.HTML_OPEN_TAG_NODE;
  }

  public boolean isSelfClosing() {
    return selfClosing;
  }

  public TagName getTagName() {
    return tagName;
  }

  @Override
  public HtmlOpenTagNode copy(CopyState copyState) {
    return new HtmlOpenTagNode(this, copyState);
  }

  @Override
  public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append("<").append(getTagName());
    for (StandaloneNode child : getChildren()) {
      sb.append(" ").append(child.toSourceString());
    }
    sb.append(selfClosing ? "/>" : ">");
    return sb.toString();
  }
}
