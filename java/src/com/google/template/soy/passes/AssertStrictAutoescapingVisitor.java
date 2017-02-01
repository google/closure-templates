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

package com.google.template.soy.passes;

import com.google.common.base.Preconditions;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.NamespaceDeclaration;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;

/**
 * Visitor that ensures files and templates use strict autoescaping. Backends such as Miso (Python)
 * can choose to disallow all other types of autoescaping besides strict.
 *
 */
final class AssertStrictAutoescapingVisitor extends AbstractSoyNodeVisitor<Void> {

  private static final SoyErrorKind INVALID_AUTOESCAPING =
      SoyErrorKind.of("Invalid use of non-strict when strict autoescaping is required.");
  private final ErrorReporter errorReporter;

  AssertStrictAutoescapingVisitor(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Void exec(SoyNode soyNode) {
    Preconditions.checkArgument(
        soyNode instanceof SoyFileSetNode || soyNode instanceof SoyFileNode);
    super.exec(soyNode);
    return null;
  }

  @Override
  protected void visitSoyFileNode(SoyFileNode node) {
    NamespaceDeclaration namespaceDeclaration = node.getNamespaceDeclaration();
    if (namespaceDeclaration.getDefaultAutoescapeMode() != AutoescapeMode.STRICT) {
      errorReporter.report(namespaceDeclaration.getAutoescapeModeLocation(), INVALID_AUTOESCAPING);
      // If the file isn't strict, skip children to avoid spamming errors.
      return;
    }

    visitChildren(node);
  }

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    if (node.getAutoescapeMode() != AutoescapeMode.STRICT) {
      errorReporter.report(node.getSourceLocation(), INVALID_AUTOESCAPING);
    }
  }

  /** Fallback implementation for all other nodes. */
  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }
}
