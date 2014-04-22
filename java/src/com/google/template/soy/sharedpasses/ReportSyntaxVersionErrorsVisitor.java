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

package com.google.template.soy.sharedpasses;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.basetree.SyntaxVersionBound;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.CommandNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;

import java.util.List;


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
public class ReportSyntaxVersionErrorsVisitor extends AbstractSoyNodeVisitor<Void> {


  /** The required minimum syntax version to check for. */
  private final SyntaxVersion requiredSyntaxVersion;

  /** True if the required syntax version that we're checking for is user-declared. False if it is
   *  inferred. */
  private final boolean isDeclared;

  /** Syntax exceptions for errors found (during a pass). */
  private List<SoySyntaxException> syntaxExceptions;


  /**
   * @param requiredSyntaxVersion The required minimum syntax version to check for.
   * @param isDeclared True if the required syntax version that we're checking for is user-declared.
   *     False if it is inferred.
   */
  public ReportSyntaxVersionErrorsVisitor(SyntaxVersion requiredSyntaxVersion, boolean isDeclared) {
    this.requiredSyntaxVersion = requiredSyntaxVersion;
    this.isDeclared = isDeclared;
    this.syntaxExceptions = null;
  }


  /**
   * {@inheritDoc}
   * @throws SoySyntaxException If the given node or a descendant does not satisfy the user-declared
   *     syntax version.
   */
  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  @Override public Void exec(SoyNode node) {

    syntaxExceptions = Lists.newArrayList();
    visitSoyNode(node);

    int numErrors = syntaxExceptions.size();
    if (numErrors != 0) {
      StringBuilder errorMsgBuilder = new StringBuilder();
      errorMsgBuilder.append(String.format(
          "Found %s where %s:",
          (numErrors == 1) ? "error" : numErrors + " errors",
          (requiredSyntaxVersion == SyntaxVersion.V1_0) ? "syntax is incorrect" :
              (isDeclared ? "declared" : "inferred") + " syntax version " + requiredSyntaxVersion +
                  " is not satisfied"));
      if (numErrors == 1) {
        errorMsgBuilder.append(' ').append(syntaxExceptions.get(0).getMessage());
      } else {
        for (int i = 0; i < numErrors; i++) {
          errorMsgBuilder.append(String.format(
              "\n%s. %s", i + 1, syntaxExceptions.get(i).getMessage()));
        }
      }
      throw SoySyntaxException.createWithoutMetaInfo(errorMsgBuilder.toString());
    }

    syntaxExceptions = null;
    return null;
  }


  @Override protected void visitSoyNode(SoyNode node) {

    // ------ Record errors for this Soy node. ------
    if (! node.couldHaveSyntaxVersionAtLeast(requiredSyntaxVersion)) {
      String nodeStringForErrorMsg =
          (node instanceof CommandNode) ? "Tag " + ((CommandNode) node).getTagString() :
              (node instanceof SoyFileNode) ? "File " + ((SoyFileNode) node).getFileName() :
                  (node instanceof PrintDirectiveNode) ?
                      "Print directive \"" + node.toSourceString() + "\"" :
                      "Node " + node.toSourceString();
      SyntaxVersionBound syntaxVersionBound = node.getSyntaxVersionBound();
      assert syntaxVersionBound != null;
      addError(nodeStringForErrorMsg + ": " + syntaxVersionBound.reasonStr, node);
    }

    // ------ Record errors for expressions held by this Soy node. ------
    if (node instanceof ExprHolderNode) {

      ReportSyntaxVersionErrorsExprVisitor exprVisitor =
          new ReportSyntaxVersionErrorsExprVisitor((ExprHolderNode) node);

      for (ExprUnion exprUnion : ((ExprHolderNode) node).getAllExprUnions()) {

        if (exprUnion.getExpr() != null) {
          // This expression is parsable as V2. Visit the expr tree to collect errors.
          exprVisitor.exec(exprUnion.getExpr());

        } else {
          // This expression is not parsable as V2. This is only an error for V2.0+.
          if (requiredSyntaxVersion.num < SyntaxVersion.V2_0.num) {
            continue;
          }

          String exprText = exprUnion.getExprText();
          String errorMsgPrefix = "Invalid expression \"" + exprText + "\"";

          // Specific error message for possible double-quoted string in expression.
          int numSingleQuotes = 0;
          int numDoubleQuotes = 0;
          for (int i = 0; i < exprText.length(); i++) {
            switch (exprText.charAt(i)) {
              case '\'': numSingleQuotes++; break;
              case '"': numDoubleQuotes++; break;
            }
          }
          if (numDoubleQuotes >= 2 && numSingleQuotes <= 1) {
            addError(
                errorMsgPrefix + ", possibly due to using double quotes instead of single quotes" +
                    " for string literal.",
                node);
            continue;
          }

          // Specific error message for &&/||/! in expression.
          if (exprText.contains("&&") || exprText.contains("||") || exprText.contains("!")) {
            addError(
                errorMsgPrefix + ", possibly due to using &&/||/! instead of and/or/not operators.",
                node);
            continue;
          }

          // General error message for V1 expression.
          addError(errorMsgPrefix + ".", node);
        }
      }
    }

    // ------ Record errors for descendants of this Soy node. ------
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }


  /**
   * Private helper for visitSoyNode().
   */
  private void addError(String errorMsg, SoyNode node) {
    syntaxExceptions.add(SoySyntaxExceptionUtils.createWithNode(errorMsg, node));
  }


  // -----------------------------------------------------------------------------------------------


  private class ReportSyntaxVersionErrorsExprVisitor extends AbstractExprNodeVisitor<Void> {


    /** The ExprHolderNode that this visitor was created for. */
    private final ExprHolderNode exprHolder;

    /** The root of the expression that we're traversing (during a pass). */
    private ExprRootNode<?> exprRoot;


    public ReportSyntaxVersionErrorsExprVisitor(ExprHolderNode exprHolder) {
      this.exprHolder = exprHolder;
    }


    @Override public Void exec(ExprNode node) {

      Preconditions.checkArgument(node instanceof ExprRootNode<?>);
      exprRoot = (ExprRootNode) node;
      visit(node);
      exprRoot = null;
      return null;
    }


    @Override public void visitExprNode(ExprNode node) {

      if (! node.couldHaveSyntaxVersionAtLeast(requiredSyntaxVersion)) {
        SyntaxVersionBound syntaxVersionBound = node.getSyntaxVersionBound();
        assert syntaxVersionBound != null;
        addError(
            String.format(
                "Invalid expression \"%s\": %s", exprRoot.toSourceString(),
                syntaxVersionBound.reasonStr),
            exprHolder);
      }

      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }

  }

}
