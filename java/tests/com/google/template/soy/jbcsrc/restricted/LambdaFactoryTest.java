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

import com.google.template.soy.jbcsrc.restricted.testing.ExpressionEvaluator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LambdaFactoryTest {
  // Interface methods to implement with a lambda
  private static final MethodRef SUPPLIER_GET = MethodRef.createPure(Supplier.class, "get");
  private static final MethodRef FUNCTION_APPLY =
      MethodRef.createPure(Function.class, "apply", Object.class);
  private static final MethodRef BIFUNCTION_APPLY =
      MethodRef.createPure(BiFunction.class, "apply", Object.class, Object.class);

  // Implementation referecences
  private static final MethodRef IDENTITY_FUNCTION =
      MethodRef.createPure(LambdaFactoryTest.class, "identity", Object.class);
  private static final MethodRef RETURNS_HELLO_FUNCTION =
      MethodRef.createPure(LambdaFactoryTest.class, "returnsHello");
  private static final MethodRef CONCAT_FUNCTION =
      MethodRef.createPure(LambdaFactoryTest.class, "concat", Object.class, Object.class);

  // Actual implementations
  public static Object identity(Object o) {
    return o;
  }

  public static Object concat(Object o, Object o2) {
    return o + ", " + o2;
  }

  public static Object returnsHello() {
    return "hello";
  }

  @Test
  public void testNoBoundParameters() throws ReflectiveOperationException {
    @SuppressWarnings("unchecked")
    Supplier<Object> supplier =
        (Supplier)
            ExpressionEvaluator.evaluate(
                LambdaFactory.create(SUPPLIER_GET, RETURNS_HELLO_FUNCTION).invoke());

    assertThat(supplier.get()).isEqualTo("hello");
  }

  @Test
  public void testOneBundParameter() throws ReflectiveOperationException {
    @SuppressWarnings("unchecked")
    Supplier<Object> supplier =
        (Supplier)
            ExpressionEvaluator.evaluate(
                LambdaFactory.create(SUPPLIER_GET, IDENTITY_FUNCTION).invoke(constant("bound")));
    assertThat(supplier.get()).isEqualTo("bound");
  }

  @Test
  public void testOneFreeParameter() throws ReflectiveOperationException {
    @SuppressWarnings("unchecked")
    Function<Object, Object> fn =
        (Function)
            ExpressionEvaluator.evaluate(
                LambdaFactory.create(FUNCTION_APPLY, IDENTITY_FUNCTION).invoke());
    assertThat(fn.apply("free")).isEqualTo("free");
  }

  @Test
  public void testOneFreeAndOneBoundParameter() throws ReflectiveOperationException {
    @SuppressWarnings("unchecked")
    Function<Object, Object> fn =
        (Function)
            ExpressionEvaluator.evaluate(
                LambdaFactory.create(FUNCTION_APPLY, CONCAT_FUNCTION).invoke(constant("first")));
    assertThat(fn.apply("second")).isEqualTo("first, second");
  }

  @Test
  public void testTwoFreeParameters() throws ReflectiveOperationException {
    @SuppressWarnings("unchecked")
    BiFunction<Object, Object, Object> fn =
        (BiFunction)
            ExpressionEvaluator.evaluate(
                LambdaFactory.create(BIFUNCTION_APPLY, CONCAT_FUNCTION).invoke());
    assertThat(fn.apply("first", "second")).isEqualTo("first, second");
  }
}
