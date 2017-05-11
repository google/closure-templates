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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.ErrorType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.NullType;
import java.util.Collection;

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

  public static boolean isNumericOrUnknown(SoyType type) {
    return type.getKind() == SoyType.Kind.UNKNOWN || NUMBER_TYPE.isAssignableFrom(type);
  }

  /**
   * Compute the most specific type that is assignable from both t0 and t1.
   *
   * @param typeRegistry Type registry.
   * @param t0 A type.
   * @param t1 Another type.
   * @return A type that is assignable from both t0 and t1.
   */
  public static SoyType computeLowestCommonType(
      SoyTypeRegistry typeRegistry, SoyType t0, SoyType t1) {
    if (t0 == ErrorType.getInstance() || t1 == ErrorType.getInstance()) {
      return ErrorType.getInstance();
    }
    if (t0.isAssignableFrom(t1)) {
      return t0;
    } else if (t1.isAssignableFrom(t0)) {
      return t1;
    } else {
      // TODO: At some point we should just give up and use 'any'.
      // Probably this should happen if the types have no relation with
      // each other.
      return typeRegistry.getOrCreateUnionType(t0, t1);
    }
  }

  /**
   * Compute the most specific type that is assignable from all types within a collection.
   *
   * @param typeRegistry Type registry.
   * @param types List of types.
   * @return A type that is assignable from all of the listed types.
   */
  public static SoyType computeLowestCommonType(
      SoyTypeRegistry typeRegistry, Collection<SoyType> types) {
    SoyType result = null;
    for (SoyType type : types) {
      result = (result == null) ? type : computeLowestCommonType(typeRegistry, result, type);
    }
    return result;
  }

  /**
   * Compute the most specific type that is assignable from both t0 and t1, taking into account
   * arithmetic promotions - that is, converting int to float if needed.
   *
   * @param t0 A type.
   * @param t1 Another type.
   * @return A type that is assignable from both t0 and t1 or absent if the types are not arithmetic
   *     meaning a subtype of 'number' or unknown.
   */
  public static Optional<SoyType> computeLowestCommonTypeArithmetic(SoyType t0, SoyType t1) {
    // If either of the types is an error type, return the error type
    if (t0 == ErrorType.getInstance() || t1 == ErrorType.getInstance()) {
      return Optional.<SoyType>of(ErrorType.getInstance());
    }
    // If either of the types isn't numeric or unknown, then this isn't valid for an arithmetic
    // operation.
    if (!isNumericOrUnknown(t0) || !isNumericOrUnknown(t1)) {
      return Optional.absent();
    }

    // Note: everything is assignable to unknown and itself.  So the first two conditions take care
    // of all cases but a mix of float and int.
    if (t0.isAssignableFrom(t1)) {
      return Optional.of(t0);
    } else if (t1.isAssignableFrom(t0)) {
      return Optional.of(t1);
    } else {
      // If we get here then we know that we have a mix of float and int.  In this case arithmetic
      // ops always 'upgrade' to float.  So just return that.
      return Optional.<SoyType>of(FloatType.getInstance());
    }
  }
}
