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

package com.google.template.soy.types.ast;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceLocation;
import java.util.stream.Stream;

/** An intersection type (eg, a&b). */
@AutoValue
public abstract class IntersectionTypeNode extends TypeNode {

  public static IntersectionTypeNode create(Iterable<TypeNode> candidates) {
    ImmutableList<TypeNode> candidateList =
        stream(candidates)
            .flatMap(
                tn ->
                    tn instanceof IntersectionTypeNode
                        ? ((IntersectionTypeNode) tn).candidates().stream()
                        : Stream.of(tn))
            .collect(toImmutableList());
    Preconditions.checkArgument(candidateList.size() > 1);
    return new AutoValue_IntersectionTypeNode(candidateList);
  }

  IntersectionTypeNode() {}

  public abstract ImmutableList<TypeNode> candidates();

  @Memoized
  @Override
  public SourceLocation sourceLocation() {
    return candidates()
        .get(0)
        .sourceLocation()
        .extend(Iterables.getLast(candidates()).sourceLocation());
  }

  @Override
  public boolean childNeedsGrouping(TypeNode child) {
    return child instanceof FunctionTypeNode
        || child instanceof TemplateTypeNode
        || child instanceof UnionTypeNode;
  }

  @Override
  public final String toString() {
    return candidates().stream()
        .map(c -> childNeedsGrouping(c) ? "(" + c + ")" : c.toString())
        .collect(joining("&"));
  }

  @Override
  public IntersectionTypeNode copy() {
    IntersectionTypeNode copy =
        create(candidates().stream().map(TypeNode::copy).collect(toImmutableList()));
    copy.copyInternal(this);
    return copy;
  }

  @Override
  public Stream<TypeNode> asStreamExpandingUnion() {
    return candidates().stream();
  }
}
