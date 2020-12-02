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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;

/**
 * An HtmlOpenTagNode represents an opening html tag.
 *
 * <p>For example, <code> <{$tag} {$attr1} foo="{$attrvalue}" /> </code>.
 *
 * <p>The first child is guaranteed to be the tag name, any after that are guaranteed to be in
 * attribute context. There is always at least one child.
 */
public final class HtmlOpenTagNode extends HtmlTagNode {

  /**
   * Whether or not the node is a self closing tag because it ends with {@code />} instead of {@code
   * >}.
   */
  private final boolean selfClosing;

  /** Whether or not this node is the root of a soy element. Populated by the SoyElementPass. */
  private boolean isElementRoot;

  /** Whether or not this node is the root of a skip node. Populated by ValidateSkipNodesPass. */
  private boolean isSkipRoot;

  public HtmlOpenTagNode(
      int id,
      StandaloneNode node,
      SourceLocation sourceLocation,
      boolean selfClosing,
      TagExistence tagExistence) {
    super(id, node, sourceLocation, tagExistence);
    this.selfClosing = selfClosing;
  }

  private HtmlOpenTagNode(HtmlOpenTagNode orig, CopyState copyState) {
    super(orig, copyState);
    this.selfClosing = orig.selfClosing;
    this.isElementRoot = orig.isElementRoot;
  }

  @Override
  public Kind getKind() {
    return Kind.HTML_OPEN_TAG_NODE;
  }

  public boolean isSelfClosing() {
    return selfClosing;
  }

  /** Returns true if this is an element root. */
  public boolean isElementRoot() {
    return isElementRoot;
  }

  /** Marks this tag as an element root. */
  public void setElementRoot() {
    isElementRoot = true;
  }

  public boolean isSlot() {
    return getTagName().isStatic()
        && getTagName().getStaticTagName().equals("parameter")
        && (getDirectAttributeNamed("slot") != null);
  }

  /** Returns true if this is an skip root. */
  public boolean isSkipRoot() {
    return isSkipRoot;
  }

  /** Marks this tag as an skip root. */
  public void setSkipRoot() {
    isSkipRoot = true;
  }

  public KeyNode getKeyNode() {
    for (SoyNode child : getChildren()) {
      if (child instanceof KeyNode) {
        return ((KeyNode) child);
      }
    }
    return null;
  }

  @Override
  public HtmlOpenTagNode copy(CopyState copyState) {
    return new HtmlOpenTagNode(this, copyState);
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append('<');
    for (int i = 0; i < numChildren(); i++) {
      StandaloneNode child = getChild(i);
      if (i != 0) {
        sb.append(' ');
      }
      sb.append(child.toSourceString());
    }
    sb.append(selfClosing ? "/>" : ">");
    return sb.toString();
  }
}
