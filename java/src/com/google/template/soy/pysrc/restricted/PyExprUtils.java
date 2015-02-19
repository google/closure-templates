/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.pysrc.restricted;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.targetexpr.ExprUtils;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Common utilities for dealing with Python expressions.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 */
public final class PyExprUtils {

  /** Expression constant for empty string. */
  private static final PyExpr EMPTY_STRING = new PyStringExpr("''");

  /**
   * Map used to provide operator precedences in Python.
   *
   * @see <a href="https://docs.python.org/2/reference/expressions.html#operator-precedence">
   *    Python operator precedence.</a>
   */
  private static final ImmutableMap<Operator, Integer> PYTHON_PRECEDENCES =
      new ImmutableMap.Builder<Operator, Integer>()
          .put(Operator.NEGATIVE, 8)
          .put(Operator.TIMES, 7)
          .put(Operator.DIVIDE_BY, 7)
          .put(Operator.MOD, 7)
          .put(Operator.PLUS, 6)
          .put(Operator.MINUS, 6)
          .put(Operator.LESS_THAN, 5)
          .put(Operator.GREATER_THAN, 5)
          .put(Operator.LESS_THAN_OR_EQUAL, 5)
          .put(Operator.GREATER_THAN_OR_EQUAL, 5)
          .put(Operator.EQUAL, 5)
          .put(Operator.NOT_EQUAL, 5)
          .put(Operator.NOT, 4)
          .put(Operator.AND, 3)
          .put(Operator.OR, 2)
          .put(Operator.NULL_COALESCING, 1)
          .put(Operator.CONDITIONAL, 1)
          .build();


  private PyExprUtils() {}

  /**
   * Builds one Python expression that computes the concatenation of the given Python expressions.
   *
   * <p>Python doesn't allow arbitrary concatentation between types, so to ensure type safety and
   * consistent behavior, coerce all expressions to Strings before joining them. Python's array
   * joining mechanism is used in place of traditional concatenation to improve performance.
   *
   * @param pyExprs The Python expressions to concatenate.
   * @return One Python expression that computes the concatenation of the given Python expressions.
   */
  public static PyExpr concatPyExprs(List<? extends PyExpr> pyExprs) {

    if (pyExprs.isEmpty()) {
      return EMPTY_STRING;
    }

    if (pyExprs.size() == 1) {
      // If there's only one element, simply return the expression as a String.
      return pyExprs.get(0).toPyString();
    }

    StringBuilder resultSb = new StringBuilder();

    // Use Python's list joining mechanism to speed up concatenation.
    resultSb.append("[");

    boolean isFirst = true;
    for (PyExpr pyExpr : pyExprs) {

      if (isFirst) {
        isFirst = false;
      } else {
        resultSb.append(',');
      }

      resultSb.append(pyExpr.toPyString().getText());
    }

    resultSb.append("]");
    return new PyListExpr(resultSb.toString(), Integer.MAX_VALUE);
  }

  /**
   * Generate a Python not null (None) check expression for a given PyExpr.
   * @param pyExpr The input expression to test.
   * @return A PyExpr containing the null check.
   */
  public static PyExpr genPyNotNullCheck(PyExpr pyExpr) {
    List<PyExpr> exprs = ImmutableList.of(pyExpr, new PyExpr("None", Integer.MAX_VALUE));

    // Note: is/is not is Python's identity comparison. It's used for None checks for performance.
    String conditionalExpr = ExprUtils.genExprWithNewToken(Operator.NOT_EQUAL, exprs, "is not");
    return new PyExpr(conditionalExpr, PyExprUtils.pyPrecedenceForOperator(Operator.NOT_EQUAL));
  }

  /**
   * Wraps an expression with parenthesis if it's not above the minimum safe precedence.
   *
   * <p>NOTE: For the sake of brevity, this implementation loses typing information in the
   * expressions.
   *
   * @param expr The expression to wrap.
   * @param minSafePrecedence The minimum safe precedence (not inclusive).
   * @return The PyExpr potentially wrapped in parenthesis.
   */
  public static PyExpr maybeProtect(PyExpr expr, int minSafePrecedence) {
    if (expr.getPrecedence() > minSafePrecedence) {
      return expr;
    } else {
      return new PyExpr("(" + expr.getText() + ")", Integer.MAX_VALUE);
    }
  }

  /**
   * Wraps an expression with the proper SanitizedContent constructor if contentKind is non-null.
   *
   * @param contentKind The kind of sanitized content.
   * @param pyExpr The expression to wrap.
   */
  public static PyExpr maybeWrapAsSanitizedContent(
      @Nullable ContentKind contentKind, PyExpr pyExpr) {
    if (contentKind == null) {
      return pyExpr;
    } else {
      String sanitizer = NodeContentKinds.toPySanitizedContentOrdainer(contentKind);
      return new PyExpr(sanitizer + "(" + pyExpr.getText() + ")", Integer.MAX_VALUE);
    }
  }

  /**
   * Provide the Python operator precedence for a given operator.
   *
   * @param op The operator.
   * @return THe python precedence as an integer.
   */
  public static int pyPrecedenceForOperator(Operator op) {
    return PYTHON_PRECEDENCES.get(op);
  }

  /**
   * Convert a java List to valid PyExpr as array.
   *
   * @param list List of Objects to be converted to PyExpr, it must be Number, PyExpr or String.
   */
  public static PyExpr convertListToPyListExpr(List<Object> list) {
    return convertListToPyExpr(list, true);
  }

  /**
   * Convert a java List to valid PyExpr as tuple.
   *
   * @param list List of Objects to be converted to PyExpr, it must be Number, PyExpr or String.
   */
  public static PyExpr convertListToPyTupleExpr(List<Object> list) {
    return convertListToPyExpr(list, false);
  }

  private static PyExpr convertListToPyExpr(List<Object> list, boolean asArray) {
    StringBuilder sb = new StringBuilder();
    String leftDelimiter = "[";
    String rightDelimiter = "]";

    if (!asArray) {
      leftDelimiter = "(";
      rightDelimiter = ")";
    }

    sb.append(leftDelimiter);
    for (Object elem : list) {
      if (!(elem instanceof Number || elem instanceof String || elem instanceof PyExpr)) {
        throw new UnsupportedOperationException("Only Number, String and PyExpr is allowed");
      }
      if (elem instanceof Number) {
        sb.append(elem);
      }
      if (elem instanceof PyExpr) {
        sb.append(((PyExpr) elem).getText());
      }
      if (elem instanceof String) {
        sb.append("'" + elem + "'");
      }
      sb.append(", ");
    }

    sb.append(rightDelimiter);

    return new PyListExpr(sb.toString(), Integer.MAX_VALUE);
  }
}
