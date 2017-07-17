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

import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.internal.base.UnescapeUtils;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;

/**
 * HTML-unescapes all {@link RawTextNode}s in {@link ContentKind#HTML} or {@link
 * ContentKind#ATTRIBUTES} contexts. This is used for Incremental DOM compilation, which treats raw
 * content in these contexts as text rather than HTML source.
 */
final class UnescapingVisitor extends AbstractSoyNodeVisitor<Void> {

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildrenAllowingConcurrentModification((ParentSoyNode<?>) node);
    }
  }

  @Override
  protected void visitRawTextNode(RawTextNode node) {
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
    node.getParent()
        .replaceChild(
            node,
            new RawTextNode(
                node.getId(),
                UnescapeUtils.unescapeHtml(node.getRawText()),
                node.getSourceLocation(),
                node.getHtmlContext()));
  }

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    visitRenderUnitNode(node);
  }

  @Override
  protected void visitCallParamContentNode(CallParamContentNode node) {
    visitRenderUnitNode(node);
  }

  @Override
  protected void visitLetContentNode(LetContentNode node) {
    visitRenderUnitNode(node);
  }

  private void visitRenderUnitNode(RenderUnitNode node) {
    if (node.getContentKind() == SanitizedContentKind.HTML
        || node.getContentKind() == SanitizedContentKind.ATTRIBUTES) {
      visitSoyNode(node);
    }
  }
}
