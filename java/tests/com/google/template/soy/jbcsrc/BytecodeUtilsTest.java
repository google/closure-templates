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

import static com.google.template.soy.jbcsrc.BytecodeUtils.compare;
import static com.google.template.soy.jbcsrc.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.BytecodeUtils.logicalAnd;
import static com.google.template.soy.jbcsrc.BytecodeUtils.logicalNot;
import static com.google.template.soy.jbcsrc.BytecodeUtils.logicalOr;
import static com.google.template.soy.jbcsrc.ExpressionTester.assertThatExpression;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Tests for {@link BytecodeUtils} */
@RunWith(JUnit4.class)
public class BytecodeUtilsTest {

  @Test
  public void testConstantExpressions() {
    // there are special cases for variously sized integers, test them all.
    assertThatExpression(constant(0)).evaluatesTo(0);
    assertThatExpression(constant(1)).evaluatesTo(1);
    assertThatExpression(constant(0L)).evaluatesTo(0L);
    assertThatExpression(constant(1L)).evaluatesTo(1L);
    assertThatExpression(constant(0.0)).evaluatesTo(0.0);
    assertThatExpression(constant(1.0)).evaluatesTo(1.0);
    assertThatExpression(constant(127)).evaluatesTo(127);
    assertThatExpression(constant(255)).evaluatesTo(255);

    assertThatExpression(constant(Integer.MAX_VALUE)).evaluatesTo(Integer.MAX_VALUE);
    assertThatExpression(constant(Integer.MIN_VALUE)).evaluatesTo(Integer.MIN_VALUE);

    assertThatExpression(constant(Long.MAX_VALUE)).evaluatesTo(Long.MAX_VALUE);
    assertThatExpression(constant(Long.MIN_VALUE)).evaluatesTo(Long.MIN_VALUE);

    assertThatExpression(constant('\n')).evaluatesTo('\n');
    assertThatExpression(constant("hello world")).evaluatesTo("hello world");
  }

  @Test
  public void testLogicalNot() {
    assertThatExpression(logicalNot(constant(false))).evaluatesTo(true);
    assertThatExpression(logicalNot(constant(true))).evaluatesTo(false);
  }

  @Test
  public void testCompareLongs() {
    Expression one = constant(1L);
    Expression two = constant(2L);
    assertThatExpression(compare(Opcodes.IFNE, one, two)).evaluatesTo(true);
    assertThatExpression(compare(Opcodes.IFLT, one, two)).evaluatesTo(true);
    assertThatExpression(compare(Opcodes.IFLE, one, two)).evaluatesTo(true);
    assertThatExpression(compare(Opcodes.IFGT, one, two)).evaluatesTo(false);
    assertThatExpression(compare(Opcodes.IFGE, one, two)).evaluatesTo(false);
    assertThatExpression(compare(Opcodes.IFEQ, one, two)).evaluatesTo(false);

    assertThatExpression(compare(Opcodes.IFLE, two, two)).evaluatesTo(true);
    assertThatExpression(compare(Opcodes.IFGE, two, two)).evaluatesTo(true);
  }

  @Test
  public void testCompareDoubles() {
    Expression one = constant(1D);
    Expression two = constant(2D);
    Expression nan = constant(Double.NaN);

    assertThatExpression(compare(Opcodes.IFNE, one, two)).evaluatesTo(true);
    assertThatExpression(compare(Opcodes.IFLT, one, two)).evaluatesTo(true);
    assertThatExpression(compare(Opcodes.IFLE, one, two)).evaluatesTo(true);
    assertThatExpression(compare(Opcodes.IFGT, one, two)).evaluatesTo(false);
    assertThatExpression(compare(Opcodes.IFGE, one, two)).evaluatesTo(false);
    assertThatExpression(compare(Opcodes.IFEQ, one, two)).evaluatesTo(false);

    assertThatExpression(compare(Opcodes.IFLE, two, two)).evaluatesTo(true);
    assertThatExpression(compare(Opcodes.IFGE, two, two)).evaluatesTo(true);

    // There are special cases for NaN that we need to test, basically every expression involving
    // NaN should evaluate to false with the exception of NaN != * which always == true
    assertThatExpression(compare(Opcodes.IFNE, nan, two)).evaluatesTo(true);
    assertThatExpression(compare(Opcodes.IFNE, two, nan)).evaluatesTo(true);
    assertThatExpression(compare(Opcodes.IFNE, nan, nan)).evaluatesTo(true);

    assertThatExpression(compare(Opcodes.IFEQ, nan, two)).evaluatesTo(false);
    assertThatExpression(compare(Opcodes.IFEQ, two, nan)).evaluatesTo(false);
    assertThatExpression(compare(Opcodes.IFEQ, nan, nan)).evaluatesTo(false);

    assertThatExpression(compare(Opcodes.IFLE, nan, two)).evaluatesTo(false);
    assertThatExpression(compare(Opcodes.IFLE, two, nan)).evaluatesTo(false);
    assertThatExpression(compare(Opcodes.IFLT, nan, two)).evaluatesTo(false);
    assertThatExpression(compare(Opcodes.IFLT, two, nan)).evaluatesTo(false);
    assertThatExpression(compare(Opcodes.IFGE, nan, two)).evaluatesTo(false);
    assertThatExpression(compare(Opcodes.IFGE, two, nan)).evaluatesTo(false);
    assertThatExpression(compare(Opcodes.IFGT, nan, two)).evaluatesTo(false);
    assertThatExpression(compare(Opcodes.IFGT, two, nan)).evaluatesTo(false);
  }

  @Test
  public void testShortCircuitingLogicalOperators_basic() {
    assertThatExpression(logicalOr(constant(false))).evaluatesTo(false);
    assertThatExpression(logicalOr(constant(true))).evaluatesTo(true);

    assertThatExpression(logicalAnd(constant(false))).evaluatesTo(false);
    assertThatExpression(logicalAnd(constant(true))).evaluatesTo(true);

    assertThatExpression(logicalOr(constant(false), constant(false))).evaluatesTo(false);
    assertThatExpression(logicalOr(constant(false), constant(true))).evaluatesTo(true);
    assertThatExpression(logicalOr(constant(true), constant(false))).evaluatesTo(true);
    assertThatExpression(logicalOr(constant(true), constant(true))).evaluatesTo(true);

    assertThatExpression(logicalAnd(constant(false), constant(false))).evaluatesTo(false);
    assertThatExpression(logicalAnd(constant(false), constant(true))).evaluatesTo(false);
    assertThatExpression(logicalAnd(constant(true), constant(false))).evaluatesTo(false);
    assertThatExpression(logicalAnd(constant(true), constant(true))).evaluatesTo(true);
  }

  @Test
  public void testShortCircuitingLogicalOperators_compatibleWithJavaOperators() {
    ImmutableList<Boolean> bools = ImmutableList.of(true, false);
    for (boolean a : bools) {
      for (boolean b : bools) {
        for (boolean c : bools) {
          for (boolean d : bools) {
            List<Expression> exprs =
                ImmutableList.of(constant(a), constant(b), constant(c), constant(d));
            assertThatExpression(logicalOr(exprs)).evaluatesTo(a || b || c || d);
            assertThatExpression(logicalAnd(exprs)).evaluatesTo(a && b && c && d);
          }
        }
      }
    }
  }

  // Use an expression that only ever throws for branches that are supposed to be skipped.
  @Test
  public void testShortCircuitingLogicalOperators_shortCircuits() {
    assertThatExpression(throwingBoolExpression()).throwsException(IllegalStateException.class);

    assertThatExpression(logicalOr(ImmutableList.of(constant(true), throwingBoolExpression())))
        .evaluatesTo(true);
    assertThatExpression(logicalAnd(ImmutableList.of(constant(false), throwingBoolExpression())))
        .evaluatesTo(false);
  }

  private static Expression throwingBoolExpression() {
    return new Expression(Type.BOOLEAN_TYPE) {
      @Override
      void doGen(CodeBuilder adapter) {
        adapter.throwException(
            Type.getType(IllegalStateException.class), "shouldn't have called me");
      }
    };
  }
}
