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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Utility method related to generic tree data structures. */
public final class TreeStreams {

  private TreeStreams() {}

  /**
   * Implements an ancestor search of a tree data structure and returns the result as a Stream.
   *
   * @param <T> the type of node in the tree
   */
  public static <T> Stream<? extends T> ancestor(T root, Function<T, ? extends T> ancestor) {
    return StreamSupport.stream(
        new AbstractSpliterator<>(
            // Our Baseclass says to pass MAX_VALUE for unsized streams
            Long.MAX_VALUE,
            // The order is meaningful and every item returned is unique.
            Spliterator.ORDERED | Spliterator.DISTINCT) {

          private T next = root;

          @Override
          public boolean tryAdvance(Consumer<? super T> action) {
            if (next == null) {
              return false;
            }
            action.accept(next);
            next = ancestor.apply(next);
            return true;
          }
        },
        /* parallel= */ false);
  }

  /**
   * Implements a breadth-first search of a tree data structure and returns the result as a Stream.
   *
   * @param <T> the type of node in the tree
   */
  public static <T> Stream<? extends T> breadthFirst(
      T root, Function<T, Iterable<? extends T>> successors) {
    Deque<T> queue = new ArrayDeque<>();
    queue.add(root);
    return StreamSupport.stream(
        new AbstractSpliterator<>(
            // Our Baseclass says to pass MAX_VALUE for unsized streams
            Long.MAX_VALUE,
            // The order is meaningful and every item returned is unique.
            Spliterator.ORDERED | Spliterator.DISTINCT) {
          @Override
          public boolean tryAdvance(Consumer<? super T> action) {
            T next = queue.poll();
            if (next == null) {
              return false;
            }
            Iterables.addAll(queue, successors.apply(next));
            action.accept(next);
            return true;
          }
        },
        /* parallel= */ false);
  }

  /**
   * Implements a depth-first search of a tree data structure and returns the result as a Stream.
   *
   * @param <T> the type of node in the tree
   */
  public static <T> Stream<? extends T> depthFirst(
      T root, Function<T, Iterable<? extends T>> successors) {
    Deque<T> stack = new ArrayDeque<>();
    stack.add(root);
    return StreamSupport.stream(
        new AbstractSpliterator<>(
            // Our Baseclass says to pass MAX_VALUE for unsized streams
            Long.MAX_VALUE,
            // The order is meaningful and every item returned is unique.
            Spliterator.ORDERED | Spliterator.DISTINCT) {
          @Override
          public boolean tryAdvance(Consumer<? super T> action) {
            T next = stack.poll();
            if (next == null) {
              return false;
            }

            Iterable<? extends T> children = successors.apply(next);
            List<? extends T> reverseOrder =
                children instanceof List
                    ? Lists.reverse((List<? extends T>) children)
                    : ImmutableList.copyOf(children).reverse();
            for (T child : reverseOrder) {
              stack.push(child);
            }

            action.accept(next);
            return true;
          }
        },
        /* parallel= */ false);
  }

  /**
   * Creates a lazy stream from the {@code source} stream, optionally collating consecutive items
   * and merging them into a single item in the resulting stream.
   *
   * @param accept predicate that is passed two consecutive items and returns whether they should be
   *     collated together
   * @param merger function that merges collated items into a single item
   */
  public static <T> Stream<T> collateAndMerge(
      Stream<? extends T> source, BiPredicate<T, T> accept, Function<List<T>, T> merger) {
    PeekingIterator<T> i = Iterators.peekingIterator(source.iterator());
    return StreamSupport.stream(
        new AbstractSpliterator<>(
            // Our Baseclass says to pass MAX_VALUE for unsized streams
            Long.MAX_VALUE,
            // The order is meaningful.
            Spliterator.ORDERED) {
          @Override
          public boolean tryAdvance(Consumer<? super T> action) {
            if (!i.hasNext()) {
              return false;
            }
            T next = i.next();
            List<T> merged = null;
            while (i.hasNext() && accept.test(next, i.peek())) {
              if (merged == null) {
                merged = new ArrayList<>();
                merged.add(next);
              }
              next = i.next();
              merged.add(next);
            }
            if (merged != null) {
              action.accept(merger.apply(merged));
            } else {
              action.accept(next);
            }
            return true;
          }
        },
        /* parallel= */ false);
  }
}
