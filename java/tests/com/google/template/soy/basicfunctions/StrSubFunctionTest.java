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

import static com.google.template.soy.data.UnsafeSanitizedContentOrdainer.ordainAsSafe;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.restricted.JsExpr;

import junit.framework.TestCase;


/**
 * Unit tests for {@link StrSubFunction}.
 *
 */
public class StrSubFunctionTest extends TestCase {


  public void testComputeForJava_noEndIndex() {
    SoyValue arg0 = StringData.forValue("foobarfoo");
    SoyValue arg1 = IntegerData.forValue(2);

    StrSubFunction f = new StrSubFunction();
    assertEquals(StringData.forValue("obarfoo"), f.computeForJava(ImmutableList.of(arg0, arg1)));
  }


  public void testComputeForJava_noEndIndex_SanitizedContent() {
    SoyValue arg0 = ordainAsSafe("foobarfoo", ContentKind.TEXT);
    SoyValue arg1 = IntegerData.forValue(2);

    StrSubFunction f = new StrSubFunction();
    assertEquals(StringData.forValue("obarfoo"), f.computeForJava(ImmutableList.of(arg0, arg1)));
  }


  public void testComputeForJava_endIndex() {
    SoyValue arg0 = StringData.forValue("foobarfoo");
    SoyValue arg1 = IntegerData.forValue(2);
    SoyValue arg2 = IntegerData.forValue(7);

    StrSubFunction f = new StrSubFunction();
    assertEquals(StringData.forValue("obarf"),
        f.computeForJava(ImmutableList.of(arg0, arg1, arg2)));
  }


  public void testComputeForJava_endIndex_SanitizedContent() {
    SoyValue arg0 = ordainAsSafe("foobarfoo", ContentKind.TEXT);
    SoyValue arg1 = IntegerData.forValue(2);
    SoyValue arg2 = IntegerData.forValue(7);

    StrSubFunction f = new StrSubFunction();
    assertEquals(StringData.forValue("obarf"),
        f.computeForJava(ImmutableList.of(arg0, arg1, arg2)));
  }


  public void testComputeForJsSrc_noEndIndex() {
    StrSubFunction f = new StrSubFunction();
    JsExpr arg0 = new JsExpr("'foo' + 'bar'", Operator.PLUS.getPrecedence());
    JsExpr arg1 = new JsExpr("3", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("('' + ('foo' + 'bar')).substring(3)", Integer.MAX_VALUE),
        f.computeForJsSrc(ImmutableList.of(arg0, arg1)));
  }


  public void testComputeForJsSrc_endIndex() {
    StrSubFunction f = new StrSubFunction();
    JsExpr arg0 = new JsExpr("'foo' + 'bar'", Operator.PLUS.getPrecedence());
    JsExpr arg1 = new JsExpr("3", Integer.MAX_VALUE);
    JsExpr arg2 = new JsExpr("5", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("('' + ('foo' + 'bar')).substring(3,5)", Integer.MAX_VALUE),
        f.computeForJsSrc(ImmutableList.of(arg0, arg1, arg2)));
  }

}
