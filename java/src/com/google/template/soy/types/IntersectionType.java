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

package com.google.template.soy.types;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.soytree.SoyTypeP;
import com.google.template.soy.types.RecordType.Member;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Type representing a set of intersecting types. In Soy the only types that may intersect are
 * record types.
 */
public final class IntersectionType extends SoyType {

  private final ImmutableSortedSet<SoyType> members;

  private IntersectionType(Iterable<? extends SoyType> members) {
    this.members = ImmutableSortedSet.copyOf(UnionType.MEMBER_ORDER, members);
    Preconditions.checkArgument(this.members.size() != 1);
    for (SoyType type : this.members) {
      if (type.getKind() == Kind.UNKNOWN) {
        throw new IllegalArgumentException(
            "Cannot create unions containing unknown: " + this.members);
      }
    }
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
    ImmutableSortedSet.Builder<SoyType> builder =
        ImmutableSortedSet.orderedBy(UnionType.MEMBER_ORDER);
    for (SoyType type : members) {
      if (type.getKind() == Kind.INTERSECTION) {
        builder.addAll(((IntersectionType) type).members);
      } else {
        builder.add(type);
      }
    }
    ImmutableSet<SoyType> flattenedMembers = builder.build();
    if (flattenedMembers.isEmpty()) {
      return NeverType.getInstance();
    } else if (flattenedMembers.size() == 1) {
      return Iterables.getOnlyElement(flattenedMembers);
    }
    return new IntersectionType(flattenedMembers);
  }

  @Override
  public Kind getKind() {
    return Kind.INTERSECTION;
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
      if (!memberType.isAssignableFromInternal(srcType, unknownPolicy)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public SoyType getEffectiveType() {
    ArrayListMultimap<String, Member> allMembers = ArrayListMultimap.create();
    Deque<SoyType> stack = new ArrayDeque<>(members);
    while (!stack.isEmpty()) {
      SoyType member = stack.removeLast().getEffectiveType();
      if (member.getKind() == Kind.INTERSECTION) {
        stack.addAll(((IntersectionType) member).members);
      } else if (member.getKind() == Kind.RECORD) {
        ((RecordType) member).getMembers().forEach(m -> allMembers.put(m.name(), m));
      } else {
        return NeverType.getInstance();
      }
    }

    List<Member> mergedMembers = new ArrayList<>();
    for (Collection<Member> value : allMembers.asMap().values()) {
      List<Member> list = (List<Member>) value;
      Member merged;
      if (value.size() == 1) {
        merged = list.get(0);
      } else {
        SoyType mergedType = null;
        for (Member member : value) {
          if (mergedType == null) {
            mergedType = member.checkedType();
          } else if (mergedType.isAssignableFromStrict(member.checkedType())) {
            // continue
          } else if (member.checkedType().isAssignableFromStrict(mergedType)) {
            mergedType = member.checkedType();
          } else {
            mergedType = NeverType.getInstance();
            break;
          }
        }
        boolean optional = SoyTypes.isUndefinable(mergedType);
        if (optional) {
          mergedType = SoyTypes.removeUndefined(mergedType);
        }
        merged = RecordType.memberOf(list.get(0).name(), optional, mergedType);
      }
      mergedMembers.add(merged);
    }
    return RecordType.of(mergedMembers);
  }

  @Override
  public String toString() {
    return Joiner.on('&').join(members);
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    SoyTypeP.IntersectionTypeP.Builder typeBuilder = builder.getIntersectionBuilder();
    for (SoyType member : members) {
      typeBuilder.addMember(member.toProto());
    }
  }

  @Override
  public boolean equals(Object other) {
    return other != null
        && other.getClass() == this.getClass()
        && ((IntersectionType) other).members.equals(members);
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
