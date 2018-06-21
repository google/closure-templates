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

package com.google.template.soy.shared.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.shared.internal.SharedRuntime.equal;
import static com.google.template.soy.shared.internal.SharedRuntime.lessThan;
import static com.google.template.soy.shared.internal.SharedRuntime.lessThanOrEqual;
import static com.google.template.soy.shared.internal.SharedRuntime.plus;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link SharedRuntime}
 *
 * <p>Mostly {@link SharedRuntime} is either trivial or covered by higher level tests, this tests
 * some of the weirder features explicitly.
 */
@RunWith(JUnit4.class)
public class SharedRuntimeTest {

  @Test
  public void testEqual() {
    assertThat(equal(IntegerData.forValue(1), IntegerData.forValue(1))).isTrue();
    assertThat(equal(IntegerData.forValue(1), IntegerData.forValue(2))).isFalse();

    assertThat(equal(StringData.forValue("1"), IntegerData.forValue(1))).isTrue();
    assertThat(equal(IntegerData.forValue(1), StringData.forValue("1"))).isTrue();

    assertThat(equal(StringData.forValue("2"), IntegerData.forValue(1))).isFalse();
    assertThat(equal(IntegerData.forValue(1), StringData.forValue("3"))).isFalse();
  }

  @Test
  public void testPlus() {
    assertThat(plus(IntegerData.forValue(1), IntegerData.forValue(2)).integerValue()).isEqualTo(3);

    // N.B. coerced to float
    assertThat(plus(FloatData.forValue(1), IntegerData.forValue(2)).numberValue()).isEqualTo(3.0);

    // coerced to string
    assertThat(plus(StringData.forValue("3"), IntegerData.forValue(2)).stringValue())
        .isEqualTo("32");

    // SanitizedContent:
    assertThat(
            plus(
                    SanitizedContents.unsanitizedText("Hello"),
                    SanitizedContents.unsanitizedText("World"))
                .stringValue())
        .isEqualTo("HelloWorld");

    // Even arrays:
    SoyValueConverter converter = SoyValueConverter.INSTANCE;
    assertThat(
            plus(
                    converter.convert(ImmutableList.of("Hello")).resolve(),
                    converter.convert(ImmutableList.of("World")).resolve())
                .stringValue())
        .isEqualTo("[Hello][World]");
  }

  @Test
  public void testLessThan() {
    assertThat(lessThan(IntegerData.forValue(1), IntegerData.forValue(1))).isFalse();
    assertThat(lessThan(IntegerData.forValue(1), IntegerData.forValue(2))).isTrue();

    assertThat(lessThan(FloatData.forValue(1), FloatData.forValue(1))).isFalse();
    assertThat(lessThan(FloatData.forValue(1), FloatData.forValue(2))).isTrue();

    assertThat(lessThan(StringData.forValue("World"), StringData.forValue("Hello"))).isFalse();
    assertThat(lessThan(StringData.forValue("Hello"), StringData.forValue("World"))).isTrue();

    assertThat(lessThan(StringData.forValue("foobar"), StringData.forValue("foo"))).isFalse();
    assertThat(lessThan(StringData.forValue("foo"), StringData.forValue("foobar"))).isTrue();
  }

  @Test
  public void testLessThanOrEqual() {
    assertThat(lessThanOrEqual(IntegerData.forValue(2), IntegerData.forValue(1))).isFalse();
    assertThat(lessThanOrEqual(IntegerData.forValue(1), IntegerData.forValue(1))).isTrue();
    assertThat(lessThanOrEqual(IntegerData.forValue(1), IntegerData.forValue(2))).isTrue();

    assertThat(lessThanOrEqual(FloatData.forValue(2), FloatData.forValue(1))).isFalse();
    assertThat(lessThanOrEqual(FloatData.forValue(1), FloatData.forValue(1))).isTrue();
    assertThat(lessThanOrEqual(FloatData.forValue(1), FloatData.forValue(2))).isTrue();

    assertThat(lessThanOrEqual(StringData.forValue("foobar"), StringData.forValue("foo")))
        .isFalse();
    assertThat(lessThanOrEqual(StringData.forValue("foo"), StringData.forValue("foobar"))).isTrue();
    assertThat(lessThanOrEqual(StringData.forValue("foo"), StringData.forValue("foo"))).isTrue();

    assertThat(lessThanOrEqual(StringData.forValue(""), StringData.forValue("!?_"))).isTrue();
    assertThat(lessThanOrEqual(StringData.forValue(""), StringData.forValue(""))).isTrue();
  }
}
