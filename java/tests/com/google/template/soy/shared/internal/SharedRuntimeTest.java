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

import static com.google.template.soy.shared.internal.SharedRuntime.equal;
import static com.google.template.soy.shared.internal.SharedRuntime.lessThan;
import static com.google.template.soy.shared.internal.SharedRuntime.lessThanOrEqual;
import static com.google.template.soy.shared.internal.SharedRuntime.plus;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    assertTrue(equal(IntegerData.forValue(1), IntegerData.forValue(1)));
    assertFalse(equal(IntegerData.forValue(1), IntegerData.forValue(2)));

    assertTrue(equal(StringData.forValue("1"), IntegerData.forValue(1)));
    assertTrue(equal(IntegerData.forValue(1), StringData.forValue("1")));

    assertFalse(equal(StringData.forValue("2"), IntegerData.forValue(1)));
    assertFalse(equal(IntegerData.forValue(1), StringData.forValue("3")));
  }

  @Test
  public void testPlus() {
    assertEquals(3, plus(IntegerData.forValue(1), IntegerData.forValue(2)).integerValue());

    // N.B. coerced to float
    assertEquals(3.0, plus(FloatData.forValue(1), IntegerData.forValue(2)).numberValue(), 0.0);

    // coerced to string
    assertEquals("32", plus(StringData.forValue("3"), IntegerData.forValue(2)).stringValue());

    // SanitizedContent:
    assertEquals(
        "HelloWorld",
        plus(SanitizedContents.unsanitizedText("Hello"), SanitizedContents.unsanitizedText("World"))
            .stringValue());

    // Even arrays:
    SoyValueConverter converter = SoyValueConverter.UNCUSTOMIZED_INSTANCE;
    assertEquals(
        "[Hello][World]",
        plus(
                converter.convert(ImmutableList.of("Hello")).resolve(),
                converter.convert(ImmutableList.of("World")).resolve())
            .stringValue());
  }

  @Test
  public void testLessThan() {
    assertFalse(lessThan(IntegerData.forValue(1), IntegerData.forValue(1)));
    assertTrue(lessThan(IntegerData.forValue(1), IntegerData.forValue(2)));

    assertFalse(lessThan(FloatData.forValue(1), FloatData.forValue(1)));
    assertTrue(lessThan(FloatData.forValue(1), FloatData.forValue(2)));

    assertFalse(lessThan(StringData.forValue("World"), StringData.forValue("Hello")));
    assertTrue(lessThan(StringData.forValue("Hello"), StringData.forValue("World")));

    assertFalse(lessThan(StringData.forValue("foobar"), StringData.forValue("foo")));
    assertTrue(lessThan(StringData.forValue("foo"), StringData.forValue("foobar")));
  }

  @Test
  public void testLessThanOrEqual() {
    assertFalse(lessThanOrEqual(IntegerData.forValue(2), IntegerData.forValue(1)));
    assertTrue(lessThanOrEqual(IntegerData.forValue(1), IntegerData.forValue(1)));
    assertTrue(lessThanOrEqual(IntegerData.forValue(1), IntegerData.forValue(2)));

    assertFalse(lessThanOrEqual(FloatData.forValue(2), FloatData.forValue(1)));
    assertTrue(lessThanOrEqual(FloatData.forValue(1), FloatData.forValue(1)));
    assertTrue(lessThanOrEqual(FloatData.forValue(1), FloatData.forValue(2)));

    assertFalse(lessThanOrEqual(StringData.forValue("foobar"), StringData.forValue("foo")));
    assertTrue(lessThanOrEqual(StringData.forValue("foo"), StringData.forValue("foobar")));
    assertTrue(lessThanOrEqual(StringData.forValue("foo"), StringData.forValue("foo")));

    assertTrue(lessThanOrEqual(StringData.forValue(""), StringData.forValue("!?_")));
    assertTrue(lessThanOrEqual(StringData.forValue(""), StringData.forValue("")));
  }
}
