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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyEasyDict;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;

import junit.framework.TestCase;

import java.util.Map;


/**
 * Unit tests for EasyDictImpl.
 *
 */
public class EasyDictImplTest extends TestCase {


  public void testSoyValueMethods() {

    SoyValue val1 = new EasyDictImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    assertTrue(val1.coerceToBoolean());  // EasyDictImpl is always truthy.
    assertEquals("{}", val1.coerceToString());
    SoyValue val2 = new EasyDictImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    assertFalse(val1.equals(val2));  // EasyDictImpl uses object identity.

    SoyValue val3 = SoyValueHelper.UNCUSTOMIZED_INSTANCE.newEasyDict("foo", 3.14);
    assertTrue(val3.coerceToBoolean());
    assertEquals("{foo: 3.14}", val3.coerceToString());

    SoyValue val4 = SoyValueHelper.UNCUSTOMIZED_INSTANCE.newEasyDict("too", true);
    assertTrue(val4.coerceToBoolean());
    assertEquals("{too: true}", val4.coerceToString());
  }


  public void testDictMethods() {

    SoyDict dict = SoyValueHelper.UNCUSTOMIZED_INSTANCE.newEasyDict("boo", "aaah", "foo", 3.14);
    Map<String, ? extends SoyValueProvider> m1 = dict.asJavaStringMap();
    assertEquals(2, m1.size());
    assertEquals("aaah", m1.get("boo").resolve().stringValue());
    Map<String, ? extends SoyValue> m2 = dict.asResolvedJavaStringMap();
    assertEquals(2, m2.size());
    assertEquals(3.14, m2.get("foo").floatValue());
  }


  public void testRecordMethods() {

    SoyEasyDict dict = new EasyDictImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
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
    assertTrue(dict.has("foo"));
    assertEquals(3.14, dict.get("foo").floatValue());
    assertEquals(true, dict.get("too").booleanValue());
  }


  public void testMapMethods() {

    StringData BOO = StringData.forValue("boo");

    SoyEasyDict dict = new EasyDictImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
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

    SoyEasyDict srcMap = new EasyDictImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    srcMap.set("boo", 111);
    srcMap.set("foo.goo", 222);
    dict.setItemsFromDict(srcMap);
    assertEquals(111, dict.get("boo").integerValue());
    assertEquals(222, ((SoyEasyDict) dict.get("foo")).get("goo").integerValue());
  }


  public void testDottedNameMethods() {

    SoyEasyDict dict = new EasyDictImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
    assertFalse(dict.has("boo"));
    assertNull(dict.get("boo"));
    assertFalse(dict.has("boo.foo"));
    assertNull(dict.get("boo.foo"));
    assertNull(dict.getProvider("boo.foo"));
    dict.set("boo.foo", 3.14);
    assertTrue(dict.has("boo"));
    assertTrue(dict.get("boo") instanceof SoyEasyDict);
    assertTrue(dict.has("boo.foo"));
    assertEquals(3.14, dict.get("boo.foo").floatValue());
    assertEquals(3.14, dict.getProvider("boo.foo").resolve().floatValue());
    dict.set("boo.goo.moo", null);
    dict.set("boo.goo.too", true);
    assertTrue(dict.get("boo.goo") instanceof SoyEasyDict);
    assertTrue(dict.has("boo.goo.moo"));
    assertTrue(dict.get("boo.goo.moo") instanceof NullData);
    assertEquals(true, dict.get("boo.goo.too").booleanValue());
    dict.del("boo.goo.moo");
    assertFalse(dict.has("boo.goo.moo"));
    assertTrue(dict.has("boo.goo.too"));
    assertTrue(dict.has("boo.foo"));
    dict.del("boo.goo");
    assertFalse(dict.has("boo.goo.too"));
    assertTrue(dict.has("boo.foo"));
    dict.del("boo");
    assertFalse(dict.has("boo.foo"));
  }


  // TODO: Maybe eventually transition to a state where we can enforce that field names are
  // always identifiers. Currently, we can't do this because some existing usages use
  // non-identifier keys.
  // public void testOnlyIdentNamesAllowed() {
  //
  //   SoyEasyDict dict = new EasyDictImpl(SoyValueHelper.UNCUSTOMIZED_INSTANCE);
  //
  //   dict.set("aa.b_b.CC.D_D.eE.f00._g.__H.i_._8", null);
  //
  //   try {
  //     dict.setField("aa.bb", NullData.INSTANCE);  // note: setField(), not set()
  //     fail();
  //   } catch (SoyDataException e) { /* Test passes. */ }
  //
  //   try {
  //     dict.set("aa-bb", NullData.INSTANCE);
  //     fail();
  //   } catch (SoyDataException e) { /* Test passes. */ }
  //
  //   try {
  //     dict.setField("0", NullData.INSTANCE);
  //     fail();
  //   } catch (SoyDataException e) { /* Test passes. */ }
  //
  //   try {
  //     dict.set("$aa", NullData.INSTANCE);
  //     fail();
  //   } catch (SoyDataException e) { /* Test passes. */ }
  // }

}
