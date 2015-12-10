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

package com.google.template.soy.jssrc.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jssrc.internal.TranslateToJsExprVisitor.TranslateToJsExprVisitorFactory;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.ExprUnion;

import java.util.Deque;
import java.util.Map;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Translator of Soy expressions to their equivalent JS expressions.
 *
 */
public final class JsExprTranslator {

  /** Factory for creating an instance of TranslateToJsExprVisitor. */
  private final TranslateToJsExprVisitorFactory translateToJsExprVisitorFactory;

  /**
   * @param translateToJsExprVisitorFactory Factory for creating an instance of
   *     TranslateToJsExprVisitor.
   */
  @Inject
  JsExprTranslator(TranslateToJsExprVisitorFactory translateToJsExprVisitorFactory) {
    this.translateToJsExprVisitorFactory = translateToJsExprVisitorFactory;
  }


  /**
   * Translates a Soy expression to the equivalent JS expression. Detects whether an expression
   * is Soy V2 or V1 syntax and performs the translation accordingly. Takes both an ExprNode and
   * the expression text as a string because Soy V1 code will not necessarily be parsable as an
   * ExprNode.
   *
   * @param expr The Soy expression to translate.
   * @param exprText The expression text. Only required for V1 support, nullable otherwise.
   * @param localVarTranslations The current stack of replacement JS expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   * @return The built JS expression.
   */
  JsExpr translateToJsExpr(
      @Nullable ExprNode expr,
      @Nullable String exprText,
      Deque<Map<String, JsExpr>> localVarTranslations,
      ErrorReporter errorReporter) {

    if (expr != null &&
        (exprText == null ||
         new CheckAllFunctionsSupportedVisitor().exec(expr))) {
      // V2 expression.
      return translateToJsExprVisitorFactory.create(localVarTranslations, errorReporter).exec(expr);
    } else {
      // V1 expression.
      SourceLocation sourceLocation = expr != null
          ? expr.getSourceLocation()
          : SourceLocation.UNKNOWN;
      Preconditions.checkNotNull(exprText);
      return V1JsExprTranslator.translateToJsExpr(
          exprText, sourceLocation, localVarTranslations, errorReporter);
    }
  }

  /**
   * Translates a Soy expression to the equivalent JS expression. Only supports V2 exprs.
   */
  JsExpr translateToJsExpr(
      ExprNode expr, Deque<Map<String, JsExpr>> localVarTranslations, ErrorReporter errorReporter) {
    checkNotNull(expr);
    return translateToJsExpr(expr, null, localVarTranslations, errorReporter);
  }

  /**
   * Translates a Soy expression to the equivalent JS expression. Detects whether an expression
   * is Soy V2 or V1 syntax and performs the translation accordingly.
   */
  JsExpr translateToJsExpr(
      ExprUnion union,
      Deque<Map<String, JsExpr>> localVarTranslations,
      ErrorReporter errorReporter) {
    return translateToJsExpr(
        union.getExpr(), union.getExprText(), localVarTranslations, errorReporter);
  }

  /**
   * Private helper class to check whether all functions in an expression are supported
   * (implemented by an available {@link SoyJsSrcFunction}).
   */
  private static final class CheckAllFunctionsSupportedVisitor
      extends AbstractExprNodeVisitor<Boolean> {

    /** Whether all functions in the expression are supported. */
    private boolean areAllFunctionsSupported;

    @Override public Boolean exec(ExprNode node) {
      areAllFunctionsSupported = true;
      visit(node);
      return areAllFunctionsSupported;
    }

    @Override protected void visitFunctionNode(FunctionNode node) {
      SoyFunction function = node.getSoyFunction();
      if (!(function instanceof SoyJsSrcFunction) && !(function instanceof BuiltinFunction)) {
        areAllFunctionsSupported = false;
        return;  // already found an unsupported function, so don't keep looking
      }
      visitChildren(node);
    }

    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        if (!areAllFunctionsSupported) {
          return;  // already found an unsupported function, so don't keep looking
        }
        visitChildren((ParentExprNode) node);
      }
    }
  }

}
