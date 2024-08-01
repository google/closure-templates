/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.types.ast;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.util.stream.Stream;

/** A union type (eg, a|b). */
@AutoValue
public abstract class UnionTypeNode extends TypeNode {

  public static UnionTypeNode create(Iterable<TypeNode> candidates) {
    ImmutableList<TypeNode> candidateList =
        Streams.stream(candidates)
            .flatMap(
                tn ->
                    tn instanceof UnionTypeNode
                        ? ((UnionTypeNode) tn).candidates().stream()
                        : Stream.of(tn))
            .collect(toImmutableList());
    Preconditions.checkArgument(candidateList.size() > 1);
    return new AutoValue_UnionTypeNode(
        candidateList
            .get(0)
            .sourceLocation()
            .extend(Iterables.getLast(candidateList).sourceLocation()),
        candidateList);
  }

  UnionTypeNode() {}

  public abstract ImmutableList<TypeNode> candidates();

  @Override
  public final String toString() {
    return Joiner.on("|").join(candidates());
  }

  @Override
  public <T> T accept(TypeNodeVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public UnionTypeNode copy() {
    ImmutableList.Builder<TypeNode> newCandidates = ImmutableList.builder();
    for (TypeNode candidate : candidates()) {
      newCandidates.add(candidate.copy());
    }
    UnionTypeNode copy = create(newCandidates.build());
    copy.copyInternal(this);
    return copy;
  }

  @Override
  public Stream<TypeNode> asStreamExpandingUnion() {
    return candidates().stream();
  }
}
