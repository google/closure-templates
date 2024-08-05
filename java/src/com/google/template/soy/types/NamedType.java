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

import com.google.auto.value.AutoValue;
import com.google.template.soy.soytree.SoyTypeP;

/** A type that is a reference to a Soy `{type}` command. */
@AutoValue
public abstract class NamedType extends SoyType {

  public static NamedType create(String name, String namespace, SoyType type) {
    return new AutoValue_NamedType(name, namespace, type);
  }

  public abstract String getName();

  public abstract String getNamespace();

  public abstract SoyType getType();

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

  @Override
  public SoyType getEffectiveType() {
    return getType().getEffectiveType();
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, UnknownAssignmentPolicy policy) {
    return getEffectiveType().isAssignableFromInternal(srcType, policy);
  }
}
