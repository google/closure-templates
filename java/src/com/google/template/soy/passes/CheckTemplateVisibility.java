/*
 * Copyright 2014 Google Inc.
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
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.Visibility;

/**
 * Visitor for checking the visibility of a template.
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class CheckTemplateVisibility extends AbstractSoyNodeVisitor<Void> {

  private static final SoyErrorKind CALLEE_NOT_VISIBLE =
      SoyErrorKind.of("{0} has {1} access in {2}");

  private final ErrorReporter errorReporter;

  /** Registry of all templates in the Soy tree. */
  private final TemplateRegistry templateRegistry;

  CheckTemplateVisibility(TemplateRegistry templateRegistry, ErrorReporter errorReporter) {
    this.templateRegistry = templateRegistry;
    this.errorReporter = errorReporter;
  }

  @Override
  protected void visitCallBasicNode(CallBasicNode node) {
    visitChildren(node);
    String calleeName = node.getCalleeName();
    TemplateNode definition = templateRegistry.getBasicTemplate(calleeName);
    if (definition != null && !isVisible(node, definition)) {
      errorReporter.report(
          node.getSourceLocation(),
          CALLEE_NOT_VISIBLE,
          calleeName,
          definition.getVisibility().getAttributeValue(),
          definition.getParent().getFilePath());
    }
  }

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

  private static boolean isVisible(CallNode caller, TemplateNode callee) {
    // The only visibility level that this pass currently cares about is PRIVATE.
    // Templates are visible if they are not private or are defined in the same file.
    return callee.getVisibility() != Visibility.PRIVATE
        || callee.getParent().equals(caller.getNearestAncestor(SoyFileNode.class));
  }
}
