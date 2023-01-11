/*
 * Copyright 2023 Google Inc.
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

package com.google.template.soy.internal.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TreeStreams}. */
@RunWith(JUnit4.class)
public final class TreeStreamsTest {

  @Test
  public void testBreadthFirst() {
    assertThat(
            bfs(
                1,
                ImmutableMap.<Integer, Iterable<Integer>>builder()
                    .put(1, list(2, 3))
                    .put(2, list(4, 5))
                    .put(3, list(6, 7))
                    .buildOrThrow()))
        .containsExactly(1, 2, 3, 4, 5, 6, 7);
  }

  @Test
  public void testDepthFirst() {
    assertThat(
            dfs(
                1,
                ImmutableMap.<Integer, Iterable<Integer>>builder()
                    .put(1, list(2, 3))
                    .put(2, list(4, 5))
                    .put(3, list(6, 7))
                    .buildOrThrow()))
        .containsExactly(1, 2, 4, 5, 3, 6, 7);
  }

  private static <T> ImmutableList<T> list(T... items) {
    return ImmutableList.copyOf(items);
  }

  private static <T> ImmutableList<T> bfs(T root, Map<T, Iterable<T>> data) {
    return TreeStreams.breadthFirst(root, k -> data.getOrDefault(k, ImmutableList.of()))
        .collect(ImmutableList.toImmutableList());
  }

  private static <T> ImmutableList<T> dfs(T root, Map<T, Iterable<T>> data) {
    return TreeStreams.breadthFirst(root, k -> data.getOrDefault(k, ImmutableList.of()))
        .collect(ImmutableList.toImmutableList());
  }
}
