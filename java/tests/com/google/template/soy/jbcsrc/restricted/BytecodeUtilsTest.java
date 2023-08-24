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
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.asImmutableList;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.soyNull;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.soyUndefined;
import static com.google.template.soy.jbcsrc.restricted.testing.ExpressionSubject.assertThatExpression;

import com.google.common.base.Utf8;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link com.google.template.soy.jbcsrc.restricted.BytecodeUtils} */
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
  public void testAsImmutableList() {
    // ImmutableList.of has overloads up to 11 arguments with a catchall varargs after that, go up
    // to 20 to test all the possibilities and then some
    for (int n = 0; n < 20; n++) {
      ImmutableList.Builder<Expression> expressionBuilder = ImmutableList.builder();
      ImmutableList.Builder<String> actualBuilder = ImmutableList.builder();
      for (int i = 0; i < n; i++) {
        String string = Integer.toString(i);
        expressionBuilder.add(constant(string));
        actualBuilder.add(string);
      }
      assertThatExpression(asImmutableList(expressionBuilder.build()))
          .evaluatesTo(actualBuilder.build());
    }
  }

  @Test
  public void testLargeStringConstant() {
    String large = "a".repeat(1 << 20);
    // 65336 is the maximum size of a string constant.
    assertThat(Utf8.encodedLength(large)).isGreaterThan(65335);
    assertThatExpression(constant(large)).evaluatesTo(large);
  }

  @Test
  public void testLargeStringConstant_surrogates() {
    assertThat("🤦‍♀️").hasLength(5);
    String large = "🤦‍♀️".repeat(1 << 20);
    // 65336 is the maximum size of a string constant.
    assertThat(Utf8.encodedLength(large)).isGreaterThan(65335);
    // prefix with single byte encoding characters so that the split points will be guaranteed to
    // fall between all bytes of the multibyte character
    assertThatExpression(constant(large)).evaluatesTo(large);
    assertThatExpression(constant('a' + large)).evaluatesTo('a' + large);
    assertThatExpression(constant("aa" + large)).evaluatesTo("aa" + large);
    assertThatExpression(constant("aaa" + large)).evaluatesTo("aaa" + large);
    assertThatExpression(constant("aaaa" + large)).evaluatesTo("aaaa" + large);
  }

  @Test
  public void testNullish() {
    assertThat(soyNull().isNonJavaNullable()).isTrue();
    assertThat(soyNull().isNonSoyNullish()).isFalse();
    assertThat(soyUndefined().isNonJavaNullable()).isTrue();
    assertThat(soyUndefined().isNonSoyNullish()).isFalse();
  }
}
