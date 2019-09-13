/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.invocationbuilders.javatypes;

import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.TO_IMMUTABLE_MAP;

/** Represents a map type for generated Soy Java invocation builders. */
public final class MapJavaType extends JavaType {

  private final JavaType keyType; // The type of the map's keys.
  private final JavaType valueType; // The type of the map's values.

  private final boolean shouldMarkAsSoyMap;

  public MapJavaType(JavaType keyType, JavaType valueType, boolean shouldMarkAsSoyMap) {
    this(keyType, valueType, shouldMarkAsSoyMap, /* isNullable= */ false);
  }

  public MapJavaType(
      JavaType keyType, JavaType valueType, boolean shouldMarkAsSoyMap, boolean isNullable) {
    super(isNullable);
    this.keyType = keyType;
    this.valueType = valueType;
    this.shouldMarkAsSoyMap = shouldMarkAsSoyMap;
  }

  @Override
  boolean isPrimitive() {
    return false;
  }

  @Override
  public String toJavaTypeString() {
    return "java.util.Map<"
        + keyType.asGenericsTypeArgumentString()
        + ", "
        + valueType.asGenericsTypeArgumentString()
        + ">";
  }

  @Override
  String asGenericsTypeArgumentString() {
    return "? extends " + toJavaTypeString();
  }

  @Override
  public String asInlineCast(String variableName) {
    String mapRef = variableName;

    // If the map's key and/or value type is long or double, convert "? extends Number" -> Long or
    // Double.
    if (keyType instanceof JavaNumberSubtype || valueType instanceof JavaNumberSubtype) {
      CodeGenUtils.Member arg1 =
          keyType instanceof JavaNumberSubtype
              ? ((JavaNumberSubtype) keyType).getMapperFunction()
              : null;
      CodeGenUtils.Member arg2 =
          valueType instanceof JavaNumberSubtype
              ? ((JavaNumberSubtype) valueType).getMapperFunction()
              : null;
      mapRef = CodeGenUtils.AS_MAP_OF_NUMBERS + "(" + mapRef + ", " + arg1 + ", " + arg2 + ")";
    } else {
      // Otherwise just make an immutable copy of the map.
      mapRef = TO_IMMUTABLE_MAP + "(" + mapRef + ")";
    }

    if (shouldMarkAsSoyMap) {
      // Mark as a "soy map" (as opposed to soy records / legacy maps). This allows keys to be
      // non-strings.
      mapRef = CodeGenUtils.MARK_AS_SOY_MAP + "(" + mapRef + ")";
    }

    return mapRef;
  }

  /** Returns the map's key type. */
  JavaType getKeyType() {
    return keyType;
  }

  /** Returns the map's value type. */
  JavaType getValueType() {
    return valueType;
  }

  @Override
  public MapJavaType asNullable() {
    return new MapJavaType(keyType, valueType, shouldMarkAsSoyMap, /* isNullable= */ true);
  }
}
