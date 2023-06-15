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

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.template.soy.soytree.SoyTypeP;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Dict type - classic dictionary type with string keys. Only works with field (dot) access.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class RecordType extends SoyType {

  /** The {name, type} pair that is a record member. */
  @AutoValue
  public abstract static class Member {
    public abstract String name();

    public abstract boolean optional();

    public abstract SoyType declaredType();

    /** Returns the member type, but made nullable if the member is optional. */
    @Memoized
    public SoyType checkedType() {
      return optional() ? SoyTypes.makeNullable(declaredType()) : declaredType();
    }
  }

  public static Member memberOf(String name, boolean optional, SoyType type) {
    return new AutoValue_RecordType_Member(name, optional, type);
  }

  private final ImmutableList<Member> members;
  private final ImmutableMap<String, SoyType> memberIndex;

  private RecordType(Iterable<Member> members) {
    this.members = ImmutableList.copyOf(members);
    this.memberIndex =
        Streams.stream(members)
            .collect(ImmutableMap.toImmutableMap(Member::name, Member::checkedType));
  }

  /**
   * This method is problematic in that it doesn't indicate to callers that the iterator order of
   * the members map matters. Prefer {@link #of(Iterable)}.
   */
  public static RecordType of(ImmutableMap<String, ? extends SoyType> members) {
    Preconditions.checkArgument(!(members instanceof NavigableMap)); // Insertion-order only, please
    return new RecordType(
        members.entrySet().stream()
            .map(e -> memberOf(e.getKey(), false, e.getValue()))
            .collect(Collectors.toList()));
  }

  public static RecordType of(Iterable<Member> members) {
    return new RecordType(members);
  }

  @Override
  public Kind getKind() {
    return Kind.RECORD;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, UnknownAssignmentPolicy policy) {
    if (srcType.getKind() == Kind.RECORD) {
      RecordType srcRecord = (RecordType) srcType;
      // The source record must have at least all of the members in the dest
      // record.
      for (Member mine : members) {
        SoyType theirType = srcRecord.getMemberType(mine.name());
        if (theirType == null) {
          if (!mine.optional()) {
            return false;
          }
        } else if (!mine.checkedType().isAssignableFromInternal(theirType, policy)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public ImmutableList<Member> getMembers() {
    return members;
  }

  public SoyType getMemberType(String fieldName) {
    return memberIndex.get(fieldName);
  }

  public Iterable<String> getMemberNames() {
    return members.stream().map(Member::name).collect(Collectors.toList());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    boolean first = true;
    for (Member member : members) {
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      sb.append(member.name());
      if (member.optional()) {
        sb.append("?");
      }
      sb.append(": ");
      sb.append(member.declaredType());
    }
    sb.append("]");
    return sb.toString();
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    SoyTypeP.RecordTypeP.Builder recordBuilder = builder.getRecordBuilder();
    for (Member member : members) {
      recordBuilder.addMembers(
          SoyTypeP.RecordMemberP.newBuilder()
              .setName(member.name())
              .setOptional(member.optional())
              .setType(member.declaredType().toProto()));
    }
  }

  @Override
  public boolean equals(Object other) {
    return other != null
        && other.getClass() == this.getClass()
        && ((RecordType) other).members.equals(members);
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
