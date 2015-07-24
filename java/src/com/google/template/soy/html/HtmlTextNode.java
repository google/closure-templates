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
 * A node representing HTML TextNode. Note that the parent of this node is not
 * the HTML node that contains it, but rather the Soy block, such as
 * {@code {if}...{/if}}.
 */
public final class HtmlTextNode extends AbstractSoyNode implements StandaloneNode {

  private final String rawText;

  /**
   * @param id The id for this node.
   * @param rawText The raw text string.
   * @param sourceLocation The node's source location.
   */
  public HtmlTextNode(int id, String rawText, SourceLocation sourceLocation) {
    super(id, sourceLocation);
    this.rawText = rawText;
  }

  private HtmlTextNode(HtmlTextNode orig, CopyState copyState) {
    super(orig, copyState);
    rawText = orig.rawText;
  }

  @Override public Kind getKind() {
    return Kind.HTML_TEXT;
  }

  public String getRawText() {
    return rawText;
  }

  @Override public String toSourceString() {
    return rawText;
  }

  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public SoyNode copy(CopyState copyState) {
    return new HtmlTextNode(this, copyState);
  }

}
