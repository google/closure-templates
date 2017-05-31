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

import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.SoyNode;

/**
 * Like {@link AbstractReturningHtmlSoyNodeVisitor}, but also visits nodes containing information
 * about the HTML structure of the page.
 */
public abstract class AbstractReturningHtmlSoyNodeVisitor<R>
    extends AbstractReturningSoyNodeVisitor<R> {

  @Override
  protected R visit(SoyNode node) {
    switch (node.getKind()) {
      case INCREMENTAL_HTML_ATTRIBUTE:
        return visitIncrementalHtmlAttributeNode((IncrementalHtmlAttributeNode) node);
      case INCREMENTAL_HTML_OPEN_TAG:
        return visitIncrementalHtmlOpenTagNode((IncrementalHtmlOpenTagNode) node);
      case INCREMENTAL_HTML_CLOSE_TAG:
        return visitIncrementalHtmlCloseTagNode((IncrementalHtmlCloseTagNode) node);
      default:
        return super.visit(node);
    }
  }

  protected R visitIncrementalHtmlAttributeNode(IncrementalHtmlAttributeNode node) {
    return visitSoyNode(node);
  }

  protected R visitIncrementalHtmlOpenTagNode(IncrementalHtmlOpenTagNode node) {
    return visitSoyNode(node);
  }

  protected R visitIncrementalHtmlCloseTagNode(IncrementalHtmlCloseTagNode node) {
    return visitSoyNode(node);
  }
}
