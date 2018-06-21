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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import java.util.Collection;

/**
 * Conformance rule banning particular HTML tags in Soy.
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class BannedHtmlTag extends Rule<HtmlOpenTagNode> {

  private final ImmutableSet<String> bannedTagNames;

  BannedHtmlTag(Collection<String> bannedTagNames, SoyErrorKind error) {
    super(error);
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    for (String tagName : bannedTagNames) {
      // According to https://www.w3.org/TR/html5/syntax.html#syntax-tag-name, tag names are all
      // case-insensitive.
      builder.add(Ascii.toLowerCase(tagName));
    }
    this.bannedTagNames = builder.build();
  }

  @Override
  protected void doCheckConformance(HtmlOpenTagNode node, ErrorReporter errorReporter) {
    if (node.getTagName().isStatic()
        && bannedTagNames.contains(node.getTagName().getStaticTagNameAsLowerCase())) {
      errorReporter.report(node.getSourceLocation(), error);
    }
  }
}
