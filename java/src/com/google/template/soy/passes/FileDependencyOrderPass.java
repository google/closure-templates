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

package com.google.template.soy.passes;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sorts the children of {@link SoyFileSetNode} into dependency order, starting with files that
 * depend on no other files in the file set. Fails on cycles. Stores the ordered list in the pass
 * manager for later use.
 */
@RunAfter(ImportsPass.class)
public class FileDependencyOrderPass implements CompilerFileSetPass {

  private static final SoyErrorKind CYCLE =
      SoyErrorKind.of("Dependency cycle between source files:\n{0}", StyleAllowance.NO_PUNCTUATION);

  private static final ImmutableSet<String> ALLOWED_CYCLE_FILES =
      ImmutableSet.of(
          );

  private static final ImmutableList<String> ALLOWED_CYCLE_DIRS =
      ImmutableList.of(
          );

  private final ErrorReporter errorReporter;
  private final Consumer<ImmutableList<SoyFileNode>> stateSetter;

  public FileDependencyOrderPass(
      ErrorReporter errorReporter, Consumer<ImmutableList<SoyFileNode>> stateSetter) {
    this.errorReporter = errorReporter;
    this.stateSetter = stateSetter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> files, IdGenerator idGenerator) {
    if (files.size() < 2) {
      stateSetter.accept(files);
      return Result.CONTINUE;
    }

    Map<String, SoyFileNode> filesByPath =
        files.stream().collect(toImmutableMap(fn -> fn.getFilePath().path(), fn -> fn));

    TopoSort<SoyFileNode> sorter = new TopoSort<>();
    try {
      ImmutableList<SoyFileNode> sorted =
          sorter.sort(
              files,
              fn ->
                  fn.getImports().stream()
                      .map(ImportNode::getPath)
                      .map(filesByPath::get)
                      .filter(Objects::nonNull)
                      .collect(Collectors.toSet()));
      stateSetter.accept(sorted);
      return Result.CONTINUE;
    } catch (NoSuchElementException e) {
      String cycleText =
          sorter.cyclicKeys.stream().map(fn -> fn.getFilePath().path()).collect(joining("\n--> "));
      if (allowedCyclical(sorter.allNonLeafKeys)) {
        errorReporter.warn(SourceLocation.UNKNOWN, CYCLE, cycleText);
        stateSetter.accept(files);
        return Result.CONTINUE;
      } else {
        errorReporter.report(SourceLocation.UNKNOWN, CYCLE, cycleText);
        return Result.STOP;
      }
    }
  }

  private static boolean allowedCyclical(Iterable<SoyFileNode> nonLeafs) {
    for (SoyFileNode fn : nonLeafs) {
      String path = fn.getFilePath().path();
      if (!ALLOWED_CYCLE_FILES.contains(path)
          && ALLOWED_CYCLE_DIRS.stream().noneMatch(path::startsWith)) {
        return false;
      }
    }
    return true;
  }

  @VisibleForTesting
  static final class TopoSort<T> {

    private Set<T> allNonLeafKeys;
    private List<T> cyclicKeys;

    ImmutableList<T> sort(Iterable<T> unsorted, Function<T, Iterable<T>> successorFunc) {
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
          revDeps.get(to).add(entry.getKey());
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
          this.allNonLeafKeys = ImmutableSet.copyOf(deps.keySet());
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
  }
}
