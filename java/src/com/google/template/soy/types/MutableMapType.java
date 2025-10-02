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

/**
 * Mutable map type for use in auto externs.
 *
 * <p>Note: This map type is designed for working with proto maps or ES6 Maps.
 */
public final class MutableMapType extends MapType {

  public static final MutableMapType ANY_MAP =
      new MutableMapType(AnyType.getInstance(), AnyType.getInstance());

  public static MutableMapType of(SoyType keyType, SoyType valueType) {
    return new MutableMapType(keyType, valueType);
  }

  private MutableMapType(SoyType keyType, SoyType valueType) {
    super(keyType, valueType);
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, AssignabilityPolicy policy) {
    return srcType instanceof MutableMapType
        && super.doIsAssignableFromNonUnionType(srcType, policy);
  }

  @Override
  public String toString() {
    return "mutable_" + super.toString();
  }
}
