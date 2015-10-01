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
import com.google.template.soy.error.SoyError;
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

  private static final SoyError DOUBLE_AMPERSAND_DOUBLE_PIPE_OR_BANG_IN_EXPR =
      SoyError.of("{0}: bad expression: ''{1}'', possibly due to using &&/||/! "
          + "instead of and/or/not operators.");
  private static final SoyError DOUBLE_QUOTED_STRING =
      SoyError.of("{0}: bad expression: ''{1}'', possibly due to using double quotes "
          + "instead of single quotes for string literal.");
  private static final SoyError GENERIC_NOT_PARSABLE_AS_V2_EXPR =
      SoyError.of("{0}: bad expression: ''{1}''.");
  private static final SoyError SYNTAX_VERSION_OUT_OF_BOUNDS =
      SoyError.of("{0}: {1}");

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
        // The required syntax version is at least V2, but the expression is not parsable as V2.
        // That's an error. Consider a couple of specific errors based on the expression text,
        // then fall back to a generic error message.
        if (exprUnion.getExpr() == null && requiredSyntaxVersion.num >= SyntaxVersion.V2_0.num) {
          String exprText = exprUnion.getExprText();
          if (possiblyContainsDoubleQuotedString(exprText)) {
            errorReporter.report(
                node.getSourceLocation(),
                DOUBLE_QUOTED_STRING,
                errorPreamble,
                exprText);
          } else if (exprText.contains("&&") || exprText.contains("||") || exprText.contains("!")) {
            errorReporter.report(
                node.getSourceLocation(),
                DOUBLE_AMPERSAND_DOUBLE_PIPE_OR_BANG_IN_EXPR,
                errorPreamble,
                exprText);
          } else {
            errorReporter.report(
                node.getSourceLocation(),
                GENERIC_NOT_PARSABLE_AS_V2_EXPR,
                errorPreamble,
                exprText);
          }
        }
      }
    }

    // ------ Record errors for descendants of this Soy node. ------
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

  /**
   * A heuristic is used instead of a simple regex, because Soy V2 allows quoting
   * double-quotes with single-quotes.
   */
  private static boolean possiblyContainsDoubleQuotedString(String exprText) {
    int numSingleQuotes = 0;
    int numDoubleQuotes = 0;
    for (int i = 0; i < exprText.length(); i++) {
      switch (exprText.charAt(i)) {
        case '\'':
          numSingleQuotes++;
          break;
        case '"':
          numDoubleQuotes++;
          break;
      }
    }
    return numDoubleQuotes >= 2 && numSingleQuotes <= 1;
  }
}
