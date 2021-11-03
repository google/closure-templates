/*
 * Copyright 2021 Google Inc.
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

import com.google.common.collect.Iterables;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Utility method related to generic tree data structures. */
public final class BreadthFirstStream {

  private BreadthFirstStream() {}

  /**
   * Implements a breadth-first search of a tree data structure and returns the result as a Stream.
   *
   * @param <T> the type of node in the tree
   */
  public static <T> Stream<? extends T> of(T root, Function<T, Iterable<? extends T>> successors) {
    Deque<T> generations = new ArrayDeque<>();
    generations.add(root);
    return StreamSupport.stream(
        new AbstractSpliterator<T>(
            // Our Baseclass says to pass MAX_VALUE for unsized streams
            Long.MAX_VALUE,
            // The order is meaningful and every item returned is unique.
            Spliterator.ORDERED | Spliterator.DISTINCT) {
          @Override
          public boolean tryAdvance(Consumer<? super T> action) {
            T next = generations.poll();
            if (next == null) {
              return false;
            }
            Iterables.addAll(generations, successors.apply(next));
            action.accept(next);
            return true;
          }
        },
        /* parallel= */ false);
  }
}
