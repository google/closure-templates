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

import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.SoyNode;

/**
 * Conformance rule banning URI fragments in a.href properties. This is useful for applications that
 * use the {@code <base>} tag to override the default relative url target. In this scenario
 * fragment-based navigation won't work as intended.
 */
final class BanFragmentNavigation extends Rule<HtmlAttributeNode> {
  BanFragmentNavigation(SoyErrorKind error) {
    super(error);
  }

  @Override
  protected void doCheckConformance(HtmlAttributeNode attributeNode, ErrorReporter errorReporter) {
    if (attributeNode.definitelyMatchesAttributeName("href")
        && attributeNode.getStaticContent() != null) {
      String value = attributeNode.getStaticContent();
      // An href== '#' likely means that the click will be intercepted by a JS handler, so allow it.
      if (value.startsWith("#") && !value.equals("#")) {
        HtmlOpenTagNode openTagNode = getOwningTag(attributeNode);
        // If we can't find an open tag (e.g. a kind="attributes" block), the open tag name is
        // dynamic, or the tag is definitely 'a', so report an error.
        // In other cases we are definitely rendering an href on a non-anchor tag, so allow it.
        if (openTagNode == null
            || !openTagNode.getTagName().isStatic()
            || openTagNode.getTagName().getStaticTagNameAsLowerCase().equals("a")) {
          errorReporter.report(attributeNode.getChild(0).getSourceLocation(), error);
        }
      }
    }
  }

  private static HtmlOpenTagNode getOwningTag(HtmlAttributeNode attributeNode) {
    SoyNode parent = attributeNode.getParent();
    while (true) {
      switch (parent.getKind()) {
        case TEMPLATE_BASIC_NODE:
        case TEMPLATE_DELEGATE_NODE:
        case TEMPLATE_ELEMENT_NODE:
          return null;
        case HTML_OPEN_TAG_NODE:
          return (HtmlOpenTagNode) parent;
        default:
          break;
      }
      parent = parent.getParent();
    }
  }
}
