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

package com.google.template.soy.soytree;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Node representing a contiguous raw text section.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class RawTextNode extends AbstractSoyNode implements StandaloneNode {


  /** The special chars we need to re-escape for toSourceString(). */
  private static final Pattern SPECIAL_CHARS_TO_ESCAPE = Pattern.compile("[\n\r\t{}]");

  /** Map from special char to be re-escaped to its special char tag (for toSourceString()). */
  private static final Map<String, String> SPECIAL_CHAR_TO_TAG =
      ImmutableMap.<String, String>builder()
          .put("\n", "{\\n}").put("\r", "{\\r}").put("\t", "{\\t}")
          .put("{", "{lb}").put("}", "{rb}")
          .build();


  /** The raw text string (after processing of special chars and literal blocks). */
  private final String rawText;

  @Nullable private HtmlContext htmlContext;

  /**
   * @param id The id for this node.
   * @param rawText The raw text string.
   * @param sourceLocation The node's source location.
   */
  public RawTextNode(int id, String rawText, SourceLocation sourceLocation) {
    super(id, sourceLocation);
    this.rawText = rawText;
  }

  public RawTextNode(int id, String rawText, SourceLocation sourceLocation,
      HtmlContext htmlContext) {
    super(id, sourceLocation);
    this.rawText = rawText;
    this.htmlContext = htmlContext;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  private RawTextNode(RawTextNode orig, CopyState copyState) {
    super(orig, copyState);
    this.rawText = orig.rawText;
    this.htmlContext = orig.htmlContext;
  }

  /**
   * Gets the HTML source context (typically tag, attribute value, HTML PCDATA, or plain text) which
   * this node emits in. This is used for incremental DOM codegen.
   */
  public HtmlContext getHtmlContext() {
    return Preconditions.checkNotNull(htmlContext,
        "Cannot access HtmlContext before HtmlTransformVisitor");
  }

  @Override public Kind getKind() {
    return Kind.RAW_TEXT_NODE;
  }

  public void setHtmlContext(HtmlContext value) {
    this.htmlContext = value;
  }


  /** Returns the raw text string (after processing of special chars and literal blocks). */
  public String getRawText() {
    return rawText;
  }


  @Override public String toSourceString() {

    StringBuffer sb = new StringBuffer();

    // Must escape special chars to create valid source text.
    Matcher matcher = SPECIAL_CHARS_TO_ESCAPE.matcher(rawText);
    while (matcher.find()) {
      String specialCharTag = SPECIAL_CHAR_TO_TAG.get(matcher.group());
      matcher.appendReplacement(sb, Matcher.quoteReplacement(specialCharTag));
    }
    matcher.appendTail(sb);

    return sb.toString();
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public RawTextNode copy(CopyState copyState) {
    return new RawTextNode(this, copyState);
  }

}
