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
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link LegacyObjectMapToMapFunction}. */
@RunWith(JUnit4.class)
public final class LegacyObjectMapToMapFunctionTest {

  private static final LegacyObjectMapToMapFunction LEGACY_OBJECT_MAP_TO_MAP =
      new LegacyObjectMapToMapFunction();
  private static final SoyValueConverter CONVERTER = SoyValueConverter.INSTANCE;

  @Test
  public void computeForJavaSource() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(LEGACY_OBJECT_MAP_TO_MAP);
    SoyLegacyObjectMap legacyObjectMap =
        SoyValueConverterUtility.newDict("x", "y", "z", SoyValueConverterUtility.newDict("xx", 2));
    SoyMapImpl map =
        SoyMapImpl.forProviderMap(
            ImmutableMap.of(
                StringData.forValue("x"), CONVERTER.convert("y"),
                StringData.forValue("z"), SoyValueConverterUtility.newDict("xx", 2)));
    SoyMapImpl convertedMap = (SoyMapImpl) tester.callFunction(legacyObjectMap);
    assertThat(map.get(StringData.forValue("x")))
        .isEqualTo(convertedMap.get(StringData.forValue("x")));
  }
}
