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
import com.google.common.collect.Lists;
import com.google.common.testing.EqualsTester;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SoyListData.
 *
 */
@RunWith(JUnit4.class)
public class SoyListDataTest {

  @Test
  public void testPutRemoveGetSingleKey() {

    SoyListData sld = new SoyListData();

    sld.put("0", StringData.forValue("moomoo"));
    assertThat(((StringData) sld.get("0")).getValue()).isEqualTo("moomoo");

    sld.put("0", (SoyData) null);
    assertThat(sld.length()).isEqualTo(1);
    assertThat(sld.get("0")).isInstanceOf(NullData.class);

    sld.remove("0");
    assertThat(sld.length()).isEqualTo(0);
    assertThat(sld.get("0")).isNull();

    sld.add(IntegerData.forValue(17));
    assertThat(((IntegerData) sld.get(0)).getValue()).isEqualTo(17);

    sld.set(0, BooleanData.FALSE);
    assertThat(((BooleanData) sld.get(0)).getValue()).isFalse();

    sld.set(1, (SoyData) null);
    assertThat(sld.get(1)).isInstanceOf(NullData.class);

    sld.add(true);
    assertThat(sld.getBoolean(2)).isTrue();
    sld.add(8);
    assertThat(sld.getInteger(3)).isEqualTo(8);
    sld.add(3.14);
    assertThat(sld.getFloat(4)).isWithin(0.0).of(3.14);
    sld.add("woohoo");
    assertThat(sld.getString(5)).isEqualTo("woohoo");

    sld.set(6, true);
    assertThat(sld.getBoolean(6)).isTrue();
    sld.set(6, -8);
    assertThat(sld.getInteger(6)).isEqualTo(-8);
    sld.set(7, -3.14);
    assertThat(sld.getFloat(7)).isWithin(0.0).of(-3.14);
    sld.set(7, "boohoo");
    assertThat(sld.getString(7)).isEqualTo("boohoo");

    assertThat(sld.length()).isEqualTo(8);
    sld.remove(2);
    sld.remove(4);
    assertThat(sld.length()).isEqualTo(6);
    assertThat(sld.getBoolean(0)).isFalse();
    assertThat(sld.getInteger(2)).isEqualTo(8);
    assertThat(sld.getInteger(4)).isEqualTo(-8);

    SoyListData sld2 = new SoyListData();
    sld.add(sld2);
    assertThat(sld.getListData(6)).isEqualTo(sld2);

    SoyMapData smd = new SoyMapData();
    sld.set(7, smd);
    assertThat(sld.getMapData(7)).isEqualTo(smd);
  }

  @Test
  public void testPutRemoveGetMultiKey() {

    SoyListData sld = new SoyListData();

    sld.put("0.0", false);
    assertThat(sld.getBoolean("0.0")).isFalse();
    assertThat(sld.getListData("0").getBoolean("0")).isFalse();

    sld.put("0.1.0", 26);
    assertThat(sld.getInteger("0.1.0")).isEqualTo(26);
    assertThat(sld.getListData("0").getInteger("1.0")).isEqualTo(26);
    assertThat(sld.getListData("0.1").getInteger("0")).isEqualTo(26);
    assertThat(sld.getListData("0").getListData("1").getInteger("0")).isEqualTo(26);

    sld.put("0.2.boo", "foo");
    sld.put("0.2.goo", 1.618);
    assertThat(sld.getString("0.2.boo")).isEqualTo("foo");
    assertThat(sld.getMapData("0.2").getFloat("goo")).isWithin(0.0).of(1.618);
  }

  @Test
  public void testConstruction() {

    List<Object> existingList = Lists.<Object>newArrayList(8, null, ImmutableList.of("blah", true));
    SoyListData sld = new SoyListData(existingList);
    sld.put("2.2", 2.71828);
    sld.add("bleh");

    assertThat(sld.getInteger(0)).isEqualTo(8);
    assertThat(sld.get(1)).isInstanceOf(NullData.class);
    assertThat(sld.getString("2.0")).isEqualTo("blah");
    assertThat(sld.getBoolean("2.1")).isTrue();
    assertThat(sld.getFloat("2.2")).isWithin(0.0).of(2.71828);
    assertThat(sld.getString(3)).isEqualTo("bleh");

    sld = new SoyListData(8, null, new SoyListData("blah", true));
    sld.put("2.2", 2.71828);
    sld.add("bleh");

    assertThat(sld.getInteger(0)).isEqualTo(8);
    assertThat(sld.get(1)).isInstanceOf(NullData.class);
    assertThat(sld.getString("2.0")).isEqualTo("blah");
    assertThat(sld.getBoolean("2.1")).isTrue();
    assertThat(sld.getFloat("2.2")).isWithin(0.0).of(2.71828);
    assertThat(sld.getString(3)).isEqualTo("bleh");
  }

  @Test
  public void testErrorDuringConstruction() {

    List<Object> existingList =
        Lists.<Object>newArrayList(8, null, ImmutableList.of(new Object(), "blah", true));

    try {
      new SoyListData(existingList);
      fail();
    } catch (SoyDataException sde) {
      assertThat(sde.getMessage().contains("At data path '[2][0]':")).isTrue();
    }

    existingList.set(2, ImmutableList.of(ImmutableList.of(0, new Object()), "blah", true));

    try {
      new SoyListData(existingList);
      fail();
    } catch (SoyDataException sde) {
      assertThat(sde.getMessage().contains("At data path '[2][0][1]':")).isTrue();
    }
  }

  @Test
  public void testCoercion() {

    SoyListData sld0 = new SoyListData();
    SoyListData sld1 = new SoyListData("boo");

    SoyListData sld2 = new SoyListData(8, null, new SoyListData("blah", true), "bleh");
    sld2.put("2.2", 2.71828);

    assertThat(sld0.coerceToString()).isEqualTo("[]");
    assertThat(sld1.coerceToString()).isEqualTo("[boo]");
    assertThat(sld2.coerceToString()).isEqualTo("[8, null, [blah, true, 2.71828], bleh]");

    assertThat(sld0.coerceToBoolean()).isTrue();
    assertThat(sld1.coerceToBoolean()).isTrue();
    assertThat(sld2.coerceToBoolean()).isTrue();
  }

  @Test
  public void testIsEqualto() {

    SoyListData sld0 = new SoyListData();
    SoyListData sld1 = new SoyListData("boo");

    new EqualsTester().addEqualityGroup(sld0).addEqualityGroup(sld1).testEquals();
    assertThat(sld0.equals(new SoyListData())).isFalse();
  }

  @Test
  public void testLongHandling() {
    // long value will loose precision if converted to double.
    long l = 987654321987654321L;
    SoyListData sld = new SoyListData();
    sld.add(l);
    assertThat(sld.getLong(0)).isEqualTo(l);

    sld = new SoyListData(l);
    assertThat(sld.getLong(0)).isEqualTo(l);
  }
}
