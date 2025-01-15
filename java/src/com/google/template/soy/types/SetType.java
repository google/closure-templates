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

import com.google.template.soy.soytree.SoyTypeP;

/** Represents the type of a list, a sequential random-access container keyed by integer. */
public final class SetType extends AbstractIterableType {

  /** Special instance used to track empty sets. Only valid with == equality. */
  private static final SetType EMPTY = new SetType(UnknownType.getInstance());

  public static final SetType ANY_SET = new SetType(AnyType.getInstance());

  public static SetType empty() {
    return EMPTY;
  }

  public static SetType of(SoyType elementType) {
    return new SetType(elementType);
  }

  private SetType(SoyType elementType) {
    super(elementType);
  }

  @Override
  public boolean isEmpty() {
    return this == EMPTY;
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
  public <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
