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

package com.google.template.soy.jssrc.restricted;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.exprtree.Operator;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Common utilities for dealing with JS expressions.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
 */
public class JsExprUtils {

  private JsExprUtils() {}


  /**
   * Builds one JS expression that computes the concatenation of the given JS expressions. The '+'
   * operator is used for concatenation. Operands will be protected with an extra pair of
   * parentheses if and only if needed.
   *
   * @param jsExprs The JS expressions to concatentate.
   * @return One JS expression that computes the concatenation of the given JS expressions.
   */
  public static JsExpr concatJsExprs(List<JsExpr> jsExprs) {

    if (jsExprs.size() == 0) {
      return new JsExpr("''", Integer.MAX_VALUE);
    }

    if (jsExprs.size() == 1) {
      return jsExprs.get(0);
    }

    int plusOpPrec = Operator.PLUS.getPrecedence();
    StringBuilder resultSb = new StringBuilder();

    boolean isFirst = true;
    for (JsExpr jsExpr : jsExprs) {

      // The first operand needs protection only if it's strictly lower precedence. The non-first
      // operands need protection when they're lower or equal precedence. (This is true for all
      // left-associative operators.)
      boolean needsProtection = isFirst ? jsExpr.getPrecedence() < plusOpPrec
                                        : jsExpr.getPrecedence() <= plusOpPrec;

      if (isFirst) {
        isFirst = false;
      } else {
        resultSb.append(" + ");
      }

      if (needsProtection) {
        resultSb.append('(').append(jsExpr.getText()).append(')');
      } else {
        resultSb.append(jsExpr.getText());
      }
    }

    return new JsExpr(resultSb.toString(), plusOpPrec);
  }


  /**
   * Wraps an expression in a function call.
   *
   * @param functionExprText expression for the function to invoke, such as a function name or
   *     constructor phrase (such as "new SomeClass").
   * @param jsExpr the expression to compute the argument to the function
   * @return a JS expression consisting of a call to the specified function, applied to the
   *     provided expression.
   */
  @VisibleForTesting
  static JsExpr wrapWithFunction(String functionExprText, JsExpr jsExpr) {
    Preconditions.checkNotNull(functionExprText);
    return new JsExpr(functionExprText + "(" + jsExpr.getText() + ")", Integer.MAX_VALUE);
  }


  /**
   * Wraps with the proper SanitizedContent constructor if contentKind is non-null.
   *
   * @param contentKind The kind of sanitized content.
   * @param jsExpr The expression to wrap.
   */
  public static JsExpr maybeWrapAsSanitizedContent(
      @Nullable ContentKind contentKind, JsExpr jsExpr) {
    if (contentKind == null) {
      return jsExpr;
    } else {
      return wrapWithFunction(NodeContentKinds.toJsSanitizedContentOrdainer(contentKind), jsExpr);
    }
  }
}
