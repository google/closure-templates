/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.types;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

class SoyTypeGraphUtils {

  private SoyTypeGraphUtils() {}

  /** Borrowed from Guava graph. */
  public interface SuccessorsFunction<N> {
    Iterable<? extends N> successors(N node);
  }

  /** Borrowed from Guava graph. */
  static final class BreadthFirstIterator<N> extends UnmodifiableIterator<N> {
    private final SuccessorsFunction<N> graph;
    private final Queue<N> queue = new ArrayDeque<>();
    private final Set<N> visited = new HashSet<>();

    BreadthFirstIterator(Iterable<? extends N> roots, SuccessorsFunction<N> graph) {
      this.graph = graph;
      for (N root : roots) {
        // add all roots to the queue, skipping duplicates
        if (visited.add(root)) {
          queue.add(root);
        }
      }
    }

    @Override
    public boolean hasNext() {
      return !queue.isEmpty();
    }

    @Override
    public N next() {
      N current = queue.remove();
      for (N neighbor : graph.successors(current)) {
        if (visited.add(neighbor)) {
          queue.add(neighbor);
        }
      }
      return current;
    }
  }

  /** Implementation of SuccessorsFunction that traverses a graph rooted at a SoyType. */
  static class SoyTypeSuccessorsFunction implements SuccessorsFunction<SoyType> {

    private final SoyTypeRegistry typeRegistry;

    public SoyTypeSuccessorsFunction(@Nullable SoyTypeRegistry typeRegistry) {
      this.typeRegistry = typeRegistry;
    }

    @Override
    public Iterable<? extends SoyType> successors(SoyType type) {
      // For any type that contains nested types, return the list of nested types. E.g. the LIST
      // type contains the list element type, the MAP type contains both the key and value types,
      // etc.
      switch (type.getKind()) {
        case UNION:
          return ((UnionType) type).getMembers();

        case LIST:
          return ImmutableList.of(((ListType) type).getElementType());

        case MAP:
        case LEGACY_OBJECT_MAP:
          AbstractMapType mapType = (AbstractMapType) type;
          return ImmutableList.of(mapType.getKeyType(), mapType.getValueType());

        case RECORD:
          return ((RecordType) type)
              .getMembers().stream().map(RecordType.Member::type).collect(Collectors.toList());

        case VE:
          VeType veType = (VeType) type;
          if (typeRegistry != null && veType.getDataType().isPresent()) {
            String protoFqn = veType.getDataType().get();
            SoyType protoType = typeRegistry.getProtoRegistry().getProtoType(protoFqn);
            if (protoType == null) {
              throw new IllegalArgumentException(protoFqn);
            }
            return ImmutableList.of(protoType);
          }
          // fall through
        default:
          return ImmutableList.of();
      }
    }
  }
}
