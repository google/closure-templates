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

package com.google.template.soy.pysrc.internal;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 *
 * A class for building code for a function call expression in Python.
 * It builds to a PyExpr so it could be used function call code recursively.
 *
 * <p> Sample Output:
 * {@code some_func_call(1, "str", foo='bar', foo=nested_call(42))}
 *
 *
 */
final class PyFunctionExprBuilder {
  private static final Function<Map.Entry<String, PyExpr>, String> KEYWORD_ARG_MAPPER =
      new Function<Map.Entry<String, PyExpr>, String>() {
    @Override
    public String apply(Map.Entry<String, PyExpr> entry) {
      String key = entry.getKey();
      PyExpr value = entry.getValue();
      return key + "=" + value.getText();
    }
  };

  private static final Function<PyExpr, String> LIST_ARG_MAPPER =
      new Function<PyExpr, String>() {
    @Override
    public String apply(PyExpr arg) {
      return arg.getText();
    }
  };

  private final String funcName;
  private final List<PyExpr> argList;
  private final Map<String, PyExpr> kwargMap;

  /**
   * @param funcName The name of the function.
   */
  PyFunctionExprBuilder(String funcName) {
    this.funcName = funcName;
    this.argList = new LinkedList<>();
    this.kwargMap = new HashMap<>();
  }

  PyFunctionExprBuilder addArg(PyExpr arg) {
    this.argList.add(arg);
    return this;
  }

  PyFunctionExprBuilder addArg(String str) {
    this.argList.add(new PyStringExpr("'" + str + "'"));
    return this;
  }

  PyFunctionExprBuilder addArg(int i) {
    this.argList.add(new PyExpr(String.valueOf(i), Integer.MAX_VALUE));
    return this;
  }

  PyFunctionExprBuilder addArg(double i) {
    this.argList.add(new PyExpr(String.valueOf(i), Integer.MAX_VALUE));
    return this;
  }

  PyFunctionExprBuilder addArg(long i) {
    this.argList.add(new PyExpr(String.valueOf(i), Integer.MAX_VALUE));
    return this;
  }

  String getFuncName() {
    return this.funcName;
  }

  PyFunctionExprBuilder addKwarg(String key, PyExpr argValue) {
    kwargMap.put(key, argValue);
    return this;
  }

  PyFunctionExprBuilder addKwarg(String key, String str) {
    kwargMap.put(key, new PyStringExpr("'" + str + "'"));
    return this;
  }

  PyFunctionExprBuilder addKwarg(String key, int i) {
    kwargMap.put(key, new PyExpr(String.valueOf(i), Integer.MAX_VALUE));
    return this;
  }

  PyFunctionExprBuilder addKwarg(String key, double i) {
    kwargMap.put(key, new PyExpr(String.valueOf(i), Integer.MAX_VALUE));
    return this;
  }

  PyFunctionExprBuilder addKwarg(String key, long i) {
    kwargMap.put(key, new PyExpr(String.valueOf(i), Integer.MAX_VALUE));
    return this;
  }

  /**
   *  Returns a valid Python function call as a String.
   */
  String build() {
    StringBuilder sb = new StringBuilder(funcName + "(");

    Iterable<String> args = Iterables.transform(argList, LIST_ARG_MAPPER);

    Joiner.on(", ").appendTo(sb, args);

    Iterable<String> keywordArgs = Iterables.transform(kwargMap.entrySet(), KEYWORD_ARG_MAPPER);

    // append ',' between list and keyed arguments only when necessary
    if (!argList.isEmpty() && !kwargMap.isEmpty()) {
      sb.append(", ");
    }

    Joiner.on(", ").appendTo(sb, keywordArgs);

    sb.append(")");
    return sb.toString();
  }


 /*
  * Use when the output function is unknown in Python runtime.
  *
  * @return A PyExpr represents the function code.
  */
  PyExpr asPyExpr() {
    return new PyExpr(build(), Integer.MAX_VALUE);
  }

 /*
  * Use when the output function is known to be a String in Python runtime.
  *
  * @return A PyStringExpr represents the function code.
  */
  PyStringExpr asPyStringExpr() {
    return new PyStringExpr(build());
  }
}
