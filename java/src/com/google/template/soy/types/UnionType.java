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

import static java.util.Comparator.comparing;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.soytree.SoyTypeP;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Type representing a set of possible alternative types.
 *
 */
public final class UnionType extends SoyType {

  /** Comparator that defines the ordering of types. */
  private static final Comparator<SoyType> MEMBER_ORDER = comparing(SoyType::toString);

  private final ImmutableSortedSet<SoyType> members;

  private UnionType(Iterable<? extends SoyType> members) {
    this.members = ImmutableSortedSet.copyOf(MEMBER_ORDER, members);
    Preconditions.checkArgument(this.members.size() != 1);
    for (SoyType type : this.members) {
      if (type.getKind() == Kind.UNKNOWN) {
        throw new IllegalArgumentException(
            "Cannot create unions containing unknown: " + this.members);
      }
    }
  }

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
  public static SoyType of(Collection<SoyType> members) {
    // sort and flatten the set of types
    ImmutableSortedSet.Builder<SoyType> builder = ImmutableSortedSet.orderedBy(MEMBER_ORDER);
    for (SoyType type : members) {
      // simplify unions containing these types
      if (type.getKind() == Kind.UNKNOWN
          || type.getKind() == Kind.ANY) {
        return type;
      }
      if (type.getKind() == Kind.UNION) {
        builder.addAll(((UnionType) type).members);
      } else {
        builder.add(type);
      }
    }
    ImmutableSet<SoyType> flattenedMembers = builder.build();
    if (flattenedMembers.size() == 1) {
      return Iterables.getOnlyElement(flattenedMembers);
    }
    return new UnionType(flattenedMembers);
  }

  @Override
  public Kind getKind() {
    return Kind.UNION;
  }

  /** Return the set of types contained in this union. */
  public ImmutableSet<SoyType> getMembers() {
    return members;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, UnknownAssignmentPolicy unknownPolicy) {
    // A type can be assigned to a union iff it is assignable to at least one
    // member of the union.
    for (SoyType memberType : members) {
      if (memberType.isAssignableFromInternal(srcType, unknownPolicy)) {
        return true;
      }
    }
    return false;
  }

  /** Returns true if the union includes the null type. */
  public boolean isNullable() {
    return members.stream().anyMatch(t -> t.getKind() == SoyType.Kind.NULL);
  }

  /** Returns a Soy type that is equivalent to this one but with 'null' removed. */
  public SoyType removeNullability() {
    if (isNullable()) {
      return of(
          members.stream()
              .filter(t -> t.getKind() != SoyType.Kind.NULL)
              .collect(Collectors.toList()));
    }
    return this;
  }

  @Override
  public String toString() {
    return Joiner.on('|').join(members);
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    SoyTypeP.UnionTypeP.Builder unionBuilder = builder.getUnionBuilder();
    for (SoyType member : members) {
      unionBuilder.addMember(member.toProto());
    }
  }

  @Override
  public boolean equals(Object other) {
    return other != null
        && other.getClass() == this.getClass()
        && ((UnionType) other).members.equals(members);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.getClass(), members);
  }

  @Override
  public <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
