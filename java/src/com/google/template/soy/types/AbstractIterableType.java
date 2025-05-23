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
import java.util.Objects;

/** Represents the type of a list, a sequential random-access container keyed by integer. */
public abstract class AbstractIterableType extends SoyType {

  protected final SoyType elementType;

  protected AbstractIterableType(SoyType elementType) {
    this.elementType = Preconditions.checkNotNull(elementType);
  }

  public SoyType getElementType() {
    return elementType;
  }

  public abstract boolean isEmpty();

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, AssignabilityPolicy policy) {
    // Handle the special empty types.
    if (this == srcType) {
      return true;
    }
    if (srcType instanceof AbstractIterableType) {
      AbstractIterableType srcListType = (AbstractIterableType) srcType;
      if (srcListType.isEmpty()) {
        return true;
      } else if (isEmpty()) {
        return false;
      }
      return elementType.isAssignableFromInternal(srcListType.elementType, policy);
    }
    return false;
  }

  @Override
  public final boolean equals(Object other) {
    return other != null
        && this.getClass() == other.getClass()
        && isEmpty() == ((AbstractIterableType) other).isEmpty()
        && Objects.equals(((AbstractIterableType) other).elementType, elementType);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(this.getClass(), elementType, isEmpty());
  }
}
