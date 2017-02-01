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
 * Unit tests for {@link com.google.template.soy.basicfunctions.StrIndexOfFunction}.
 *
 */
@RunWith(JUnit4.class)
public class StrIndexOfFunctionTest {

  @Test
  public void testComputeForJava_containsString() {
    StrIndexOfFunction strIndexOf = new StrIndexOfFunction();
    SoyValue arg0 = StringData.forValue("foobarfoo");
    SoyValue arg1 = StringData.forValue("bar");
    assertEquals(IntegerData.forValue(3), strIndexOf.computeForJava(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForJava_containsSanitizedContent() {
    StrIndexOfFunction strIndexOf = new StrIndexOfFunction();
    SoyValue arg0 = ordainAsSafe("foobarfoo", ContentKind.TEXT);
    SoyValue arg1 = ordainAsSafe("bar", ContentKind.TEXT);
    assertEquals(IntegerData.forValue(3), strIndexOf.computeForJava(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForJava_doesNotContainString() {
    StrIndexOfFunction strIndexOf = new StrIndexOfFunction();
    SoyValue arg0 = StringData.forValue("foobarfoo");
    SoyValue arg1 = StringData.forValue("baz");
    assertEquals(IntegerData.forValue(-1), strIndexOf.computeForJava(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForJava_doesNotContainSanitizedContent() {
    StrIndexOfFunction strIndexOf = new StrIndexOfFunction();
    SoyValue arg0 = ordainAsSafe("foobarfoo", ContentKind.TEXT);
    SoyValue arg1 = ordainAsSafe("baz", ContentKind.TEXT);
    assertEquals(IntegerData.forValue(-1), strIndexOf.computeForJava(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForJsSrc_lowPrecedenceArg() {
    StrIndexOfFunction strIndexOf = new StrIndexOfFunction();
    JsExpr arg0 = new JsExpr("'foo' + 'bar'", Operator.PLUS.getPrecedence());
    JsExpr arg1 = new JsExpr("'ba' + 'r'", Operator.PLUS.getPrecedence());
    assertEquals(
        new JsExpr("('' + ('foo' + 'bar')).indexOf('' + ('ba' + 'r'))", Integer.MAX_VALUE),
        strIndexOf.computeForJsSrc(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForJsSrc_maxPrecedenceArgs() {
    StrIndexOfFunction strIndexOf = new StrIndexOfFunction();
    JsExpr arg0 = new JsExpr("'foobar'", Integer.MAX_VALUE);
    JsExpr arg1 = new JsExpr("'bar'", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("('foobar').indexOf('bar')", Integer.MAX_VALUE),
        strIndexOf.computeForJsSrc(ImmutableList.of(arg0, arg1)));
  }

  @Test
  public void testComputeForPySrc_stringInput() {
    StrIndexOfFunction strIndexOf = new StrIndexOfFunction();
    PyExpr base = new PyStringExpr("'foobar'", Integer.MAX_VALUE);
    PyExpr substring = new PyStringExpr("'bar'", Integer.MAX_VALUE);
    assertThat(strIndexOf.computeForPySrc(ImmutableList.of(base, substring)))
        .isEqualTo(new PyExpr("('foobar').find('bar')", Integer.MAX_VALUE));
  }

  @Test
  public void testComputeForPySrc_nonStringInput() {
    StrIndexOfFunction strIndexOf = new StrIndexOfFunction();
    PyExpr base = new PyExpr("foobar", Integer.MAX_VALUE);
    PyExpr substring = new PyExpr("bar", Integer.MAX_VALUE);
    assertThat(strIndexOf.computeForPySrc(ImmutableList.of(base, substring)))
        .isEqualTo(new PyExpr("(str(foobar)).find(str(bar))", Integer.MAX_VALUE));
  }
}
