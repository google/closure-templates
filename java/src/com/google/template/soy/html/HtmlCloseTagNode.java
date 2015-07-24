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
import com.google.template.soy.soytree.AbstractSoyNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

/**
 * A Node representing the closing tag of an HTML Element.
 */
public final class HtmlCloseTagNode extends AbstractSoyNode
    implements StandaloneNode {

  private final String tagName;

  /**
   * @param id The id for this node.
   * @param tagName The tagName for this tag.
   * @param sourceLocation The node's source location.
   */
  public HtmlCloseTagNode(int id, String tagName, SourceLocation sourceLocation) {
    super(id, sourceLocation);
    this.tagName = tagName;
  }

  private HtmlCloseTagNode(HtmlCloseTagNode orig, CopyState copyState) {
    super(orig, copyState);
    tagName = orig.tagName;
  }

  @Override public Kind getKind() {
    return Kind.HTML_CLOSE_TAG;
  }

  public String getTagName() {
    return tagName;
  }

  @Override public String toSourceString() {
    return "</" + tagName + ">";
  }

  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public SoyNode copy(CopyState copyState) {
    return new HtmlCloseTagNode(this, copyState);
  }

}
