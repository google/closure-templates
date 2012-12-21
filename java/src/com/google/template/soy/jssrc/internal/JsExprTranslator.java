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

import com.google.inject.Inject;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.TranslateToJsExprVisitor.TranslateToJsExprVisitorFactory;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.internal.NonpluginFunction;

import java.util.Deque;
import java.util.Map;


/**
 * Translator of Soy expressions to their equivalent JS expressions.
 *
 * @author Kai Huang
 */
class JsExprTranslator {


  /** Map of all SoyJsSrcFunctions (name to function). */
  private final Map<String, SoyJsSrcFunction> soyJsSrcFunctionsMap;

  /** The options for generating JS source code. */
  private final SoyJsSrcOptions jsSrcOptions;

  /** Factory for creating an instance of TranslateToJsExprVisitor. */
  private final TranslateToJsExprVisitorFactory translateToJsExprVisitorFactory;


  /**
   * @param soyJsSrcFunctionsMap Map of all SoyJsSrcFunctions (name to function).
   * @param jsSrcOptions The options for generating JS source code.
   * @param translateToJsExprVisitorFactory Factory for creating an instance of
   *     TranslateToJsExprVisitor.
   */
  @Inject
  JsExprTranslator(
      Map<String, SoyJsSrcFunction> soyJsSrcFunctionsMap, SoyJsSrcOptions jsSrcOptions,
      TranslateToJsExprVisitorFactory translateToJsExprVisitorFactory) {
    this.soyJsSrcFunctionsMap = soyJsSrcFunctionsMap;
    this.jsSrcOptions = jsSrcOptions;
    this.translateToJsExprVisitorFactory = translateToJsExprVisitorFactory;
  }


  /**
   * Translates a Soy expression to the equivalent JS expression. Detects whether an expression
   * is Soy V2 or V1 syntax and performs the translation accordingly. Takes both an ExprNode and
   * the expression text as a string because Soy V1 code will not necessarily be parsable as an
   * ExprNode.
   *
   * @param expr The Soy expression to translate.
   * @param exprText The expression text.
   * @param localVarTranslations The current stack of replacement JS expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   * @return The built JS expression.
   */
  public JsExpr translateToJsExpr(
      ExprNode expr, String exprText, Deque<Map<String, JsExpr>> localVarTranslations) {

    if (expr != null &&
        (! jsSrcOptions.shouldAllowDeprecatedSyntax() ||
         (new CheckAllFunctionsSupportedVisitor(soyJsSrcFunctionsMap)).exec(expr))) {
      // V2 expression.
      return translateToJsExprVisitorFactory.create(localVarTranslations).exec(expr);
    } else {
      // V1 expression.
      return V1JsExprTranslator.translateToJsExpr(exprText, localVarTranslations);
    }
  }


  /**
   * Private helper class to check whether all functions in an expression are supported
   * (implemented by an available SoyJsSrcFunction).
   */
  private static class CheckAllFunctionsSupportedVisitor extends AbstractExprNodeVisitor<Boolean> {

    /** Map of all SoyJsSrcFunctions (name to function). */
    private final Map<String, SoyJsSrcFunction> soyJsSrcFunctionsMap;

    /** Whether all functions in the expression are supported. */
    private boolean areAllFunctionsSupported;

    public CheckAllFunctionsSupportedVisitor(Map<String, SoyJsSrcFunction> soyJsSrcFunctionsMap) {
      this.soyJsSrcFunctionsMap = soyJsSrcFunctionsMap;
    }

    @Override public Boolean exec(ExprNode node) {
      areAllFunctionsSupported = true;
      visit(node);
      return areAllFunctionsSupported;
    }

    @Override protected void visitFunctionNode(FunctionNode node) {

      String fnName = node.getFunctionName();
      if (NonpluginFunction.forFunctionName(fnName) == null &&
          ! soyJsSrcFunctionsMap.containsKey(fnName)) {
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
