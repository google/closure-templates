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
import com.google.common.collect.Lists;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;

import junit.framework.TestCase;

import java.util.List;


/**
 * Unit tests for SoyListData.
 *
 */
public class SoyListDataTest extends TestCase {


  public void testPutRemoveGetSingleKey() {

    SoyListData sld = new SoyListData();

    sld.put("0", StringData.forValue("moomoo"));
    assertEquals("moomoo", ((StringData) sld.get("0")).getValue());

    sld.put("0", (SoyData) null);
    assertEquals(1, sld.length());
    assertTrue(sld.get("0") instanceof NullData);

    sld.remove("0");
    assertEquals(0, sld.length());
    assertEquals(null, sld.get("0"));

    sld.add(IntegerData.forValue(17));
    assertEquals(17, ((IntegerData) sld.get(0)).getValue());

    sld.set(0, BooleanData.FALSE);
    assertEquals(false, ((BooleanData) sld.get(0)).getValue());

    sld.set(1, (SoyData) null);
    assertTrue(sld.get(1) instanceof NullData);

    sld.add(true);
    assertEquals(true, sld.getBoolean(2));
    sld.add(8);
    assertEquals(8, sld.getInteger(3));
    sld.add(3.14);
    assertEquals(3.14, sld.getFloat(4));
    sld.add("woohoo");
    assertEquals("woohoo", sld.getString(5));

    sld.set(6, true);
    assertEquals(true, sld.getBoolean(6));
    sld.set(6, -8);
    assertEquals(-8, sld.getInteger(6));
    sld.set(7, -3.14);
    assertEquals(-3.14, sld.getFloat(7));
    sld.set(7, "boohoo");
    assertEquals("boohoo", sld.getString(7));

    assertEquals(8, sld.length());
    sld.remove(2);
    sld.remove(4);
    assertEquals(6, sld.length());
    assertEquals(false, sld.getBoolean(0));
    assertEquals(8, sld.getInteger(2));
    assertEquals(-8, sld.getInteger(4));

    SoyListData sld2 = new SoyListData();
    sld.add(sld2);
    assertEquals(sld2, sld.getListData(6));

    SoyMapData smd = new SoyMapData();
    sld.set(7, smd);
    assertEquals(smd, sld.getMapData(7));
  }


  public void testPutRemoveGetMultiKey() {

    SoyListData sld = new SoyListData();

    sld.put("0.0", false);
    assertEquals(false, sld.getBoolean("0.0"));
    assertEquals(false, sld.getListData("0").getBoolean("0"));

    sld.put("0.1.0", 26);
    assertEquals(26, sld.getInteger("0.1.0"));
    assertEquals(26, sld.getListData("0").getInteger("1.0"));
    assertEquals(26, sld.getListData("0.1").getInteger("0"));
    assertEquals(26, sld.getListData("0").getListData("1").getInteger("0"));

    sld.put("0.2.boo", "foo");
    sld.put("0.2.goo", 1.618);
    assertEquals("foo", sld.getString("0.2.boo"));
    assertEquals(1.618, sld.getMapData("0.2").getFloat("goo"));
  }


  public void testConstruction() {

    List<Object> existingList = Lists.<Object>newArrayList(8, null, ImmutableList.of("blah", true));
    SoyListData sld = new SoyListData(existingList);
    sld.put("2.2", 2.71828);
    sld.add("bleh");

    assertEquals(8, sld.getInteger(0));
    assertTrue(sld.get(1) instanceof NullData);
    assertEquals("blah", sld.getString("2.0"));
    assertEquals(true, sld.getBoolean("2.1"));
    assertEquals(2.71828, sld.getFloat("2.2"));
    assertEquals("bleh", sld.getString(3));

    sld = new SoyListData(8, null, new SoyListData("blah", true));
    sld.put("2.2", 2.71828);
    sld.add("bleh");

    assertEquals(8, sld.getInteger(0));
    assertTrue(sld.get(1) instanceof NullData);
    assertEquals("blah", sld.getString("2.0"));
    assertEquals(true, sld.getBoolean("2.1"));
    assertEquals(2.71828, sld.getFloat("2.2"));
    assertEquals("bleh", sld.getString(3));
  }


  public void testErrorDuringConstruction() {

    List<Object> existingList =
        Lists.<Object>newArrayList(8, null, ImmutableList.of(new Object(), "blah", true));

    try {
      new SoyListData(existingList);
      fail();
    } catch (SoyDataException sde) {
      assertTrue(sde.getMessage().contains("At data path '[2][0]':"));
    }

    existingList.set(2, ImmutableList.of(ImmutableList.of(0, new Object()), "blah", true));

    try {
      new SoyListData(existingList);
      fail();
    } catch (SoyDataException sde) {
      assertTrue(sde.getMessage().contains("At data path '[2][0][1]':"));
    }
  }


  public void testCoercion() {

    SoyListData sld0 = new SoyListData();
    SoyListData sld1 = new SoyListData("boo");

    SoyListData sld2 = new SoyListData(8, null, new SoyListData("blah", true), "bleh");
    sld2.put("2.2", 2.71828);

    assertEquals("[]", sld0.toString());
    assertEquals("[boo]", sld1.toString());
    assertEquals("[8, null, [blah, true, 2.71828], bleh]", sld2.toString());

    assertEquals(true, sld0.toBoolean());
    assertEquals(true, sld1.toBoolean());
    assertEquals(true, sld2.toBoolean());
  }


  public void testIsEqualto() {

    SoyListData sld0 = new SoyListData();
    SoyListData sld1 = new SoyListData("boo");

    assertTrue(sld0.equals(sld0));
    assertTrue(sld1.equals(sld1));
    assertFalse(sld0.equals(sld1));
    assertFalse(sld0.equals(new SoyListData()));
  }

}
