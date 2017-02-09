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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.testing.EqualsTester;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SoyMapData.
 *
 */
@RunWith(JUnit4.class)
public class SoyMapDataTest {

  @Test
  public void testPutRemoveGetSingleKey() {

    SoyMapData smd = new SoyMapData();

    smd.put("boo", StringData.forValue("boohoo"));
    assertThat(((StringData) smd.get("boo")).getValue()).isEqualTo("boohoo");

    smd.put("boo", (SoyData) null);
    assertThat(smd.get("boo")).isInstanceOf(NullData.class);

    smd.remove("boo");
    assertThat(smd.get("boo")).isNull();

    smd.put("woob", true);
    assertThat(smd.getBoolean("woob")).isTrue();
    smd.put("wooi", 8);
    assertThat(smd.getInteger("wooi")).isEqualTo(8);
    smd.put("woof", 3.14);
    assertThat(smd.getFloat("woof")).isWithin(0.0).of(3.14);
    smd.put("woos", "woohoo");
    assertThat(smd.getString("woos")).isEqualTo("woohoo");

    SoyMapData smd2 = new SoyMapData();
    smd.put("foo", smd2);
    assertThat(smd.getMapData("foo")).isEqualTo(smd2);

    SoyListData sld = new SoyListData();
    smd.put("goo", sld);
    assertThat(smd.getListData("goo")).isEqualTo(sld);
  }

  @Test
  public void testPutRemoveGetMultiKey() {

    SoyMapData smd = new SoyMapData();

    smd.put("boo.foo", false);
    assertThat(smd.getBoolean("boo.foo")).isFalse();
    assertThat(smd.getMapData("boo").getBoolean("foo")).isFalse();

    smd.put("boo.goo.moo", 26);
    assertThat(smd.getInteger("boo.goo.moo")).isEqualTo(26);
    assertThat(smd.getMapData("boo").getInteger("goo.moo")).isEqualTo(26);
    assertThat(smd.getMapData("boo.goo").getInteger("moo")).isEqualTo(26);
    assertThat(smd.getMapData("boo").getMapData("goo").getInteger("moo")).isEqualTo(26);

    smd.put("boo.zoo.0", "too");
    smd.put("boo.zoo.1", 1.618);
    assertThat(smd.getString("boo.zoo.0")).isEqualTo("too");
    assertThat(smd.getListData("boo.zoo").getFloat("1")).isWithin(0.0).of(1.618);
  }

  @Test
  public void testConstruction() {

    Map<String, Object> existingMap = Maps.newHashMap();
    existingMap.put("boo", 8);
    existingMap.put("foo", null);
    existingMap.put("goo", ImmutableMap.of("buntu", "blah", "dy", true));
    SoyMapData smd = new SoyMapData(existingMap);
    smd.put("moo", "bleh", "too.seven", 2.71828);

    assertThat(smd.getInteger("boo")).isEqualTo(8);
    assertThat(smd.get("foo")).isInstanceOf(NullData.class);
    assertThat(smd.getString("goo.buntu")).isEqualTo("blah");
    assertThat(smd.getBoolean("goo.dy")).isTrue();
    assertThat(smd.getString("moo")).isEqualTo("bleh");
    assertThat(smd.getFloat("too.seven")).isWithin(0.0).of(2.71828);
  }

  @Test
  public void testErrorDuringConstruction() {

    Map<String, Object> existingMap = Maps.newHashMap();
    existingMap.put("boo", 8);
    existingMap.put("foo", null);
    existingMap.put("goo", ImmutableMap.of("buntu", "blah", "fy", new Object(), "dy", true));

    try {
      new SoyMapData(existingMap);
      fail();
    } catch (SoyDataException sde) {
      assertThat(sde.getMessage()).contains("At data path 'goo.fy':");
    }

    existingMap.put("goo", ImmutableList.of(0, 1, new Object(), 3));

    try {
      new SoyMapData(existingMap);
      fail();
    } catch (SoyDataException sde) {
      assertThat(sde.getMessage()).contains("At data path 'goo[2]':");
    }

    existingMap.put(
        "goo",
        ImmutableMap.of(
            "buntu", "blah", "fy", ImmutableList.of(0, 1, new Object(), 3), "dy", true));

    try {
      new SoyMapData(existingMap);
      fail();
    } catch (SoyDataException sde) {
      assertThat(sde.getMessage()).contains("At data path 'goo.fy[2]':");
    }

    existingMap.put("goo", ImmutableMap.of(new Object(), "blah"));

    try {
      new SoyMapData(existingMap);
      fail();
    } catch (SoyDataException sde) {
      assertThat(sde.getMessage())
          .contains(
              "At data path 'goo': "
                  + "Attempting to convert a map with non-string key to Soy data");
    }
  }

  @Test
  public void testCoercion() {

    SoyMapData smd0 = new SoyMapData();
    SoyMapData smd1 = new SoyMapData("boo", "foo");
    Map<String, Object> existingMap = Maps.newHashMap();
    existingMap.put("boo", 8);
    existingMap.put("foo", null);
    existingMap.put("goo", ImmutableMap.of("buntu", "blah", "dy", true));
    SoyMapData smd2 = new SoyMapData(existingMap);
    smd2.put("moo", "bleh", "too.seven", 2.71828);

    assertThat(smd0.coerceToString()).isEqualTo("{}");
    assertThat(smd1.coerceToString()).isEqualTo("{boo: foo}");

    String smd2Str = smd2.coerceToString();
    assertThat(smd2Str).contains("boo: 8");
    assertThat(smd2Str).contains("foo: null");
    assertThat(
            smd2Str.contains("goo: {buntu: blah, dy: true}")
                || smd2Str.contains("goo: {dy: true, buntu: blah}"))
        .isTrue();
    assertThat(smd2Str).contains("moo: bleh");
    assertThat(smd2Str).contains("too: {seven: 2.71828}");

    assertThat(smd0.coerceToBoolean()).isTrue();
    assertThat(smd1.coerceToBoolean()).isTrue();
    assertThat(smd2.coerceToBoolean()).isTrue();
  }

  @Test
  public void testIsEqualto() {

    SoyMapData smd0 = new SoyMapData();
    SoyMapData smd1 = new SoyMapData("boo", "foo");

    new EqualsTester().addEqualityGroup(smd0).addEqualityGroup(smd1).testEquals();
    assertThat(smd0.equals(new SoyMapData())).isFalse();
  }

  @Test
  public void testLongHandling() {
    // long value will loose precision if converted to double.
    long l = 987654321987654321L;
    SoyMapData smd = new SoyMapData();
    smd.put("long", l);
    assertThat(smd.getLong("long")).isEqualTo(l);

    smd = new SoyMapData("long", l);
    assertThat(smd.getLong("long")).isEqualTo(l);
  }
}
