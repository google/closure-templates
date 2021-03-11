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


/** Represents a map type for generated Soy Java invocation builders. */
public final class MapJavaType extends JavaType {

  public static final CodeGenUtils.Member AS_MAP = CodeGenUtils.castFunction("asMap");
  public static final CodeGenUtils.Member AS_NULLABLE_MAP =
      CodeGenUtils.castFunction("asNullableMap");
  public static final CodeGenUtils.Member AS_NULLABLE_LEGACY_OBJECT_MAP =
      CodeGenUtils.castFunction("asNullableLegacyObjectMap");
  public static final CodeGenUtils.Member AS_LEGACY_OBJECT_MAP =
      CodeGenUtils.castFunction("asLegacyObjectMap");

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
  public String asInlineCast(String variableName, int depth) {
    // Mark as a "soy map" (as opposed to soy records / legacy maps). This allows keys to be
    // non-strings.
    if (shouldMarkAsSoyMap) {
      return (isNullable() ? AS_NULLABLE_MAP : AS_MAP)
          + "("
          + variableName
          + ", "
          + keyType.getAsInlineCastFunction(depth)
          + ", "
          + valueType.getAsInlineCastFunction(depth)
          + ")";
    } else {
      return (isNullable() ? AS_NULLABLE_LEGACY_OBJECT_MAP : AS_LEGACY_OBJECT_MAP)
          + "("
          + variableName
          + ", "
          + valueType.getAsInlineCastFunction(depth)
          + ")";
    }
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
