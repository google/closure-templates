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
 * <p>Note: This map type is designed for working with proto maps or ES6 Maps.
 */
public final class MapType extends AbstractMapType {

  /** Special instance used to track empty maps. Only valid with == equality. */
  private static final MapType EMPTY =
      new MapType(UnknownType.getInstance(), UnknownType.getInstance());

  public static final MapType ANY_MAP = new MapType(AnyType.getInstance(), AnyType.getInstance());

  public static MapType empty() {
    return EMPTY;
  }

  public static MapType of(SoyType keyType, SoyType valueType) {
    return new MapType(keyType, valueType);
  }

  /** The declared type of item keys in this map. */
  private final SoyType keyType;

  /** The declared type of item values in this map. */
  private final SoyType valueType;

  private MapType(SoyType keyType, SoyType valueType) {
    this.keyType = Preconditions.checkNotNull(keyType);
    this.valueType = Preconditions.checkNotNull(valueType);
  }

  @Override
  public boolean isEmpty() {
    return this == EMPTY;
  }

  private static final SoyType ALLOWED_KINDS =
      UnionType.of(
          BoolType.getInstance(),
          NumberType.getInstance(),
          StringType.getInstance(),
          GbigintType.getInstance());

  /** Whether the type is permissible as a key in a declared map type literal. */
  // LINT.IfChange(allowed_soy_map_key_types)
  public static boolean isAllowedKeyType(SoyType type) {
    return type.equals(AnyType.getInstance())
        || type.equals(NeverType.getInstance())
        || ALLOWED_KINDS.isAssignableFromLoose(type);
  }

  /** Whether the type is permissible as a key in a map literal. */
  public static boolean isAllowedKeyValueType(SoyType type) {
    return ALLOWED_KINDS.isAssignableFromStrict(type);
  }

  // LINT.ThenChange(../data/SoyMap.java:allowed_soy_map_key_types)

  @Override
  public Kind getKind() {
    return Kind.MAP;
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
    if (srcType.getKind() == Kind.MAP) {
      MapType srcMapType = (MapType) srcType;
      if (srcMapType == EMPTY) {
        return true;
      } else if (this == EMPTY) {
        return false;
      }
      // Maps are covariant.
      return keyType.isAssignableFromInternal(srcMapType.keyType, policy)
          && valueType.isAssignableFromInternal(srcMapType.valueType, policy);
    }
    return false;
  }

  @Override
  public String toString() {
    return "map<" + keyType + "," + valueType + ">";
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.getMapBuilder().setKey(keyType.toProto()).setValue(valueType.toProto());
  }
}
