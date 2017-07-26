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
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/**
 * Conformance rule banning inline event handlers in Soy templates. This is useful to enforce when
 * deploying a strong Content Security Policy, since the only way to allow inline event handlers in
 * CSP is to use <code>unsafe-inline</code>.
 *
 * @see http://www.w3.org/TR/CSP/#directive-script-src
 * @author brndn@google.com (Brendan Linn)
 */
public final class BanInlineEventHandlers extends Rule<SoyFileNode> {
  public BanInlineEventHandlers(SoyErrorKind error) {
    super(error);
  }

  @Override
  protected void checkConformance(SoyFileNode node, ErrorReporter errorReporter) {
    for (HtmlAttributeNode attributeNode :
        SoyTreeUtils.getAllNodesOfType(node, HtmlAttributeNode.class)) {
      if (!attributeNode.hasValue()) {
        // inline event handlers all have values
        continue;
      }
      // Ban all html attributes which start with 'on'
      // this is the same logic that the autoescaper uses to decide if a given attribute is an
      // inline event handler.
      StandaloneNode attrName = attributeNode.getChild(0);
      if (attrName instanceof RawTextNode
          && Ascii.toLowerCase(((RawTextNode) attrName).getRawText()).startsWith("on")) {
        errorReporter.report(attributeNode.getChild(0).getSourceLocation(), error);
      }
    }
  }
}
