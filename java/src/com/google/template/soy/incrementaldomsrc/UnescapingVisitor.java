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
package com.google.template.soy.incrementaldomsrc;

import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.internal.base.UnescapeUtils;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/**
 * HTML-unescapes all {@link RawTextNode}s in {@link ContentKind#HTML} or {@link
 * ContentKind#ATTRIBUTES} contexts. This is used for Incremental DOM compilation, which treats raw
 * content in these contexts as text rather than HTML source.
 */
final class UnescapingVisitor {

  static void unescapeRawTextInHtml(SoyNode node) {
    for (RawTextNode rawText : SoyTreeUtils.getAllNodesOfType(node, RawTextNode.class)) {
      maybeRewriteRawText(rawText);
    }
  }

  private static void maybeRewriteRawText(RawTextNode node) {
    if (node.getHtmlContext() != HtmlContext.HTML_PCDATA
        && node.getHtmlContext() != HtmlContext.HTML_NORMAL_ATTR_VALUE) {
      return;
    }
    // Don't unescape the raw translated text nodes within an {msg} tag.  Do escape text nodes which
    // are nested in placeholders inside {msg} tags (since they aren't directly translated).  Unless
    // they're further in {msg} tags that are nested in placeholders.  Don't worry; we've got tests!
    MsgFallbackGroupNode containingMsg = node.getNearestAncestor(MsgFallbackGroupNode.class);
    if (containingMsg != null) {
      MsgPlaceholderNode containingPlaceholder = node.getNearestAncestor(MsgPlaceholderNode.class);
      // Unless we're _directly_ in a placeholder.
      if (containingPlaceholder == null
          || !SoyTreeUtils.isDescendantOf(containingPlaceholder, containingMsg)) {
        return;
      }
    }
    String unescaped = UnescapeUtils.unescapeHtml(node.getRawText());
    if (!unescaped.equals(node.getRawText())) {
      RawTextNode textNode = new RawTextNode(node.getId(), unescaped, node.getSourceLocation());
      textNode.setHtmlContext(node.getHtmlContext());
      node.getParent().replaceChild(node, textNode);
    }
  }
}
