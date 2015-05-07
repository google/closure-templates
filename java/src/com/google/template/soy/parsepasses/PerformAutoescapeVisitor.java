/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.parsepasses;

import com.google.common.collect.Lists;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.coredirectives.EscapeHtmlDirective;
import com.google.template.soy.coredirectives.NoAutoescapeDirective;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.Map;

import javax.inject.Inject;

/**
 * Visitor for performing autoescape (for templates that have autoescape turned on, ensure there is
 * HTML-escaping on all 'print' nodes).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} should be called on a full parse tree. The directives on 'print' nodes may be
 * modified. There is no return value.
 *
 */
public final class PerformAutoescapeVisitor extends AbstractSoyNodeVisitor<Void> {

  private static final SoyError UNKNOWN_PRINT_DIRECTIVE = SoyError.of(
      "Unknown print directive ''{0}''.");

  /** Map of all SoyPrintDirectives (name to directive). */
  private final Map<String, SoyPrintDirective> soyDirectivesMap;

  /** The node id generator for the parse tree. Retrieved from the root SoyFileSetNode. */
  private IdGenerator nodeIdGen;

  /** Whether the current template that we're in has autoescape turned on (during a visit pass). */
  private boolean currTemplateShouldAutoescape;


  /**
   * @param soyDirectivesMap Map of all SoyPrintDirectives (name to directive).
   */
  @Inject
  public PerformAutoescapeVisitor(
      Map<String, SoyPrintDirective> soyDirectivesMap, ErrorReporter errorReporter) {
    super(errorReporter);
    this.soyDirectivesMap = soyDirectivesMap;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {
    nodeIdGen = node.getNodeIdGenerator();
    visitChildren(node);
  }


  @Override protected void visitTemplateNode(TemplateNode node) {
    currTemplateShouldAutoescape = node.getAutoescapeMode() != AutoescapeMode.FALSE;
    visitChildren(node);
  }


  @Override protected void visitPrintNode(PrintNode node) {

    // Traverse the list to (a) record whether we saw any directive that cancels autoescape
    // (including 'noAutoescape' of course) and (b) remove 'noAutoescape' directives.
    boolean shouldCancelAutoescape = false;
    for (PrintDirectiveNode directiveNode : Lists.newArrayList(node.getChildren()) /*copy*/) {
      SoyPrintDirective directive = soyDirectivesMap.get(directiveNode.getName());
      if (directive == null) {
        errorReporter.report(
            directiveNode.getSourceLocation(), UNKNOWN_PRINT_DIRECTIVE, directiveNode.getName());
        continue; // Proceeding would cause NullPointerExceptions.
      }
      if (directive.shouldCancelAutoescape()) {
        shouldCancelAutoescape = true;
        if (!currTemplateShouldAutoescape && directive instanceof NoAutoescapeDirective) {
          // Remove reundant noAutoescape in autoescape="false" templates; however, keep it for
          // other templates. This ensures filterNoAutoescape gets called for all (even
          // non-contextually) autoescaped templates, as a safeguard against tainted
          // ContentKind.TEXT from ending up noAutoescaped.
          node.removeChild(directiveNode);
        }
      }
    }

    // If appropriate, apply autoescape by adding an |escapeHtml directive (should be applied first
    // because other directives may add HTML tags).
    if (currTemplateShouldAutoescape && !shouldCancelAutoescape) {
      PrintDirectiveNode newEscapeHtmlDirectiveNode = new PrintDirectiveNode.Builder(
          nodeIdGen.genId(), EscapeHtmlDirective.NAME, "", SourceLocation.UNKNOWN)
          .build(errorReporter);
      node.addChild(0, newEscapeHtmlDirectiveNode);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

}
