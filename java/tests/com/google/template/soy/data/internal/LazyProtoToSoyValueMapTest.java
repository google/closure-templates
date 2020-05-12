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
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.ProtoFieldInterpreter;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LazyProtoToSoyValueMapTest {

  @Test
  public void size_empty_isZero() {
    LazyProtoToSoyValueMap<String, Integer> map =
        LazyProtoToSoyValueMap.forMap(
            ImmutableMap.of(),
            ProtoFieldInterpreter.STRING,
            ProtoFieldInterpreter.INT,
            String.class);

    assertThat(map.size()).isEqualTo(0);
  }

  @Test
  public void size_withValues() {
    Map<Integer, String> contents = new HashMap<>();
    contents.put(1, "cat");
    contents.put(34, "dog");
    contents.put(-234, "sheep");

    LazyProtoToSoyValueMap<Integer, String> map =
        LazyProtoToSoyValueMap.forMap(
            contents, ProtoFieldInterpreter.INT, ProtoFieldInterpreter.STRING, Integer.class);

    assertThat(map.size()).isEqualTo(3);
  }

  @Test
  public void keys_empty() {
    LazyProtoToSoyValueMap<String, Integer> map =
        LazyProtoToSoyValueMap.forMap(
            ImmutableMap.of(),
            ProtoFieldInterpreter.STRING,
            ProtoFieldInterpreter.INT,
            String.class);

    assertThat(map.keys()).isEmpty();
  }

  @Test
  public void keys_withValues() {
    LazyProtoToSoyValueMap<String, Integer> map =
        LazyProtoToSoyValueMap.forMap(
            ImmutableMap.of("cats", 4, "dogs", 8),
            ProtoFieldInterpreter.STRING,
            ProtoFieldInterpreter.INT,
            String.class);

    assertThat(map.keys())
        .containsExactly(StringData.forValue("cats"), StringData.forValue("dogs"));
  }

  @Test
  public void containsKey() {
    LazyProtoToSoyValueMap<String, Integer> map =
        LazyProtoToSoyValueMap.forMap(
            ImmutableMap.of("cats", 4, "dogs", 8),
            ProtoFieldInterpreter.STRING,
            ProtoFieldInterpreter.INT,
            String.class);

    assertThat(map.containsKey(StringData.forValue("cats"))).isTrue();
    assertThat(map.containsKey(StringData.forValue("cows"))).isFalse();
  }

  @Test
  public void get_returnsCorrectValue() {
    LazyProtoToSoyValueMap<Integer, String> map =
        LazyProtoToSoyValueMap.forMap(
            ImmutableMap.of(18, "blue", 23, "purple", 19, "green", -72, "yellow"),
            ProtoFieldInterpreter.INT,
            ProtoFieldInterpreter.STRING,
            Integer.class);

    assertThat(map.get(IntegerData.forValue(18))).isEqualTo(StringData.forValue("blue"));
    assertThat(map.get(IntegerData.forValue(-72))).isEqualTo(StringData.forValue("yellow"));
    assertThat(map.getProvider(IntegerData.forValue(19))).isEqualTo(StringData.forValue("green"));

    assertThat(map.get(IntegerData.forValue(289))).isNull();
    assertThat(map.getProvider(IntegerData.forValue(-128))).isNull();
  }

  @Test
  public void get_nullKey() {
    LazyProtoToSoyValueMap<Integer, String> map =
        LazyProtoToSoyValueMap.forMap(
            ImmutableMap.of(18, "purple", 19, "yellow"),
            ProtoFieldInterpreter.INT,
            ProtoFieldInterpreter.STRING,
            Integer.class);

    assertThat(map.get(NullData.INSTANCE)).isNull();
    assertThat(map.getProvider(NullData.INSTANCE)).isNull();
  }

  @Test
  public void asJavaMap_returnsCorrectMap() {
    LazyProtoToSoyValueMap<Boolean, String> map =
        LazyProtoToSoyValueMap.forMap(
            ImmutableMap.of(true, "blue", false, "purple"),
            ProtoFieldInterpreter.BOOL,
            ProtoFieldInterpreter.STRING,
            Boolean.class);

    assertThat(map.asJavaMap())
        .containsExactly(
            BooleanData.FALSE,
            StringData.forValue("purple"),
            BooleanData.TRUE,
            StringData.forValue("blue"));
  }

  @Test
  public void get_cachesValues() {
    LazyProtoToSoyValueMap<Boolean, String> map =
        LazyProtoToSoyValueMap.forMap(
            ImmutableMap.of(true, "blue", false, "purple"),
            ProtoFieldInterpreter.BOOL,
            ProtoFieldInterpreter.STRING,
            Boolean.class);

    SoyValue purple = map.get(BooleanData.FALSE);

    assertThat(map.get(BooleanData.FALSE)).isSameInstanceAs(purple);
    assertThat(map.getProvider(BooleanData.FALSE)).isSameInstanceAs(purple);
  }

  @Test
  public void keys_cachesKey() {
    LazyProtoToSoyValueMap<String, Integer> map =
        LazyProtoToSoyValueMap.forMap(
            ImmutableMap.of("cats", 4, "dogs", 8),
            ProtoFieldInterpreter.STRING,
            ProtoFieldInterpreter.INT,
            String.class);

    ImmutableSet<SoyValue> keys = map.keys();
    ImmutableSet<SoyValue> keysAgain = map.keys();

    for (SoyValue key : keys) {
      assertContainsSameInstance(keysAgain, key);
    }

    ImmutableMap<SoyValue, SoyValue> javaMap = map.asJavaMap();
    for (SoyValue javaMapKey : javaMap.keySet()) {
      assertContainsSameInstance(keys, javaMapKey);
    }
  }

  @Test
  public void get_cachesKey() {
    LazyProtoToSoyValueMap<Long, String> map =
        LazyProtoToSoyValueMap.forMap(
            ImmutableMap.of(234L, "small", 32387L, "bigger", -23472L, "negative"),
            ProtoFieldInterpreter.LONG_AS_INT,
            ProtoFieldInterpreter.STRING,
            Long.class);

    SoyValue key1 = IntegerData.forValue(32387);
    SoyValue key2 = IntegerData.forValue(-23472);

    map.get(key1);
    map.getProvider(key2);

    assertContainsSameInstance(map.keys(), key1);
    assertContainsSameInstance(map.keys(), key2);
  }

  @Test
  public void containsKey_cachesKey() {
    LazyProtoToSoyValueMap<String, String> map =
        LazyProtoToSoyValueMap.forMap(
            ImmutableMap.of("hermione", "granger", "ginny", "weasley"),
            ProtoFieldInterpreter.STRING,
            ProtoFieldInterpreter.STRING,
            String.class);

    SoyValue key = StringData.forValue("hermione");

    map.containsKey(key);

    assertContainsSameInstance(map.keys(), key);
  }

  @Test
  public void asJavaMap_cachesKeys() {
    LazyProtoToSoyValueMap<String, Integer> map =
        LazyProtoToSoyValueMap.forMap(
            ImmutableMap.of("blue", 83927, "yellow", 28347),
            ProtoFieldInterpreter.STRING,
            ProtoFieldInterpreter.INT,
            String.class);

    ImmutableMap<SoyValue, SoyValue> javaMap = map.asJavaMap();

    for (SoyValue key : map.keys()) {
      assertContainsSameInstance(javaMap.keySet(), key);
    }
  }

  /** Asserts that {@code keys} contains {@code key} and that they're the same instance. */
  private static void assertContainsSameInstance(ImmutableSet<SoyValue> keys, SoyValue key) {
    for (SoyValue keyToCheck : keys) {
      if (keyToCheck.equals(key)) {
        assertThat(key).isSameInstanceAs(keyToCheck);
        return;
      }
    }
    fail(String.format("didn't find match for key \"%s\" in keys %s", key, keys));
  }
}
