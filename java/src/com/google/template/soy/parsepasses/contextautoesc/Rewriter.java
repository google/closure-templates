/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.EscapingMode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

/**
 * Applies changes specified in {@link Inferences} to a Soy parse tree.
 *
 */
final class Rewriter {

  /** The changes to make. */
  private final Inferences inferences;

  private final IdGenerator idGen;

  private final ImmutableMap<String, ? extends SoyPrintDirective> printDirectives;
  private final RewriterVisitor mutator = new RewriterVisitor();

  Rewriter(
      Inferences inferences,
      IdGenerator idGen,
      ImmutableMap<String, ? extends SoyPrintDirective> printDirectives) {
    this.inferences = inferences;
    this.idGen = idGen;
    this.printDirectives = printDirectives;
  }

  /** @return Derived templates that should be added to the parse tree. */
  public void rewrite(SoyFileNode file) {
    mutator.exec(file);
  }

  /** A visitor that applies the changes in Inferences to a Soy tree. */
  private final class RewriterVisitor extends AbstractSoyNodeVisitor<Void> {

    /** Add any escaping directives. */
    @Override
    protected void visitPrintNode(PrintNode printNode) {
      ImmutableList<EscapingMode> escapingModes = inferences.getEscapingModesForNode(printNode);
      for (EscapingMode escapingMode : escapingModes) {
        PrintDirectiveNode newPrintDirective =
            PrintDirectiveNode.createSyntheticNode(
                idGen.genId(),
                Identifier.create(escapingMode.directiveName, printNode.getSourceLocation()),
                printNode.getSourceLocation(),
                printDirectives.get(escapingMode.directiveName));
        // Figure out where to put the new directive.
        // Normally they go at the end to ensure that the value printed is of the appropriate type,
        // but if there are SanitizedContentOperators at the end, then make sure that their input
        // is of the appropriate type since we know that they will not change the content type.
        int newPrintDirectiveIndex = printNode.numChildren();
        while (newPrintDirectiveIndex > 0) {
          SoyPrintDirective printDirective =
              printNode.getChild(newPrintDirectiveIndex - 1).getPrintDirective();
          SanitizedContentKind contentKind =
              printDirective instanceof SanitizedContentOperator
                  ? SanitizedContentKind.valueOf(
                      ((SanitizedContentOperator) printDirective).getContentKind().name())
                  : null;
          if (contentKind == null || contentKind != escapingMode.contentKind) {
            break;
          }
          --newPrintDirectiveIndex;
        }

        printNode.addChild(newPrintDirectiveIndex, newPrintDirective);
      }
    }

    /** Do nothing. */
    @Override
    protected void visitRawTextNode(RawTextNode rawTextNode) {
      // TODO: Possibly normalize raw text nodes by adding quotes around unquoted attributes with
      // non-noescape dynamic content to avoid the need for space escaping.
    }

    /** Grabs the inferred escaping directives from the node in string form. */
    private ImmutableList<SoyPrintDirective> getDirectivesForNode(SoyNode node) {
      ImmutableList.Builder<SoyPrintDirective> escapingDirectiveNames =
          new ImmutableList.Builder<>();
      for (EscapingMode escapingMode : inferences.getEscapingModesForNode(node)) {
        escapingDirectiveNames.add(printDirectives.get(escapingMode.directiveName));
      }
      return escapingDirectiveNames.build();
    }

    /** Sets the escaping directives we inferred on the node. */
    @Override
    protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
      node.setEscapingDirectives(getDirectivesForNode(node));
      visitChildren(node);
    }

    /** Rewrite call targets. */
    @Override
    protected void visitCallNode(CallNode node) {
      node.setEscapingDirectives(getDirectivesForNode(node));

      visitChildren(node);
    }

    /** Recurses to children. */
    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }
}
