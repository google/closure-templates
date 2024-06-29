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

import com.google.common.base.Preconditions;
import com.google.template.soy.soytree.SoyTypeP;
import java.util.Objects;

/**
 * Represents the type of a list, a sequential random-access container keyed by integer.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class ListType extends AbstractIterableType {

  // TODO(lukes): see if this can be replaced with list<?>
  public static final ListType EMPTY_LIST = new ListType(null);
  public static final ListType ANY_LIST = new ListType(AnyType.getInstance());

  private ListType(SoyType elementType) {
    super(elementType);
  }

  public static ListType of(SoyType elementType) {
    Preconditions.checkNotNull(elementType);
    return new ListType(elementType);
  }

  @Override
  public Kind getKind() {
    return Kind.LIST;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, UnknownAssignmentPolicy policy) {
    return srcType.getKind() == Kind.LIST && super.doIsAssignableFromNonUnionType(srcType, policy);
  }

  @Override
  public String toString() {
    return "list<" + elementType + ">";
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.setListElement(elementType.toProto());
  }

  @Override
  public boolean equals(Object other) {
    return other != null
        && this.getClass() == other.getClass()
        && Objects.equals(((ListType) other).elementType, elementType);
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
