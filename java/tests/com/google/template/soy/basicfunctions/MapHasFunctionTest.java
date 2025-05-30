/*
 * Copyright 2025 Google Inc.
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
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for MapHasFunction. */
@RunWith(JUnit4.class)
public class MapHasFunctionTest {

  @Test
  public void testComputeForJavaSource() {
    MapHasFunction mapHasFunction = new MapHasFunction();
    SoyMapImpl map =
        SoyMapImpl.forProviderMap(
            ImmutableMap.of(
                StringData.forValue("hello"), IntegerData.forValue(1),
                StringData.forValue("world"), IntegerData.forValue(2)));
    SoyJavaSourceFunctionTester factory = new SoyJavaSourceFunctionTester(mapHasFunction);
    assertThat(factory.callMethod(map, StringData.forValue("hello"))).isEqualTo(true);
    assertThat(factory.callMethod(map, StringData.forValue("world"))).isEqualTo(true);
    assertThat(factory.callMethod(map, StringData.forValue("lorem"))).isEqualTo(false);
  }
}
