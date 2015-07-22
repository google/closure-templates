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

package com.google.template.soy.jbcsrc;

import static com.google.template.soy.jbcsrc.BytecodeUtils.STRING_TYPE;
import static com.google.template.soy.jbcsrc.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.BytecodeUtils.constantNull;
import static com.google.template.soy.jbcsrc.ExpressionTester.assertThatExpression;
import static com.google.template.soy.jbcsrc.SoyExpression.forList;
import static com.google.template.soy.jbcsrc.SoyExpression.forSanitizedString;
import static com.google.template.soy.jbcsrc.SoyExpression.forString;

import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.primitive.AnyType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;

import junit.framework.TestCase;

import org.objectweb.asm.Type;

import java.util.List;

/**
 * Tests for {@link SoyExpression}
 */
public class SoyExpressionTest extends TestCase {

  public void testIntExpressions() {
    SoyExpression expr = SoyExpression.forInt(constant(12L));
    assertThatExpression(expr).evaluatesTo(12L);
    assertThatExpression(expr.box()).evaluatesTo(IntegerData.forValue(12));
    assertThatExpression(expr.box().unboxAs(long.class)).evaluatesTo(12L);
    assertThatExpression(expr.coerceToDouble()).evaluatesTo(12D);
    assertThatExpression(
            SoyExpression.forSoyValue(AnyType.getInstance(), expr.box()).coerceToDouble())
        .evaluatesTo(12D);

    assertThatExpression(expr.coerceToBoolean()).evaluatesTo(true);
    assertThatExpression(SoyExpression.forInt(constant(0L)).coerceToBoolean()).evaluatesTo(false);
    assertThatExpression(expr.coerceToString()).evaluatesTo("12");
  }

  public void testFloatExpressions() {
    SoyExpression expr = SoyExpression.forFloat(constant(12.34D));
    assertThatExpression(expr).evaluatesTo(12.34D);
    assertThatExpression(expr.box()).evaluatesTo(FloatData.forValue(12.34D));
    assertThatExpression(expr.box().coerceToDouble()).evaluatesTo(12.34D);
    assertThatExpression(
            SoyExpression.forSoyValue(FloatType.getInstance(), expr.box()).coerceToString())
        .evaluatesTo("12.34");

    assertThatExpression(expr.coerceToBoolean()).evaluatesTo(true);
    assertThatExpression(SoyExpression.forFloat(constant(0D)).coerceToBoolean()).evaluatesTo(false);

    assertThatExpression(expr.coerceToString()).evaluatesTo("12.34");
  }

  public void testBooleanExpressions() {
    SoyExpression expr = SoyExpression.FALSE;
    assertThatExpression(expr).evaluatesTo(false);  // sanity
    assertThatExpression(expr.box()).evaluatesTo(BooleanData.FALSE);
    assertThatExpression(expr.box().coerceToBoolean()).evaluatesTo(false);
    assertThatExpression(expr.coerceToString()).evaluatesTo("false");

    expr = SoyExpression.TRUE;
    assertThatExpression(expr).evaluatesTo(true);
    assertThatExpression(expr.box()).evaluatesTo(BooleanData.TRUE);
    assertThatExpression(expr.box().coerceToBoolean()).evaluatesTo(true);
    assertThatExpression(expr.coerceToString()).evaluatesTo("true");
  }

  public void testNullExpression() {
    assertThatExpression(SoyExpression.NULL).evaluatesTo(null);
    assertThatExpression(SoyExpression.NULL.box()).evaluatesTo(null);
    assertThatExpression(SoyExpression.NULL.box().unboxAs(Object.class)).evaluatesTo(null);
    assertThatExpression(SoyExpression.NULL.coerceToBoolean()).evaluatesTo(false);
    assertThatExpression(SoyExpression.NULL.coerceToString()).evaluatesTo("null");
  }

  public void testSanitizedExpressions() {
    assertThatExpression(forSanitizedString(constant("foo"), ContentKind.ATTRIBUTES).box())
        .evaluatesTo(UnsafeSanitizedContentOrdainer.ordainAsSafe("foo", ContentKind.ATTRIBUTES));
    assertThatExpression(
            forSanitizedString(constant("foo"), ContentKind.ATTRIBUTES).coerceToBoolean())
        .evaluatesTo(true);
  }

  public void testStringExpression() {
    assertThatExpression(forString(constantNull(STRING_TYPE)).coerceToBoolean()).evaluatesTo(false);
    assertThatExpression(forString(constantNull(STRING_TYPE)).box()).evaluatesTo(null);
    assertThatExpression(forString(constant("")).coerceToBoolean()).evaluatesTo(false);
    assertThatExpression(forString(constant("")).box()).evaluatesTo(StringData.EMPTY_STRING);
    assertThatExpression(forString(constant("truthy")).coerceToBoolean()).evaluatesTo(true);
  }

  public void testListExpression() {
    ListType list = ListType.of(IntType.getInstance());
    assertThatExpression(forList(list, constantNull(Type.getType(List.class))).coerceToBoolean())
        .evaluatesTo(false);
    assertThatExpression(forList(list, constantNull(Type.getType(List.class))).box())
        .evaluatesTo(null);
    assertThatExpression(forList(list, MethodRef.IMMUTABLE_LIST_OF.invoke()).coerceToBoolean())
        .evaluatesTo(true);
    // SoyList uses Object identity for equality so we can't really assert on the value.
    assertThatExpression(forList(list, MethodRef.IMMUTABLE_LIST_OF.invoke()).box())
        .evaluatesToInstanceOf(ListImpl.class);
  }
}
