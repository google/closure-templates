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

import com.google.template.soy.base.internal.IndentedLinesBuilder;
import java.util.Optional;

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
  public String appendRunTimeOperations(IndentedLinesBuilder ilb, String variableName) {
    String mapRef = super.appendRunTimeOperations(ilb, variableName);

    // If the map's key and/or value type is long or double, convert "? extends Number" -> Long or
    // Double.
    if (keyType instanceof JavaNumberSubtype || valueType instanceof JavaNumberSubtype) {
      mapRef = appendMapUsingLongOrDoubleInsteadOfNumber(ilb, variableName);
    } else {
      // Otherwise just make an immutable copy of the map.
      mapRef = "ImmutableMap.copyOf(" + mapRef + ")";
    }

    if (shouldMarkAsSoyMap) {
      // Mark as a "soy map" (as opposed to soy records / legacy maps). This allows keys to be
      // non-strings.
      ilb.appendLine("Object soyMap = SoyValueConverter.markAsSoyMap(" + mapRef + ");");
      return "soyMap";
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

  /**
   * Given a map variable name, appends code to coerce a map with key or value type "? extends
   * Number" to use types Long or Double, if we know that the types should be more specific.
   *
   * <p>For example, if this map's keys are of type {@link JavaTypeEnum.STRING} and its values are
   * of type {@link JavaTypeEnum.LONG}, this would convert a Map<String, ? extends Number> to an
   * {@code ImmutableMap<String, Long>}.
   */
  private String appendMapUsingLongOrDoubleInsteadOfNumber(
      IndentedLinesBuilder ilb, String mapVariableName) {

    ilb.appendLine(
        "ImmutableMap<"
            + maybeGetBoxedNumberSubtype(keyType).orElse(keyType.asGenericsTypeArgumentString())
            + ", "
            + maybeGetBoxedNumberSubtype(valueType).orElse(valueType.asGenericsTypeArgumentString())
            + "> stronglyTypedMap = ");
    ilb.increaseIndent(2);
    ilb.appendLine(mapVariableName + ".entrySet().stream()");
    ilb.increaseIndent(2);
    ilb.appendLine(".collect(");
    ilb.increaseIndent(2);
    ilb.appendLine(
        "toImmutableMap(e -> "
            + maybeTransformNumberToStricterType(keyType, "e.getKey()").orElse("e.getKey()")
            + ", e -> "
            + maybeTransformNumberToStricterType(valueType, "e.getValue()").orElse("e.getValue()")
            + "));");
    ilb.decreaseIndent(6);
    return "stronglyTypedMap";
  }

  /**
   * If the given type is a {@link JavaNumberSubtype}, returns the corresponding boxed type name
   * (e.g. "Long" or "Double" ).
   */
  private static Optional<String> maybeGetBoxedNumberSubtype(JavaType t) {
    if (t instanceof JavaNumberSubtype) {
      return Optional.of(((JavaNumberSubtype) t).getBoxedTypeNameString());
    }

    return Optional.empty();
  }

  /**
   * If the given type is a {@link JavaNumberSubtype}, returns a generated code string to coerce a
   * {@link Number} reference to a stricter subtype (e.g. Long or Double). For example, this might
   * return "myNumVar.longValue()".
   */
  private static Optional<String> maybeTransformNumberToStricterType(
      JavaType t, String numberRefInGenCode) {

    if (t instanceof JavaNumberSubtype) {
      return Optional.of(
          ((JavaNumberSubtype) t).transformNumberTypeToStricterSubtype(numberRefInGenCode));
    }

    return Optional.empty();
  }

  @Override
  public MapJavaType asNullable() {
    return new MapJavaType(keyType, valueType, shouldMarkAsSoyMap, /* isNullable= */ true);
  }
}
