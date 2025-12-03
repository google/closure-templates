/*
 * Copyright 2024 Google Inc.
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

package com.google.template.soy.jbcsrc.shared;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.template.soy.msgs.restricted.PlaceholderName;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.function.ToIntFunction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MsgDefaultConstantFactoryTest {

  @Test
  public void placeholderOrdering() {
    var ordering =
        MsgDefaultConstantFactory.placeholderOrdering(
            MethodHandles.lookup(), "name", ImmutableList.class, "a", "b", "c", "d");
    assertThat(ordering)
        .isEqualTo(
            ImmutableSetMultimap.of(
                PlaceholderName.create("a"),
                PlaceholderName.create("b"),
                PlaceholderName.create("c"),
                PlaceholderName.create("d")));
  }

  @Test
  public void placeholderOrdering_invalidSize() {
    var e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                MsgDefaultConstantFactory.placeholderOrdering(
                    MethodHandles.lookup(), "name", ImmutableList.class));
    assertThat(e).hasMessageThat().contains("Expected at least one  placeholder pair");
    e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                MsgDefaultConstantFactory.placeholderOrdering(
                    MethodHandles.lookup(), "name", ImmutableList.class, "a"));
    assertThat(e).hasMessageThat().contains("Expected an even number of placeholder pairs");
  }

  @Test
  public void placeholderOrdering_invalidOrdering() {
    var e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                MsgDefaultConstantFactory.placeholderOrdering(
                    MethodHandles.lookup(), "name", ImmutableList.class, "a", "b", "b", "d"));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Expected placeholder PlaceholderName{b} is supposed to come before PlaceholderName{a},"
                + " but it also is configured to come after [PlaceholderName{d}]. Order constraints"
                + " cannot be transitive");
  }

  private static ToIntFunction<String> placeholderIndexFunction(String... names) {
    var fn =
        MsgDefaultConstantFactory.placeholderIndexFunction(
            MethodHandles.lookup(), "name", ImmutableList.class, names);
    return (s) -> fn.applyAsInt(PlaceholderName.create(s));
  }

  @Test
  public void placeholderIndexFunction_noPlaceholders() {
    var e = assertThrows(IllegalArgumentException.class, () -> placeholderIndexFunction());
    assertThat(e).hasMessageThat().contains("No placeholders, should not have been called.");
  }

  @Test
  public void placeholderIndexFunction_mustBeSorted() {
    var e = assertThrows(IllegalArgumentException.class, () -> placeholderIndexFunction("b", "a"));
    assertThat(e).hasMessageThat().contains("Expected names to be sorted.");
  }

  @Test
  public void placeholderIndexFunction_rejectsUnknownPlaceholder() {
    var fn = placeholderIndexFunction("a");
    assertThat(fn.applyAsInt("a")).isEqualTo(0);
    var e = assertThrows(IllegalArgumentException.class, () -> fn.applyAsInt("b"));
    assertThat(e).hasMessageThat().contains("Unknown placeholder: PlaceholderName{b}");
  }

  @Test
  public void placeholderIndexFunction_manySizes() {
    final var allNames =
        new String[] {
          "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r",
          "s", "t", "u", "v", "w", "x", "y", "z"
        };
    for (int i = 1; i < allNames.length; i++) {
      var names = Arrays.copyOf(allNames, i);
      var fn = placeholderIndexFunction(names);
      for (int j = 0; j < i; j++) {
        assertThat(fn.applyAsInt(names[j])).isEqualTo(j);
      }
    }
  }
}
