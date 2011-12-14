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

package com.google.template.soy.javasrc.restricted;

import com.google.template.soy.exprtree.Operator;

import java.util.List;


/**
 * Common utilities for dealing with Java expressions.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 */
public class JavaExprUtils {

  private JavaExprUtils() {}


  /**
   * Builds one Java expression that computes the concatenation of the given Java expressions. The
   * '+' operator is used for concatenation. Operands will be protected with an extra pair of
   * parentheses if and only if needed.
   *
   * @param javaExprs The Java expressions to concatentate.
   * @return One Java expression that computes the concatenation of the given Java expressions.
   */
  public static JavaExpr concatJavaExprs(List<JavaExpr> javaExprs) {

    if (javaExprs.size() == 0) {
      return new JavaExpr("\"\"", String.class, Integer.MAX_VALUE);
    }

    if (javaExprs.size() == 1) {
      return javaExprs.get(0);
    }

    int plusOpPrec = Operator.PLUS.getPrecedence();
    StringBuilder resultSb = new StringBuilder();

    boolean isFirst = true;
    for (JavaExpr javaExpr : javaExprs) {

      // The first operand needs protection only if it's strictly lower precedence. The non-first
      // operands need protection when they're lower or equal precedence. (This is true for all
      // left-associative operators.)
      boolean needsProtection = isFirst ? javaExpr.getPrecedence() < plusOpPrec
                                        : javaExpr.getPrecedence() <= plusOpPrec;

      if (isFirst) {
        isFirst = false;
      } else {
        resultSb.append(" + ");
      }

      if (needsProtection) {
        resultSb.append("(").append(javaExpr.getText()).append(")");
      } else {
        resultSb.append(javaExpr.getText());
      }
    }

    return new JavaExpr(resultSb.toString(), String.class, plusOpPrec);
  }

}
