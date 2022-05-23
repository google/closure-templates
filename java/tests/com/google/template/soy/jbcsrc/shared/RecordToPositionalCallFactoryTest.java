/*
 * Copyright 2021 Google Inc.
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
package com.google.template.soy.jbcsrc.shared;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.invoke.MethodType.methodType;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** */
@RunWith(JUnit4.class)
public final class RecordToPositionalCallFactoryTest {

  private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

  @Test
  public void testDelegate_arity0() throws Throwable {
    MethodHandle example0 =
        lookup.findStatic(
            RecordToPositionalCallFactoryTest.class, "example0", methodType(String.class));

    MethodHandle delegate =
        RecordToPositionalCallFactory.bootstrapDelegate(
                lookup, "delegate", methodType(String.class, SoyRecord.class), example0)
            .getTarget();
    String result = (String) delegate.invokeExact(asRecord(ImmutableMap.of("p1", "foobar")));
    assertThat(result).isEqualTo("nada");
  }

  public static String example0() throws IOException {
    return "nada";
  }

  @Test
  public void testDelegate_arity1() throws Throwable {
    MethodHandle example1 =
        lookup.findStatic(
            RecordToPositionalCallFactoryTest.class,
            "example1",
            methodType(String.class, SoyValueProvider.class));

    MethodHandle delegate =
        RecordToPositionalCallFactory.bootstrapDelegate(
                lookup, "delegate", methodType(String.class, SoyRecord.class), example1, "p1")
            .getTarget();
    String result = (String) delegate.invokeExact(asRecord(ImmutableMap.of("p1", "foobar")));
    assertThat(result).isEqualTo("p1: foobar");
  }

  public static String example1(SoyValueProvider p1) throws IOException {
    return "p1: " + p1.resolve().coerceToString();
  }

  @Test
  public void testDelegate_arity5() throws Throwable {
    MethodHandle example5 =
        lookup.findStatic(
            RecordToPositionalCallFactoryTest.class,
            "example5",
            methodType(
                String.class,
                SoyValueProvider.class,
                SoyValueProvider.class,
                SoyValueProvider.class,
                SoyValueProvider.class,
                SoyValueProvider.class));

    MethodHandle delegate =
        RecordToPositionalCallFactory.bootstrapDelegate(
                lookup,
                "delegate",
                methodType(String.class, SoyRecord.class),
                example5,
                "p1",
                "p2",
                "p3",
                "p4",
                "p5")
            .getTarget();
    String result =
        (String)
            delegate.invokeExact(
                asRecord(ImmutableMap.of("p1", "a", "p2", "b", "p3", "c", "p4", "d", "p5", "e")));
    assertThat(result).isEqualTo("p1: a, p2: b, p3: c, p4: d, p5: e");
  }

  public static String example5(
      SoyValueProvider p1,
      SoyValueProvider p2,
      SoyValueProvider p3,
      SoyValueProvider p4,
      SoyValueProvider p5)
      throws IOException {
    return "p1: "
        + p1.resolve().coerceToString()
        + ", p2: "
        + p2.resolve().coerceToString()
        + ", p3: "
        + p3.resolve().coerceToString()
        + ", p4: "
        + p4.resolve().coerceToString()
        + ", p5: "
        + p5.resolve().coerceToString();
  }

  static SoyRecord asRecord(Map<String, ?> params) {
    return (SoyRecord) SoyValueConverter.INSTANCE.convert(params);
  }
}
