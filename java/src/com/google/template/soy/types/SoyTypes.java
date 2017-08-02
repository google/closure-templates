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
import com.google.template.soy.types.primitive.StringType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

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

  /**
   * If the type is nullable, makes it non-nullable.
   *
   * <p>If the type is the null type, then it returns the null type.
   */
  public static SoyType tryRemoveNull(SoyType soyType) {
    if (soyType == NullType.getInstance()) {
      return NullType.getInstance();
    }
    return removeNull(soyType);
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

  /**
   * Helper method used by {@link getSoyTypeForBinaryOperator} for handling {@code UnionType}
   * instances.
   */
  @Nullable
  private static SoyType getSoyTypeFromUnionForBinaryOperator(
      UnionType t0, SoyType t1, boolean isNullable, SoyTypeBinaryOperator operator) {
    List<SoyType> subTypes = new ArrayList<>();
    for (SoyType unionMember : t0.getMembers()) {
      SoyType result = getSoyTypeForBinaryOperator(unionMember, t1, operator);
      if (result == null) {
        return null;
      }
      subTypes.add(result);
    }
    SoyType result = UnionType.of(subTypes);
    return isNullable ? makeNullable(result) : result;
  }

  /**
   * Returns the {@code SoyType} that should be the result of a binary operator.
   *
   * <p>This method is mostly for handling {@code UnionType} instances. For primitive soy types, the
   * logic should be defined in the {@code SoyTypeBinaryOperator}. Recursion is used for collecting
   * all possible combinations if any of the operands is {@code UnionType}. This method will return
   * null if any of the combinations is incompatible (the operator returns null).
   */
  @Nullable
  public static SoyType getSoyTypeForBinaryOperator(
      SoyType t0, SoyType t1, SoyTypeBinaryOperator operator) {
    // If both types are nullable, we will make the result nullable as well.
    // If only one of these input types is nullable, we don't. For example, {int} and {int|null}
    // probably should return {int} instead of {int|null}.
    boolean isNullable = isNullable(t0) && isNullable(t1);
    // TODO(b/64098780): Make arithmetic operations on nullable type consistent.
    // For now, we remove nulltype from the union type.
    SoyType left = tryRemoveNull(t0);
    SoyType right = tryRemoveNull(t1);
    if (left.getKind() == SoyType.Kind.UNION) {
      return getSoyTypeFromUnionForBinaryOperator((UnionType) left, right, isNullable, operator);
    }
    if (right.getKind() == SoyType.Kind.UNION) {
      // When we calculate the return type of a binary operator, it should always be commutative so
      // the order should not matter.
      return getSoyTypeFromUnionForBinaryOperator((UnionType) right, left, isNullable, operator);
    }
    SoyType result = operator.resolve(left, right);
    if (result == null) {
      return null;
    }
    return isNullable ? makeNullable(result) : result;
  }

  /**
   * A type resolver interface that can be passed into getSoyTypeForBinaryOperator method. Note that
   * the implementation of this resolver does not need to handle union types. The logic for union
   * type should be handled by the callers that take this resolver as an argument.
   */
  public interface SoyTypeBinaryOperator {
    SoyType resolve(SoyType left, SoyType right);
  }

  /**
   * Type resolver for plus operators.
   *
   * <p>A plus operator can be an arithmetic operator or a string concat operator.
   *
   * <ul>
   *   <li>If both operands are numbers, returns the number type calculated by {@link
   *       computeLowestCommonTypeArithmetic}.
   *   <li>If any of the operands is disallowed, returns null that indicates a compilation error.
   *   <li>If any of the operands is string type, returns string type.
   *   <li>Otherwise, returns null that indicates a compilation error.
   * </ul>
   */
  public static final class SoyTypePlusOperator implements SoyTypeBinaryOperator {
    /**
     * Returns true for SoyTypes that are not allowed to be operands of plus operators. Note that a
     * plus operator can be an arithmetic operator or a string concat operator.
     */
    private boolean isIllegalOperandForPlusOps(SoyType type) {
      return type.getKind().isIllegalOperandForBinaryOps()
          && !type.getKind().isKnownStringOrSanitizedContent();
    }

    @Override
    @Nullable
    public SoyType resolve(SoyType left, SoyType right) {
      Optional<SoyType> arithmeticType = SoyTypes.computeLowestCommonTypeArithmetic(left, right);
      SoyType stringType = StringType.getInstance();
      if (arithmeticType.isPresent()) {
        return arithmeticType.get();
      } else if (isIllegalOperandForPlusOps(left) || isIllegalOperandForPlusOps(right)) {
        // If any of the types is not allowed to be operands (for example, list and map), we return
        // null here. Returning null indicates a compilation error.
        return null;
      } else if (stringType.isAssignableFrom(left) || stringType.isAssignableFrom(right)) {
        // If any of these types can be coerced to string, returns string type. In this case plus
        // operation means string concat (instead of arithmetic operation).
        return stringType;
      } else {
        // At this point, we know that both types are not string type or number type, and every
        // backend does different things. Returns null that indicates a compilation error.
        return null;
      }
    }
  }

  /**
   * Type resolver for all arithmetic operators (except plus operator). In particular, minus,
   * multiply, divide and modulo.
   */
  public static final class SoyTypeArithmeticOperator implements SoyTypeBinaryOperator {
    @Override
    @Nullable
    public SoyType resolve(SoyType left, SoyType right) {
      Optional<SoyType> arithmeticType = SoyTypes.computeLowestCommonTypeArithmetic(left, right);
      return arithmeticType.orNull();
    }
  }
}
