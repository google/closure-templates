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

/**
 * Map type - generalized mapping type with key and value type arguments.
 *
 * <p>Note: This map type does not interoperate with proto maps or ES6 Maps. We are introducing a
 * second map type to handle these cases. We intend to migrate everyone to the new map type and
 * eventually delete LegacyObjectMapType. See b/69046114.
 */
public final class LegacyObjectMapType extends AbstractMapType {

  public static final LegacyObjectMapType ANY_MAP =
      new LegacyObjectMapType(AnyType.getInstance(), AnyType.getInstance());

  public static LegacyObjectMapType of(SoyType keyType, SoyType valueType) {
    return new LegacyObjectMapType(keyType, valueType);
  }

  /** The declared type of item keys in this map. */
  private final SoyType keyType;

  /** The declared type of item values in this map. */
  private final SoyType valueType;

  private LegacyObjectMapType(SoyType keyType, SoyType valueType) {
    this.keyType = Preconditions.checkNotNull(keyType);
    this.valueType = Preconditions.checkNotNull(valueType);
  }

  @Override
  public Kind getKind() {
    return Kind.LEGACY_OBJECT_MAP;
  }

  @Override
  public SoyType getKeyType() {
    return keyType;
  }

  @Override
  public SoyType getValueType() {
    return valueType;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, AssignabilityPolicy policy) {
    if (srcType.getKind() == Kind.LEGACY_OBJECT_MAP) {
      LegacyObjectMapType srcMapType = (LegacyObjectMapType) srcType;
      // Maps are covariant.
      return keyType.isAssignableFromInternal(srcMapType.keyType, policy)
          && valueType.isAssignableFromInternal(srcMapType.valueType, policy);
    }
    return false;
  }

  @Override
  public String toString() {
    return "legacy_object_map<" + keyType + "," + valueType + ">";
  }

  @Override
  protected void doToProto(SoyTypeP.Builder builder) {
    builder.getLegacyObjectMapBuilder().setKey(keyType.toProto()).setValue(valueType.toProto());
  }
}
