/*
 * Copyright 2009 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.SoyGeneralOptions.CssHandlingScheme;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoytreeUtils;

import java.util.List;

/**
 * Visitor for handling 'css' commands.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} must be called on a full parse tree.
 *
 * <p> Each scheme is handled as follows:
 * (a) LITERAL: Turn each CssNode into a RawTextNode.
 * (b) REFERENCE: Turn each CssNode into a PrintNode.
 * (c) BACKEND_SPECIFIC: Don't change anything. Let backend handle 'css' commands.
 *
 */
public final class HandleCssCommandVisitor extends AbstractSoyNodeVisitor<Void> {

  private static final SoyError INVALID_CSS_REFERENCE = SoyError.of(
    "The css-handling scheme is ''reference'', but '{css}' tag does not contain a valid reference");


  /** Scheme for handling 'css' commands. */
  private final CssHandlingScheme cssHandlingScheme;

  /**
   * @param cssHandlingScheme Scheme for handling 'css' commands.
   * @param errorReporter For reporting errors.
   */
  public HandleCssCommandVisitor(CssHandlingScheme cssHandlingScheme, ErrorReporter errorReporter) {
    super(errorReporter);
    this.cssHandlingScheme = cssHandlingScheme;
  }


  @Override public Void exec(SoyNode node) {
    visit(node);
    return null;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {

    if (cssHandlingScheme == CssHandlingScheme.BACKEND_SPECIFIC) {
      return;  // leave CssNodes alone to be handled by backend
    }

    // We find all the CssNodes before replacing them because we don't want the modifications to
    // interfere with the traversal.

    // This pass simply finds all the CssNodes.
    List<CssNode> cssNodes = SoytreeUtils.getAllNodesOfType(node, CssNode.class);

    // Perform the replacments.
    IdGenerator nodeIdGen = node.getNodeIdGenerator();
    for (CssNode cssNode : cssNodes) {

      StandaloneNode newNode;

      if (cssHandlingScheme == CssHandlingScheme.LITERAL) {
        newNode = new RawTextNode(
            nodeIdGen.genId(), cssNode.getCommandText(), cssNode.getSourceLocation());

      } else if (cssHandlingScheme == CssHandlingScheme.REFERENCE) {
        PrintNode newPrintNode = new PrintNode.Builder(
            nodeIdGen.genId(),
            false /* isImplicit */,
            SourceLocation.UNKNOWN)
            .exprText(cssNode.getCommandText())
            .build(errorReporter);
        newPrintNode.addChild(new PrintDirectiveNode.Builder(
            nodeIdGen.genId(), "|noAutoescape", "", SourceLocation.UNKNOWN)
            .build(errorReporter));
        newNode = newPrintNode;
        // Check that the expression is a valid reference.
        boolean isInvalidExpr = false;
        if (newPrintNode.getExprUnion().getExpr() == null) {
          isInvalidExpr = true;
        } else {
          ExprNode exprNode = newPrintNode.getExprUnion().getExpr().getRoot();
          if (!(exprNode instanceof GlobalNode || exprNode instanceof VarRefNode ||
              exprNode instanceof DataAccessNode)) {
            isInvalidExpr = true;
          }
        }
        if (isInvalidExpr) {
          errorReporter.report(cssNode.getSourceLocation(), INVALID_CSS_REFERENCE);
        }

      } else {
        throw new AssertionError();
      }

      cssNode.getParent().replaceChild(cssNode, newNode);
    }
  }
}
