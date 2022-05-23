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

package com.google.template.soy.data.restricted;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for CollectionData. */
@RunWith(JUnit4.class)
public class CollectionDataTest {

  @Test
  public void testCreateFromExistingData() {

    assertThat(CollectionData.createFromExistingData(null)).isInstanceOf(NullData.class);
    assertThat(CollectionData.createFromExistingData(StringData.forValue("boo")).stringValue())
        .isEqualTo("boo");
    assertThat(CollectionData.createFromExistingData("boo").stringValue()).isEqualTo("boo");
    assertThat(CollectionData.createFromExistingData(true).booleanValue()).isTrue();
    assertThat(CollectionData.createFromExistingData(8).integerValue()).isEqualTo(8);
    Truth.assertThat(
            ((SoyMapData) CollectionData.createFromExistingData(ImmutableMap.of("boo", "foo")))
                .getString("boo"))
        .isEqualTo("foo");
    Truth.assertThat(
            ((SoyListData) CollectionData.createFromExistingData(ImmutableList.of("goo")))
                .getString(0))
        .isEqualTo("goo");
    assertThat(
            ((SoyListData) CollectionData.createFromExistingData(ImmutableSet.of("hoo")))
                .getString(0))
        .isEqualTo("hoo");

    assertThat(CollectionData.createFromExistingData(3.14).floatValue()).isEqualTo(3.14);
    assertThat((float) CollectionData.createFromExistingData(3.14F).floatValue()).isEqualTo(3.14F);
  }
}
