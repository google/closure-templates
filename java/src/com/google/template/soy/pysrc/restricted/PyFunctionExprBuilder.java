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


import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A class for building code for a function call expression in Python. It builds to a PyExpr so it
 * could be used function call code recursively.
 *
 * <p>Sample Output: {@code some_func_call(1, "str", foo='bar', foo=nested_call(42))}
 *
 */
public final class PyFunctionExprBuilder {

  private final String funcName;
  private final Deque<PyExpr> argList;
  private final Map<String, PyExpr> kwargMap;
  private String unpackedKwargs = null;

  /** @param funcName The name of the function. */
  public PyFunctionExprBuilder(String funcName) {
    this.funcName = funcName;
    this.argList = new ArrayDeque<>();
    this.kwargMap = new LinkedHashMap<>();
  }

  public PyFunctionExprBuilder addArg(PyExpr arg) {
    this.argList.add(arg);
    return this;
  }

  public PyFunctionExprBuilder addArgs(List<PyExpr> argList) {
    this.argList.addAll(argList);
    return this;
  }

  public PyFunctionExprBuilder addArg(String str) {
    this.argList.add(new PyStringExpr("'" + str + "'"));
    return this;
  }

  public PyFunctionExprBuilder addArg(boolean b) {
    this.argList.add(new PyExpr(b ? "True" : "False", Integer.MAX_VALUE));
    return this;
  }

  public PyFunctionExprBuilder addArg(int i) {
    this.argList.add(new PyExpr(String.valueOf(i), Integer.MAX_VALUE));
    return this;
  }

  public PyFunctionExprBuilder addArg(double i) {
    this.argList.add(new PyExpr(String.valueOf(i), Integer.MAX_VALUE));
    return this;
  }

  public PyFunctionExprBuilder addArg(long i) {
    this.argList.add(new PyExpr(String.valueOf(i), Integer.MAX_VALUE));
    return this;
  }

  public String getFuncName() {
    return this.funcName;
  }

  public PyFunctionExprBuilder addKwarg(String key, PyExpr argValue) {
    kwargMap.put(key, argValue);
    return this;
  }

  public PyFunctionExprBuilder addKwarg(String key, String str) {
    kwargMap.put(key, new PyStringExpr("'" + str + "'"));
    return this;
  }

  public PyFunctionExprBuilder addKwarg(String key, int i) {
    kwargMap.put(key, new PyExpr(String.valueOf(i), Integer.MAX_VALUE));
    return this;
  }

  public PyFunctionExprBuilder addKwarg(String key, double i) {
    kwargMap.put(key, new PyExpr(String.valueOf(i), Integer.MAX_VALUE));
    return this;
  }

  public PyFunctionExprBuilder addKwarg(String key, long i) {
    kwargMap.put(key, new PyExpr(String.valueOf(i), Integer.MAX_VALUE));
    return this;
  }

  /**
   * Unpacking keyword arguments will expand a dictionary into a series of keyword arguments.
   *
   * <p>NOTE: Keyword unpacking behavior is only guaranteed for mapping expressions. Non-mapping
   * expressions which attempt to unpack will result in Python runtime errors.
   *
   * @param mapping The mapping expression to unpack.
   * @return This PyFunctionExprBuilder instance.
   */
  public PyFunctionExprBuilder setUnpackedKwargs(PyExpr mapping) {
    if (unpackedKwargs != null) {
      throw new UnsupportedOperationException("Only one kwarg unpacking allowed per expression.");
    }
    StringBuilder expr = new StringBuilder("**");
    if (mapping.getPrecedence() < Integer.MAX_VALUE) {
      expr.append("(").append(mapping.getText()).append(")");
    } else {
      expr.append(mapping.getText());
    }
    unpackedKwargs = expr.toString();
    return this;
  }

  /** Returns a valid Python function call as a String. */
  public String build() {
    StringBuilder sb = new StringBuilder(funcName + "(");

    // Join args and kwargs into simple strings.
    String args =
        argList.stream()
            .map(PyExpr::getText)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(", "));
    String kwargs =
        kwargMap.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue().getText())
            .filter(Objects::nonNull)
            .collect(Collectors.joining(", "));

    // Strip empty strings.
    args = Strings.emptyToNull(args);
    kwargs = Strings.emptyToNull(kwargs);

    // Join all pieces together.
    Joiner.on(", ").skipNulls().appendTo(sb, args, kwargs, unpackedKwargs);

    sb.append(")");
    return sb.toString();
  }

  /**
   * Use when the output function is unknown in Python runtime.
   *
   * @return A PyExpr represents the function code.
   */
  public PyExpr asPyExpr() {
    return new PyExpr(build(), Integer.MAX_VALUE);
  }

  /**
   * Use when the output function is known to be a String in Python runtime.
   *
   * @return A PyStringExpr represents the function code.
   */
  public PyStringExpr asPyStringExpr() {
    return new PyStringExpr(build());
  }
}
