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

package com.google.template.soy.types.aggregate;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSortedSet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.types.SoyType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

/**
 * Type representing a set of possible alternative types.
 *
 */
public final class UnionType implements SoyType {

  /** Comparator that defines the ordering of types. */
  private static final Comparator<SoyType> MEMBER_ORDER = new Comparator<SoyType>() {
    @Override public int compare(SoyType st1, SoyType st2) {
      return st1.toString().compareTo(st2.toString());
    }
  };


  private final ImmutableSortedSet<SoyType> members;


  private UnionType(Collection<SoyType> members) {
    this.members = flatten(members);
  }


  /**
   * Convenience method for creating unions.
   * @param members Member types of the union.
   * @return Union of those types.
   */
  public static UnionType of(SoyType... members) {
    return new UnionType(Arrays.asList(members));
  }


  /**
   * Create a union from a collection of types.
   * @param members Member types of the union.
   * @return Union of those types.
   */
  public static UnionType of(Collection<SoyType> members) {
    return new UnionType(members);
  }


  @Override public Kind getKind() {
    return Kind.UNION;
  }


  /**
   * Return the set of types contained in this union.
   */
  public Set<SoyType> getMembers() {
    return members;
  }


  @Override public boolean isAssignableFrom(SoyType srcType) {
    if (srcType.getKind() == Kind.UNION) {
      // A union is assignable to a union if every type in the source
      // union is assignable to some type in the destination union.
      UnionType fromUnion = (UnionType) srcType;
      for (SoyType fromMember : fromUnion.members) {
        if (!isAssignableFrom(fromMember)) {
          return false;
        }
      }
      return true;
    } else {
      // A type can be assigned to a union iff it is assignable to at least one
      // member of the union.
      for (SoyType memberType : members) {
        if (memberType.isAssignableFrom(srcType)) {
          return true;
        }
      }
      return false;
    }
  }


  @Override public boolean isInstance(SoyValue value) {
    for (SoyType memberType : members) {
      if (memberType.isInstance(value)) {
        return true;
      }
    }
    return false;
  }


  /** Returns true if the union includes the null type. */
  public boolean isNullable() {
    for (SoyType memberType : members) {
      if (memberType.getKind() == SoyType.Kind.NULL) {
        return true;
      }
    }
    return false;
  }


  @Override public String toString() {
    return Joiner.on('|').join(members);
  }


  @Override public boolean equals(Object other) {
    return other != null &&
        other.getClass() == this.getClass() &&
        ((UnionType) other).members.equals(members);
  }


  @Override public int hashCode() {
    return Objects.hash(this.getClass(), members);
  }


  /**
   * Create a set containing all of the types contained in the input collection.
   * If any of the members of the input collection are unions, add the
   * individual members to the result union, thus "flattening" the union.
   * @param members The input types.
   * @return The set of all types in the input collection.
   */
  private static ImmutableSortedSet<SoyType> flatten(Collection<SoyType> members) {
    ImmutableSortedSet.Builder<SoyType> builder =
        new ImmutableSortedSet.Builder<SoyType>(MEMBER_ORDER);
    for (SoyType type : members) {
      if (type.getKind() == Kind.UNION) {
        builder.addAll(((UnionType) type).members);
      } else {
        builder.add(type);
      }
    }
    return builder.build();
  }
}
