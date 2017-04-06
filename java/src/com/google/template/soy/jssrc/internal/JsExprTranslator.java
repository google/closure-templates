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

import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.internal.TranslateExprNodeVisitor.TranslateExprNodeVisitorFactory;
import javax.inject.Inject;

/**
 * Translator of Soy expressions to their equivalent JS expressions.
 *
 * <p>This class only handles switching between V1 and V2 expressions. All actual work is done in
 * {@link TranslateExprNodeVisitor}.
 *
 */
// TODO(user): Consider removing this layer.
public final class JsExprTranslator {

  private final TranslateExprNodeVisitorFactory translateExprNodeVisitorFactory;

  @Inject
  JsExprTranslator(TranslateExprNodeVisitorFactory translateExprNodeVisitorFactory) {
    this.translateExprNodeVisitorFactory = translateExprNodeVisitorFactory;
  }

  /**
   * Translates a Soy expression to the equivalent JS expression.
   *
   * @param expr The Soy expression to translate.
   * @param translationContext
   * @param errorReporter For reporting syntax errors.
   * @return The built JS expression.
   */
  CodeChunk.WithValue translateToCodeChunk(
      ExprNode expr, TranslationContext translationContext, ErrorReporter errorReporter) {
    checkNotNull(expr);
    return translateExprNodeVisitorFactory.create(translationContext, errorReporter).exec(expr);
  }
}
