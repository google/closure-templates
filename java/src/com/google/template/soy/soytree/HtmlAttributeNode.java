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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import javax.annotation.Nullable;

/**
 * An HtmlAttributeNode is an AST node that marks the extent of one or more html attributes.
 *
 * <p>TODO(lukes): consider adding more structure, possibly splitting into 2 nodes for when we have
 * a single attribute or a dynamic attribute (which may print 0-N attributes dynamically).
 */
public final class HtmlAttributeNode extends AbstractParentSoyNode<StandaloneNode>
    implements StandaloneNode {

  private static final ImmutableMap<String, String> CONCATENATED_ATTRIBUTES =
      ImmutableMap.of(
          "@class", " ", "@style", ";", "@jsdata", ";", "@jsaction", ";", "@jsmodel", ";");

  /** Will be null if this attribute node doesn't have a value. */
  @Nullable private final SourceLocation.Point equalsSignLocation;

  public HtmlAttributeNode(
      int id, SourceLocation location, @Nullable SourceLocation.Point equalsSignLocation) {
    super(id, location);
    this.equalsSignLocation = equalsSignLocation;
  }

  @Nullable
  public String getConcatenationDelimiter() {
    if (getStaticKey() != null && CONCATENATED_ATTRIBUTES.containsKey(getStaticKey())) {
      return CONCATENATED_ATTRIBUTES.get(this.getStaticKey());
    }
    return null;
  }

  public HtmlAttributeNode(
      int id,
      SourceLocation location,
      @Nullable SourceLocation.Point equalsSignLocation,
      boolean isSoyAttr) {
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

  public boolean isSoyAttr() {
    return getStaticKey() != null && getStaticKey().startsWith("@");
  }

  /** Returns the static value, if one exists, or null otherwise. */
  @Nullable
  public String getStaticContent() {
    if (!hasValue()) {
      return null;
    }
    SoyNode value = getChild(1);
    if (!(value instanceof HtmlAttributeValueNode)) {
      return null;
    }
    HtmlAttributeValueNode attrValue = (HtmlAttributeValueNode) value;
    if (attrValue.numChildren() == 0) {
      return "";
    }
    if (attrValue.numChildren() > 1) {
      return null;
    }
    StandaloneNode attrValueNode = attrValue.getChild(0);
    if (!(attrValueNode instanceof RawTextNode)) {
      return null;
    }
    return ((RawTextNode) attrValueNode).getRawText();
  }

  @Nullable
  public String getStaticKey() {
    if (getChild(0) instanceof RawTextNode) {
      return ((RawTextNode) getChild(0)).getRawText();
    }
    return null;
  }

  public SourceLocation getEqualsLocation() {
    checkState(equalsSignLocation != null, "This attribute doesn't have a value");
    return equalsSignLocation.asLocation(getSourceLocation().getFilePath());
  }

  /**
   * Returns {@code true} if the attribute name is static and matches the given name (ignoring
   * case).
   */
  public boolean definitelyMatchesAttributeName(String attributeName) {
    return getChild(0) instanceof RawTextNode
        && Ascii.equalsIgnoreCase(attributeName, ((RawTextNode) getChild(0)).getRawText());
  }

  @Override
  public Kind getKind() {
    return Kind.HTML_ATTRIBUTE_NODE;
  }

  @Override
  public HtmlAttributeNode copy(CopyState copyState) {
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

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }
}
