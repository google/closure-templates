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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Ascii;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
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
 * <p>For {@code DynamicTagName}, the equality semantics are based on the {@code ExprUnion}
 * associated with the {@code PrintNode}.
 *
 * <p>TODO(b/31771679): Convert this to a Soy AST node so that it is visible for {@code
 * NodeVisitor}.
 */
public final class TagName {
  /**
   * An enum to represent special tag names.
   *
   * <p>These tag names imply important changes in how children of the tag should be interpreted,
   * specifically the content should be interpreted as {@code rcdata} instead of {@code pcdata}.
   */
  public enum SpecialTagName {
    SCRIPT,
    STYLE,
    TITLE,
    TEXTAREA;

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

  // According to https://www.w3.org/TR/html5/syntax.html#optional-tags, this is a list of tags
  // that can potentially omit the end tags. We are not supporting cases that start tags might be
  // omitted.
  private static final ImmutableSet<String> OPTIONAL_TAG_NAMES =
      ImmutableSet.of(
          "body",
          "colgroup",
          "dd",
          "dt",
          "head",
          "html",
          "li",
          "optgroup",
          "option",
          "p",
          "rb",
          "rp",
          "rt",
          "rtc",
          "tbody",
          "td",
          "tfoot",
          "th",
          "thead",
          "tr");

  private final StandaloneNode node;
  @Nullable private final String nameAsLowerCase;
  @Nullable private final SpecialTagName specialTagName;

  public TagName(RawTextNode node) {
    this.node = checkNotNull(node);
    this.nameAsLowerCase = Ascii.toLowerCase(node.getRawText());
    switch (nameAsLowerCase) {
      case "script":
        specialTagName = SpecialTagName.SCRIPT;
        break;
      case "style":
        specialTagName = SpecialTagName.STYLE;
        break;
      case "textarea":
        specialTagName = SpecialTagName.TEXTAREA;
        break;
      case "title":
        specialTagName = SpecialTagName.TITLE;
        break;
      default:
        specialTagName = null;
        break;
    }
  }

  public TagName(PrintNode node) {
    this.node = checkNotNull(node);
    this.nameAsLowerCase = null;
    this.specialTagName = null;
  }

  public boolean isStatic() {
    return node instanceof RawTextNode;
  }

  public boolean isDefinitelyVoid() {
    return VOID_TAG_NAMES.contains(nameAsLowerCase);
  }

  public boolean isDefinitelyOptional() {
    return OPTIONAL_TAG_NAMES.contains(nameAsLowerCase);
  }

  @Nullable
  public SpecialTagName getSpecialTagName() {
    return specialTagName;
  }

  /** Returns the static name. */
  public RawTextNode getStaticTagName() {
    checkState(isStatic());
    return (RawTextNode) node;
  }

  public Optional<String> getStaticTagNameAsLowerCase() {
    return Optional.fromNullable(nameAsLowerCase);
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
    ExprUnionEquivalence exprUnionEquivalence = ExprUnionEquivalence.get();
    if (!exprUnionEquivalence.equivalent(firstNode.getExprUnion(), secondNode.getExprUnion())) {
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
      if (!exprUnionEquivalence
          .pairwise()
          .equivalent(
              firstNodeDirectives.get(i).getAllExprUnions(),
              secondNodeDirectives.get(i).getAllExprUnions())) {
        return false;
      }
    }
    return true;
  }

  private static int hashPrintNode(PrintNode node) {
    ExprUnionEquivalence exprUnionEquivalence = ExprUnionEquivalence.get();
    int hc = exprUnionEquivalence.hash(node.getExprUnion());
    for (PrintDirectiveNode child : node.getChildren()) {
      hc = 31 * hc + child.getName().hashCode();
      hc = 31 * hc + exprUnionEquivalence.pairwise().hash(child.getAllExprUnions());
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
