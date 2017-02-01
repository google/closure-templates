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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for DictImpl.
 *
 */
@RunWith(JUnit4.class)
public class DictImplTest {

  @Test
  public void testSoyValueMethods() {

    SoyValue val1 = DictImpl.forProviderMap(ImmutableMap.<String, SoyValue>of());
    assertTrue(val1.coerceToBoolean()); // DictImpl is always truthy.
    assertEquals("{}", val1.coerceToString());
    SoyValue val2 = DictImpl.forProviderMap(ImmutableMap.<String, SoyValue>of());
    assertFalse(val1.equals(val2)); // DictImpl uses object identity.

    SoyValue val3 =
        DictImpl.forProviderMap(
            ImmutableMap.<String, SoyValue>of(
                "foo", FloatData.forValue(3.14), "too", BooleanData.TRUE));
    assertTrue(val3.coerceToBoolean());
    assertEquals("{foo: 3.14, too: true}", val3.coerceToString());
  }

  @Test
  public void testDictMethods() {

    SoyDict dict =
        DictImpl.forProviderMap(
            ImmutableMap.<String, SoyValue>of(
                "boo", StringData.forValue("aaah"), "foo", FloatData.forValue(3.14)));
    Map<String, ? extends SoyValueProvider> m1 = dict.asJavaStringMap();
    assertEquals(2, m1.size());
    assertEquals("aaah", m1.get("boo").resolve().stringValue());
    Map<String, ? extends SoyValue> m2 = dict.asResolvedJavaStringMap();
    assertEquals(2, m2.size());
    assertEquals(3.14, m2.get("foo").floatValue(), 0.0);
  }

  @Test
  public void testRecordMethods() {

    Map<String, SoyValueProvider> providerMap = Maps.newHashMap();
    SoyDict dict = DictImpl.forProviderMap(providerMap);
    assertFalse(dict.hasField("boo"));
    assertNull(dict.getField("boo"));
    assertNull(dict.getFieldProvider("boo"));
    providerMap.put("boo", StringData.forValue("blah"));
    assertTrue(dict.hasField("boo"));
    assertEquals("blah", dict.getField("boo").stringValue());
    assertEquals("blah", dict.getFieldProvider("boo").resolve().stringValue());
    providerMap.remove("boo");
    assertFalse(dict.hasField("boo"));
    assertNull(dict.getField("boo"));
    assertNull(dict.getFieldProvider("boo"));

    providerMap.put("foo", FloatData.forValue(3.14));
    providerMap.put("too", BooleanData.TRUE);
    assertTrue(dict.hasField("foo"));
    assertEquals(3.14, dict.getField("foo").floatValue(), 0.0);
    assertEquals(true, dict.getField("too").booleanValue());
  }

  @Test
  public void testMapMethods() {

    StringData BOO = StringData.forValue("boo");

    Map<String, SoyValueProvider> providerMap = Maps.newHashMap();
    SoyDict dict = DictImpl.forProviderMap(providerMap);
    assertEquals(0, dict.getItemCnt());
    assertEquals(0, Iterables.size(dict.getItemKeys()));
    assertFalse(dict.hasItem(BOO));
    assertNull(dict.getItem(BOO));
    assertNull(dict.getItemProvider(BOO));
    providerMap.put("boo", IntegerData.forValue(111));
    assertEquals(1, dict.getItemCnt());
    assertEquals(1, Iterables.size(dict.getItemKeys()));
    assertEquals("boo", Iterables.getOnlyElement(dict.getItemKeys()).stringValue());
    providerMap.put("foo", IntegerData.forValue(222));
    providerMap.put("goo", IntegerData.forValue(333));
    assertEquals(3, dict.getItemCnt());
    assertEquals(3, Iterables.size(dict.getItemKeys()));
    assertTrue(dict.hasItem(BOO));
    assertEquals(111, dict.getItem(BOO).integerValue());
    assertEquals(111, dict.getItemProvider(BOO).resolve().integerValue());
    providerMap.remove("foo");
    assertEquals(2, dict.getItemCnt());
    providerMap.remove("boo");
    providerMap.remove("goo");
    assertEquals(0, dict.getItemCnt());
    assertEquals(0, Iterables.size(dict.getItemKeys()));
    assertFalse(dict.hasItem(BOO));
    assertNull(dict.getItem(BOO));
    assertNull(dict.getItemProvider(BOO));
  }
}
