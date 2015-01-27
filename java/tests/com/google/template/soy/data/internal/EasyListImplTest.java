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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.data.SoyEasyList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;

import junit.framework.TestCase;


/**
 * Unit tests for EasyListImpl.
 *
 */
public class EasyListImplTest extends TestCase {


  public void testSoyValueMethods() {

    SoyValue val1 = new EasyListImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    assertTrue(val1.coerceToBoolean());  // EasyListImpl is always truthy.
    assertEquals("[]", val1.coerceToString());
    SoyValue val2 = new EasyListImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    assertFalse(val1.equals(val2));  // EasyListImpl uses object identity.

    SoyValue val3 = SoyValueHelper.UNCUSTOMIZED_INSTANCE.newEasyList(111, true);
    assertTrue(val3.coerceToBoolean());
    assertEquals("[111, true]", val3.coerceToString());
  }


  public void testListMethods() {

    StringData BLAH_0 = StringData.forValue("blah");
    FloatData PI = FloatData.forValue(3.14);
    SoyValue BLAH_2 = StringData.forValue("blah");

    SoyEasyList list = new EasyListImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    assertEquals(0, list.length());
    assertEquals(0, Iterables.size(list.asJavaList()));
    assertEquals(0, Iterables.size(list.asResolvedJavaList()));
    assertNull(list.get(0));
    assertNull(list.getProvider(0));
    list.add(BLAH_2);
    list.add(BLAH_2);
    list.add(0, BLAH_0);
    list.set(1, PI);
    // At this point, list should be [BLAH_0, PI, BLAH_2].
    assertEquals(3, list.length());
    assertEquals(ImmutableList.of(BLAH_0, PI, BLAH_2), list.asJavaList());
    assertEquals(ImmutableList.of(BLAH_0, PI, BLAH_2), list.asResolvedJavaList());
    assertSame(BLAH_0, list.get(0));
    assertNotSame(BLAH_2, list.get(0));
    assertEquals(BLAH_2, list.get(0));  // not same, but they compare equal
    assertEquals(3.14, list.getProvider(1).resolve().floatValue());
    list.del(1);
    assertSame(BLAH_2, list.get(1));
  }


  public void testAddAllMethods() {

    SoyEasyList srcList = new EasyListImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    assertEquals(0, srcList.length());
    srcList.add("blah");
    srcList.add(111);

    SoyEasyList list = new EasyListImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    list.addAllFromList(srcList);
    list.addAllFromJavaIterable(ImmutableList.of(3.14, true));
    // At this point, list should be [blah, 111, 3.14, true].
    assertEquals(4, list.length());
    list.add((Object) null);
    list.add(0, 333);
    list.set(1, "bleh");
    assertEquals("[333, bleh, 111, 3.14, true, null]", list.coerceToString());
  }


  public void testMapMethods() {

    SoyEasyList list = SoyValueHelper.UNCUSTOMIZED_INSTANCE.newEasyList(3.14, true);
    assertEquals(2, list.getItemCnt());
    assertEquals(ImmutableList.of(IntegerData.ZERO, IntegerData.ONE), list.getItemKeys());
    assertTrue(list.hasItem(IntegerData.ONE));
    assertEquals(3.14, list.getItem(IntegerData.ZERO).floatValue());
    assertEquals(true, list.getItemProvider(IntegerData.ONE).resolve().booleanValue());

    // For backwards compatibility: accept string arguments.
    assertTrue(list.hasItem(StringData.forValue("0")));
    assertFalse(list.hasItem(StringData.forValue("-99")));
    assertFalse(list.hasItem(StringData.forValue("99")));
    assertEquals(3.14, list.getItem(StringData.forValue("0")).floatValue());
    assertEquals(true, list.getItemProvider(StringData.forValue("1")).resolve().booleanValue());

    list.set(1, StringData.forValue("blah"));
    list.del(0);
    assertEquals(1, list.getItemCnt());
    assertEquals(ImmutableList.of(IntegerData.ZERO), list.getItemKeys());
    assertFalse(list.hasItem(IntegerData.ONE));
    assertEquals("blah", list.getItem(IntegerData.ZERO).stringValue());
  }

}
