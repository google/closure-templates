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
 * A Node representing the end of an open tag of an HTML Element. This must appear following an
 * {@link HtmlOpenTagStartNode} and only once per {@link HtmlOpenTagStartNode}. This node
 * corresponds to the closing angle bracket, {@code >}, in {@code <div ... >}.
 */
public final class HtmlOpenTagEndNode extends AbstractSoyNode
    implements StandaloneNode {

  private final String tagName;

  private final InferredElementNamespace namespace;
  
  /**
   * @param id The id for this node.
   * @param tagName The tagName for this tag.
   * @param namespace The namespace for this tag.
   * @param sourceLocation The node's source location.
   */
  public HtmlOpenTagEndNode(
      int id, String tagName, InferredElementNamespace namespace, SourceLocation sourceLocation) {
    super(id, sourceLocation);
    this.tagName = tagName;
    this.namespace = namespace;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private HtmlOpenTagEndNode(HtmlOpenTagEndNode orig, CopyState copyState) {
    super(orig, copyState);
    tagName = orig.tagName;
    namespace = orig.namespace;
  }

  public String getTagName() {
    return tagName;
  }

  public InferredElementNamespace getNamespace() {
    return namespace;
  }

  @Override public Kind getKind() {
    return Kind.HTML_OPEN_TAG_END;
  }

  @Override public String toSourceString() {
    return ">";
  }

  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public SoyNode copy(CopyState copyState) {
    return new HtmlOpenTagEndNode(this, copyState);
  }
  
}
