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
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.exprtree.Operator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Common utilities for dealing with JS expressions.
 *
 * <p>Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 */
public class JsExprUtils {

  /** Expression constant for empty string. */
  private static final JsExpr EMPTY_STRING = new JsExpr("''", Integer.MAX_VALUE);

  private JsExprUtils() {}

  /**
   * Builds one JS expression that computes the concatenation of the given JS expressions. The '+'
   * operator is used for concatenation. Operands will be protected with an extra pair of
   * parentheses if and only if needed.
   *
   * <p>The resulting expression is not guaranteed to be a string if the operands do not produce
   * strings when combined with the plus operator; e.g. 2+2 might be 4 instead of '22'.
   *
   * @param jsExprs The JS expressions to concatentate.
   * @return One JS expression that computes the concatenation of the given JS expressions.
   */
  public static JsExpr concatJsExprs(List<? extends JsExpr> jsExprs) {
    if (jsExprs.isEmpty()) {
      return EMPTY_STRING;
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
      boolean needsProtection =
          isFirst ? jsExpr.getPrecedence() < plusOpPrec : jsExpr.getPrecedence() <= plusOpPrec;

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

  public static boolean isStringLiteral(JsExpr jsExpr) {
    String jsExprText = jsExpr.getText();
    int jsExprTextLastIndex = jsExprText.length() - 1;
    if (jsExprTextLastIndex < 1
        || jsExprText.charAt(0) != '\''
        || jsExprText.charAt(jsExprTextLastIndex) != '\'') {
      return false;
    }
    for (int i = 1; i < jsExprTextLastIndex; ++i) {
      char c = jsExprText.charAt(i);
      if (c == '\'') {
        return false;
      }
      if (c == '\\') {
        // We do not bother skipping through the whole escape if it takes up more than one character
        // beyond the backslash, e.g. \u1234 or \123 or \x12, since none of such escapes' characters
        // can be an apostrophe, which is all we really care about. Nor do we check that the escape
        // doesn't include the final apostrophe, since that would mean the JS expression is invalid.
        ++i;
      }
    }
    return true;
  }

  public static JsExpr toString(JsExpr expr) {
    // If the expression is a string, nothing to do.
    if (isStringLiteral(expr)) {
      return expr;
    }
    // Add empty string first, which ensures the plus operator always means string concatenation.
    // Consider:
    //    '' + 6 + 6 + 6 = '666'
    //    6 + 6 + 6 + '' = '18'
    return concatJsExprs(ImmutableList.of(EMPTY_STRING, expr));
  }

  /**
   * Wraps an expression in a function call.
   *
   * @param functionExprText expression for the function to invoke, such as a function name or
   *     constructor phrase (such as "new SomeClass").
   * @param jsExpr the expression to compute the argument to the function
   * @return a JS expression consisting of a call to the specified function, applied to the provided
   *     expression.
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
   * @deprecated This method is not safe to use without a security review, please migrate away from
   *     it.
   */
  @Deprecated
  public static JsExpr maybeWrapAsSanitizedContent(
      @Nullable ContentKind contentKind, JsExpr jsExpr) {
    if (contentKind == null) {
      return jsExpr;
    } else {
      return wrapWithFunction(
          NodeContentKinds.toJsSanitizedContentOrdainer(
              SanitizedContentKind.valueOf(contentKind.name())),
          jsExpr);
    }
  }
}
