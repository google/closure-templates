/*
 * Copyright 2018 Google Inc.
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
import static com.google.template.soy.data.SoyMaps.asSoyMap;
import static com.google.template.soy.data.SoyMaps.isMapOrLegacyObjectMap;
import static com.google.template.soy.data.SoyMaps.legacyObjectMapToMap;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.restricted.StringData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SoyMaps}. */
@RunWith(JUnit4.class)
public class SoyMapsTest {

  @Test
  public void testIsMapOrLegacyObjectMap() {
    assertThat(isMapOrLegacyObjectMap(SoyMapImpl.forProviderMap(ImmutableMap.of()))).isTrue();
    assertThat(
            isMapOrLegacyObjectMap(
                DictImpl.forProviderMap(ImmutableMap.of(), RuntimeMapTypeTracker.Type.UNKNOWN)))
        .isTrue();
    assertThat(isMapOrLegacyObjectMap(StringData.EMPTY_STRING)).isFalse();
    assertThat(isMapOrLegacyObjectMap(ParamStore.EMPTY_INSTANCE)).isFalse();
  }

  @Test
  public void testAsSoyMap() {
    SoyMap soyMap = SoyMapImpl.forProviderMap(ImmutableMap.of());
    assertThat(asSoyMap(soyMap)).isEqualTo(soyMap);

    SoyMap dictMap = DictImpl.forProviderMap(ImmutableMap.of(), RuntimeMapTypeTracker.Type.MAP);
    assertThat(asSoyMap(dictMap)).isEqualTo(dictMap);
  }

  @Test
  public void testAsSoyMapThrows() {
    try {
      asSoyMap(StringData.EMPTY_STRING);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testAsSoyMapLegacy() {
    DictImpl legacyMap =
        DictImpl.forProviderMap(
            ImmutableMap.of(
                "first", StringData.forValue("second"), "third", StringData.forValue("fourth")),
            RuntimeMapTypeTracker.Type.LEGACY_OBJECT_MAP_OR_RECORD);

    SoyMap map = asSoyMap(legacyMap);

    assertThat(map.size()).isEqualTo(2);
    assertThat(map.getProvider(StringData.forValue("first")).resolve())
        .isEqualTo(StringData.forValue("second"));
    assertThat(map.getProvider(StringData.forValue("third")).resolve())
        .isEqualTo(StringData.forValue("fourth"));
  }

  @Test
  public void testLegacyObjectMapToMap() {
    SoyLegacyObjectMap legacyMap =
        DictImpl.forProviderMap(
            ImmutableMap.of(
                "hello", StringData.forValue("goodbye"), "yes", StringData.forValue("no")),
            RuntimeMapTypeTracker.Type.LEGACY_OBJECT_MAP_OR_RECORD);

    SoyMap map = legacyObjectMapToMap(legacyMap);

    assertThat(map.keys())
        .containsExactly(StringData.forValue("hello"), StringData.forValue("yes"));
    assertThat(map.get(StringData.forValue("hello")).stringValue()).isEqualTo("goodbye");
    assertThat(map.get(StringData.forValue("yes")).stringValue()).isEqualTo("no");
  }
}
