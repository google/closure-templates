/*
 * Copyright 2020 Google Inc.
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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.ProtoFieldInterpreter;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.testing.Foo.InnerEnum;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LazyProtoToSoyValueListTest {

  @Test
  public void length_emptyList_isZero() {
    LazyProtoToSoyValueList<String> list =
        LazyProtoToSoyValueList.forList(new ArrayList<>(), ProtoFieldInterpreter.STRING);

    assertThat(list.length()).isEqualTo(0);
  }

  @Test
  public void length_withValues() {
    LazyProtoToSoyValueList<Integer> list =
        LazyProtoToSoyValueList.forList(ImmutableList.of(1, 2, 3), ProtoFieldInterpreter.INT);

    assertThat(list.length()).isEqualTo(3);
  }

  @Test
  public void get_returnsValue() {
    List<String> contents = new ArrayList<>();
    contents.add("hello");
    contents.add("i");
    contents.add("am");
    contents.add("harry");
    contents.add("potter");

    LazyProtoToSoyValueList<String> list =
        LazyProtoToSoyValueList.forList(contents, ProtoFieldInterpreter.STRING);

    assertThat(list.get(0)).isEqualTo(StringData.forValue("hello"));
    assertThat(list.get(1)).isEqualTo(StringData.forValue("i"));
    assertThat(list.get(2)).isEqualTo(StringData.forValue("am"));
    assertThat(list.get(3)).isEqualTo(StringData.forValue("harry"));
    assertThat(list.get(4)).isEqualTo(StringData.forValue("potter"));
  }

  @Test
  public void getProvider_returnsValue() {
    List<Integer> contents = new ArrayList<>();
    contents.add(234);
    contents.add(234);
    contents.add(3453);
    contents.add(9873);
    contents.add(-2392);

    LazyProtoToSoyValueList<Integer> list =
        LazyProtoToSoyValueList.forList(contents, ProtoFieldInterpreter.INT);

    assertThat(list.getProvider(0)).isEqualTo(IntegerData.forValue(234));
    assertThat(list.getProvider(1)).isEqualTo(IntegerData.forValue(234));
    assertThat(list.getProvider(2)).isEqualTo(IntegerData.forValue(3453));
    assertThat(list.getProvider(3)).isEqualTo(IntegerData.forValue(9873));
    assertThat(list.getProvider(4)).isEqualTo(IntegerData.forValue(-2392));
  }

  @Test
  public void asJavaList_returnsCorrectList() {
    LazyProtoToSoyValueList<InnerEnum> list =
        LazyProtoToSoyValueList.forList(
            ImmutableList.of(InnerEnum.ONE, InnerEnum.ONE, InnerEnum.THREE),
            ProtoFieldInterpreter.ENUM_FROM_PROTO);

    assertThat(list.asJavaList())
        .containsExactly(IntegerData.forValue(1), IntegerData.forValue(1), IntegerData.forValue(3))
        .inOrder();
  }

  @Test
  public void asResolvedJavaList_returnsCorrectList() {
    LazyProtoToSoyValueList<Long> list =
        LazyProtoToSoyValueList.forList(
            ImmutableList.of(23L, -129L, 290473L, 29348710L), ProtoFieldInterpreter.LONG_AS_STRING);

    assertThat(list.asResolvedJavaList())
        .containsExactly(
            StringData.forValue("23"),
            StringData.forValue("-129"),
            StringData.forValue("290473"),
            StringData.forValue("29348710"))
        .inOrder();
  }

  @Test
  public void get_outOfBounds_throws() {
    LazyProtoToSoyValueList<Boolean> list =
        LazyProtoToSoyValueList.forList(ImmutableList.of(true, false), ProtoFieldInterpreter.BOOL);

    assertThrows(ArrayIndexOutOfBoundsException.class, () -> list.get(-1));
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> list.get(-234));
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> list.get(3));
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> list.get(563));
  }

  @Test
  public void get_valuesCached() {
    LazyProtoToSoyValueList<String> list =
        LazyProtoToSoyValueList.forList(
            ImmutableList.of("orange", "blue", "red"), ProtoFieldInterpreter.STRING);

    SoyValue orange = list.get(0);

    assertThat(list.get(0)).isSameInstanceAs(orange);
    assertThat(list.getProvider(0)).isSameInstanceAs(orange);
  }

  @Test
  public void asJavaList_valuesCached() {
    LazyProtoToSoyValueList<String> list =
        LazyProtoToSoyValueList.forList(
            ImmutableList.of("orange", "blue", "red"), ProtoFieldInterpreter.STRING);

    SoyValueProvider red = list.getProvider(2);

    ImmutableList<SoyValue> javaList = list.asJavaList();
    ImmutableList<SoyValue> resolvedJavaList = list.asResolvedJavaList();

    assertThat(javaList.get(2)).isSameInstanceAs(red);
    assertThat(list.asResolvedJavaList().get(2)).isSameInstanceAs(red);

    assertThat(list.get(0)).isSameInstanceAs(javaList.get(0));
    assertThat(list.get(1)).isSameInstanceAs(resolvedJavaList.get(1));
  }
}
