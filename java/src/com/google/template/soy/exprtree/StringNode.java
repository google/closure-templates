/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.exprtree;

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.StringType;

/**
 * Node representing a string value.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class StringNode extends AbstractPrimitiveNode {

  /** The string value. */
  private final String value;

  private final QuoteStyle quoteStyle;

  private final boolean isXid;

  /**
   * @param value The string value.
   * @param sourceLocation The node's source location.
   */
  public StringNode(String value, QuoteStyle quoteStyle, SourceLocation sourceLocation) {
    this(value, quoteStyle, sourceLocation, false);
  }

  public StringNode(
      String value, QuoteStyle quoteStyle, SourceLocation sourceLocation, boolean isXid) {
    super(sourceLocation);
    this.value = Preconditions.checkNotNull(value);
    Preconditions.checkArgument(
        quoteStyle == QuoteStyle.SINGLE || quoteStyle == QuoteStyle.DOUBLE,
        "StringNode quote style must be SINGLE or DOUBLE");
    this.quoteStyle = quoteStyle;
    this.isXid = isXid;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private StringNode(StringNode orig, CopyState copyState) {
    super(orig, copyState);
    this.value = orig.value;
    this.quoteStyle = orig.quoteStyle;
    this.isXid = orig.isXid;
  }

  @Override
  public Kind getKind() {
    return Kind.STRING_NODE;
  }

  @Override
  public StringType getType() {
    return StringType.getInstance();
  }

  /** Returns the string value. */
  public String getValue() {
    return value;
  }

  /** Return the quote style of this string. */
  public QuoteStyle getQuoteStyle() {
    return quoteStyle;
  }

  public boolean isXid() {
    return isXid;
  }

  /**
   * Equivalent to {@code toSourceString(false)}.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public String toSourceString() {
    return toSourceString(false);
  }

  /**
   * Builds a Soy string literal for this string value (including the surrounding single quotes).
   *
   * @param escapeToAscii Whether to escape non-ASCII characters as Unicode hex escapes (backslash +
   *     'u' + 4 hex digits).
   * @return A Soy string literal for this string value (including the surrounding single quotes).
   */
  public String toSourceString(boolean escapeToAscii) {
    return BaseUtils.escapeToWrappedSoyString(value, escapeToAscii, quoteStyle);
  }

  @Override
  public StringNode copy(CopyState copyState) {
    return new StringNode(this, copyState);
  }
}
