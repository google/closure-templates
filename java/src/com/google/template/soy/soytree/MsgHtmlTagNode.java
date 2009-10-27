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
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderNode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Node representing an HTML tag within a 'msg' statement/block.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class MsgHtmlTagNode extends AbstractParentSoyNode<SoyNode> implements MsgPlaceholderNode {


  /** Pattern for matching the tag name in the initial raw text. */
  private static final Pattern TAG_NAME_PATTERN =
      Pattern.compile("(?<= ^< ) /? [a-zA-Z0-9]+", Pattern.COMMENTS);

  /**
   * Map from lower-case HTML tag name to human-readable placeholder name. For HTML tags not lised
   * here, the base placeholder name should simply be the tag name in all caps.
   */
  private static final Map<String, String> LC_TAG_NAME_TO_PLACEHOLDER_NAME_MAP =
      ImmutableMap.<String, String>builder()
          .put("a", "link").put("br", "break").put("b", "bold").put("i", "italic")
          .put("li", "item").put("ol", "ordered_list").put("ul", "unordered_list")
          .put("p", "paragraph").put("img", "image").put("em", "emphasis")
          .build();


  /** The lower-case HTML tag name (includes '/' for end tags). */
  private final String lcTagName;

  /** Whether this HTML tag is self-ending (i.e. ends with "/>") */
  private final boolean isSelfEnding;

  /** Whether this HTML tag only has raw text (i.e. has only a single RawTextNode child). */
  private final boolean isOnlyRawText;

  /** Only applicable when isOnlyRawText is true. A hash of the full tag text. */
  private final int fullTagTextHash;

  /** The generated base placeholder name, or null if it has not yet been generated. */
  private String basePlaceholderName;


  /**
   * @param id The id for this node.
   * @param sourceContent The source content of this HTML tag.
   * @param isOnlyRawText Whether this HTML tag only has raw text (i.e. will have only a single
   *     RawTextNode child).
   * @throws SoySyntaxException If a syntax error is found.
   */
  public MsgHtmlTagNode(String id, String sourceContent, boolean isOnlyRawText)
      throws SoySyntaxException {
    super(id);

    Matcher matcher = TAG_NAME_PATTERN.matcher(sourceContent);
    if (!matcher.find()) {
      if (sourceContent.startsWith("!--")) {
        throw new SoySyntaxException(
            "Found HTML comment within 'msg' block: " + sourceContent);
      } else {
        throw new SoySyntaxException(
            "HTML tag within 'msg' block has no tag name: " + sourceContent);
      }
    }
    lcTagName = matcher.group().toLowerCase();

    isSelfEnding = sourceContent.endsWith("/>");

    this.isOnlyRawText = isOnlyRawText;
    if (isOnlyRawText) {
      fullTagTextHash = sourceContent.hashCode();
    } else {
      fullTagTextHash = 0;
    }
  }


  /** Returns the lower-case HTML tag name (includes '/' for end tags). */
  public String getLcTagName() {
    return lcTagName;
  }


  @Override public String genBasePlaceholderName() {

    if (basePlaceholderName != null) {
      return basePlaceholderName;
    }

    boolean isEndTag;
    String baseLcTagName;
    if (lcTagName.startsWith("/")) {
      isEndTag = true;
      baseLcTagName = lcTagName.substring(1);
    } else {
      isEndTag = false;
      baseLcTagName = lcTagName;
    }
    basePlaceholderName = LC_TAG_NAME_TO_PLACEHOLDER_NAME_MAP.containsKey(baseLcTagName) ?
                          LC_TAG_NAME_TO_PLACEHOLDER_NAME_MAP.get(baseLcTagName) : baseLcTagName;
    if (isEndTag) {
      basePlaceholderName = "end_" + basePlaceholderName;
    } else if (!isSelfEnding) {
      basePlaceholderName = "start_" + basePlaceholderName;
    }
    basePlaceholderName = basePlaceholderName.toUpperCase();

    return basePlaceholderName;
  }


  @Override public boolean isSamePlaceholderAs(MsgPlaceholderNode other) {
    if (!(other instanceof MsgHtmlTagNode)) {
      return false;
    }
    MsgHtmlTagNode otherMhtn = (MsgHtmlTagNode) other;
    return this.isOnlyRawText && otherMhtn.isOnlyRawText &&
           this.fullTagTextHash == otherMhtn.fullTagTextHash;
  }


  @Override public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    appendSourceStringForChildren(sb);
    return sb.toString();
  }

}
