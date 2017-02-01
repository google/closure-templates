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
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.restricted.JsExpr;
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
  public void testComputeForJava_noEndIndex() {
    StrSubFunction strSub = new StrSubFunction();
    SoyValue arg0 = StringData.forValue("foobarfoo");
    SoyValue arg1 = IntegerData.forValue(2);
    assertEquals(
        StringData.forValue("obarfoo"), strSub.computeForJava(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForJava_noEndIndex_SanitizedContent() {
    StrSubFunction strSub = new StrSubFunction();
    SoyValue arg0 = ordainAsSafe("foobarfoo", ContentKind.TEXT);
    SoyValue arg1 = IntegerData.forValue(2);
    assertEquals(
        StringData.forValue("obarfoo"), strSub.computeForJava(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForJava_endIndex() {
    StrSubFunction strSub = new StrSubFunction();
    SoyValue arg0 = StringData.forValue("foobarfoo");
    SoyValue arg1 = IntegerData.forValue(2);
    SoyValue arg2 = IntegerData.forValue(7);
    assertEquals(
        StringData.forValue("obarf"), strSub.computeForJava(ImmutableList.of(arg0, arg1, arg2)));
  }

  @Test
  public void testComputeForJava_endIndex_SanitizedContent() {
    StrSubFunction strSub = new StrSubFunction();
    SoyValue arg0 = ordainAsSafe("foobarfoo", ContentKind.TEXT);
    SoyValue arg1 = IntegerData.forValue(2);
    SoyValue arg2 = IntegerData.forValue(7);
    assertEquals(
        StringData.forValue("obarf"), strSub.computeForJava(ImmutableList.of(arg0, arg1, arg2)));
  }

  @Test
  public void testComputeForJsSrc_noEndIndex() {
    StrSubFunction strSub = new StrSubFunction();
    JsExpr arg0 = new JsExpr("'foo' + 'bar'", Operator.PLUS.getPrecedence());
    JsExpr arg1 = new JsExpr("3", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("('' + ('foo' + 'bar')).substring(3)", Integer.MAX_VALUE),
        strSub.computeForJsSrc(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForJsSrc_endIndex() {
    StrSubFunction strSub = new StrSubFunction();
    JsExpr arg0 = new JsExpr("'foo' + 'bar'", Operator.PLUS.getPrecedence());
    JsExpr arg1 = new JsExpr("3", Integer.MAX_VALUE);
    JsExpr arg2 = new JsExpr("5", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("('' + ('foo' + 'bar')).substring(3,5)", Integer.MAX_VALUE),
        strSub.computeForJsSrc(ImmutableList.of(arg0, arg1, arg2)));
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
