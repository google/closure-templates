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
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.types.SoyType;
import java.util.Objects;

/**
 * Map type - generalized mapping type with key and value type arguments.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MapType implements SoyType {

  public static final MapType EMPTY_MAP = new MapType(null, null);

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

  @Override
  public Kind getKind() {
    return Kind.MAP;
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
  public boolean isInstance(SoyValue value) {
    return value instanceof SoyMap;
  }

  @Override
  public String toString() {
    return "map<" + keyType + "," + valueType + ">";
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
