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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SoyData.
 *
 */
@RunWith(JUnit4.class)
public class SoyDataTest {

  @Test
  public void testCreateFromExistingData() {

    assertThat(SoyData.createFromExistingData(null)).isInstanceOf(NullData.class);
    assertThat(SoyData.createFromExistingData(StringData.forValue("boo")).stringValue())
        .isEqualTo("boo");
    assertThat(SoyData.createFromExistingData("boo").stringValue()).isEqualTo("boo");
    assertThat(SoyData.createFromExistingData(true).booleanValue()).isTrue();
    assertThat(SoyData.createFromExistingData(8).integerValue()).isEqualTo(8);
    assertThat(
            ((SoyMapData) SoyData.createFromExistingData(ImmutableMap.of("boo", "foo")))
                .getString("boo"))
        .isEqualTo("foo");
    assertThat(((SoyListData) SoyData.createFromExistingData(ImmutableList.of("goo"))).getString(0))
        .isEqualTo("goo");
    assertThat(((SoyListData) SoyData.createFromExistingData(ImmutableSet.of("hoo"))).getString(0))
        .isEqualTo("hoo");

    assertThat(SoyData.createFromExistingData(3.14).floatValue()).isEqualTo(3.14);
    assertThat((float) SoyData.createFromExistingData(3.14F).floatValue()).isEqualTo(3.14F);
  }
}
