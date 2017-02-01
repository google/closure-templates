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

import static com.google.common.base.Preconditions.checkState;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import javax.annotation.Nullable;

/**
 * An HtmlAttributeNode is an AST node that marks the extent of one or more html attributes.
 *
 * <p>TODO(lukes): consider adding more structure, possibly splitting into 2 nodes for when we have
 * a single attribute or a dynamic attribute (which may print 0-N attributes dynamically).
 */
public final class HtmlAttributeNode extends AbstractParentSoyNode<SoyNode.StandaloneNode>
    implements SoyNode.StandaloneNode, SoyNode.BlockNode {

  /** Will be null if this attribute node doesn't have a value. */
  @Nullable private final SourceLocation.Point equalsSignLocation;

  public HtmlAttributeNode(
      int id, SourceLocation location, @Nullable SourceLocation.Point equalsSignLocation) {
    super(id, location);
    this.equalsSignLocation = equalsSignLocation;
  }

  private HtmlAttributeNode(HtmlAttributeNode orig, CopyState copyState) {
    super(orig, copyState);
    this.equalsSignLocation = orig.equalsSignLocation;
  }

  public boolean hasValue() {
    return equalsSignLocation != null;
  }

  public SourceLocation getEqualsLocation() {
    checkState(equalsSignLocation != null, "This attribute doesn't have a value");
    return equalsSignLocation.asLocation(getSourceLocation().getFilePath());
  }

  @Override
  public Kind getKind() {
    return Kind.HTML_ATTRIBUTE_NODE;
  }

  @Override
  public SoyNode copy(CopyState copyState) {
    return new HtmlAttributeNode(this, copyState);
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getChild(0).toSourceString());
    if (hasValue()) {
      sb.append('=');
      sb.append(getChild(1).toSourceString());
    }
    return sb.toString();
  }

  @Override
  public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }
}
