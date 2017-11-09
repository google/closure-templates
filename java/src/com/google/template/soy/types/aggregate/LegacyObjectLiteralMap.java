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

package com.google.template.soy.types.aggregate;

import com.google.common.base.Preconditions;
import com.google.template.soy.types.SoyType;
import java.util.Objects;

/**
 * Map type - generalized mapping type with key and value type arguments.
 *
 * <p>Note: This map type does not interoperate with proto maps or ES6 Maps. We are introducing a
 * second map type to handle these cases. We intend to migrate everyone to the new map type and
 * eventually delete LegacyObjectLiteralMap. See b/69046114.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class LegacyObjectLiteralMap implements SoyType {

  public static final LegacyObjectLiteralMap EMPTY_MAP = new LegacyObjectLiteralMap(null, null);

  /** The declared type of item keys in this map. */
  private final SoyType keyType;

  /** The declared type of item values in this map. */
  private final SoyType valueType;

  private LegacyObjectLiteralMap(SoyType keyType, SoyType valueType) {
    this.keyType = keyType;
    this.valueType = valueType;
  }

  public static LegacyObjectLiteralMap of(SoyType keyType, SoyType valueType) {
    Preconditions.checkNotNull(keyType);
    Preconditions.checkNotNull(valueType);
    return new LegacyObjectLiteralMap(keyType, valueType);
  }

  @Override
  public Kind getKind() {
    return Kind.LEGACY_OBJECT_LITERAL_MAP;
  }

  /** Returns the type for keys of this map. */
  public SoyType getKeyType() {
    return keyType;
  }

  /** Returns the type for values in this map. */
  public SoyType getValueType() {
    return valueType;
  }

  @Override
  public boolean isAssignableFrom(SoyType srcType) {
    if (srcType.getKind() == Kind.LEGACY_OBJECT_LITERAL_MAP) {
      LegacyObjectLiteralMap srcMapType = (LegacyObjectLiteralMap) srcType;
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
    // TODO(b/69046843): string representation should be old_map
    return "map<" + keyType + "," + valueType + ">";
  }

  @Override
  public boolean equals(Object other) {
    if (other != null && other.getClass() == this.getClass()) {
      LegacyObjectLiteralMap otherMap = (LegacyObjectLiteralMap) other;
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
