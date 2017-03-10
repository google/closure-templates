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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Node representing an HTML tag within a {@code msg} statement/block.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MsgHtmlTagNode extends AbstractBlockNode implements MsgPlaceholderInitialNode {

  private static final SoyErrorKind HTML_COMMENT_WITHIN_MSG_BLOCK =
      SoyErrorKind.of("Found HTML comment within ''msg'' block: {0}");
  private static final SoyErrorKind UNNAMED_HTML_TAG_WITHIN_MSG_BLOCK =
      SoyErrorKind.of("HTML tag within ''msg'' block has no tag name: {0}");
  private static final SoyErrorKind INVALID_PHNAME_ATTRIBUTE =
      SoyErrorKind.of("''phname'' attribute is not a valid identifier");
  private static final SoyErrorKind MULTIPLE_PHNAME_ATTRIBUTES =
      SoyErrorKind.of("Multiple ''phname'' attributes in HTML tag.");

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
  private static final ImmutableMap<String, String> LC_TAG_NAME_TO_PLACEHOLDER_NAME_MAP =
      ImmutableMap.<String, String>builder()
          .put("a", "link")
          .put("br", "break")
          .put("b", "bold")
          .put("i", "italic")
          .put("li", "item")
          .put("ol", "ordered_list")
          .put("ul", "unordered_list")
          .put("p", "paragraph")
          .put("img", "image")
          .put("em", "emphasis")
          .build();


  /** The lower-case HTML tag name (includes '/' for end tags). */
  private final String lcTagName;

  /** Whether this HTML tag is self-ending (i.e. ends with "/>") */
  private final boolean isSelfEnding;

  /** Whether this HTML tag only has raw text (i.e. has only a single RawTextNode child). */
  private final boolean isOnlyRawText;

  /** Only applicable when isOnlyRawText is true. The full tag text. */
  @Nullable private final String fullTagText;

  /** The user-supplied placeholder name, or null if not supplied or not applicable. */
  @Nullable private final String userSuppliedPlaceholderName;


  private MsgHtmlTagNode(
      int id,
      SourceLocation sourceLocation,
      String userSuppliedPlaceholderName,
      String lcTagName,
      boolean isSelfEnding,
      boolean isOnlyRawText,
      String fullTagText,
      List<StandaloneNode> children) {
    super(id, sourceLocation);
    this.userSuppliedPlaceholderName = userSuppliedPlaceholderName;
    this.lcTagName = lcTagName;
    this.isSelfEnding = isSelfEnding;
    this.isOnlyRawText = isOnlyRawText;
    this.fullTagText = fullTagText;
    addChildren(children);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  private MsgHtmlTagNode(MsgHtmlTagNode orig, CopyState copyState) {
    super(orig, copyState);
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


  @Override public String getUserSuppliedPhName() {
    return userSuppliedPlaceholderName;
  }


  @Override public String genBasePhName() {

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
    // considered the same placeholder (so use the instance as the sameness key)
    return isOnlyRawText ?
        Pair.of(userSuppliedPlaceholderName, fullTagText) : this;
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


  @Override public MsgHtmlTagNode copy(CopyState copyState) {
    return new MsgHtmlTagNode(this, copyState);
  }

  /**
   * Builder for {@link MsgHtmlTagNode}.
   */
  public static final class Builder {

    private static MsgHtmlTagNode error(SourceLocation location) {
      return new Builder(
              -1,
              ImmutableList.<StandaloneNode>of(new RawTextNode(-1, "<body/>", location)),
              location)
          .build(ExplodingErrorReporter.get()); // guaranteed to be valid
    }

    private final int id;
    private ImmutableList<StandaloneNode> children;
    private final SourceLocation sourceLocation;

    /**
     * @param id The node's id.
     * @param children The node's children.
     * @param sourceLocation The node's source location.
     */
    public Builder(int id, ImmutableList<StandaloneNode> children, SourceLocation sourceLocation) {
      this.id = id;
      this.children = children;
      this.sourceLocation = sourceLocation;
    }

    /**
     * Returns a new {@link MsgHtmlTagNode} built from the builder's state. If the builder's state
     * is invalid, errors are reported to the {@code errorManager} and {Builder#error} is returned.
     */
    public MsgHtmlTagNode build(ErrorReporter errorReporter) {
      Checkpoint checkpoint = errorReporter.checkpoint();

      String userSuppliedPlaceholderName = computePlaceholderName(errorReporter);
      String lcTagName = computeLcTagName(errorReporter);
      boolean selfEnding = isSelfEnding();
      boolean isOnlyRawText = children.size() == 1;
      String fullTagText = computeFullTagText();

      if (errorReporter.errorsSince(checkpoint)) {
        return error(sourceLocation);
      }

      return new MsgHtmlTagNode(
          id,
          sourceLocation,
          userSuppliedPlaceholderName,
          lcTagName,
          selfEnding,
          isOnlyRawText,
          fullTagText,
          children);
    }

    private boolean isSelfEnding() {
      int numChildren = children.size();
      String lastChildText = ((RawTextNode) children.get(numChildren - 1)).getRawText();
      return lastChildText.endsWith("/>");
    }

    private String computeFullTagText() {
      String fullTagText = null;
      if (children.size() == 1) {
        StringBuilder fullTagTextSb = new StringBuilder();
        for (StandaloneNode child : children) {
          fullTagTextSb.append(child.toSourceString());
        }
        fullTagText = fullTagTextSb.toString();
      }
      return fullTagText;
    }

    @Nullable private String computeLcTagName(ErrorReporter errorReporter) {
      String firstChildText = ((RawTextNode) children.get(0)).getRawText();
      Matcher matcher = TAG_NAME_PATTERN.matcher(firstChildText);
      if (!matcher.find()) {
        if (firstChildText.startsWith("<!--")) {
          errorReporter.report(sourceLocation, HTML_COMMENT_WITHIN_MSG_BLOCK, firstChildText);
        } else {
          errorReporter.report(sourceLocation, UNNAMED_HTML_TAG_WITHIN_MSG_BLOCK, firstChildText);
        }
        return null;
      }
      return matcher.group().toLowerCase(Locale.ENGLISH);
    }

    @Nullable private String computePlaceholderName(ErrorReporter errorReporter) {
      List<String> names = new ArrayList<>();
      ImmutableList.Builder<StandaloneNode> transformedChildren = new ImmutableList.Builder<>();
      for (StandaloneNode child : children) {
        transformedChildren.add(extractPlaceholderName(child, names));
      }
      children = transformedChildren.build();

      for (String name : names) {
        if (!BaseUtils.isIdentifier(name)) {
          errorReporter.report(sourceLocation, INVALID_PHNAME_ATTRIBUTE);
        }
      }

      if (names.size() > 1) {
        errorReporter.report(sourceLocation, MULTIPLE_PHNAME_ATTRIBUTES);
      }

      return Iterables.getFirst(names, null);
    }

    private static StandaloneNode extractPlaceholderName(
        StandaloneNode node, List<String> names) {
      if (!(node instanceof RawTextNode)) {
        return node;
      }
      String rawText = ((RawTextNode) node).getRawText();
      Matcher matcher = PHNAME_ATTR_PATTERN.matcher(rawText);

      if (matcher.find()) {
        StringBuffer sb = new StringBuffer(rawText.length());
        do {
          String userSuppliedPlaceholderName = matcher.group(1);
          names.add(userSuppliedPlaceholderName);
          matcher.appendReplacement(sb, "");
        } while (matcher.find());
        String replacementText = matcher.appendTail(sb).toString();
        return new RawTextNode(node.getId(), replacementText, node.getSourceLocation());
      }
      return node;
    }
  }
}
