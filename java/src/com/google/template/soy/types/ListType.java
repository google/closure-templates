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

import com.google.template.soy.soytree.SoyTypeP;

/** Represents the type of a list, a sequential random-access container keyed by integer. */
public final class ListType extends AbstractIterableType {

  /** Special instance used to track empty lists. Only valid with == equality. */
  private static final ListType EMPTY = new ListType(UnknownType.getInstance());

  public static final ListType ANY_LIST = new ListType(AnyType.getInstance());

  public static ListType of(SoyType elementType) {
    return new ListType(elementType);
  }

  public static ListType empty() {
    return EMPTY;
  }

  private ListType(SoyType elementType) {
    super(elementType);
  }

  @Override
  public boolean isEmpty() {
    return this == EMPTY;
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
}
