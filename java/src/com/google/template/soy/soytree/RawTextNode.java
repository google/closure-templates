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

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Node representing a contiguous raw text section.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class RawTextNode extends AbstractSoyNode implements StandaloneNode {


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


  /**
   * @param id The id for this node.
   * @param rawText The raw text string.
   */
  public RawTextNode(int id, String rawText) {
    super(id);
    this.rawText = rawText;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected RawTextNode(RawTextNode orig) {
    super(orig);
    this.rawText = orig.rawText;
  }


  @Override public Kind getKind() {
    return Kind.RAW_TEXT_NODE;
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


  @Override public RawTextNode clone() {
    return new RawTextNode(this);
  }

}
