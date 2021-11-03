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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Basic opographical sort utility. */
public final class TopoSort<T> {

  private ImmutableList<T> cyclicKeys;

  /**
   * Topologically sorts {@code unsorted}.
   *
   * <p>Self edges are not allowed. All successors must appear in the input to sort.
   *
   * @throws NoSuchElementException if a cycle is encountered
   */
  public ImmutableList<T> sort(Iterable<T> unsorted, Function<T, Iterable<T>> successorFunc) {
    cyclicKeys = null;

    Map<T, Set<T>> deps = new LinkedHashMap<>();
    Map<T, Set<T>> revDeps = new HashMap<>();
    unsorted.forEach(
        fn -> {
          Set<T> successors = Sets.newHashSet(successorFunc.apply(fn));
          Preconditions.checkArgument(!successors.contains(fn), "No self edges please");
          deps.put(fn, successors);
          revDeps.put(fn, new HashSet<>());
        });

    // Tracking reverse deps avoids worst case O(n^2) sort.
    for (Map.Entry<T, Set<T>> entry : deps.entrySet()) {
      for (T to : entry.getValue()) {
        Set<T> rev = revDeps.get(to);
        Preconditions.checkNotNull(rev, "Successor %s of %s not in input", to, entry.getKey());
        rev.add(entry.getKey());
      }
    }

    List<T> reordered = new ArrayList<>(deps.size());

    Set<T> cleared = ImmutableSet.of();
    Set<T> candidateLeaves =
        deps.keySet().stream().filter(k -> deps.get(k).isEmpty()).collect(Collectors.toSet());

    // Topological sort.
    while (!deps.isEmpty()) {
      Set<T> nextCleared = new HashSet<>();
      Set<T> nextCandidateLeaves = new HashSet<>();

      for (T possibleLeaf : candidateLeaves) {
        Set<T> leafDeps = deps.get(possibleLeaf);
        leafDeps.removeAll(cleared);
        if (leafDeps.isEmpty()) {
          reordered.add(possibleLeaf);
          deps.remove(possibleLeaf);

          nextCleared.add(possibleLeaf);
          nextCandidateLeaves.addAll(revDeps.get(possibleLeaf));
        }
      }

      if (nextCleared.isEmpty()) {
        this.cyclicKeys = findCycle(deps);
        throw new NoSuchElementException("cycle");
      }

      candidateLeaves = nextCandidateLeaves;
      cleared = nextCleared;
    }

    return ImmutableList.copyOf(reordered);
  }

  private static <T> ImmutableList<T> findCycle(Map<T, Set<T>> remaining) {
    // Because all remaining nodes are not leaves and all have at least one dep, every one must
    // be part of at least one cycle. So it doesn't matter where we start searching from.
    T root = remaining.keySet().iterator().next();

    Set<T> visited = new HashSet<>();
    Deque<T> chain = new ArrayDeque<>();

    if (!dfs(visited, chain, root, remaining::get)) {
      throw new IllegalArgumentException("no cycle found");
    }

    // Chain now contains a traversal with a cycle in it. But the cycle might not be the entire
    // chain so let's trim it to just the cycle.
    T lastItem = chain.getLast();
    while (!chain.getFirst().equals(lastItem)) {
      chain.removeFirst();
    }
    Preconditions.checkArgument(chain.size() > 2, "Short chain %s", chain);
    return ImmutableList.copyOf(chain);
  }

  private static <T> boolean dfs(
      Set<T> visited, Deque<T> chain, T node, Function<T, Iterable<T>> successors) {
    chain.addLast(node);

    if (!visited.add(node)) {
      return true;
    }

    for (T t : successors.apply(node)) {
      if (dfs(visited, chain, t, successors)) {
        return true;
      }
    }

    chain.removeLast();
    return false;
  }

  /**
   * If {@link #sort} threw a {@link NoSuchElementException} then this method returns one of the
   * illegal cycles in the graph.
   */
  public ImmutableList<T> getCyclicKeys() {
    return Preconditions.checkNotNull(cyclicKeys, "Topo sort did not fail.");
  }
}
