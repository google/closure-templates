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

import java.util.List;

/**
 * Common utilities for dealing with Python expressions.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 */
public final class PyExprUtils {

  /** Expression constant for empty string. */
  private static final PyExpr EMPTY_STRING = new PyExpr("''", Integer.MAX_VALUE);


  private PyExprUtils() {}

  /**
   * Builds one Python expression that computes the concatenation of the given Python expressions.
   *
   * <p>Python doesn't allow arbitrary concatentation between types, so to ensure type safety and
   * consistent behavior, coerce all expressions to Strings before joinging them. Python's array
   * joining mechanism is used in place of traditional concatenation to improve performance.
   *
   * @param pyExprs The Python expressions to concatenate.
   * @return One Python expression that computes the concatenation of the given Python expressions.
   */
  public static PyExpr concatPyExprs(List<PyExpr> pyExprs) {

    if (pyExprs.isEmpty()) {
      return EMPTY_STRING;
    }

    if (pyExprs.size() == 1) {
      return new PyExpr("str(" + pyExprs.get(0).getText() + ")", Integer.MAX_VALUE);
    }

    StringBuilder resultSb = new StringBuilder();

    // Use Python's list joining mechanism to speed up concatenation.
    resultSb.append("''.join([");

    boolean isFirst = true;
    for (PyExpr pyExpr : pyExprs) {

      if (isFirst) {
        isFirst = false;
      } else {
        resultSb.append(',');
      }

      // TODO(dcphillips): Consider simple tests for string literals to avoid pointless casting.
      resultSb.append("str(").append(pyExpr.getText()).append(")");
    }

    resultSb.append("])");
    return new PyExpr(resultSb.toString(), Integer.MAX_VALUE);
  }
}
