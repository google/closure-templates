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

import com.google.template.soy.pysrc.restricted.PyExprUtils;

import junit.framework.TestCase;

import java.util.ArrayList;

/**
 * Unit tests for PyFunctionBuilder.
 *
 */

public final class PyFunctionExprBuilderTest extends TestCase {
  public void testSingleNumberArgument() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.addArg(600851475143L);
    assertEquals(func.build(), "some_func(600851475143)");
  }

  public void testSingleStringArgument() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.addArg("10");
    assertEquals("some_func('10')", func.build());
  }

  public void testSingleArrayArgument() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    ArrayList<Object> list = new ArrayList<>();
    list.add("foo");
    list.add("bar");
    list.add(42);

    func.addArg(PyExprUtils.convertListToPyListExpr(list));
    assertEquals("some_func(['foo', 'bar', 42, ])", func.build());
  }

  public void testSingleTupleArgument() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    ArrayList<Object> list = new ArrayList<>();
    list.add("foo");
    list.add("bar");
    list.add(42);

    func.addArg(PyExprUtils.convertListToPyTupleExpr(list));
    assertEquals("some_func(('foo', 'bar', 42, ))", func.build());
  }

  public void testSinglePyFunctionBuilderArgument() {
    PyFunctionExprBuilder nestedFunc = new PyFunctionExprBuilder("nested_func");
    nestedFunc.addArg(10);

    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.addArg(nestedFunc.asPyExpr());

    assertEquals(func.build(), "some_func(nested_func(10))");
  }

  public void testSingleKeyedStringArgument() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.addKwarg("foo", "bar");
    assertEquals(func.build(), "some_func(foo='bar')");
  }

  public void testSingleKeyedIntArgument() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.addKwarg("foo", 10);
    assertEquals(func.build(), "some_func(foo=10)");
  }

  public void testSingleKeyedPyFunctionBuilderArgument() {
    PyFunctionExprBuilder nestedFunc = new PyFunctionExprBuilder("nested_func");
    nestedFunc.addArg(10);

    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.addKwarg("foo", nestedFunc.asPyExpr());

    assertEquals(func.build(), "some_func(foo=nested_func(10))");
  }

  public void testMultipleArguments() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.addArg(42);
    func.addArg("foobar");
    func.addKwarg("foo1", 10);
    func.addKwarg("foo", "bar");
    assertEquals(func.build(), "some_func(42, 'foobar', foo1=10, foo='bar')");
  }
}
