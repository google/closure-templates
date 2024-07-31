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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.template.soy.soytree.SoyTypeP;
import com.google.template.soy.types.RecordType.Member;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

/** A type that is a reference to a Soy `{type}` command. */
@AutoValue
public abstract class NamedType extends SoyType {

  public static NamedType pointer(String name, String namespace) {
    return new AutoValue_NamedType(name, namespace);
  }

  public static NamedType create(
      String name, String namespace, SoyType type, @Nullable SoyType superType) {
    NamedType namedType = new AutoValue_NamedType(name, namespace);
    namedType.type = Preconditions.checkNotNull(type);
    namedType.superType = superType;
    return namedType;
  }

  private SoyType type;
  private SoyType superType;

  public abstract String getName();

  public abstract String getNamespace();

  public void resolve(SoyType type, @Nullable SoyType superType) {
    Preconditions.checkState(this.type == null);
    this.type = Preconditions.checkNotNull(type);
    this.superType = superType;
  }

  public SoyType getType() {
    Preconditions.checkState(!isPointerOnly());
    return type;
  }

  public SoyType getSuperType() {
    Preconditions.checkState(!isPointerOnly());
    return superType;
  }

  public String getFqn() {
    return getNamespace() + "." + getName();
  }

  @Override
  public Kind getKind() {
    return Kind.NAMED;
  }

  @Override
  public final String toString() {
    return getName();
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.getNamedBuilder().setName(getName()).setNamespace(getNamespace());
  }

  @Override
  public <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public boolean isPointerOnly() {
    return type == null;
  }

  @Memoized
  @Override
  public SoyType getEffectiveType() {
    Preconditions.checkState(!isPointerOnly(), "Never resolved %s", getFqn());

    SoyType thisType = type.getEffectiveType();
    if (superType == null) {
      return thisType;
    }
    SoyType superType = this.superType.getEffectiveType();
    Preconditions.checkState(
        thisType.getKind() == Kind.RECORD && superType.getKind() == Kind.RECORD,
        "Not both record types: %s %s",
        thisType,
        superType);

    Set<String> memberNames = new HashSet<>();
    ImmutableList<Member> members =
        Streams.concat(
                ((RecordType) thisType).getMembers().stream(),
                ((RecordType) superType).getMembers().stream())
            .filter(member -> memberNames.add(member.name()))
            .collect(toImmutableList());
    return RecordType.of(members);
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, UnknownAssignmentPolicy policy) {
    return getEffectiveType().isAssignableFromInternal(srcType, policy);
  }
}
