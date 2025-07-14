/*
 * Copyright 2013 Google Inc.
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

import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.soytree.SoyTypeP;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Predicate;

/** Type representing a set of possible alternative types. */
@AutoValue
public abstract class UnionType extends SoyType {

  /** Comparator that defines the ordering of types. */
  static final Comparator<SoyType> MEMBER_ORDER = comparing(SoyType::toString);

  /**
   * Convenience method for creating unions.
   *
   * @param members Member types of the union.
   * @return Union of those types. If there is exactly one distinct type in members, then this will
   *     not be a UnionType.
   */
  public static SoyType of(SoyType... members) {
    return of(Arrays.asList(members));
  }

  /**
   * Create a union from a collection of types.
   *
   * @param members Member types of the union.
   * @return Union of those types. If there is exactly one distinct type in members, then this will
   *     not be a UnionType.
   */
  public static SoyType of(Iterable<SoyType> members) {
    // sort and flatten the set of types
    ImmutableSortedSet.Builder<SoyType> builder = ImmutableSortedSet.orderedBy(MEMBER_ORDER);
    for (SoyType type : members) {
      // simplify unions containing these types
      if (type.isOfKind(Kind.UNKNOWN) || type.isOfKind(Kind.ANY)) {
        return type;
      }
      if (type.isOfKind(Kind.NEVER)) {
        // `never` is assignable to everything, so we can always just drop from the union.
        continue;
      }
      if (type instanceof UnionType) {
        builder.addAll(((UnionType) type).sortedMembers());
      } else {
        builder.add(type);
      }
    }
    ImmutableSortedSet<SoyType> flattenedMembers = builder.build();
    if (flattenedMembers.isEmpty()) {
      return NeverType.getInstance();
    } else if (flattenedMembers.size() == 1) {
      return Iterables.getOnlyElement(flattenedMembers);
    }
    return new AutoValue_UnionType(flattenedMembers);
  }

  abstract ImmutableSortedSet<SoyType> sortedMembers();

  @Override
  public Kind getKind() {
    return Kind.UNION;
  }

  /** Return the set of types contained in this union. */
  public ImmutableSet<SoyType> getMembers() {
    return sortedMembers();
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, AssignabilityPolicy policy) {
    // A type can be assigned to a union iff it is assignable to at least one
    // member of the union.
    for (SoyType memberType : sortedMembers()) {
      if (memberType.isAssignableFromInternal(srcType, policy)) {
        return true;
      }
    }
    return false;
  }

  /** Returns a Soy type that is equivalent to this with certain members filtered out. */
  public SoyType filter(Predicate<SoyType> filter) {
    ImmutableSortedSet<SoyType> filtered =
        sortedMembers().stream().filter(filter).collect(toImmutableSortedSet(MEMBER_ORDER));
    if (filtered.size() != sortedMembers().size()) {
      return of(filtered);
    }
    return this;
  }

  @Override
  public final String toString() {
    return Joiner.on('|').join(sortedMembers());
  }

  @Override
  protected void doToProto(SoyTypeP.Builder builder) {
    SoyTypeP.UnionTypeP.Builder unionBuilder = builder.getUnionBuilder();
    for (SoyType member : sortedMembers()) {
      unionBuilder.addMember(member.toProto());
    }
  }
}
