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

package com.google.template.soy.passes;

import com.google.common.base.Preconditions;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.basetree.SyntaxVersionUpperBound;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

/**
 * Visitor for asserting that all the nodes in a parse tree or subtree conform to the user-declared
 * syntax version.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} may be called on any node. There is no return value. However, a
 * {@code SoySyntaxException} is thrown if the given node or a descendant does not satisfy the
 * user-declared syntax version.
 *
 */
final class ReportSyntaxVersionErrorsVisitor extends AbstractSoyNodeVisitor<Void> {

  private static final SoyErrorKind SYNTAX_VERSION_OUT_OF_BOUNDS = SoyErrorKind.of("{0}: {1}");

  private final SyntaxVersion requiredSyntaxVersion;
  private final ErrorReporter errorReporter;
  private final String errorPreamble;

  /**
   * @param requiredSyntaxVersion The required minimum syntax version to check for.
   * @param isDeclared True if the required syntax version that we're checking for is user-declared.
   *     False if it is inferred.
   * @param errorReporter For reporting errors.
   */
  ReportSyntaxVersionErrorsVisitor(
      SyntaxVersion requiredSyntaxVersion, boolean isDeclared, ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
    this.requiredSyntaxVersion = requiredSyntaxVersion;
    this.errorPreamble = (requiredSyntaxVersion == SyntaxVersion.V1_0)
        ? "incorrect v1 syntax"
        : ((isDeclared ? "declared" : "inferred")
            + " syntax version " + requiredSyntaxVersion + " not satisfied");
  }

  @Override public Void exec(SoyNode node) {
    visitSoyNode(node);
    return null;
  }

  @Override protected void visitSoyNode(SoyNode node) {
    // ------ Record errors for this Soy node. ------
    if (!node.couldHaveSyntaxVersionAtLeast(requiredSyntaxVersion)) {
      SyntaxVersionUpperBound syntaxVersionBound = node.getSyntaxVersionUpperBound();
      Preconditions.checkNotNull(syntaxVersionBound);
      errorReporter.report(
          node.getSourceLocation(),
          SYNTAX_VERSION_OUT_OF_BOUNDS,
          errorPreamble,
          syntaxVersionBound.reasonStr);
    }

    // ------ Record errors for expressions held by this Soy node. ------
    if (node instanceof ExprHolderNode) {
      for (ExprUnion exprUnion : ((ExprHolderNode) node).getAllExprUnions()) {
        // Log parse errors if the required syntax version is at least V2, but the expression was
        // not parsable as V2.
        if (exprUnion.getExpr() == null && requiredSyntaxVersion.num >= SyntaxVersion.V2_0.num) {
          exprUnion.reportV2ParseErrors(errorReporter);
        }
      }
    }

    // ------ Record errors for descendants of this Soy node. ------
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }
}
