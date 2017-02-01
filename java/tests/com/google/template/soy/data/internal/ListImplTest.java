/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.data.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for ListImpl.
 *
 */
@RunWith(JUnit4.class)
public class ListImplTest {

  private static final ImmutableList<SoyValueProvider> EMPTY = ImmutableList.<SoyValueProvider>of();

  @Test
  public void testSoyValueMethods() {

    SoyValue val1 = ListImpl.forProviderList(EMPTY);
    assertTrue(val1.coerceToBoolean()); // ListImpl is always truthy.
    assertEquals("[]", val1.coerceToString());
    SoyValue val2 = ListImpl.forProviderList(EMPTY);
    assertFalse(val1.equals(val2)); // ListImpl uses object identity.

    SoyValue val3 =
        ListImpl.forProviderList(ImmutableList.of(IntegerData.forValue(111), BooleanData.TRUE));
    assertTrue(val3.coerceToBoolean());
    assertEquals("[111, true]", val3.coerceToString());
  }

  @Test
  public void testListMethods() {

    StringData BLAH_0 = StringData.forValue("blah");
    FloatData PI = FloatData.forValue(3.14);
    SoyValue BLAH_2 = StringData.forValue("blah");

    SoyList list = ListImpl.forProviderList(EMPTY);
    assertEquals(0, list.length());
    assertEquals(0, Iterables.size(list.asJavaList()));
    assertEquals(0, Iterables.size(list.asResolvedJavaList()));
    assertNull(list.get(0));
    assertNull(list.getProvider(0));
    list = ListImpl.forProviderList(ImmutableList.of(BLAH_0, PI, BLAH_2));
    // At this point, list should be [BLAH_0, PI, BLAH_2].
    assertEquals(3, list.length());
    assertEquals(ImmutableList.of(BLAH_0, PI, BLAH_2), list.asJavaList());
    assertEquals(ImmutableList.of(BLAH_0, PI, BLAH_2), list.asResolvedJavaList());
    assertSame(BLAH_0, list.get(0));
    assertNotSame(BLAH_2, list.get(0));
    assertEquals(BLAH_2, list.get(0)); // not same, but they compare equal
    assertEquals(3.14, list.getProvider(1).resolve().floatValue(), 0.0);
  }

  @Test
  public void testMapMethods() {

    SoyList list =
        ListImpl.forProviderList(ImmutableList.of(FloatData.forValue(3.14), BooleanData.TRUE));
    assertEquals(2, list.getItemCnt());
    assertEquals(ImmutableList.of(IntegerData.ZERO, IntegerData.ONE), list.getItemKeys());
    assertTrue(list.hasItem(IntegerData.ONE));
    assertEquals(3.14, list.getItem(IntegerData.ZERO).floatValue(), 0.0);
    assertEquals(true, list.getItemProvider(IntegerData.ONE).resolve().booleanValue());

    // For backwards compatibility: accept string arguments.
    assertTrue(list.hasItem(StringData.forValue("0")));
    assertFalse(list.hasItem(StringData.forValue("-99")));
    assertFalse(list.hasItem(StringData.forValue("99")));
    assertEquals(3.14, list.getItem(StringData.forValue("0")).floatValue(), 0.0);
    assertEquals(true, list.getItemProvider(StringData.forValue("1")).resolve().booleanValue());
  }
}
