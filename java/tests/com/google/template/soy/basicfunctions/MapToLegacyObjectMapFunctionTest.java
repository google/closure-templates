/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.basicfunctions;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link MapToLegacyObjectMapFunction}. */
@RunWith(JUnit4.class)
public final class MapToLegacyObjectMapFunctionTest {

  private static final MapToLegacyObjectMapFunction MAP_TO_LEGACY_OBJECT_MAP =
      new MapToLegacyObjectMapFunction();
  private static final SoyValueConverter CONVERTER = SoyValueConverter.INSTANCE;

  @Test
  public void computeForJava() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(MAP_TO_LEGACY_OBJECT_MAP);
    SoyMapImpl map =
        SoyMapImpl.forProviderMap(
            ImmutableMap.of(
                IntegerData.forValue(42), CONVERTER.convert("y"),
                StringData.forValue("z"), SoyValueConverterUtility.newDict("xx", 2)));
    SoyDict expectedMap =
        SoyValueConverterUtility.newDict("42", "y", "z", SoyValueConverterUtility.newDict("xx", 2));
    SoyDict convertedMap = (SoyDict) tester.callFunction(map);
    // Keys are coerced to strings in the legacy object map.
    assertThat(convertedMap.getItem(StringData.forValue("42")))
        .isEqualTo(expectedMap.getItem(StringData.forValue("42")));
  }
}
