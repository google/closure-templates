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

/** Base class for primitive types. */
abstract class PrimitiveType extends SoyType {

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType) {
    return srcType.getKind() == getKind();
  }

  @Override
  public boolean equals(Object other) {
    if (other == null) {
      return false;
    }
    return other.getClass() == this.getClass();
  }

  @Override
  public int hashCode() {
    // All instances of a given primitive type are considered equal.
    return this.getClass().hashCode();
  }
}
