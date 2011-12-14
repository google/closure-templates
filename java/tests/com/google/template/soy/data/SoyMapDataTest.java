/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.data;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;

import junit.framework.TestCase;

import java.util.Map;


/**
 * Unit tests for SoyMapData.
 *
 */
public class SoyMapDataTest extends TestCase {


  public void testPutRemoveGetSingleKey() {

    SoyMapData smd = new SoyMapData();

    smd.put("boo", StringData.forValue("boohoo"));
    assertEquals("boohoo", ((StringData) smd.get("boo")).getValue());

    smd.put("boo", (SoyData) null);
    assertTrue(smd.get("boo") instanceof NullData);

    smd.remove("boo");
    assertEquals(null, smd.get("boo"));

    smd.put("woob", true);
    assertEquals(true, smd.getBoolean("woob"));
    smd.put("wooi", 8);
    assertEquals(8, smd.getInteger("wooi"));
    smd.put("woof", 3.14);
    assertEquals(3.14, smd.getFloat("woof"));
    smd.put("woos", "woohoo");
    assertEquals("woohoo", smd.getString("woos"));

    SoyMapData smd2 = new SoyMapData();
    smd.put("foo", smd2);
    assertEquals(smd2, smd.getMapData("foo"));

    SoyListData sld = new SoyListData();
    smd.put("goo", sld);
    assertEquals(sld, smd.getListData("goo"));
  }


  public void testPutRemoveGetMultiKey() {

    SoyMapData smd = new SoyMapData();

    smd.put("boo.foo", false);
    assertEquals(false, smd.getBoolean("boo.foo"));
    assertEquals(false, smd.getMapData("boo").getBoolean("foo"));

    smd.put("boo.goo.moo", 26);
    assertEquals(26, smd.getInteger("boo.goo.moo"));
    assertEquals(26, smd.getMapData("boo").getInteger("goo.moo"));
    assertEquals(26, smd.getMapData("boo.goo").getInteger("moo"));
    assertEquals(26, smd.getMapData("boo").getMapData("goo").getInteger("moo"));

    smd.put("boo.zoo.0", "too");
    smd.put("boo.zoo.1", 1.618);
    assertEquals("too", smd.getString("boo.zoo.0"));
    assertEquals(1.618, smd.getListData("boo.zoo").getFloat("1"));
  }


  public void testConstruction() {

    Map<String, Object> existingMap = Maps.newHashMap();
    existingMap.put("boo", 8);
    existingMap.put("foo", null);
    existingMap.put("goo", ImmutableMap.of("buntu", "blah", "dy", true));
    SoyMapData smd = new SoyMapData(existingMap);
    smd.put("moo", "bleh", "too.seven", 2.71828);

    assertEquals(8, smd.getInteger("boo"));
    assertTrue(smd.get("foo") instanceof NullData);
    assertEquals("blah", smd.getString("goo.buntu"));
    assertEquals(true, smd.getBoolean("goo.dy"));
    assertEquals("bleh", smd.getString("moo"));
    assertEquals(2.71828, smd.getFloat("too.seven"));
  }


  public void testErrorDuringConstruction() {

    Map<String, Object> existingMap = Maps.newHashMap();
    existingMap.put("boo", 8);
    existingMap.put("foo", null);
    existingMap.put("goo", ImmutableMap.of("buntu", "blah", "fy", new Object(), "dy", true));

    try {
      new SoyMapData(existingMap);
      fail();
    } catch (SoyDataException sde) {
      assertTrue(sde.getMessage().contains("At data path 'goo.fy':"));
    }

    existingMap.put("goo", ImmutableList.of(0, 1, new Object(), 3));

    try {
      new SoyMapData(existingMap);
      fail();
    } catch (SoyDataException sde) {
      assertTrue(sde.getMessage().contains("At data path 'goo[2]':"));
    }

    existingMap.put("goo", ImmutableMap.of(
        "buntu", "blah",
        "fy", ImmutableList.of(0, 1, new Object(), 3),
        "dy", true));

    try {
      new SoyMapData(existingMap);
      fail();
    } catch (SoyDataException sde) {
      assertTrue(sde.getMessage().contains("At data path 'goo.fy[2]':"));
    }

    existingMap.put("goo", ImmutableMap.of(new Object(), "blah"));

    try {
      new SoyMapData(existingMap);
      fail();
    } catch (SoyDataException sde) {
      assertTrue(sde.getMessage().contains(
          "At data path 'goo': Attempting to convert a map with non-string key to Soy data"));
    }
  }


  public void testCoercion() {

    SoyMapData smd0 = new SoyMapData();
    SoyMapData smd1 = new SoyMapData("boo", "foo");
    Map<String, Object> existingMap = Maps.newHashMap();
    existingMap.put("boo", 8);
    existingMap.put("foo", null);
    existingMap.put("goo", ImmutableMap.of("buntu", "blah", "dy", true));
    SoyMapData smd2 = new SoyMapData(existingMap);
    smd2.put("moo", "bleh", "too.seven", 2.71828);

    assertEquals("{}", smd0.toString());
    assertEquals("{boo: foo}", smd1.toString());

    String smd2Str = smd2.toString();
    assertTrue(smd2Str.contains("boo: 8"));
    assertTrue(smd2Str.contains("foo: null"));
    assertTrue(smd2Str.contains("goo: {buntu: blah, dy: true}") ||
               smd2Str.contains("goo: {dy: true, buntu: blah}"));
    assertTrue(smd2Str.contains("moo: bleh"));
    assertTrue(smd2Str.contains("too: {seven: 2.71828}"));

    assertEquals(true, smd0.toBoolean());
    assertEquals(true, smd1.toBoolean());
    assertEquals(true, smd2.toBoolean());
  }


  public void testIsEqualto() {

    SoyMapData smd0 = new SoyMapData();
    SoyMapData smd1 = new SoyMapData("boo", "foo");

    assertTrue(smd0.equals(smd0));
    assertTrue(smd1.equals(smd1));
    assertFalse(smd0.equals(smd1));
    assertFalse(smd0.equals(new SoyMapData()));
  }

}
