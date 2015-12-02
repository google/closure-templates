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
      SoyErrorKind.of("Template {0} has {1} visibility, not visible from here.");

  private final ErrorReporter errorReporter;

  /** Registry of all templates in the Soy tree. */
  private final TemplateRegistry templateRegistry;

  /** Save the name of the file and template currently being visited. */
  private String currentFileName;

  CheckTemplateVisibility(TemplateRegistry templateRegistry, ErrorReporter errorReporter) {
    this.templateRegistry = templateRegistry;
    this.errorReporter = errorReporter;
  }

  @Override protected void visitSoyFileNode(SoyFileNode node) {
    currentFileName = node.getSourceLocation().getFileName();
    visitChildren(node);
    currentFileName = null;
  }

  @Override protected void visitTemplateNode(TemplateNode node) {
    visitChildren(node);
  }

  @Override protected void visitCallNode(CallNode node) {
    if (node instanceof CallBasicNode) {
      handleBasicNode((CallBasicNode) node);
    }
    visitChildren(node);
  }

  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

  private void handleBasicNode(CallBasicNode node) {
    String calleeName = node.getCalleeName();
    TemplateNode definition = templateRegistry.getBasicTemplate(calleeName);
    if (definition != null && !isVisible(definition)) {
      errorReporter.report(
          node.getSourceLocation(),
          CALLEE_NOT_VISIBLE,
          calleeName,
          definition.getVisibility().getAttributeValue());
    }
  }

  private boolean isVisible(TemplateNode definition) {
    // The only visibility level that this pass currently cares about is PRIVATE.
    if (definition.getVisibility() != Visibility.PRIVATE) {
      return true;
    }
    // TODO(lukes): this check isn't right, it is only checking file names, it should be checking
    // file paths (or really, it should be checking that both templates have the same parent)
    // Templates marked visibility="private" are only visible to other templates in the same file.
    return currentFileName.equals(definition.getSourceLocation().getFileName());
  }
}

