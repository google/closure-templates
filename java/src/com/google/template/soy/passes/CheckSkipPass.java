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
import com.google.template.soy.compilermetrics.Impression;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.SkipNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

/** Checks for validity of skip nodes wrt their host node. */
@RunBefore(FinalizeTemplateRegistryPass.class)
final class CheckSkipPass implements CompilerFilePass {

  private static final SoyErrorKind SOY_SKIP_OPEN_TAG_CLOSE_AMBIGUOUS =
      SoyErrorKind.of(
          "Skip element open tags must map to exactly one close tag.",
          Impression.ERROR_CHECK_SKIP_PASS_SOY_SKIP_OPEN_TAG_CLOSE_AMBIGUOUS);

  private static final SoyErrorKind SOY_SKIP_MUST_BE_DIRECT_CHILD_OF_TAG =
      SoyErrorKind.of(
          "Skip commands must be direct children of html tags.",
          Impression.ERROR_CHECK_SKIP_PASS_SOY_SKIP_MUST_BE_DIRECT_CHILD_OF_TAG);

  private final ErrorReporter errorReporter;

  public CheckSkipPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    new CheckSkipPassVisitor(errorReporter).exec(file);
  }

  private static final class CheckSkipPassVisitor extends AbstractSoyNodeVisitor<Void> {
    private final ErrorReporter errorReporter;

    public CheckSkipPassVisitor(ErrorReporter errorReporter) {
      this.errorReporter = errorReporter;
    }

    @Override
    public void visitSkipNode(SkipNode skipNode) {
      if (skipNode.getParent() instanceof HtmlOpenTagNode) {
        HtmlOpenTagNode openTag = (HtmlOpenTagNode) skipNode.getParent();
        if (skipNode.skipOnlyChildren()) {
          openTag.setSkipChildren();
        } else {
          openTag.setSkipRoot();
        }
        if (!openTag.isSelfClosing() && openTag.getTaggedPairs().size() > 1) {
          errorReporter.report(openTag.getSourceLocation(), SOY_SKIP_OPEN_TAG_CLOSE_AMBIGUOUS);
        }
      } else {
        errorReporter.report(skipNode.getSourceLocation(), SOY_SKIP_MUST_BE_DIRECT_CHILD_OF_TAG);
      }
    }

    @Override
    public void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }
}
