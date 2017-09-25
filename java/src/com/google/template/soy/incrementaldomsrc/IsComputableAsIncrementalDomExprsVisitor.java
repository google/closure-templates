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

import com.google.template.soy.jssrc.internal.IsComputableAsJsExprsVisitor;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;

/**
 * Overrides the JS code generation version of this visitor to not generate HTML/attribute params as
 * a JavaScript expression. This allows for generating formatted JavaScript for the Incremental DOM
 * functions that is more readable than if they had been generated as an expression.
 */
final class IsComputableAsIncrementalDomExprsVisitor extends IsComputableAsJsExprsVisitor {


  @Override
  protected Boolean visitCallParamContentNode(CallParamContentNode node) {
    switch (node.getContentKind()) {
      case HTML:
      case ATTRIBUTES:
        return false;
      default:
        return super.visitCallParamContentNode(node);
    }
  }

  @Override
  protected Boolean visitPrintNode(PrintNode node) {
    // idom prints HTML & attributes values by emitting function calls, not concatenable strings.
    return node.getHtmlContext() != HtmlContext.HTML_TAG
        && node.getHtmlContext() != HtmlContext.HTML_PCDATA;
  }

  @Override
  protected Boolean visitRawTextNode(RawTextNode node) {
    // idom prints HTML text content by emitting function calls, not concatenable strings.
    return node.getHtmlContext() != HtmlContext.HTML_PCDATA;
  }

  @Override
  protected boolean canSkipChild(SoyNode child) {
    // Cannot skip textual nodes, since some of them are not expressions (see above).
    return false;
  }
}
