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
 * A Node representing the open tag of an HTML Element. Note that HTML Elements and TextNodes that
 * are children of this Element are not treated as children in Soy.
 */
public final class IncrementalHtmlOpenTagNode extends AbstractParentSoyNode<StandaloneNode>
    implements StandaloneNode, BlockNode {

  private final String tagName;

  private final InferredElementNamespace namespace;

  /**
   * @param id The id for this node.
   * @param tagName The tagName for this tag.
   * @param sourceLocation The node's source location.
   */
  public IncrementalHtmlOpenTagNode(
      int id, String tagName, InferredElementNamespace namespace, SourceLocation sourceLocation) {
    super(id, sourceLocation);
    this.tagName = tagName;
    this.namespace = namespace;
  }

  private IncrementalHtmlOpenTagNode(IncrementalHtmlOpenTagNode orig, CopyState copyState) {
    super(orig, copyState);
    tagName = orig.tagName;
    namespace = orig.namespace;
  }

  @Override
  public Kind getKind() {
    return Kind.INCREMENTAL_HTML_OPEN_TAG;
  }

  public String getTagName() {
    return tagName;
  }

  public InferredElementNamespace getNamespace() {
    return namespace;
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append("<" + tagName);

    for (SoyNode child : getChildren()) {
      sb.append(child.toSourceString());
    }

    return sb.append(">").toString();
  }

  @Override
  public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public SoyNode copy(CopyState copyState) {
    return new IncrementalHtmlOpenTagNode(this, copyState);
  }
}
