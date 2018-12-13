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
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.SoyTypeP;
import java.util.Objects;

/**
 * Map type - generalized mapping type with key and value type arguments.
 *
 * <p>Note: This map type is designed for working with proto maps or ES6 Maps.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MapType extends AbstractMapType {

  public static final MapType EMPTY_MAP = new MapType(null, null);

  public static final MapType ANY_MAP = new MapType(AnyType.getInstance(), AnyType.getInstance());

  public static final SoyErrorKind BAD_MAP_KEY_TYPE =
      SoyErrorKind.of(
          "''{0}'' is not allowed as a map key type. Allowed map key types: "
              + "bool, int, float, number, string, proto enum.");

  /** The declared type of item keys in this map. */
  private final SoyType keyType;

  /** The declared type of item values in this map. */
  private final SoyType valueType;

  private MapType(SoyType keyType, SoyType valueType) {
    this.keyType = keyType;
    this.valueType = valueType;
  }

  public static MapType of(SoyType keyType, SoyType valueType) {
    Preconditions.checkNotNull(keyType);
    Preconditions.checkNotNull(valueType);
    return new MapType(keyType, valueType);
  }

  // IMPORTANT: if the allowed key types change, make sure to update BAD_MAP_KEY_TYPE above.
  /** Whether the type is permissible as a key in a Soy {@code map} ({@link MapType}). */
  public static boolean isAllowedKeyType(SoyType type) {
    switch (type.getKind()) {
      case BOOL:
      case INT:
      case FLOAT:
      case STRING:
      case PROTO_ENUM:
        return true;
      default:
        return type == SoyTypes.NUMBER_TYPE;
    }
  }

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
  boolean doIsAssignableFromNonUnionType(SoyType srcType) {
    if (srcType.getKind() == Kind.MAP) {
      MapType srcMapType = (MapType) srcType;
      if (srcMapType == EMPTY_MAP) {
        return true;
      } else if (this == EMPTY_MAP) {
        return false;
      }
      // Maps are covariant.
      return keyType.isAssignableFrom(srcMapType.keyType)
          && valueType.isAssignableFrom(srcMapType.valueType);
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

  @Override
  public boolean equals(Object other) {
    if (other != null && other.getClass() == this.getClass()) {
      MapType otherMap = (MapType) other;
      return Objects.equals(otherMap.keyType, keyType)
          && Objects.equals(otherMap.valueType, valueType);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.getClass(), keyType, valueType);
  }
}
