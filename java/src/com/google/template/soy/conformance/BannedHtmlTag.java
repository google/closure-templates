/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.conformance;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.util.Collection;

/** Conformance rule banning particular HTML tags in Soy. */
final class BannedHtmlTag extends Rule<HtmlOpenTagNode> {

  private final ImmutableSet<String> bannedTagNames;
  private final ImmutableSet<String> bannedPossiblyPresentAttributes;
  private final ImmutableSet<String> bannedPossiblyMissingAttributes;

  BannedHtmlTag(
      Collection<String> bannedTagNames,
      Collection<String> bannedPossiblyPresentAttributes,
      Collection<String> bannedPossiblyMissingAttributes,
      SoyErrorKind error) {
    super(error);

    // According to https://www.w3.org/TR/html5/syntax.html#syntax-tag-name, tag names and
    // attributes are all case-insensitive.
    this.bannedTagNames = bannedTagNames.stream().map(Ascii::toLowerCase).collect(toImmutableSet());

    this.bannedPossiblyPresentAttributes =
        bannedPossiblyPresentAttributes.stream().map(Ascii::toLowerCase).collect(toImmutableSet());

    this.bannedPossiblyMissingAttributes =
        bannedPossiblyMissingAttributes.stream().map(Ascii::toLowerCase).collect(toImmutableSet());
  }

  @Override
  protected void doCheckConformance(HtmlOpenTagNode node, ErrorReporter errorReporter) {
    if (hasConformanceError(node)) {
      errorReporter.report(node.getSourceLocation(), error);
    }
  }

  private boolean hasConformanceError(HtmlOpenTagNode node) {
    boolean isBannedTag = isBannedTag(node);
    if (!isBannedTag) {
      return false;
    }
    if (bannedPossiblyPresentAttributes.isEmpty() && bannedPossiblyMissingAttributes.isEmpty()) {
      return true;
    }

    boolean containsBannedAttributes =
        !bannedPossiblyPresentAttributes.isEmpty()
            && BannedHtmlTag.hasAllAttributes(node, bannedPossiblyPresentAttributes);
    boolean containsMissingAttributes =
        !bannedPossiblyMissingAttributes.isEmpty()
            && !BannedHtmlTag.hasAllAttributes(node, bannedPossiblyMissingAttributes);

    return containsBannedAttributes || containsMissingAttributes;
  }

  private boolean isBannedTag(HtmlOpenTagNode node) {
    return node.getTagName().isStatic()
        && bannedTagNames.contains(node.getTagName().getStaticTagNameAsLowerCase());
  }

  private static boolean hasAllAttributes(HtmlOpenTagNode node, ImmutableSet<String> attributes) {
    return attributes.stream()
        .allMatch(
            attrName ->
                SoyTreeUtils.allNodesOfType(node, HtmlAttributeNode.class)
                    .anyMatch(attr -> attr.definitelyMatchesAttributeName(attrName)));
  }
}
