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

import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.STRING_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constantNull;
import static com.google.template.soy.jbcsrc.restricted.SoyExpression.forList;
import static com.google.template.soy.jbcsrc.restricted.SoyExpression.forString;
import static com.google.template.soy.jbcsrc.restricted.testing.ExpressionSubject.assertThatExpression;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.SoyString;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.runtime.JbcSrcRuntime;
import com.google.template.soy.testing.Proto3Message;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnknownType;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.objectweb.asm.Type;

/** Tests for {@link com.google.template.soy.jbcsrc.restricted.SoyExpression} */
@RunWith(JUnit4.class)
public class SoyExpressionTest {

  @Test
  public void testIntExpressions() {
    SoyExpression expr = SoyExpression.forInt(constant(12L));
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
    assertThatExpression(SoyExpression.NULL).evaluatesTo(null);
    assertThatExpression(SoyExpression.NULL.box()).evaluatesTo(null);
    assertThatExpression(SoyExpression.NULL.box().unboxAsList()).evaluatesTo(null);
    assertThatExpression(SoyExpression.NULL.box().unboxAsString()).evaluatesTo(null);
    assertThatExpression(SoyExpression.NULL.box().unboxAsMessage()).evaluatesTo(null);
    assertThatExpression(SoyExpression.NULL.unboxAsMessage()).evaluatesTo(null);
    assertThatExpression(SoyExpression.NULL.coerceToBoolean()).evaluatesTo(false);
    assertThatExpression(SoyExpression.NULL.coerceToString()).evaluatesTo("null");
  }


  @Test
  public void testStringExpression() {
    assertThatExpression(forString(constantNull(STRING_TYPE)).coerceToBoolean()).evaluatesTo(false);
    assertThatExpression(forString(constantNull(STRING_TYPE)).box()).evaluatesTo(null);
    assertThatExpression(forString(constant("")).coerceToBoolean()).evaluatesTo(false);
    assertThatExpression(forString(constant("")).box()).evaluatesTo(StringData.EMPTY_STRING);
    assertThatExpression(forString(constant("truthy")).coerceToBoolean()).evaluatesTo(true);
  }

  @Test
  public void testListExpression() {
    ListType list = ListType.of(IntType.getInstance());
    assertThatExpression(forList(list, constantNull(Type.getType(List.class))).coerceToBoolean())
        .evaluatesTo(false);
    assertThatExpression(forList(list, constantNull(Type.getType(List.class))).box())
        .evaluatesTo(null);
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
    MethodRef stringDataGetValue = MethodRef.create(SoyString.class, "stringValue");
    SoyExpression nullableString = SoyExpression.forString(constant("hello").asNullable());
    assertThatExpression(nullableString).evaluatesTo("hello");
    assertThatExpression(nullableString.box().invoke(stringDataGetValue)).evaluatesTo("hello");
  }

  @Test
  public void testBoxAsSoyValueProvider() {
    // primitives get boxed
    assertThatExpression(
            SoyExpression.forBool(BytecodeUtils.constant(false)).boxAsSoyValueProvider())
        .evaluatesTo(BooleanData.FALSE);
    // null boxed types get converted to NULL_PROVIDER
    assertThatExpression(
            SoyExpression.forSoyValue(
                    UnknownType.getInstance(), constantNull(BytecodeUtils.SOY_VALUE_TYPE))
                .boxAsSoyValueProvider())
        .evaluatesTo(JbcSrcRuntime.NULL_PROVIDER);
    // null unboxed values get converted to NULL_PROVIDER
    assertThatExpression(SoyExpression.forString(constantNull(STRING_TYPE)).boxAsSoyValueProvider())
        .evaluatesTo(JbcSrcRuntime.NULL_PROVIDER);
  }

  // similar to the above, but in the unboxing codepath
  @Test
  public void testUnboxNullable() {
    SoyExpression nullableString =
        SoyExpression.forSoyValue(
            StringType.getInstance(),
            MethodRef.STRING_DATA_FOR_VALUE.invoke(constant("hello")).asNullable());
    assertThatExpression(nullableString).evaluatesTo(StringData.forValue("hello"));
    assertThatExpression(nullableString.unboxAsString().invoke(MethodRef.STRING_IS_EMPTY))
        .evaluatesTo(false);
  }

  @Test
  public void testUnboxNullMarkedNonNullable() {
    SoyExpression secretNullString =
        SoyExpression.forSoyValue(
            StringType.getInstance(),
            new Expression(BytecodeUtils.SOY_STRING_TYPE, Feature.NON_NULLABLE) {
              @Override
              protected void doGen(CodeBuilder cb) {
                cb.pushNull();
              }
            });
    assertThatExpression(secretNullString.unboxAsString())
        .throwsException(NullPointerException.class);

    SoyExpression secretNullList =
        SoyExpression.forSoyValue(
            ListType.ANY_LIST,
            new Expression(BytecodeUtils.SOY_LIST_TYPE, Feature.NON_NULLABLE) {
              @Override
              protected void doGen(CodeBuilder cb) {
                cb.pushNull();
              }
            });
    assertThatExpression(secretNullList.unboxAsList()).throwsException(NullPointerException.class);

    SoyExpression secretNullProto =
        SoyExpression.forSoyValue(
            new SoyProtoType(
                new SoyTypeRegistry(), Proto3Message.getDescriptor(), ImmutableSet.of()),
            new Expression(BytecodeUtils.SOY_PROTO_VALUE_TYPE, Feature.NON_NULLABLE) {
              @Override
              protected void doGen(CodeBuilder cb) {
                cb.pushNull();
              }
            });
    assertThatExpression(secretNullProto.unboxAsMessage())
        .throwsException(NullPointerException.class);
  }
}
