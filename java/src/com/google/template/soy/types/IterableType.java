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

/**
 * Represents the type of a list, a sequential random-access container keyed by integer.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public abstract class IterableType extends SoyType {

  protected final SoyType elementType;

  protected IterableType(SoyType elementType) {
    this.elementType = elementType;
  }

  public SoyType getElementType() {
    return elementType;
  }

  public boolean isEmpty() {
    return elementType == null;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, UnknownAssignmentPolicy policy) {
    if (srcType instanceof IterableType) {
      IterableType srcListType = (IterableType) srcType;
      if (srcListType.isEmpty()) {
        return true;
      } else if (isEmpty()) {
        return false;
      }
      return elementType.isAssignableFromInternal(srcListType.elementType, policy);
    }
    return false;
  }
}
