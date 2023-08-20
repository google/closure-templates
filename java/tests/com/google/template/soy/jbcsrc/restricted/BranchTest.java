/*
 * Copyright 2023 Google Inc.
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
import static com.google.template.soy.jbcsrc.restricted.testing.ExpressionSubject.assertThatExpression;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.jbcsrc.restricted.testing.ExpressionSubject;
import com.google.template.soy.types.StringType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

@RunWith(JUnit4.class)
public final class BranchTest {

  ExpressionSubject assertThatBranch(Branch branch) {
    return ExpressionSubject.assertThatExpression(branch.asBoolean());
  }

  @Test
  public void invert() {
    assertThatBranch(Branch.ifTrue(constant(true)).negate()).hasCode("ICONST_0").evaluatesTo(false);
    assertThatBranch(Branch.ifTrue(constant(false)).negate()).hasCode("ICONST_1").evaluatesTo(true);
  }

  @Test
  public void testCompareLongs() {
    Expression one = constant(1L);
    Expression two = constant(2L);
    assertThatBranch(Branch.compare(Opcodes.IFNE, one, two)).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFLT, one, two)).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFLE, one, two)).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFGT, one, two)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFGE, one, two)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFEQ, one, two)).evaluatesTo(false);

    assertThatBranch(Branch.compare(Opcodes.IFLE, two, two)).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFGE, two, two)).evaluatesTo(true);
  }

  @Test
  public void testCompareDoubles() {
    Expression one = constant(1D);
    Expression two = constant(2D);
    Expression nan = constant(Double.NaN);

    assertThatBranch(Branch.compare(Opcodes.IFNE, one, two)).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFLT, one, two)).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFLE, one, two)).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFGT, one, two)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFGE, one, two)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFEQ, one, two)).evaluatesTo(false);

    assertThatBranch(Branch.compare(Opcodes.IFLE, two, two)).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFGE, two, two)).evaluatesTo(true);

    // There are special cases for NaN that we need to test, basically every expression involving
    // NaN should evaluate to false with the exception of NaN != * which always == true
    assertThatBranch(Branch.compare(Opcodes.IFNE, nan, two)).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFNE, two, nan)).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFNE, nan, nan)).evaluatesTo(true);

    assertThatBranch(Branch.compare(Opcodes.IFEQ, nan, two)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFEQ, nan, two).negate()).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFEQ, two, nan)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFEQ, two, nan).negate()).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFEQ, nan, nan)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFEQ, nan, nan).negate()).evaluatesTo(true);

    assertThatBranch(Branch.compare(Opcodes.IFLE, nan, two)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFLE, nan, two).negate()).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFLE, two, nan)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFLE, two, nan).negate()).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFLT, nan, two)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFLT, nan, two).negate()).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFLT, two, nan)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFLT, two, nan).negate()).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFGE, nan, two)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFGE, nan, two).negate()).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFGE, two, nan)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFGE, two, nan).negate()).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFGT, nan, two)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFGT, nan, two).negate()).evaluatesTo(true);
    assertThatBranch(Branch.compare(Opcodes.IFGT, two, nan)).evaluatesTo(false);
    assertThatBranch(Branch.compare(Opcodes.IFGT, two, nan).negate()).evaluatesTo(true);
  }

  @Test
  public void testOr() {
    assertThatBranch(Branch.or(Branch.never())).evaluatesTo(false);
    assertThatBranch(Branch.or(Branch.always())).evaluatesTo(true);

    assertThatBranch(Branch.or(Branch.never(), Branch.never())).evaluatesTo(false);
    assertThatBranch(Branch.or(Branch.never(), Branch.always())).evaluatesTo(true);
    assertThatBranch(Branch.or(Branch.always(), Branch.never())).evaluatesTo(true);
    assertThatBranch(Branch.or(Branch.always(), Branch.always())).evaluatesTo(true);
  }

  @Test
  public void testAnd() {
    assertThatBranch(Branch.and(Branch.never())).evaluatesTo(false);
    assertThatBranch(Branch.and(Branch.always())).evaluatesTo(true);

    assertThatBranch(Branch.and(Branch.never(), Branch.never())).evaluatesTo(false);
    assertThatBranch(Branch.and(Branch.never(), Branch.always())).evaluatesTo(false);
    assertThatBranch(Branch.and(Branch.always(), Branch.never())).evaluatesTo(false);
    assertThatBranch(Branch.and(Branch.always(), Branch.always())).evaluatesTo(true);
  }

  @Test
  public void testAndOrCompatibleWithJava() {
    ImmutableList<Boolean> bools = ImmutableList.of(true, false);
    for (boolean a : bools) {
      for (boolean b : bools) {
        for (boolean c : bools) {
          for (boolean d : bools) {
            Branch aBranch = Branch.ifTrue(constant(a));
            Branch bBranch = Branch.ifTrue(constant(b));
            Branch cBranch = Branch.ifTrue(constant(c));
            Branch dBranch = Branch.ifTrue(constant(d));
            assertThatBranch(Branch.or(aBranch, bBranch, cBranch, dBranch))
                .evaluatesTo(a || b || c || d);
            assertThatBranch(Branch.and(Branch.or(aBranch, bBranch), Branch.or(cBranch, dBranch)))
                .evaluatesTo((a || b) && (c || d));
            assertThatBranch(Branch.and(aBranch, bBranch, cBranch, dBranch))
                .evaluatesTo(a && b && c && d);
            assertThatBranch(Branch.or(Branch.and(aBranch, bBranch), Branch.and(cBranch, dBranch)))
                .evaluatesTo((a && b) || (c && d));
          }
        }
      }
    }
  }

  // Use an expression that only ever throws for branches that are supposed to be skipped.
  @Test
  public void testShortCircuitingLogicalOperators_shortCircuits() {
    assertThatBranch(
            Branch.or(Branch.always(), Branch.ifTrue(throwingExpression(Type.BOOLEAN_TYPE))))
        .evaluatesTo(true);
    assertThatBranch(
            Branch.and(Branch.never(), Branch.ifTrue(throwingExpression(Type.BOOLEAN_TYPE))))
        .evaluatesTo(false);
  }

  private static Expression throwingExpression(Type type) {
    return new Expression(type) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.throwException(
            Type.getType(IllegalStateException.class), "shouldn't have called me");
      }
    };
  }

  @Test
  public void testTernary() {
    assertThatExpression(
            Branch.always().ternary(BytecodeUtils.STRING_TYPE, constant("foo"), constant("bar")))
        .evaluatesTo("foo");
    assertThatExpression(
            Branch.never().ternary(BytecodeUtils.STRING_TYPE, constant("foo"), constant("bar")))
        .evaluatesTo("bar");
  }

  @Test
  public void testTernary_doesntEvaluateUntakenBranch() {
    assertThatExpression(
            Branch.always()
                .ternary(
                    BytecodeUtils.STRING_TYPE,
                    constant("foo"),
                    throwingExpression(BytecodeUtils.STRING_TYPE)))
        .evaluatesTo("foo");
    assertThatExpression(
            Branch.never()
                .ternary(
                    BytecodeUtils.STRING_TYPE,
                    throwingExpression(BytecodeUtils.STRING_TYPE),
                    constant("bar")))
        .evaluatesTo("bar");
  }

  @Test
  public void testTernary_errors() {
    Throwable error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                Branch.never()
                    .ternary(
                        Type.DOUBLE_TYPE, /* ifTrue= */ constant(2), /* ifFalse= */ constant(3)));
    assertThat(error).hasMessageThat().isEqualTo("expected I to be assignable to D");
    error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                Branch.never()
                    .ternary(
                        Type.DOUBLE_TYPE, /* ifTrue= */ constant(2.0), /* ifFalse= */ constant(3)));
    assertThat(error).hasMessageThat().isEqualTo("expected I to be assignable to D");
  }

  @Test
  public void testIfNotNull() {
    assertThatBranch(
            Branch.ifNonSoyNullish(SoyExpression.forSoyValue(StringType.getInstance(), soyNull())))
        .evaluatesTo(false);
    assertThatBranch(
            Branch.ifNonSoyNullish(SoyExpression.forSoyValue(StringType.getInstance(), soyNull()))
                .negate())
        .evaluatesTo(true);
  }

  public static boolean returnsTrue() {
    return true;
  }

  public static boolean returnsFalse() {
    return false;
  }

  private static final MethodRef RETURNS_TRUE = MethodRef.create(BranchTest.class, "returnsTrue");
  private static final MethodRef RETURNS_FALSE = MethodRef.create(BranchTest.class, "returnsFalse");

  @Test
  public void testIfTrue() {
    assertThatBranch(Branch.ifTrue(RETURNS_TRUE.invoke())).evaluatesTo(true);
    assertThatBranch(Branch.ifTrue(RETURNS_FALSE.invoke())).evaluatesTo(false);
  }
}
