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

package com.google.template.soy.html;

import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyNode;

/**
 * Like {@link AbstractSoyNodeVisitor}, but also visits nodes containing information about the HTML
 * structure of the page.
 */
public abstract class AbstractHtmlSoyNodeVisitor<R> extends AbstractSoyNodeVisitor<R> {

  @Override
  protected void visit(SoyNode node) {
    switch (node.getKind()) {
      case INCREMENTAL_HTML_ATTRIBUTE:
        visitIncrementalHtmlAttributeNode((IncrementalHtmlAttributeNode) node);
        break;
      case INCREMENTAL_HTML_OPEN_TAG:
        visitIncrementalHtmlOpenTagNode((IncrementalHtmlOpenTagNode) node);
        break;
      case INCREMENTAL_HTML_CLOSE_TAG:
        visitIncrementalHtmlCloseTagNode((IncrementalHtmlCloseTagNode) node);
        break;
      default:
        super.visit(node);
    }
  }

  protected void visitIncrementalHtmlAttributeNode(IncrementalHtmlAttributeNode node) {
    visitSoyNode(node);
  }

  protected void visitIncrementalHtmlOpenTagNode(IncrementalHtmlOpenTagNode node) {
    visitSoyNode(node);
  }

  protected void visitIncrementalHtmlCloseTagNode(IncrementalHtmlCloseTagNode node) {
    visitSoyNode(node);
  }
}
