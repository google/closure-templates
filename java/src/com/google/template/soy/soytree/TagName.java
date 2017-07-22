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
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import java.util.List;
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
          "area", "base", "br", "col", "command", "embed", "hr", "img", "input", "keygen", "link",
          "meta", "param", "source", "track", "wbr");

  /**
   * A map that is used to check whether a particular optional tag can be popped (auto closed) by a
   * following close tag. See {@link checkOptionalTagShouldBePopped} method for more information.
   *
   * <p>In particular, the keys of this map are all optional tags defined in
   * https://www.w3.org/TR/html5/syntax.html#optional-tags. The values of this map are names for
   * close tag that can auto-close the optional tag. For example, {@code <li>} is an optional tag
   * and whenever we see {@code </ul>} and {@code </ol>} we believe the last {@code <li>} is auto
   * closed.
   *
   * <p>This map defines the rules for strict HTML validation: whenever we see a open tag that is in
   * this map, only a subset of close tags can auto-close it. There are two optional tags that are
   * not include in this map: {@code <p>} tags can be auto-closed by everyone (almost), and {@code
   * <html>} tags should never be auto-closed. While {@code <html>} is an optional tag, it must be
   * kept in the stack; we can only close it when we are at the end of a soy template.
   */
  private static final ImmutableSetMultimap<String, String> OPTIONAL_TAG_POPPING_RULES =
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

  // According to https://www.w3.org/TR/html5/syntax.html#optional-tags, this is a list of tags
  // that can potentially omit the end tags.
  private static final ImmutableSet<String> SPECIAL_OPTIONAL_TAG_NAMES =
      ImmutableSet.of("html", "p");

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

  public boolean isDefinitelyOptional() {
    return SPECIAL_OPTIONAL_TAG_NAMES.contains(nameAsLowerCase)
        || OPTIONAL_TAG_POPPING_RULES.containsKey(nameAsLowerCase);
  }

  /**
   * Checks if the an open tag can be auto-closed by a following close tag.
   *
   * <p>We throws an {@code IllegalArgumentException} if two inputs have the same tag names, since
   * this should never happen (should be handled by previous logic in {@code
   * StrictHtmlValidationPass}).
   *
   * <p>This implements half of the content model described in
   * https://www.w3.org/TR/html5/syntax.html#optional-tags. Notably we do nothing when we see cases
   * like "li element is immediately followed by another li element". The validation logic relies on
   * auto-closing open tags when we see close tags. Since only {@code </ul>} and {@code </ol>} are
   * allowed to pop {@code <li>}, we believe this should still give us a confident error message. We
   * might consider adding support for popping open tags when we visit open tags in the future.
   */
  public static boolean checkOptionalTagShouldBePopped(TagName openTag, TagName closeTag) {
    if (!openTag.isStatic() || !openTag.isDefinitelyOptional()) {
      return false;
    }
    if (!closeTag.isStatic()) {
      return true;
    }
    String openTagName = openTag.getStaticTagNameAsLowerCase();
    String closeTagName = closeTag.getStaticTagNameAsLowerCase();
    checkArgument(!openTagName.equals(closeTagName));
    if (SPECIAL_OPTIONAL_TAG_NAMES.contains(openTagName)) {
      return openTagName.equals("p");
    }
    return OPTIONAL_TAG_POPPING_RULES.containsEntry(openTagName, closeTagName);
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
  public boolean equals(Object other) {
    if (other instanceof TagName) {
      TagName tag = (TagName) other;
      if (isStatic() != tag.isStatic()) {
        return false;
      }
      if (isStatic()) {
        return nameAsLowerCase.equals(tag.nameAsLowerCase);
      }
      return comparePrintNode((PrintNode) node, (PrintNode) tag.node);
    }
    return false;
  }

  private boolean comparePrintNode(PrintNode firstNode, PrintNode secondNode) {
    ExprEquivalence exprEquivalence = ExprEquivalence.get();
    if (!exprEquivalence.equivalent(firstNode.getExpr(), secondNode.getExpr())) {
      return false;
    }
    List<PrintDirectiveNode> firstNodeDirectives = firstNode.getChildren();
    List<PrintDirectiveNode> secondNodeDirectives = secondNode.getChildren();
    if (firstNodeDirectives.size() != secondNodeDirectives.size()) {
      return false;
    }
    for (int i = 0; i < firstNodeDirectives.size(); ++i) {
      if (firstNodeDirectives.get(i).getName().equals(secondNodeDirectives.get(i).getName())) {
        return false;
      }
      // cast ImmutableList<ExprRootNode> to List<ExprNode>
      @SuppressWarnings("unchecked")
      List<ExprNode> one = (List<ExprNode>) ((List<?>) firstNodeDirectives.get(i).getExprList());
      @SuppressWarnings("unchecked")
      List<ExprNode> two = (List<ExprNode>) ((List<?>) secondNodeDirectives.get(i).getExprList());
      if (!exprEquivalence.pairwise().equivalent(one, two)) {
        return false;
      }
    }
    return true;
  }

  private static int hashPrintNode(PrintNode node) {
    ExprEquivalence exprEquivalence = ExprEquivalence.get();
    int hc = exprEquivalence.hash(node.getExpr());
    for (PrintDirectiveNode child : node.getChildren()) {
      // cast ImmutableList<ExprRootNode> to List<ExprNode>
      @SuppressWarnings("unchecked")
      List<ExprNode> list = (List<ExprNode>) ((List<?>) child.getExprList());
      hc = 31 * hc + child.getName().hashCode();
      hc = 31 * hc + exprEquivalence.pairwise().hash(list);
    }
    return hc;
  }

  @Override
  public int hashCode() {
    return isStatic() ? nameAsLowerCase.hashCode() : hashPrintNode((PrintNode) node);
  }

  @Override
  public String toString() {
    return node.toSourceString();
  }
}
