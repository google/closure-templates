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
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.internal.TranslateExprNodeVisitor.TranslateExprNodeVisitorFactory;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.ExprUnion;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Translator of Soy expressions to their equivalent JS expressions.
 *
 * <p>This class only handles switching between V1 and V2 expressions. All actual work is done in
 * either {@link TranslateExprNodeVisitor} or {@link V1JsExprTranslator}.
 *
 */
public final class JsExprTranslator {

  private final TranslateExprNodeVisitorFactory translateExprNodeVisitorFactory;

  @Inject
  JsExprTranslator(TranslateExprNodeVisitorFactory translateExprNodeVisitorFactory) {
    this.translateExprNodeVisitorFactory = translateExprNodeVisitorFactory;
  }


  /**
   * Translates a Soy expression to the equivalent JS expression. Detects whether an expression
   * is Soy V2 or V1 syntax and performs the translation accordingly. Takes both an ExprNode and
   * the expression text as a string because Soy V1 code will not necessarily be parsable as an
   * ExprNode.
   *
   * @param expr The Soy expression to translate.
   * @param exprText The expression text. Only required for V1 support, nullable otherwise.
   * @param errorReporter
   * @return The built JS expression.
   */
  CodeChunk.WithValue translateToCodeChunk(
      @Nullable ExprNode expr,
      @Nullable String exprText,
      TranslationContext translationContext,
      ErrorReporter errorReporter) {

    if (expr != null &&
        (exprText == null ||
         new CheckAllFunctionsSupportedVisitor().exec(expr))) {
      // V2 expression.
      return translateExprNodeVisitorFactory
          .create(translationContext, errorReporter)
          .exec(expr);
    } else {
      // V1 expression.
      SourceLocation sourceLocation = expr != null
          ? expr.getSourceLocation()
          : SourceLocation.UNKNOWN;
      Preconditions.checkNotNull(exprText);
      // NOTE: this may cause the jscompiler to emit warnings, users will need to whitelist them or
      // fix their use of v1 exprs.
      return CodeChunk.fromExpr(
          V1JsExprTranslator.translateToJsExpr(
              exprText,
              sourceLocation,
              translationContext.soyToJsVariableMappings(),
              errorReporter),
          ImmutableList.<GoogRequire>of());
    }
  }

  /**
   * Translates a Soy expression to the equivalent JS expression. Only supports V2 exprs.
   */
  CodeChunk.WithValue translateToCodeChunk(
      ExprNode expr, TranslationContext translationContext, ErrorReporter errorReporter) {
    checkNotNull(expr);
    return translateToCodeChunk(expr, null /* exprText */, translationContext, errorReporter);
  }

  /**
   * Translates a Soy expression to the equivalent JS expression. Detects whether an expression
   * is Soy V2 or V1 syntax and performs the translation accordingly.
   */
  CodeChunk.WithValue translateToCodeChunk(
      ExprUnion union, TranslationContext translationContext, ErrorReporter errorReporter) {
    return translateToCodeChunk(
        union.getExpr(), union.getExprText(), translationContext, errorReporter);
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
