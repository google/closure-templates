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
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

/** An Html attributed value with optional surrounding quotation marks. */
public final class HtmlAttributeValueNode extends AbstractParentSoyNode<StandaloneNode>
    implements StandaloneNode {

  /** The quotation mark style. */
  public enum Quotes {
    DOUBLE("\""),
    SINGLE("'"),
    NONE("");

    private final String qMark;

    Quotes(String qMark) {
      this.qMark = qMark;
    }

    public String getQuotationCharacter() {
      return qMark;
    }
  }

  private final Quotes quotes;

  public HtmlAttributeValueNode(int id, SourceLocation location, Quotes quotes) {
    super(id, location);
    this.quotes = quotes;
  }

  private HtmlAttributeValueNode(HtmlAttributeValueNode orig, CopyState copyState) {
    super(orig, copyState);
    this.quotes = orig.quotes;
  }

  public Quotes getQuotes() {
    return quotes;
  }

  @Override
  public Kind getKind() {
    return Kind.HTML_ATTRIBUTE_VALUE_NODE;
  }

  @Override
  public SoyNode copy(CopyState copyState) {
    return new HtmlAttributeValueNode(this, copyState);
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(quotes.qMark);
    appendSourceStringForChildren(sb);
    sb.append(quotes.qMark);
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }
}
