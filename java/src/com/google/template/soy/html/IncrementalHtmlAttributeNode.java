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
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

/**
 * A Node representing an HTML attribute. An attribute can have one or more children, such as raw
 * text, an {@link IfNode}, and {@link PrintNode}, etc. that make up the attribute's value. The
 * values of the children are combined during render to generate the attribute's value.
 */
public final class IncrementalHtmlAttributeNode extends AbstractParentSoyNode<StandaloneNode>
    implements StandaloneNode, BlockNode {

  private final String name;

  /**
   * @param id The id for this node.
   * @param name The attribute's name.
   * @param sourceLocation The node's source location.
   */
  public IncrementalHtmlAttributeNode(int id, String name, SourceLocation sourceLocation) {
    super(id, sourceLocation);
    this.name = name;
  }

  private IncrementalHtmlAttributeNode(IncrementalHtmlAttributeNode orig, CopyState copyState) {
    super(orig, copyState);
    name = orig.name;
  }

  @Override
  public Kind getKind() {
    return Kind.INCREMENTAL_HTML_ATTRIBUTE;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder(" ").append(name).append("=\"");

    for (SoyNode child : this.getChildren()) {
      sb.append(child.toSourceString());
    }

    return sb.append("\"").toString();
  }

  @Override
  public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public SoyNode copy(CopyState copyState) {
    return new IncrementalHtmlAttributeNode(this, copyState);
  }
}
