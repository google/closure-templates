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
package com.google.template.soy.jbcsrc.shared;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StringSwitchFactoryTest {
  private static final MethodType STRING_MT = MethodType.methodType(int.class, String.class);
  private static final MethodType SOY_VALUE_MT = MethodType.methodType(int.class, SoyValue.class);

  @Test
  public void testSwitchMethod_string() throws Throwable {
    MethodHandle switcher =
        StringSwitchFactory.bootstrapStringSwitch(null, null, STRING_MT, "foo", "bar", "baz")
            .getTarget();
    assertThat((int) switcher.invokeExact("qux")).isEqualTo(-1);
    assertThat((int) switcher.invokeExact("foo")).isEqualTo(0);
    assertThat((int) switcher.invokeExact("bar")).isEqualTo(1);
    assertThat((int) switcher.invokeExact("baz")).isEqualTo(2);
  }

  @Test
  public void testSwitchMethod_soyValue() throws Throwable {
    MethodHandle switcher =
        StringSwitchFactory.bootstrapStringSwitch(null, null, SOY_VALUE_MT, "foo", "bar", "baz")
            .getTarget();
    assertThat((int) switcher.invokeExact((SoyValue) StringData.forValue("qux"))).isEqualTo(-1);
    assertThat((int) switcher.invokeExact((SoyValue) IntegerData.forValue(2))).isEqualTo(-1);
    assertThat((int) switcher.invokeExact((SoyValue) StringData.forValue("foo"))).isEqualTo(0);
    assertThat((int) switcher.invokeExact((SoyValue) StringData.forValue("bar"))).isEqualTo(1);
    assertThat((int) switcher.invokeExact((SoyValue) StringData.forValue("baz"))).isEqualTo(2);

    assertThat((int) switcher.invokeExact((SoyValue) SanitizedContents.constantHtml("foo")))
        .isEqualTo(0);
    assertThat((int) switcher.invokeExact((SoyValue) SanitizedContents.constantHtml("bar")))
        .isEqualTo(1);
    assertThat((int) switcher.invokeExact((SoyValue) SanitizedContents.constantHtml("baz")))
        .isEqualTo(2);
  }

  @Test
  public void testBadSwitchExpr() throws Throwable {
    assertThrows(
        AssertionError.class,
        () -> StringSwitchFactory.bootstrapStringSwitch(null, null, STRING_MT));
    assertThrows(
        AssertionError.class,
        () -> StringSwitchFactory.bootstrapStringSwitch(null, null, SOY_VALUE_MT));
  }
}
