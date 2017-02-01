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
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyEasyDict;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for EasyDictImpl.
 *
 */
@RunWith(JUnit4.class)
public class EasyDictImplTest {

  @Test
  public void testSoyValueMethods() {

    SoyValue val1 = new EasyDictImpl(SoyValueConverter.UNCUSTOMIZED_INSTANCE);
    assertTrue(val1.coerceToBoolean()); // EasyDictImpl is always truthy.
    assertEquals("{}", val1.coerceToString());
    SoyValue val2 = new EasyDictImpl(SoyValueConverter.UNCUSTOMIZED_INSTANCE);
    assertFalse(val1.equals(val2)); // EasyDictImpl uses object identity.

    SoyValue val3 = SoyValueConverter.UNCUSTOMIZED_INSTANCE.newEasyDict("foo", 3.14);
    assertTrue(val3.coerceToBoolean());
    assertEquals("{foo: 3.14}", val3.coerceToString());

    SoyValue val4 = SoyValueConverter.UNCUSTOMIZED_INSTANCE.newEasyDict("too", true);
    assertTrue(val4.coerceToBoolean());
    assertEquals("{too: true}", val4.coerceToString());
  }

  @Test
  public void testDictMethods() {

    SoyDict dict = SoyValueConverter.UNCUSTOMIZED_INSTANCE.newEasyDict("boo", "aaah", "foo", 3.14);
    Map<String, ? extends SoyValueProvider> m1 = dict.asJavaStringMap();
    assertEquals(2, m1.size());
    assertEquals("aaah", m1.get("boo").resolve().stringValue());
    Map<String, ? extends SoyValue> m2 = dict.asResolvedJavaStringMap();
    assertEquals(2, m2.size());
    assertEquals(3.14, m2.get("foo").floatValue(), 0.0);
  }

  @Test
  public void testRecordMethods() {

    SoyEasyDict dict = new EasyDictImpl(SoyValueConverter.UNCUSTOMIZED_INSTANCE);
    assertFalse(dict.hasField("boo"));
    assertNull(dict.getField("boo"));
    assertNull(dict.getFieldProvider("boo"));
    dict.setField("boo", StringData.forValue("blah"));
    assertTrue(dict.hasField("boo"));
    assertEquals("blah", dict.getField("boo").stringValue());
    assertEquals("blah", dict.getFieldProvider("boo").resolve().stringValue());
    dict.delField("boo");
    assertFalse(dict.hasField("boo"));
    assertNull(dict.getField("boo"));
    assertNull(dict.getFieldProvider("boo"));

    dict.setFieldsFromJavaStringMap(ImmutableMap.of("foo", 3.14, "too", true));
    assertEquals(3.14, dict.get("foo").floatValue(), 0.0);
    assertEquals(true, dict.get("too").booleanValue());
  }

  @Test
  public void testMapMethods() {

    StringData BOO = StringData.forValue("boo");

    SoyEasyDict dict = new EasyDictImpl(SoyValueConverter.UNCUSTOMIZED_INSTANCE);
    assertEquals(0, dict.getItemCnt());
    assertEquals(0, Iterables.size(dict.getItemKeys()));
    assertFalse(dict.hasItem(BOO));
    assertNull(dict.getItem(BOO));
    assertNull(dict.getItemProvider(BOO));
    dict.setField("boo", IntegerData.forValue(111));
    assertEquals(1, dict.getItemCnt());
    assertEquals(1, Iterables.size(dict.getItemKeys()));
    assertEquals("boo", Iterables.getOnlyElement(dict.getItemKeys()).stringValue());
    dict.setField("foo", IntegerData.forValue(222));
    dict.setField("goo", IntegerData.forValue(333));
    assertEquals(3, dict.getItemCnt());
    assertEquals(3, Iterables.size(dict.getItemKeys()));
    assertTrue(dict.hasItem(BOO));
    assertEquals(111, dict.getItem(BOO).integerValue());
    assertEquals(111, dict.getItemProvider(BOO).resolve().integerValue());
    dict.delField("foo");
    assertEquals(2, dict.getItemCnt());
    dict.delField("boo");
    dict.delField("goo");
    assertEquals(0, dict.getItemCnt());
    assertEquals(0, Iterables.size(dict.getItemKeys()));
    assertFalse(dict.hasItem(BOO));
    assertNull(dict.getItem(BOO));
    assertNull(dict.getItemProvider(BOO));
  }

  @Test
  public void testDottedNameMethods() {

    SoyEasyDict dict = new EasyDictImpl(SoyValueConverter.UNCUSTOMIZED_INSTANCE);
    assertNull(dict.get("boo"));
    assertNull(dict.get("boo.foo"));
    dict.set("boo.foo", 3.14);
    assertTrue(dict.get("boo") instanceof SoyEasyDict);
    assertEquals(3.14, dict.get("boo.foo").floatValue(), 0.0);
    dict.set("boo.goo.moo", null);
    dict.set("boo.goo.too", true);
    assertTrue(dict.get("boo.goo") instanceof SoyEasyDict);
    assertTrue(dict.get("boo.goo.moo") instanceof NullData);
    assertEquals(true, dict.get("boo.goo.too").booleanValue());
  }

}
