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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for ListImpl.
 *
 */
@RunWith(JUnit4.class)
public class ListImplTest {

  private static final ImmutableList<SoyValueProvider> EMPTY = ImmutableList.of();

  @Test
  public void testSoyValueMethods() {

    SoyValue val1 = ListImpl.forProviderList(EMPTY);
    assertThat(val1.coerceToBoolean()).isTrue(); // ListImpl is always truthy.
    assertThat(val1.coerceToString()).isEqualTo("[]");
    SoyValue val2 = ListImpl.forProviderList(EMPTY);
    assertThat(val1.equals(val2)).isFalse(); // ListImpl uses object identity.

    SoyValue val3 =
        ListImpl.forProviderList(ImmutableList.of(IntegerData.forValue(111), BooleanData.TRUE));
    assertThat(val3.coerceToBoolean()).isTrue();
    assertThat(val3.coerceToString()).isEqualTo("[111, true]");
  }

  @Test
  public void testListMethods() {

    StringData BLAH_0 = StringData.forValue("blah");
    FloatData PI = FloatData.forValue(3.14);
    SoyValue BLAH_2 = StringData.forValue("blah");

    SoyList list = ListImpl.forProviderList(EMPTY);
    assertThat(list.length()).isEqualTo(0);
    assertThat(list.asJavaList()).isEmpty();
    assertThat(list.asResolvedJavaList()).isEmpty();
    assertThat(list.get(0)).isNull();
    assertThat(list.getProvider(0)).isNull();
    list = ListImpl.forProviderList(ImmutableList.of(BLAH_0, PI, BLAH_2));
    // At this point, list should be [BLAH_0, PI, BLAH_2].
    assertThat(list.length()).isEqualTo(3);
    assertThat(list.asJavaList()).isEqualTo(ImmutableList.of(BLAH_0, PI, BLAH_2));
    assertThat(list.asResolvedJavaList()).isEqualTo(ImmutableList.of(BLAH_0, PI, BLAH_2));
    assertThat(list.get(0)).isSameInstanceAs(BLAH_0);
    assertThat(list.get(0)).isNotSameInstanceAs(BLAH_2);
    assertThat(list.get(0)).isEqualTo(BLAH_2); // not same, but they compare equal
    assertThat(list.getProvider(1).resolve().floatValue()).isEqualTo(3.14);
  }

  @Test
  public void testMapMethods() {

    SoyList list =
        ListImpl.forProviderList(ImmutableList.of(FloatData.forValue(3.14), BooleanData.TRUE));
    assertThat(list.getItemCnt()).isEqualTo(2);
    assertThat(list.getItemKeys()).containsExactly(IntegerData.ZERO, IntegerData.ONE).inOrder();
    assertThat(list.hasItem(IntegerData.ONE)).isTrue();
    assertThat(list.getItem(IntegerData.ZERO).floatValue()).isEqualTo(3.14);
    assertThat(list.getItemProvider(IntegerData.ONE).resolve().booleanValue()).isTrue();

    // For backwards compatibility: accept string arguments.
    assertThat(list.hasItem(StringData.forValue("0"))).isTrue();
    assertThat(list.hasItem(StringData.forValue("-99"))).isFalse();
    assertThat(list.hasItem(StringData.forValue("99"))).isFalse();
    assertThat(list.getItem(StringData.forValue("0")).floatValue()).isEqualTo(3.14);
    assertThat(list.getItemProvider(StringData.forValue("1")).resolve().booleanValue()).isTrue();
  }
}
