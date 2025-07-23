/*
 * Copyright 2025 Google Inc.
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

/** Array (a mutable list for use in auto externs). */
public final class MutableListType extends ListType {

  /** Special instance used to track empty lists. Only valid with == equality. */
  private static final MutableListType EMPTY = new MutableListType(UnknownType.getInstance());

  public static MutableListType empty() {
    return EMPTY;
  }

  public static MutableListType of(SoyType elementType) {
    return new MutableListType(elementType);
  }

  private MutableListType(SoyType elementType) {
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
  boolean doIsAssignableFromNonUnionType(SoyType srcType, AssignabilityPolicy policy) {
    return srcType instanceof MutableListType
        && super.doIsAssignableFromNonUnionType(srcType, policy);
  }

  @Override
  public String toString() {
    return "mutable_list<" + elementType + ">";
  }
}
