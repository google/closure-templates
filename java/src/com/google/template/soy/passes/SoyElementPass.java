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

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.KeyNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import java.util.function.Predicate;

/** Validates restrictions specific to Soy elements. */
final class SoyElementPass extends CompilerFilePass {

  private static final SoyErrorKind HAS_SOYDOC_PARAMS =
      SoyErrorKind.of(
          "Soy '{element}' templates must not have SoyDoc parameters.  "
              + "Move all parameters to '{@param}' commands."
          );

  private static final SoyErrorKind ROOT_HAS_KEY_NODE =
      SoyErrorKind.of(
          "The root node of Soy elements must not have a key. "
              + "Instead, consider wrapping the Soy element in a keyed tag node.");

  private static final SoyErrorKind ROOT_IS_DYNAMIC_TAG =
      SoyErrorKind.of("The root node of Soy elements must not be a dynamic HTML tag.");

  private static final SoyErrorKind SOY_ELEMENT_OPEN_TAG_NOT_AMBIGUOUS =
      SoyErrorKind.of("Soy element open tags must map to exactly one close tag.");

  private static final SoyErrorKind SOY_ELEMENT_EXACTLY_ONE_TAG =
      SoyErrorKind.of(
          "Soy elements must contain exactly one top-level HTML element (e.g, span, div).");

  private static final Predicate<SoyNode> OPEN_TAG_MATCHER =
      node -> node.getKind() == Kind.HTML_OPEN_TAG_NODE;

  private static final Predicate<SoyNode> CLOSE_TAG_MATCHER =
      node -> node.getKind() == Kind.HTML_CLOSE_TAG_NODE;

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
      if (template.hasLegacyParams()) {
        errorReporter.report(
            template.getSourceLocation(),
            HAS_SOYDOC_PARAMS
            );
      }
      HtmlOpenTagNode firstOpenTagNode = null;
      HtmlCloseTagNode lastCloseTagNode = null;
      VeLogNode firstVeLog =
          (VeLogNode) template.firstChildThatMatches(node -> node.getKind() == Kind.VE_LOG_NODE);
      firstOpenTagNode = (HtmlOpenTagNode) template.firstChildThatMatches(OPEN_TAG_MATCHER);
      lastCloseTagNode = (HtmlCloseTagNode) template.lastChildThatMatches(CLOSE_TAG_MATCHER);

      if (firstVeLog != null) {
        if (firstOpenTagNode != null || lastCloseTagNode != null) {
          errorReporter.report(firstVeLog.getSourceLocation(), SOY_ELEMENT_EXACTLY_ONE_TAG);
        }
        firstOpenTagNode = (HtmlOpenTagNode) firstVeLog.firstChildThatMatches(OPEN_TAG_MATCHER);
        lastCloseTagNode = (HtmlCloseTagNode) firstVeLog.lastChildThatMatches(CLOSE_TAG_MATCHER);
      }

      if (firstOpenTagNode == null || !firstOpenTagNode.isSelfClosing()) {
        if (firstOpenTagNode == null || lastCloseTagNode == null) {
          errorReporter.report(template.getSourceLocation(), SOY_ELEMENT_EXACTLY_ONE_TAG);
          continue;
        }
        validateSoyElementHasOneRootTagNode(firstOpenTagNode, lastCloseTagNode);
        validateTagNodeHasOneChild(firstOpenTagNode);
      }
      validateNoKey(firstOpenTagNode);
      validateNoDynamicTag(firstOpenTagNode);
    }
  }

  private void validateTagNodeHasOneChild(HtmlOpenTagNode firstTagNode) {
    if (firstTagNode.getTaggedPairs().size() > 1) {
      errorReporter.report(firstTagNode.getSourceLocation(), SOY_ELEMENT_OPEN_TAG_NOT_AMBIGUOUS);
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

  private void validateSoyElementHasOneRootTagNode(
      HtmlOpenTagNode firstNode, HtmlCloseTagNode lastCloseTagNode) {
    if (firstNode.getTaggedPairs().size() != 1
        || lastCloseTagNode.getTaggedPairs().size() != 1
        || !firstNode.getTaggedPairs().get(0).equals(lastCloseTagNode)
        || !lastCloseTagNode.getTaggedPairs().get(0).equals(firstNode)) {
      errorReporter.report(lastCloseTagNode.getSourceLocation(), SOY_ELEMENT_EXACTLY_ONE_TAG);
    }
  }
}
