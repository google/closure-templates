/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.html.passes;

import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.html.AbstractHtmlSoyNodeVisitor;
import com.google.template.soy.html.HtmlDefinitions;
import com.google.template.soy.html.IncrementalHtmlCloseTagNode;
import com.google.template.soy.html.IncrementalHtmlOpenTagNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

/**
 * Looks for void element closing tags that are not immediately preceded by an open tag of the same
 * type. This indicates an error on the developer's part as void elements may not contain any
 * content. While the spec prohibits void elements from having closing tags at all, we allow them
 * for people who like XML.
 */
public final class VoidElementVerifyingVisitor extends AbstractHtmlSoyNodeVisitor<Void> {
  private static final SoyErrorKind INVALID_CLOSE_TAG =
      SoyErrorKind.of(
          "Closing tag for a void HTML "
              + "Element was not immediately preceeded by an open tag for the same element. Void"
              + " HTML Elements are not allowed to have any content. See: "
              + "http://www.w3.org/TR/html-markup/syntax.html#void-element");

  private final ErrorReporter errorReporter;

  public VoidElementVerifyingVisitor(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  protected void visitIncrementalHtmlCloseTagNode(IncrementalHtmlCloseTagNode node) {
    String tagName = node.getTagName();

    if (!HtmlDefinitions.HTML5_VOID_ELEMENTS.contains(tagName)) {
      return;
    }

    BlockNode parent = node.getParent();
    int previousIndex = parent.getChildIndex(node) - 1;

    // The close tag is the first node in its parent. In theory, the template could still be valid
    // (e.g. {if $foo}<input>{/if}{if $foo}</input>{/if}), but it is almost certainly always a
    // mistake.
    if (previousIndex < 0) {
      errorReporter.report(node.getSourceLocation(), INVALID_CLOSE_TAG);
    } else {
      StandaloneNode previousNode = parent.getChild(previousIndex);

      if (previousNode instanceof IncrementalHtmlOpenTagNode
          && ((IncrementalHtmlOpenTagNode) previousNode).getTagName().equals(tagName)) {
        return;
      }

      errorReporter.report(node.getSourceLocation(), INVALID_CLOSE_TAG);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }
}
