/*
 * Copyright 2015 Google Inc.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.NullType;

/** Utility methods for operating on {@link SoyType} instances. */
public final class SoyTypes {
  /** Shared constant for the 'number' type. */
  public static final SoyType NUMBER_TYPE =
      UnionType.of(IntType.getInstance(), FloatType.getInstance());

  private static final ImmutableSet<SoyType.Kind> ALWAYS_COMPARABLE_KINDS =
      Sets.immutableEnumSet(SoyType.Kind.UNKNOWN, SoyType.Kind.ANY, SoyType.Kind.NULL);

  private static final ImmutableSet<SoyType.Kind> BOOLEAN_AND_NUMERIC_PRIMITIVES =
      Sets.immutableEnumSet(
          SoyType.Kind.BOOL, SoyType.Kind.INT, SoyType.Kind.FLOAT, SoyType.Kind.PROTO_ENUM);

  /** Returns true if it is always "safe" to compare the input type to another type. */
  public static boolean isDefiniteComparable(SoyType type) {
    return ALWAYS_COMPARABLE_KINDS.contains(type.getKind());
  }

  /**
   * Returns true if the input type is a numeric primitive type, such as bool, int, float, proto
   * enum, and number.
   */
  public static boolean isNumericPrimitive(SoyType type) {
    SoyType.Kind kind = type.getKind();
    if (BOOLEAN_AND_NUMERIC_PRIMITIVES.contains(kind)) {
      return true;
    }
    if (type.isAssignableFrom(NUMBER_TYPE) || NUMBER_TYPE.isAssignableFrom(type)) {
      return true;
    }
    return false;
  }

  /**
   * Returns true if the input type is a primitive type. This includes bool, int, float, string and
   * all sanitized contents. Two special cases are proto enum and number: these are proto or
   * aggregate type in Soy's type system, but they should really be treated as primitive types.
   */
  public static boolean isDefinitePrimitive(SoyType type) {
    return isNumericPrimitive(type) || type.getKind().isKnownStringOrSanitizedContent();
  }

  public static SoyType removeNull(SoyType type) {
    checkArgument(!NullType.getInstance().equals(type), "Can't remove null from null");
    if (type.getKind() == SoyType.Kind.UNION) {
      return ((UnionType) type).removeNullability();
    }
    return type;
  }

  public static SoyType makeNullable(SoyType type) {
    if (isNullable(type)) {
      return type;
    }
    return UnionType.of(type, NullType.getInstance());
  }

  public static boolean isNullable(SoyType type) {
    return type.equals(NullType.getInstance())
        || (type.getKind() == SoyType.Kind.UNION && ((UnionType) type).isNullable());
  }
}
