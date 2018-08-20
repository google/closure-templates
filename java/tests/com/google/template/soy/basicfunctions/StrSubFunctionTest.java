/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.basicfunctions;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.data.UnsafeSanitizedContentOrdainer.ordainAsSafe;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link StrSubFunction}.
 *
 */
@RunWith(JUnit4.class)
public class StrSubFunctionTest {

  @Test
  public void testComputeForJavaSource_noEndIndex() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(new StrSubFunction());
    assertThat(tester.callFunction("foobarfoo", 2)).isEqualTo("obarfoo");
  }

  @Test
  public void testComputeForJavaSource_noEndIndex_SanitizedContent() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(new StrSubFunction());
    assertThat(tester.callFunction(ordainAsSafe("foobarfoo", ContentKind.TEXT), 2))
        .isEqualTo("obarfoo");
  }

  @Test
  public void testComputeForJavaSource_endIndex() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(new StrSubFunction());
    assertThat(tester.callFunction("foobarfoo", 2, 7)).isEqualTo("obarf");
  }

  @Test
  public void testComputeForJavaSource_endIndex_SanitizedContent() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(new StrSubFunction());
    assertThat(tester.callFunction(ordainAsSafe("foobarfoo", ContentKind.TEXT), 2, 7))
        .isEqualTo("obarf");
  }

  @Test
  public void testComputeForJsSrc_noEndIndex() {
    StrSubFunction strSub = new StrSubFunction();
    JsExpr arg0 = new JsExpr("'foo' + 'bar'", Operator.PLUS.getPrecedence());
    JsExpr arg1 = new JsExpr("3", Integer.MAX_VALUE);
    assertThat(strSub.computeForJsSrc(ImmutableList.of(arg0, arg1)))
        .isEqualTo(new JsExpr("('' + ('foo' + 'bar')).substring(3)", Integer.MAX_VALUE));
  }

  @Test
  public void testComputeForJsSrc_endIndex() {
    StrSubFunction strSub = new StrSubFunction();
    JsExpr arg0 = new JsExpr("'foo' + 'bar'", Operator.PLUS.getPrecedence());
    JsExpr arg1 = new JsExpr("3", Integer.MAX_VALUE);
    JsExpr arg2 = new JsExpr("5", Integer.MAX_VALUE);
    assertThat(strSub.computeForJsSrc(ImmutableList.of(arg0, arg1, arg2)))
        .isEqualTo(new JsExpr("('' + ('foo' + 'bar')).substring(3,5)", Integer.MAX_VALUE));
  }

  @Test
  public void testComputeForPySrc_noEndIndex() {
    StrSubFunction strSub = new StrSubFunction();
    PyExpr base = new PyStringExpr("'foobar'", Integer.MAX_VALUE);
    PyExpr start = new PyExpr("3", Integer.MAX_VALUE);
    assertThat(strSub.computeForPySrc(ImmutableList.of(base, start)))
        .isEqualTo(new PyStringExpr("('foobar')[3:]", Integer.MAX_VALUE));
  }

  @Test
  public void testComputeForPySrc_endIndex() {
    StrSubFunction strSub = new StrSubFunction();
    PyExpr base = new PyStringExpr("'foobar'", Integer.MAX_VALUE);
    PyExpr start = new PyExpr("3", Integer.MAX_VALUE);
    PyExpr end = new PyExpr("5", Integer.MAX_VALUE);
    assertThat(strSub.computeForPySrc(ImmutableList.of(base, start, end)))
        .isEqualTo(new PyStringExpr("('foobar')[3:5]", Integer.MAX_VALUE));
  }

  @Test
  public void testComputeForPySrc_nonStringInput() {
    StrSubFunction strSub = new StrSubFunction();
    PyExpr base = new PyExpr("foobar", Integer.MAX_VALUE);
    PyExpr start = new PyExpr("3", Integer.MAX_VALUE);
    assertThat(strSub.computeForPySrc(ImmutableList.of(base, start)))
        .isEqualTo(new PyStringExpr("(str(foobar))[3:]", Integer.MAX_VALUE));
  }
}
