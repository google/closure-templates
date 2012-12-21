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
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;


/**
 * Node representing an HTML tag within a 'msg' statement/block.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class MsgHtmlTagNode extends AbstractBlockNode implements MsgPlaceholderInitialNode {


  /** Pattern for matching the 'phname' attribute. */
  private static final Pattern PHNAME_ATTR_PATTERN =
      Pattern.compile("\\s phname=\" ( [^\"]* ) \"", Pattern.COMMENTS);

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

  /** Only applicable when isOnlyRawText is true. The full tag text. */
  private final @Nullable String fullTagText;

  /** The user-supplied placeholder name, or null if not supplied or not applicable. */
  @Nullable private final String userSuppliedPlaceholderName;


  /**
   * @param id The id for this node.
   * @param children The children nodes representing the content of this HTML tag. The first and
   *     last children must be RawTextNodes (can be the same node if there's only one child). If
   *     there is any 'phname' attribute, it should not have been stripped out yet (this constructor
   *     will handle parsing and stripping any 'phname' attribute).
   * @throws SoySyntaxException If a syntax error is found.
   */
  public MsgHtmlTagNode(int id, List<StandaloneNode> children) throws SoySyntaxException {

    super(id);

    int numChildren = children.size();

    // ------ Strip out the 'phname' attribute (if any). ------
    String userSuppliedPlaceholderName = null;

    for (int i = 0; i < numChildren; i++) {
      StandaloneNode child = children.get(i);
      if (! (child instanceof RawTextNode)) {
        continue;
      }

      boolean didReplaceChild;
      do {
        String rawText = ((RawTextNode) child).getRawText();
        Matcher matcher = PHNAME_ATTR_PATTERN.matcher(rawText);

        if (matcher.find()) {
          if (userSuppliedPlaceholderName != null) {
            throw SoySyntaxException.createWithoutMetaInfo(
                "Found multiple 'phname' attributes in HTML tag (phname=\"" +
                    userSuppliedPlaceholderName + "\" and phname=\"" + matcher.group(1) + "\").");
          }

          userSuppliedPlaceholderName = matcher.group(1);
          if (! BaseUtils.isIdentifier(userSuppliedPlaceholderName)) {
            throw SoySyntaxException.createWithoutMetaInfo(
                "Found 'phname' attribute in HTML tag that is not a valid identifier (phname=\"" +
                    userSuppliedPlaceholderName + "\").");
          }

          RawTextNode replacementChild =
              new RawTextNode(child.getId(), rawText.replaceFirst(matcher.group(), ""));
          children.set(i, replacementChild);

          child = replacementChild;  // set 'child' for next iteration of do-while loop
          didReplaceChild = true;

        } else {
          didReplaceChild = false;
        }

      } while (didReplaceChild);
    }

    this.userSuppliedPlaceholderName = userSuppliedPlaceholderName;

    // ------ Compute other fields. ------
    String firstChildText = ((RawTextNode) children.get(0)).getRawText();
    Matcher matcher = TAG_NAME_PATTERN.matcher(firstChildText);
    if (!matcher.find()) {
      if (firstChildText.startsWith("<!--")) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Found HTML comment within 'msg' block: " + firstChildText);
      } else {
        throw SoySyntaxException.createWithoutMetaInfo(
            "HTML tag within 'msg' block has no tag name: " + firstChildText);
      }
    }
    this.lcTagName = matcher.group().toLowerCase(Locale.ENGLISH);

    String lastChildText = ((RawTextNode) children.get(numChildren - 1)).getRawText();
    this.isSelfEnding = lastChildText.endsWith("/>");

    this.isOnlyRawText = numChildren == 1;

    if (this.isOnlyRawText) {
      StringBuilder fullTagTextSb = new StringBuilder();
      for (StandaloneNode child : children) {
        fullTagTextSb.append(child.toSourceString());
      }
      this.fullTagText = fullTagTextSb.toString();
    } else {
      this.fullTagText = null;
    }

    // ------ Add children. ------
    this.addChildren(children);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected MsgHtmlTagNode(MsgHtmlTagNode orig) {
    super(orig);
    this.lcTagName = orig.lcTagName;
    this.isSelfEnding = orig.isSelfEnding;
    this.isOnlyRawText = orig.isOnlyRawText;
    this.fullTagText = orig.fullTagText;
    this.userSuppliedPlaceholderName = orig.userSuppliedPlaceholderName;
  }


  @Override public Kind getKind() {
    return Kind.MSG_HTML_TAG_NODE;
  }


  /** Returns the lower-case HTML tag name (includes '/' for end tags). */
  public String getLcTagName() {
    return lcTagName;
  }


  /** Returns tag text used to determine whether two nodes refer to the same placeholder. */
  public @Nullable String getFullTagText() {
    return fullTagText;
  }


  @Override public String getUserSuppliedPlaceholderName() {
    return userSuppliedPlaceholderName;
  }


  @Override public String genBasePlaceholderName() {

    if (userSuppliedPlaceholderName != null) {
      return BaseUtils.convertToUpperUnderscore(userSuppliedPlaceholderName);
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
    String basePlaceholderName =
        LC_TAG_NAME_TO_PLACEHOLDER_NAME_MAP.containsKey(baseLcTagName) ?
            LC_TAG_NAME_TO_PLACEHOLDER_NAME_MAP.get(baseLcTagName) : baseLcTagName;
    if (isEndTag) {
      basePlaceholderName = "end_" + basePlaceholderName;
    } else if (!isSelfEnding) {
      basePlaceholderName = "start_" + basePlaceholderName;
    }
    return basePlaceholderName.toUpperCase();
  }


  @Override public Object genSamenessKey() {
    // If two MsgHtmlTagNodes are both only raw text, then they are considered the same placeholder
    // if they both have the same user-supplied placeholder name (if any) and the same tag text.
    // If one of the MsgHtmlTagNodes is not only raw text, then the two MsgHtmlTagNodes are never
    // considered the same placeholder.
    return isOnlyRawText ?
        Pair.of(userSuppliedPlaceholderName, fullTagText) : Integer.valueOf(getId());
  }


  @Override public String toSourceString() {

    StringBuilder sb = new StringBuilder();

    appendSourceStringForChildren(sb);

    if (userSuppliedPlaceholderName != null) {
      int indexBeforeClose;
      if (isSelfEnding) {
        indexBeforeClose = sb.length() - 2;
        if (! sb.substring(indexBeforeClose).equals("/>")) {
          throw new AssertionError();
        }
      } else {
        indexBeforeClose = sb.length() - 1;
        if (! sb.substring(indexBeforeClose).equals(">")) {
          throw new AssertionError();
        }
      }
      sb.insert(indexBeforeClose, " phname=\"" + userSuppliedPlaceholderName + "\"");
    }

    return sb.toString();
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public MsgHtmlTagNode clone() {
    return new MsgHtmlTagNode(this);
  }

}
