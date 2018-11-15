/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.passes;

import com.google.common.base.Predicate;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.KeyNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;

/** Validates restrictions specific to Soy elements. */
final class SoyElementPass extends CompilerFilePass {

  private static final SoyErrorKind ROOT_HAS_KEY_NODE =
      SoyErrorKind.of(
          "The root node of Soy elements must not have a key. "
              + "Instead, consider wrapping the Soy element in a keyed tag node.");

  private static final SoyErrorKind ROOT_IS_DYNAMIC_TAG =
      SoyErrorKind.of("The root node of Soy elements must not be a dynamic HTML tag.");

  private final ErrorReporter errorReporter;

  SoyElementPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode template : file.getChildren()) {
      if (!(template instanceof TemplateElementNode)) {
        continue;
      }

      HtmlOpenTagNode firstOpenTagNode =
          (HtmlOpenTagNode)
              template.firstChildThatMatches(
                  new Predicate<SoyNode>() {
                    @Override
                    public boolean apply(SoyNode node) {
                      return node.getKind() == Kind.HTML_OPEN_TAG_NODE;
                    }
                  });
      if (firstOpenTagNode == null) {
        // A prior pass will report an error if the Soy element has no open tag node,
        // so just skip in this case.
        continue;
      }

      validateNoKey(firstOpenTagNode);
      validateNoDynamicTag(firstOpenTagNode);
    }
  }

  // See go/soy-element-keyed-roots for reasoning on why this is disallowed.
  private void validateNoKey(HtmlOpenTagNode firstTagNode) {
    for (SoyNode child : firstTagNode.getChildren()) {
      if (child instanceof KeyNode) {
        errorReporter.report(firstTagNode.getSourceLocation(), ROOT_HAS_KEY_NODE);
      }
    }
  }

  private void validateNoDynamicTag(HtmlOpenTagNode firstTagNode) {
    if (!firstTagNode.getTagName().isStatic()) {
      errorReporter.report(firstTagNode.getSourceLocation(), ROOT_IS_DYNAMIC_TAG);
    }
  }
}
