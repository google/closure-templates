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
 * Unit tests for {@link StrLenFunction}.
 *
 */
@RunWith(JUnit4.class)
public class StrLenFunctionTest {

  @Test
  public void testComputeForJava_containsString() {
    StrLenFunction strLen = new StrLenFunction();
    SoyValue arg0 = StringData.forValue("foobarfoo");
    assertEquals(IntegerData.forValue(9), strLen.computeForJava(ImmutableList.of(arg0)));
  }

  @Test
  public void testComputeForJava_containsSanitizedContent() {
    StrLenFunction strLen = new StrLenFunction();
    SoyValue arg0 = ordainAsSafe("foobarfoo", ContentKind.TEXT);
    assertEquals(IntegerData.forValue(9), strLen.computeForJava(ImmutableList.of(arg0)));
  }

  @Test
  public void testComputeForJsSrc() {
    StrLenFunction strLen = new StrLenFunction();
    JsExpr arg0 = new JsExpr("'foo' + 'bar'", Operator.PLUS.getPrecedence());
    assertEquals(
        new JsExpr("('' + ('foo' + 'bar')).length", Integer.MAX_VALUE),
        strLen.computeForJsSrc(ImmutableList.of(arg0)));
  }

  @Test
  public void testComputeForPySrc() {
    StrLenFunction strLen = new StrLenFunction();

    PyExpr string = new PyStringExpr("'data'");
    assertThat(strLen.computeForPySrc(ImmutableList.of(string)))
        .isEqualTo(new PyExpr("len('data')", Integer.MAX_VALUE));

    PyExpr data = new PyExpr("data", Integer.MAX_VALUE);
    assertThat(strLen.computeForPySrc(ImmutableList.of(data)))
        .isEqualTo(new PyExpr("len(str(data))", Integer.MAX_VALUE));
  }
}
