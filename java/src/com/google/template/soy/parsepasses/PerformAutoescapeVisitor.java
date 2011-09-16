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
import com.google.inject.Inject;
import com.google.template.soy.base.IdGenerator;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.coredirectives.EscapeHtmlDirective;
import com.google.template.soy.coredirectives.NoAutoescapeDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.Map;


/**
 * Visitor for performing autoescape (for templates that have autoescape turned on, ensure there is
 * HTML-escaping on all 'print' nodes).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} should be called on a full parse tree. The directives on 'print' nodes may be
 * modified. There is no return value.
 *
 * @author Kai Huang
 */
public class PerformAutoescapeVisitor extends AbstractSoyNodeVisitor<Void> {


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
  public PerformAutoescapeVisitor(Map<String, SoyPrintDirective> soyDirectivesMap) {
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
        throw new SoySyntaxException(
            "Failed to find SoyPrintDirective with name '" + directiveNode.getName() + "'" +
            " (tag " + node.toSourceString() +")");
      }
      if (directive.shouldCancelAutoescape()) {
        shouldCancelAutoescape = true;
        if (directive instanceof NoAutoescapeDirective) {
          node.removeChild(directiveNode);
        }
      }
    }

    // If appropriate, apply autoescape by adding an |escapeHtml directive (should be applied first
    // because other directives may add HTML tags).
    if (currTemplateShouldAutoescape && !shouldCancelAutoescape) {
      PrintDirectiveNode newEscapeHtmlDirectiveNode =
          new PrintDirectiveNode(nodeIdGen.genId(), EscapeHtmlDirective.NAME, "");
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
