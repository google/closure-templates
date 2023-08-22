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

package com.google.template.soy.jbcsrc.restricted;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.soyNull;
import static com.google.template.soy.jbcsrc.restricted.SoyExpression.forList;
import static com.google.template.soy.jbcsrc.restricted.SoyExpression.forSoyValue;
import static com.google.template.soy.jbcsrc.restricted.SoyExpression.forString;
import static com.google.template.soy.jbcsrc.restricted.testing.ExpressionSubject.assertThatExpression;

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnknownType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link com.google.template.soy.jbcsrc.restricted.SoyExpression} */
@RunWith(JUnit4.class)
public class SoyExpressionTest {

  @Test
  public void testIntExpressions() {
    SoyExpression expr = SoyExpression.forInt(constant(12L));
    assertThat(expr.isNonJavaNullable()).isTrue();
    assertThat(expr.isNonSoyNullish()).isTrue();
    assertThat(expr.box().isNonJavaNullable()).isTrue();
    assertThat(expr.box().isNonSoyNullish()).isTrue();

    assertThatExpression(expr).evaluatesTo(12L);
    assertThatExpression(expr.box()).evaluatesTo(IntegerData.forValue(12));
    assertThatExpression(expr.box().unboxAsLong()).evaluatesTo(12L);
    assertThatExpression(expr.coerceToDouble()).evaluatesTo(12D);
    assertThatExpression(
            SoyExpression.forSoyValue(AnyType.getInstance(), expr.box()).coerceToDouble())
        .evaluatesTo(12D);

    assertThatExpression(expr.coerceToBoolean()).evaluatesTo(true);
    assertThatExpression(SoyExpression.forInt(constant(0L)).coerceToBoolean()).evaluatesTo(false);
    assertThatExpression(expr.coerceToString()).evaluatesTo("12");
  }

  @Test
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

  @Test
  public void testBooleanExpressions() {
    SoyExpression expr = SoyExpression.FALSE;
    assertThatExpression(expr).evaluatesTo(false); // sanity
    assertThatExpression(expr.box()).evaluatesTo(BooleanData.FALSE);
    assertThatExpression(expr.box().coerceToBoolean()).evaluatesTo(false);
    assertThatExpression(expr.coerceToString()).evaluatesTo("false");

    expr = SoyExpression.TRUE;
    assertThatExpression(expr).evaluatesTo(true);
    assertThatExpression(expr.box()).evaluatesTo(BooleanData.TRUE);
    assertThatExpression(expr.box().coerceToBoolean()).evaluatesTo(true);
    assertThatExpression(expr.coerceToString()).evaluatesTo("true");
  }

  @Test
  public void testNullExpression() {
    assertThatExpression(SoyExpression.SOY_NULL).evaluatesTo(NullData.INSTANCE);
    assertThatExpression(SoyExpression.SOY_NULL.box()).evaluatesTo(NullData.INSTANCE);
    assertThatExpression(SoyExpression.SOY_NULL.box().unboxAsListOrJavaNull()).evaluatesTo(null);
    assertThatExpression(SoyExpression.SOY_NULL.box().unboxAsStringOrJavaNull()).evaluatesTo(null);
    assertThatExpression(
            SoyExpression.SOY_NULL.box().unboxAsMessageOrJavaNull(BytecodeUtils.MESSAGE_TYPE))
        .evaluatesTo(null);
    assertThatExpression(
            SoyExpression.SOY_NULL.unboxAsMessageOrJavaNull(BytecodeUtils.MESSAGE_TYPE))
        .evaluatesTo(null);
    assertThatExpression(SoyExpression.SOY_NULL.coerceToBoolean()).evaluatesTo(false);
    assertThatExpression(SoyExpression.SOY_NULL.coerceToString()).evaluatesTo("null");

    assertThat(SoyExpression.SOY_NULL.isBoxed()).isTrue();
    assertThat(SoyExpression.SOY_NULL.isNonJavaNullable()).isTrue();
    assertThat(SoyExpression.SOY_NULL.isNonSoyNullish()).isFalse();
  }

  @Test
  public void testStringExpression() {
    assertThatExpression(forSoyValue(StringType.getInstance(), soyNull()).coerceToBoolean())
        .evaluatesTo(false);
    assertThatExpression(forSoyValue(StringType.getInstance(), soyNull()))
        .evaluatesTo(NullData.INSTANCE);
    assertThatExpression(forString(constant("")).coerceToBoolean()).evaluatesTo(false);
    assertThatExpression(forString(constant("")).box()).evaluatesTo(StringData.EMPTY_STRING);
    assertThatExpression(forString(constant("truthy")).coerceToBoolean()).evaluatesTo(true);
  }

  @Test
  public void testListExpression() {
    ListType list = ListType.of(IntType.getInstance());
    assertThatExpression(forSoyValue(list, soyNull()).coerceToBoolean()).evaluatesTo(false);
    assertThatExpression(forSoyValue(list, soyNull())).evaluatesTo(NullData.INSTANCE);
    assertThatExpression(
            forList(list, MethodRef.IMMUTABLE_LIST_OF.get(0).invoke()).coerceToBoolean())
        .evaluatesTo(true);
    // SoyList uses Object identity for equality so we can't really assert on the value.
    assertThatExpression(forList(list, MethodRef.IMMUTABLE_LIST_OF.get(0).invoke()).box())
        .evaluatesToInstanceOf(ListImpl.class);
  }

  // Tests for a bug where the generic boxing code would cause ASM to emit an invalid frame.
  //
  // for example, assume a nullable string is at the top of the stack.  Then SoyExpression would
  // emit the following code to box it while preserving null.
  //
  // DUP
  // IFNULL L1
  // INVOKESTATIC StringData.forValue
  // L1:
  //
  // So when execution arrives at L1 there should either be a nullreference or a StringData object
  // at the top of the stack.   Howerver L1 is also the target of a jump (the IFNULL condition), so
  // ASM will generate a stack frame at this location.
  // (see https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.4 for background
  // on stack frames).
  // So ASM tries to determine the type of the top item on the stack and to do so it compares the
  // type at the DUP instruction to the type at the INVOKESTATIC.  The former is String and the
  // latter is StringData.  The common supertype of this is Object, so ASM outputs a stack frame
  // that says the top of the stack is an Object instead of StringData.  This means if we happen to
  // invoke a StringData method next we will get a verification error.
  @Test
  public void testBoxNullable() {
    MethodRef stringDataGetValue = MethodRef.create(SoyValue.class, "stringValue");
    SoyExpression nullableString = forString(constant("hello").asJavaNullable());
    assertThatExpression(nullableString).evaluatesTo("hello");
    assertThatExpression(nullableString.box().invoke(stringDataGetValue)).evaluatesTo("hello");
  }

  @Test
  public void testBoxAsSoyValueProvider() {
    // primitives get boxed
    assertThatExpression(SoyExpression.forBool(BytecodeUtils.constant(false)).box())
        .evaluatesTo(BooleanData.FALSE);
    // null boxed types get converted to NULL_PROVIDER
    assertThatExpression(SoyExpression.forSoyValue(UnknownType.getInstance(), soyNull()).box())
        .evaluatesTo(NullData.INSTANCE);
    // null unboxed values get converted to NULL_PROVIDER
    assertThatExpression(forSoyValue(StringType.getInstance(), soyNull()).box())
        .evaluatesTo(NullData.INSTANCE);
  }

  // similar to the above, but in the unboxing codepath
  @Test
  public void testUnboxNullable() {
    SoyExpression nullableString =
        SoyExpression.forSoyValue(
            StringType.getInstance(),
            MethodRef.STRING_DATA_FOR_VALUE.invoke(constant("hello")).asJavaNullable());
    assertThatExpression(nullableString).evaluatesTo(StringData.forValue("hello"));
    assertThatExpression(nullableString.unboxAsStringOrJavaNull().invoke(MethodRef.STRING_IS_EMPTY))
        .evaluatesTo(false);
  }

  @Test
  public void testCoerceToBoolean_strings() {
    assertThatExpression(forString(constant("foo")).coerceToBoolean()).evaluatesTo(true);
    assertThatExpression(forString(constant("")).coerceToBoolean()).evaluatesTo(false);
  }

  @Test
  public void testCoerceToBoolean_nullableStrings() {
    assertThatExpression(forString(constant("foo")).asJavaNullable().coerceToBoolean())
        .evaluatesTo(true);
    assertThatExpression(forString(constant("")).asJavaNullable().coerceToBoolean())
        .evaluatesTo(false);
    assertThatExpression(forSoyValue(StringType.getInstance(), soyNull()).coerceToBoolean())
        .evaluatesTo(false);
  }

  @Test
  public void testCoerceToBoolean_boxed() {
    assertThatExpression(forString(constant("foo")).box().coerceToBoolean()).evaluatesTo(true);
    assertThatExpression(forString(constant("")).box().coerceToBoolean()).evaluatesTo(false);
  }

  @Test
  public void testCoerceToBoolean_nullableBoxed() {
    assertThatExpression(forString(constant("foo")).box().asJavaNullable().coerceToBoolean())
        .evaluatesTo(true);
    assertThatExpression(forString(constant("")).box().asJavaNullable().coerceToBoolean())
        .evaluatesTo(false);
    assertThatExpression(forSoyValue(StringType.getInstance(), soyNull()).box().coerceToBoolean())
        .evaluatesTo(false);
  }

  @Test
  public void testCoerceToBoolean_primitives_int() {
    assertThatExpression(SoyExpression.forInt(constant(1L)).coerceToBoolean()).evaluatesTo(true);
    assertThatExpression(SoyExpression.forInt(constant(0L)).coerceToBoolean()).evaluatesTo(false);
  }

  @Test
  public void testCoerceToBoolean_primitives_float() {
    assertThatExpression(SoyExpression.forFloat(constant(1D)).coerceToBoolean()).evaluatesTo(true);
    assertThatExpression(SoyExpression.forFloat(constant(0D)).coerceToBoolean()).evaluatesTo(false);
    assertThatExpression(SoyExpression.forFloat(constant(Double.NaN)).coerceToBoolean())
        .evaluatesTo(false);
  }
}
