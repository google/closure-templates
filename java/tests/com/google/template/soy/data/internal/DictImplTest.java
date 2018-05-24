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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import java.util.HashMap;
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

    SoyValue val1 = DictImpl.forProviderMap(ImmutableMap.of(), RuntimeMapTypeTracker.Type.UNKNOWN);
    assertThat(val1.coerceToBoolean()).isTrue(); // DictImpl is always truthy.
    assertThat(val1.coerceToString()).isEqualTo("{}");
    SoyValue val2 = DictImpl.forProviderMap(ImmutableMap.of(), RuntimeMapTypeTracker.Type.UNKNOWN);
    assertThat(val1.equals(val2)).isFalse(); // DictImpl uses object identity.

    SoyValue val3 =
        DictImpl.forProviderMap(
            ImmutableMap.<String, SoyValue>of(
                "foo", FloatData.forValue(3.14), "too", BooleanData.TRUE),
            RuntimeMapTypeTracker.Type.UNKNOWN);
    assertThat(val3.coerceToBoolean()).isTrue();
    assertThat(val3.coerceToString()).isEqualTo("{foo: 3.14, too: true}");
  }

  @Test
  public void testDictMethods() {

    SoyDict dict =
        DictImpl.forProviderMap(
            ImmutableMap.of("boo", StringData.forValue("aaah"), "foo", FloatData.forValue(3.14)),
            RuntimeMapTypeTracker.Type.UNKNOWN);
    Map<String, ? extends SoyValueProvider> m1 = dict.asJavaStringMap();
    assertThat(m1).hasSize(2);
    assertThat(m1.get("boo").resolve().stringValue()).isEqualTo("aaah");
    Map<String, ? extends SoyValue> m2 = dict.asResolvedJavaStringMap();
    assertThat(m2).hasSize(2);
    assertThat(m2.get("foo").floatValue()).isEqualTo(3.14);
  }

  @Test
  public void testRecordMethods() {

    Map<String, SoyValueProvider> providerMap = new HashMap<>();
    SoyDict dict = DictImpl.forProviderMap(providerMap, RuntimeMapTypeTracker.Type.UNKNOWN);
    assertThat(dict.hasField("boo")).isFalse();
    assertThat(dict.getField("boo")).isNull();
    assertThat(dict.getFieldProvider("boo")).isNull();
    providerMap.put("boo", StringData.forValue("blah"));
    assertThat(dict.hasField("boo")).isTrue();
    assertThat(dict.getField("boo").stringValue()).isEqualTo("blah");
    assertThat(dict.getFieldProvider("boo").resolve().stringValue()).isEqualTo("blah");
    providerMap.remove("boo");
    assertThat(dict.hasField("boo")).isFalse();
    assertThat(dict.getField("boo")).isNull();
    assertThat(dict.getFieldProvider("boo")).isNull();

    providerMap.put("foo", FloatData.forValue(3.14));
    providerMap.put("too", BooleanData.TRUE);
    assertThat(dict.hasField("foo")).isTrue();
    assertThat(dict.getField("foo").floatValue()).isEqualTo(3.14);
    assertThat(dict.getField("too").booleanValue()).isTrue();
  }

  @Test
  public void testLegacyObjectMapMethods() {
    StringData boo = StringData.forValue("boo");
    Map<String, SoyValueProvider> providerMap = new HashMap<>();
    DictImpl dict = DictImpl.forProviderMap(providerMap, RuntimeMapTypeTracker.Type.UNKNOWN);
    assertThat(dict.getItemCnt()).isEqualTo(0);
    assertThat(dict.getItemKeys()).isEmpty();
    assertThat(dict.hasItem(boo)).isFalse();
    assertThat(dict.getItem(boo)).isNull();
    assertThat(dict.getItemProvider(boo)).isNull();
    providerMap.put("boo", IntegerData.forValue(111));
    assertThat(dict.getItemCnt()).isEqualTo(1);
    assertThat(dict.getItemKeys()).hasSize(1);
    assertThat(Iterables.getOnlyElement(dict.getItemKeys()).stringValue()).isEqualTo("boo");
    providerMap.put("foo", IntegerData.forValue(222));
    providerMap.put("goo", IntegerData.forValue(333));
    assertThat(dict.getItemCnt()).isEqualTo(3);
    assertThat(dict.getItemKeys()).hasSize(3);
    assertThat(dict.hasItem(boo)).isTrue();
    assertThat(dict.getItem(boo).integerValue()).isEqualTo(111);
    assertThat(dict.getItemProvider(boo).resolve().integerValue()).isEqualTo(111);
    providerMap.remove("foo");
    assertThat(dict.getItemCnt()).isEqualTo(2);
    providerMap.remove("boo");
    providerMap.remove("goo");
    assertThat(dict.getItemCnt()).isEqualTo(0);
    assertThat(dict.getItemKeys()).isEmpty();
    assertThat(dict.hasItem(boo)).isFalse();
    assertThat(dict.getItem(boo)).isNull();
    assertThat(dict.getItemProvider(boo)).isNull();
  }

  @Test
  public void testMapMethods() {
    StringData boo = StringData.forValue("boo");
    Map<String, SoyValueProvider> providerMap = new HashMap<>();
    DictImpl dict = DictImpl.forProviderMap(providerMap, RuntimeMapTypeTracker.Type.UNKNOWN);
    assertThat(dict.size()).isEqualTo(0);
    assertThat(dict.keys()).isEmpty();
    assertThat(dict.containsKey(boo)).isFalse();
    assertThat(dict.get(boo)).isNull();
    assertThat(dict.getProvider(boo)).isNull();
    providerMap.put("boo", IntegerData.forValue(111));
    assertThat(dict.size()).isEqualTo(1);
    assertThat(dict.keys()).hasSize(1);
    assertThat(Iterables.getOnlyElement(dict.keys()).stringValue()).isEqualTo("boo");
    providerMap.put("foo", IntegerData.forValue(222));
    providerMap.put("goo", IntegerData.forValue(333));
    assertThat(dict.size()).isEqualTo(3);
    assertThat(dict.keys()).hasSize(3);
    assertThat(dict.containsKey(boo)).isTrue();
    assertThat(dict.get(boo).integerValue()).isEqualTo(111);
    assertThat(dict.getProvider(boo).resolve().integerValue()).isEqualTo(111);
    providerMap.remove("foo");
    assertThat(dict.size()).isEqualTo(2);
    providerMap.remove("boo");
    providerMap.remove("goo");
    assertThat(dict.size()).isEqualTo(0);
    assertThat(dict.keys()).isEmpty();
    assertThat(dict.containsKey(boo)).isFalse();
    assertThat(dict.get(boo)).isNull();
    assertThat(dict.getProvider(boo)).isNull();
  }

  @Test
  public void testMapInteroperability() {
    Map<String, SoyValueProvider> providerMap = new HashMap<>();
    DictImpl dict = DictImpl.forProviderMap(providerMap, RuntimeMapTypeTracker.Type.UNKNOWN);
    assertThat(dict.size()).isEqualTo(0);
    try {
      dict.getItemCnt();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Expected a value of type `legacy_object_map`, got `map`. "
                  + "These two map types are not interoperable. "
                  + "Use `mapToLegacyObjectMap()` to convert this object to a legacy_object_map.");
    }
    // Recreate the map that resets the internal state.
    dict = DictImpl.forProviderMap(providerMap, RuntimeMapTypeTracker.Type.UNKNOWN);
    assertThat(dict.getItemCnt()).isEqualTo(0);
    try {
      dict.keys();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Expected a value of type `map`, got `legacy_object_map`. "
                  + "These two map types are not interoperable. "
                  + "Use `legacyObjectMapToMap()` to convert this object to a map.");
    }
  }
}
