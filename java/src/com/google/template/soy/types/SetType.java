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

import com.google.common.base.Preconditions;
import com.google.template.soy.soytree.SoyTypeP;
import java.util.Objects;

/**
 * Represents the type of a list, a sequential random-access container keyed by integer.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class SetType extends IterableType {

  public static final SetType EMPTY_SET = new SetType(null);
  public static final SetType ANY_SET = new SetType(AnyType.getInstance());

  private SetType(SoyType elementType) {
    super(elementType);
  }

  public static SetType of(SoyType elementType) {
    Preconditions.checkNotNull(elementType);
    return new SetType(elementType);
  }

  @Override
  public Kind getKind() {
    return Kind.SET;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, UnknownAssignmentPolicy policy) {
    return srcType.getKind() == Kind.SET && super.doIsAssignableFromNonUnionType(srcType, policy);
  }

  @Override
  public String toString() {
    return "set<" + elementType + ">";
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.setSetElement(elementType.toProto());
  }

  @Override
  public boolean equals(Object other) {
    return other != null
        && this.getClass() == other.getClass()
        && Objects.equals(((SetType) other).elementType, elementType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.getClass(), elementType);
  }

  @Override
  public <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
