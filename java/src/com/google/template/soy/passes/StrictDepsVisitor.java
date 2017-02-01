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

import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;

/**
 * Visitor to check that there are no external calls. Used by backends that disallow external calls,
 * such as the Tofu (JavaObj) backend.
 *
 * <p>{@link #exec} should be called on a {@code SoyFileSetNode} or a {@code SoyFileNode}. There is
 * no return value. A {@code SoySyntaxException} is thrown if an error is found.
 *
 */
public final class StrictDepsVisitor extends AbstractSoyNodeVisitor<Void> {

  private static final SoyErrorKind CALL_TO_UNDEFINED_TEMPLATE =
      SoyErrorKind.of("Undefined template ''{0}''.");
  private static final SoyErrorKind CALL_TO_INDIRECT_DEPENDENCY =
      SoyErrorKind.of(
          "Call is satisfied only by indirect dependency {0}. Add it as a direct dependency.");
  private static final SoyErrorKind CALL_FROM_DEP_TO_SRC =
      SoyErrorKind.of(
          "Illegal call to ''{0}'', because according to the dependency graph, {1} depends on {2}, "
              + "not the other way around.");

  /** Registry of all templates in the Soy tree. */
  private final TemplateRegistry templateRegistry;

  private final ErrorReporter errorReporter;

  public StrictDepsVisitor(TemplateRegistry templateRegistry, ErrorReporter errorReporter) {
    this.templateRegistry = templateRegistry;
    this.errorReporter = errorReporter;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  // TODO(gboyer): Consider some deltemplate checking, but it's hard to make a coherent case for
  // deltemplates since it's legitimate to have zero implementations, or to have the implementation
  // in a different part of the dependency graph (if it's late-bound).
  @Override
  protected void visitCallBasicNode(CallBasicNode node) {
    TemplateNode callee = templateRegistry.getBasicTemplate(node.getCalleeName());

    if (callee == null) {
      errorReporter.report(
          node.getSourceLocation(), CALL_TO_UNDEFINED_TEMPLATE, node.getCalleeName());
    } else {
      SoyFileKind callerKind = node.getNearestAncestor(SoyFileNode.class).getSoyFileKind();
      SoyFileKind calleeKind = callee.getParent().getSoyFileKind();
      if (calleeKind == SoyFileKind.INDIRECT_DEP && callerKind == SoyFileKind.SRC) {
        errorReporter.report(
            node.getSourceLocation(),
            CALL_TO_INDIRECT_DEPENDENCY,
            callee.getSourceLocation().getFilePath());
      }

      // Double check if a dep calls a source. We shouldn't usually see this since the dependency
      // should fail due to unknown template, but it doesn't hurt to add this.
      if (calleeKind == SoyFileKind.SRC && callerKind != SoyFileKind.SRC) {
        errorReporter.report(
            node.getSourceLocation(),
            CALL_FROM_DEP_TO_SRC,
            callee.getTemplateNameForUserMsgs(),
            callee.getSourceLocation().getFilePath(),
            node.getSourceLocation().getFilePath());
      }
    }

    // Don't forget to visit content within CallParamContentNodes.
    visitChildren(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }
}
