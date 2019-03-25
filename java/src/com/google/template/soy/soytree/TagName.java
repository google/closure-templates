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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import javax.annotation.Nullable;

/**
 * An html tag name that could either be a {@code StaticTagName} or a {@code PrintNode}. We only
 * allow {@code PrintNode} for dynamic tag name at this point.
 *
 * <p>For {code @StaticTagName}, the equality semantics are based on the lower-ascii tag name and
 * ignore source location. So 'DIV' and 'div' are considered equivalent.
 *
 * <p>For {@code DynamicTagName}, the equality semantics are based on the {@code ExprRootNode}
 * associated with the {@code PrintNode}.
 */
public final class TagName {
  /**
   * An enum to represent tags that have {@code rcdata} content.
   *
   * <p>These tag names imply important changes in how children of the tag should be interpreted,
   * specifically the content should be interpreted as {@code rcdata} instead of {@code pcdata}.
   */
  public enum RcDataTagName {
    SCRIPT,
    STYLE,
    TITLE,
    TEXTAREA,
    XMP;

    @Override
    public String toString() {
      return Ascii.toLowerCase(name());
    }
  }

  // According to https://www.w3.org/TR/html-markup/syntax.html#syntax-elements, this is a list of
  // void tags in HTML spec.
  private static final ImmutableSet<String> VOID_TAG_NAMES =
      ImmutableSet.of(
          "area",
          "base",
          "br",
          "col",
          "command",
          "embed",
          "hr",
          "img",
          "input",
          "keygen",
          "link",
          "meta",
          "param",
          "plaintext", // go/framebusting recommends using unclosed <plaintext> and <plaintext/>.
          "source",
          "track",
          "wbr");

  // With default styling, these elements do not start on a new line and only takes up as much width
  private static final ImmutableSet<String> INLINE_TAG_NAME =
      ImmutableSet.of(
          "a",
          "abbr",
          "acronym",
          "b",
          "bdo",
          "big",
          "br",
          "button",
          "cite",
          "code",
          "dfn",
          "em",
          "i",
          "img",
          "input",
          "kbd",
          "label",
          "map",
          "object",
          "output",
          "q",
          "samp",
          "script",
          "select",
          "small",
          "span",
          "strong",
          "sub",
          "sup",
          "textarea",
          "time",
          "tt",
          "var");

  /**
   * A map that is used to check whether a particular optional tag can be auto closed by a following
   * close tag. See {@link #checkCloseTagClosesOptional} method for more information.
   *
   * <p>In particular, the keys of this map are all optional tags defined in <a
   * href="https://www.w3.org/TR/html5/syntax.html#optional-tags">https://www.w3.org/TR/html5/syntax.html#optional-tags</a>.
   * The values of this map are names for close tags that can auto-close the optional tag. For
   * example, {@code <li>} is an optional tag and whenever we see {@code </ul>} and {@code </ol>} we
   * believe the last {@code <li>} is auto closed.
   *
   * <p>This map defines the rules for strict HTML validation: whenever we see a open tag that is in
   * this map, only a subset of close tags can auto-close it. There is one optional tags that is not
   * include in this map: {@code <html>} tags should never be auto-closed. While {@code <html>} is
   * an optional tag, it is ignored by the tag matching system.
   */
  private static final ImmutableSetMultimap<String, String> OPTIONAL_TAG_CLOSE_TAG_RULES =
      new ImmutableSetMultimap.Builder<String, String>()
          .putAll("head", "body", "html")
          .put("body", "html")
          .putAll("li", "ul", "ol")
          .put("dt", "dl")
          .put("dd", "dl")
          .put("rb", "ruby")
          .put("rt", "ruby")
          .put("rtc", "ruby")
          .put("rp", "ruby")
          .put("optgroup", "select")
          .putAll("option", "select", "datalist", "optgroup")
          .put("colgroup", "table")
          .put("thead", "table")
          .put("tbody", "table")
          .put("tfoot", "table")
          .putAll("tr", "thead", "tbody", "tfoot", "table")
          .putAll("td", "tr", "thead", "tbody", "tfoot", "table")
          .putAll("th", "tr", "thead", "tbody", "tfoot", "table")
          .build();

  /**
   * List of tags that do <em>not</em> close a {@code <p>} tag.
   *
   * <p>The {@code <p>} tag gets special treatment, because it can be closed by any HTML close tag
   * except the ones in this list. See <a *
   * href="https://www.w3.org/TR/html5/syntax.html#optional-tags">https://www.w3.org/TR/html5/syntax.html#optional-tags</a>
   */
  private static final ImmutableSet<String> PTAG_CLOSE_EXCEPTIONS =
      ImmutableSet.of("a", "audio", "del", "ins", "map", "noscript", "video");

  /**
   * Certain optional tags need to be excluded from the HTML matcher graph during validation. In
   * general developers will usually close these tags in a header template and close in a footer
   * template. In this case, it does little to enforce that these tags are balanced.
   */
  private static final ImmutableSet<String> HTML_OPEN_TAG_EXCLUDE_SET =
      ImmutableSet.of("head", "body", "html");

  /**
   * A map that is used to check whether a particular optional tag can be implicitly closed by a
   * following open tag. See {@link #checkCloseTagClosesOptional} method for more information.
   *
   * <p>In particular, the keys of this map are all optional tags defined in <a
   * href="https://www.w3.org/TR/html5/syntax.html#optional-tags">https://www.w3.org/TR/html5/syntax.html#optional-tags</a>.
   * The values of this map are names for open tags that implicitly close the optional tag. For
   * example, {@code <li>} is an optional tag, if it is followed by another {@code <li>}, the first
   * {@code <li>} is implicitly closed. In a similar fashion, {@code <td>} is implicitly closed by
   * another {@code <td>} or a {@code <th>}.
   */
  private static final ImmutableSetMultimap<String, String> OPTIONAL_TAG_OPEN_CLOSE_RULES =
      new ImmutableSetMultimap.Builder<String, String>()
          .put("li", "li")
          .putAll("dt", "dt", "dd")
          .putAll("dd", "dd", "dt")
          .putAll("rt", "rt", "rp")
          .putAll("rp", "rp", "rt")
          .put("optgroup", "optgroup")
          .putAll("option", "option", "optgroup")
          .putAll("p", "p")
          .putAll("thead", "tbody", "tfoot")
          .putAll("tbody", "tbody", "tfoot")
          .put("tfoot", "table")
          .put("tr", "tr")
          .putAll("td", "tr", "th")
          .putAll("th", "td", "th")
          .build();

  private final StandaloneNode node;
  @Nullable private final String nameAsLowerCase;
  @Nullable private final RcDataTagName rcDataTagName;

  public TagName(RawTextNode node) {
    this.node = checkNotNull(node);
    this.nameAsLowerCase = Ascii.toLowerCase(node.getRawText());
    switch (nameAsLowerCase) {
      case "script":
        rcDataTagName = RcDataTagName.SCRIPT;
        break;
      case "style":
        rcDataTagName = RcDataTagName.STYLE;
        break;
      case "textarea":
        rcDataTagName = RcDataTagName.TEXTAREA;
        break;
      case "title":
        rcDataTagName = RcDataTagName.TITLE;
        break;
      case "xmp":
        rcDataTagName = RcDataTagName.XMP;
        break;
      default:
        rcDataTagName = null;
        break;
    }
  }

  public TagName(PrintNode node) {
    this.node = checkNotNull(node);
    this.nameAsLowerCase = null;
    this.rcDataTagName = null;
  }

  public boolean isStatic() {
    return node instanceof RawTextNode;
  }

  public boolean isDefinitelyVoid() {
    return VOID_TAG_NAMES.contains(nameAsLowerCase);
  }

  public boolean isDefinitelyInline() {
    return INLINE_TAG_NAME.contains(nameAsLowerCase);
  }

  public boolean isExcludedOptionalTag() {
    return HTML_OPEN_TAG_EXCLUDE_SET.contains(nameAsLowerCase);
  }

  public boolean isDefinitelyOptional() {
    return OPTIONAL_TAG_CLOSE_TAG_RULES.containsKey(nameAsLowerCase)
        || OPTIONAL_TAG_OPEN_CLOSE_RULES.containsKey(nameAsLowerCase)
        || "html".equals(nameAsLowerCase);
  }

  /**
   * Checks if the an open tag can be auto-closed by a following close tag which does not have the
   * same tag name as the open tag.
   *
   * <p>We throws an {@code IllegalArgumentException} if two inputs have the same tag names, since
   * this should never happen (should be handled by previous logic in {@code
   * StrictHtmlValidationPass}).
   *
   * <p>This implements half of the content model described in <a
   * href="https://www.w3.org/TR/html5/syntax.html#optional-tags">https://www.w3.org/TR/html5/syntax.html#optional-tags</a>.
   * Notably we do nothing when we see cases like "li element is immediately followed by another li
   * element". The validation logic relies on auto-closing open tags when we see close tags. Since
   * only {@code </ul>} and {@code </ol>} are allowed to close {@code <li>}, we believe this should
   * still give us a confident error message. We might consider adding support for popping open tags
   * when we visit open tags in the future.
   */
  public static boolean checkCloseTagClosesOptional(TagName closeTag, TagName optionalOpenTag) {
    // TODO(b/120994894): Replace this with checkArgument() when HtmlTagEntry can be replaced.
    if (!optionalOpenTag.isStatic() || !optionalOpenTag.isDefinitelyOptional()) {
      return false;
    }
    if (!closeTag.isStatic()) {
      return true;
    }
    String openTagName = optionalOpenTag.getStaticTagNameAsLowerCase();
    String closeTagName = closeTag.getStaticTagNameAsLowerCase();
    checkArgument(!openTagName.equals(closeTagName));
    if ("p".equals(openTagName)) {
      return !PTAG_CLOSE_EXCEPTIONS.contains(closeTagName);
    }
    return OPTIONAL_TAG_CLOSE_TAG_RULES.containsEntry(openTagName, closeTagName);
  }

  /**
   * Checks if the given open tag can implicitly close the given optional tag.
   *
   * <p>This implements the content model described in
   * https://www.w3.org/TR/html5/syntax.html#optional-tags.
   *
   * <p><b>Note:</b>If {@code this} is a dynamic tag, then this test alsways returns {@code false}
   * because the tag name can't be determined at parse time.
   *
   * <p>Detects two types of implicit closing scenarios:
   *
   * <ol>
   *   <li>an open tag can implicitly close another open tag, for example: {@code <li> <li>}
   *   <li>a close tag can implicitly close an open tag, for example: {@code <li> </ul>}
   * </ol>
   *
   * @param openTag the open tag name to check. Must be an {@link HtmlOpenTagNode}.
   * @param optionalOpenTag the optional tag that may be closed by this tag. This must be an
   *     optional open tag.
   * @return whether {@code htmlTagName} can close {@code optionalTagName}
   */
  public static boolean checkOpenTagClosesOptional(TagName openTag, TagName optionalOpenTag) {
    checkArgument(optionalOpenTag.isDefinitelyOptional(), "Open tag is not optional.");
    if (!(openTag.isStatic() && optionalOpenTag.isStatic())) {
      return false;
    }
    String optionalTagName = optionalOpenTag.getStaticTagNameAsLowerCase();
    String openTagName = openTag.getStaticTagNameAsLowerCase();
    return OPTIONAL_TAG_OPEN_CLOSE_RULES.containsEntry(optionalTagName, openTagName);
  }

  public boolean isForeignContent() {
    return "svg".equals(nameAsLowerCase);
  }

  /** Returns the {@link RcDataTagName} for this node, if any. */
  @Nullable
  public RcDataTagName getRcDataTagName() {
    return rcDataTagName;
  }

  /** Returns the static name. */
  public String getStaticTagName() {
    checkState(isStatic());
    return ((RawTextNode) node).getRawText();
  }

  /**
   * Returns the static name in ascii lowercase.
   *
   * @throws IllegalStateException if this tag name isn't static.
   */
  public String getStaticTagNameAsLowerCase() {
    checkState(isStatic());
    return nameAsLowerCase;
  }

  public StandaloneNode getNode() {
    return node;
  }

  public PrintNode getDynamicTagName() {
    checkState(!isStatic());
    return (PrintNode) node;
  }

  public SourceLocation getTagLocation() {
    return node.getSourceLocation();
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (other instanceof TagName) {
      TagName tag = (TagName) other;
      if (isStatic() != tag.isStatic()) {
        return false;
      }
      if (isStatic()) {
        return nameAsLowerCase.equals(tag.nameAsLowerCase);
      }
      return PrintNode.PrintEquivalence.get().equivalent((PrintNode) node, (PrintNode) tag.node);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return isStatic()
        ? nameAsLowerCase.hashCode()
        : PrintNode.PrintEquivalence.get().hash((PrintNode) node);
  }

  @Override
  public String toString() {
    return node.toSourceString();
  }
}
