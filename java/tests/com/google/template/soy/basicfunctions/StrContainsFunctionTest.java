/*
 * Copyright 2012 Google Inc.
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
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link StrContainsFunction}.
 *
 */
@RunWith(JUnit4.class)
public class StrContainsFunctionTest {

  @Test
  public void testComputeForJava_containsString() {
    StrContainsFunction strContains = new StrContainsFunction();
    SoyValue arg0 = StringData.forValue("foobarfoo");
    SoyValue arg1 = StringData.forValue("bar");
    assertEquals(BooleanData.TRUE, strContains.computeForJava(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForJava_containsSanitizedContent() {
    StrContainsFunction strContains = new StrContainsFunction();
    SoyValue arg0 = ordainAsSafe("foobarfoo", ContentKind.TEXT);
    SoyValue arg1 = ordainAsSafe("bar", ContentKind.TEXT);
    assertEquals(BooleanData.TRUE, strContains.computeForJava(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForJava_doesNotContainString() {
    StrContainsFunction strContains = new StrContainsFunction();
    SoyValue arg0 = StringData.forValue("foobarfoo");
    SoyValue arg1 = StringData.forValue("baz");
    assertEquals(BooleanData.FALSE, strContains.computeForJava(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForJava_doesNotContainSanitizedContent() {
    StrContainsFunction strContains = new StrContainsFunction();
    SoyValue arg0 = ordainAsSafe("foobarfoo", ContentKind.TEXT);
    SoyValue arg1 = ordainAsSafe("baz", ContentKind.TEXT);
    assertEquals(BooleanData.FALSE, strContains.computeForJava(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForJsSrc_lowPrecedenceArg() {
    StrContainsFunction strContains = new StrContainsFunction();
    JsExpr arg0 = new JsExpr("'foo' + 'bar'", Operator.PLUS.getPrecedence());
    JsExpr arg1 = new JsExpr("'ba' + 'r'", Operator.PLUS.getPrecedence());
    assertEquals(
        new JsExpr(
            "('' + ('foo' + 'bar')).indexOf('' + ('ba' + 'r')) != -1",
            Operator.NOT_EQUAL.getPrecedence()),
        strContains.computeForJsSrc(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForJsSrc_maxPrecedenceArgs() {
    StrContainsFunction strContains = new StrContainsFunction();
    JsExpr arg0 = new JsExpr("'foobar'", Integer.MAX_VALUE);
    JsExpr arg1 = new JsExpr("'bar'", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("('foobar').indexOf('bar') != -1", Operator.NOT_EQUAL.getPrecedence()),
        strContains.computeForJsSrc(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForPySrc_stringInput() {
    StrContainsFunction strContains = new StrContainsFunction();
    PyExpr base = new PyStringExpr("'foobar'", Integer.MAX_VALUE);
    PyExpr substring = new PyStringExpr("'bar'", Integer.MAX_VALUE);
    assertThat(strContains.computeForPySrc(ImmutableList.of(base, substring)))
        .isEqualTo(
            new PyExpr(
                "('foobar').find('bar') != -1",
                PyExprUtils.pyPrecedenceForOperator(Operator.NOT_EQUAL)));
  }

  @Test
  public void testComputeForPySrc_nonStringInput() {
    StrContainsFunction strContains = new StrContainsFunction();
    PyExpr base = new PyExpr("foobar", Integer.MAX_VALUE);
    PyExpr substring = new PyExpr("bar", Integer.MAX_VALUE);
    assertThat(strContains.computeForPySrc(ImmutableList.of(base, substring)))
        .isEqualTo(
            new PyExpr(
                "(str(foobar)).find(str(bar)) != -1",
                PyExprUtils.pyPrecedenceForOperator(Operator.NOT_EQUAL)));
  }
}
