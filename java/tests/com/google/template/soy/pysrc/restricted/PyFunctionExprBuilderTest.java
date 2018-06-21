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

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.exprtree.Operator;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for PyFunctionBuilder.
 *
 */
@RunWith(JUnit4.class)
public final class PyFunctionExprBuilderTest {
  @Test
  public void testSingleNumberArgument() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.addArg(600851475143L);
    assertThat(func.build()).isEqualTo("some_func(600851475143)");
  }

  @Test
  public void testSingleStringArgument() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.addArg("10");
    assertThat(func.build()).isEqualTo("some_func('10')");
  }

  @Test
  public void testSingleArrayArgument() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    ArrayList<Object> list = new ArrayList<>();
    list.add("foo");
    list.add("bar");
    list.add(42);

    func.addArg(PyExprUtils.convertIterableToPyListExpr(list));
    assertThat(func.build()).isEqualTo("some_func(['foo', 'bar', 42])");
  }

  @Test
  public void testSingleTupleArgument() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    ArrayList<Object> list = new ArrayList<>();
    list.add("foo");
    list.add("bar");
    list.add(42);

    func.addArg(PyExprUtils.convertIterableToPyTupleExpr(list));
    assertThat(func.build()).isEqualTo("some_func(('foo', 'bar', 42))");
  }

  @Test
  public void testSinglePyFunctionBuilderArgument() {
    PyFunctionExprBuilder nestedFunc = new PyFunctionExprBuilder("nested_func");
    nestedFunc.addArg(10);

    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.addArg(nestedFunc.asPyExpr());

    assertThat(func.build()).isEqualTo("some_func(nested_func(10))");
  }

  @Test
  public void testSingleKeyedStringArgument() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.addKwarg("foo", "bar");
    assertThat(func.build()).isEqualTo("some_func(foo='bar')");
  }

  @Test
  public void testSingleKeyedIntArgument() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.addKwarg("foo", 10);
    assertThat(func.build()).isEqualTo("some_func(foo=10)");
  }

  @Test
  public void testSingleKeyedPyFunctionBuilderArgument() {
    PyFunctionExprBuilder nestedFunc = new PyFunctionExprBuilder("nested_func");
    nestedFunc.addArg(10);

    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.addKwarg("foo", nestedFunc.asPyExpr());

    assertThat(func.build()).isEqualTo("some_func(foo=nested_func(10))");
  }

  @Test
  public void testMultipleArguments() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.addArg(42);
    func.addArg("foobar");
    func.addKwarg("foo1", 10);
    func.addKwarg("foo", "bar");
    assertThat(func.build()).isEqualTo("some_func(42, 'foobar', foo1=10, foo='bar')");
  }

  @Test
  public void testUnpackedKwargs() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.setUnpackedKwargs(new PyExpr("map", Integer.MAX_VALUE));
    assertThat(func.build()).isEqualTo("some_func(**map)");
  }

  @Test
  public void testUnpackedKwargs_lowPrecedence() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.setUnpackedKwargs(
        new PyExpr("map", PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
    assertThat(func.build()).isEqualTo("some_func(**(map))");
  }

  @Test
  public void testUnpackedKwargs_multipleArguments() {
    PyFunctionExprBuilder func = new PyFunctionExprBuilder("some_func");
    func.setUnpackedKwargs(new PyExpr("map", Integer.MAX_VALUE));
    func.addArg("foobar");
    func.addKwarg("foo", "bar");
    assertThat(func.build()).isEqualTo("some_func('foobar', foo='bar', **map)");
  }
}
