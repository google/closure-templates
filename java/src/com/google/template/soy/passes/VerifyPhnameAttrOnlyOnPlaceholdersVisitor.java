/*
 * Copyright 2011 Google Inc.
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

import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

/**
 * Visitor to verify that all occurrences of the 'phname' attribute are on message placeholders.
 *
 * <p>Note: Doesn't check HTML tags since we don't parse HTML tags outside of messages anyway. Only
 * checks PrintNode and CallNode.
 *
 */
public final class VerifyPhnameAttrOnlyOnPlaceholdersVisitor extends AbstractSoyNodeVisitor<Void> {
  private static final SoyErrorKind INVALID_PLACEHOLDER =
      SoyErrorKind.of("''phname'' attributes are only valid inside '''{'msg...'' tags");
  private final ErrorReporter errorReporter;

  public VerifyPhnameAttrOnlyOnPlaceholdersVisitor(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  protected void visitPrintNode(PrintNode node) {
    visitMsgPlaceholderInitialContentNodeHelper(node);
  }

  @Override
  protected void visitCallNode(CallNode node) {
    visitMsgPlaceholderInitialContentNodeHelper(node);
    visitChildren(node);
  }

  private void visitMsgPlaceholderInitialContentNodeHelper(MsgPlaceholderInitialNode node) {
    if (node.getUserSuppliedPhName() != null && !(node.getParent() instanceof MsgPlaceholderNode)) {
      errorReporter.report(node.getSourceLocation(), INVALID_PLACEHOLDER);
    }
  }

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }
}
