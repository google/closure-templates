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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.template.soy.internal.util.TreeStreams;
import com.google.template.soy.types.SanitizedType.AttributesType;
import com.google.template.soy.types.SanitizedType.ElementType;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.SanitizedType.JsType;
import com.google.template.soy.types.SanitizedType.StyleType;
import com.google.template.soy.types.SanitizedType.TrustedResourceUriType;
import com.google.template.soy.types.SanitizedType.UriType;
import com.google.template.soy.types.SoyType.Kind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Utility methods for operating on {@link SoyType} instances. */
public final class SoyTypes {

  private SoyTypes() {}

  /** Shared constant for the 'number' type. */
  public static final SoyType INT_OR_FLOAT =
      UnionType.of(IntType.getInstance(), FloatType.getInstance());

  // TODO: b/319288438 - Remove these types once soy setters are migrated to gbigint.
  //
  // The gbigint or number/string types are for use by setters during the migration to gbigint. JSPB
  // setters in JS normally accept gbigint|number|string regardless of any jstype annotations. This
  // has not historically been the case in Soy, so we need to smooth over the difference.
  public static final SoyType GBIGINT_OR_NUMBER_FOR_MIGRATION =
      UnionType.of(GbigintType.getInstance(), INT_OR_FLOAT);

  public static final SoyType GBIGINT_OR_STRING_FOR_MIGRATION =
      UnionType.of(GbigintType.getInstance(), StringType.getInstance());

  public static final SoyType NULL_OR_UNDEFINED =
      UnionType.of(NullType.getInstance(), UndefinedType.getInstance());

  public static final ImmutableMap<String, SanitizedType> SAFE_PROTO_TO_SANITIZED_TYPE =
      ImmutableMap.<String, SanitizedType>builder()
          .put(SafeHtmlProto.getDescriptor().getFullName(), SanitizedType.HtmlType.getInstance())
          .put(SafeScriptProto.getDescriptor().getFullName(), SanitizedType.JsType.getInstance())
          .put(SafeStyleProto.getDescriptor().getFullName(), SanitizedType.StyleType.getInstance())
          .put(
              SafeStyleSheetProto.getDescriptor().getFullName(),
              SanitizedType.StyleType.getInstance())
          .put(SafeUrlProto.getDescriptor().getFullName(), SanitizedType.UriType.getInstance())
          .put(
              TrustedResourceUrlProto.getDescriptor().getFullName(),
              SanitizedType.TrustedResourceUriType.getInstance())
          .build();

  private static final ImmutableSet<SoyType> SANITIZED_TYPE_KINDS =
      ImmutableSet.of(
          HtmlType.getInstance(),
          ElementType.getInstance(""),
          AttributesType.getInstance(),
          JsType.getInstance(),
          StyleType.getInstance(),
          UriType.getInstance(),
          TrustedResourceUriType.getInstance());

  public static final SoyType ANY_SANITIZED_KIND = UnionType.of(SANITIZED_TYPE_KINDS);

  public static final SoyType STRINGISH =
      UnionType.of(StringType.getInstance(), ANY_SANITIZED_KIND);

  private static final ImmutableSet<Kind> NULLISH_KINDS =
      Sets.immutableEnumSet(Kind.NULL, Kind.UNDEFINED);

  private static final SoyType ANY_PRIMITIVE =
      UnionType.of(NumberType.getInstance(), STRINGISH, BoolType.getInstance());

  public static boolean isIntFloatOrNumber(SoyType type) {
    return SoyTypes.INT_OR_FLOAT.isAssignableFromStrict(type);
  }

  public static boolean isAlwaysComparableKind(SoyType type) {
    return type.equals(AnyType.getInstance()) || NULL_OR_UNDEFINED.isAssignableFromLoose(type);
  }

  public static boolean isArithmeticPrimitive(SoyType type) {
    return NumberType.getInstance().isAssignableFromStrict(type)
        && !SoyProtoEnumType.ANY_ENUM.isAssignableFromStrict(type);
  }

  /**
   * Returns true if the input type is a numeric primitive type, such as int, float, proto enum, and
   * number.
   */
  public static boolean isNumericPrimitive(SoyType type) {
    return NumberType.getInstance().isAssignableFromStrict(type);
  }

  public static boolean bothAssignableFrom(SoyType left, SoyType right, SoyType of) {
    return of.isAssignableFromStrict(left) && of.isAssignableFromStrict(right);
  }

  public static boolean eitherAssignableFrom(SoyType left, SoyType right, SoyType of) {
    return of.isAssignableFromStrict(left) || of.isAssignableFromStrict(right);
  }

  public static SoyType excludeNull(SoyType type) {
    return modifyUnion(type, t -> t != NullType.getInstance());
  }

  public static SoyType excludeUndefined(SoyType type) {
    return modifyUnion(type, t -> t != UndefinedType.getInstance());
  }

  public static SoyType excludeNullish(SoyType type) {
    return modifyUnion(type, t -> !NULLISH_KINDS.contains(t.getKind()));
  }

  public static SoyType extractNullish(SoyType type) {
    return modifyUnion(type, t -> NULLISH_KINDS.contains(t.getKind()));
  }

  private static SoyType modifyUnion(SoyType type, Predicate<SoyType> filter) {
    ImmutableSet<SoyType> members = flattenUnionToSet(type);
    ImmutableSet<SoyType> filtered = members.stream().filter(filter).collect(toImmutableSet());
    if (members.size() == filtered.size()) {
      // No modification of type, so return the original with named types intact.
      return type;
    }
    return UnionType.of(filtered);
  }

  public static SoyType tryExcludeNullish(SoyType type) {
    if (isNullOrUndefined(type)) {
      return type;
    }
    return excludeNullish(type);
  }

  public static SoyType unionWithNullish(SoyType type) {
    boolean nullable = isNullable(type);
    boolean undefinable = isUndefinable(type);
    if (nullable && undefinable) {
      return type;
    }
    if (nullable) {
      return UnionType.of(type, UndefinedType.getInstance());
    } else if (undefinable) {
      return UnionType.of(type, NullType.getInstance());
    } else {
      return UnionType.of(type, NullType.getInstance(), UndefinedType.getInstance());
    }
  }

  public static SoyType unionWithNull(SoyType type) {
    return isNullable(type) ? type : UnionType.of(type, NullType.getInstance());
  }

  public static SoyType unionWithUndefined(SoyType type) {
    return isUndefinable(type) ? type : UnionType.of(type, UndefinedType.getInstance());
  }

  public static boolean isUnknownOrAny(SoyType type) {
    return type.isEffectivelyEqual(UnknownType.getInstance())
        || type.isEffectivelyEqual(AnyType.getInstance());
  }

  public static boolean isNullable(SoyType type) {
    return !isUnknownOrAny(type) && type.isAssignableFromStrict(NullType.getInstance());
  }

  public static boolean isUndefinable(SoyType type) {
    return !isUnknownOrAny(type) && type.isAssignableFromStrict(UndefinedType.getInstance());
  }

  /** Returns true if the type is null, undefined, or a union containing one of those kinds. */
  public static boolean isNullish(SoyType type) {
    return isNullable(type) || isUndefinable(type);
  }

  /** Return true if value can't be nullish. taking "any" into account. */
  public static boolean isDefinitelyNonNullish(SoyType type) {
    return !isNullish(type) && !isUnknownOrAny(type);
  }

  /** Returns true if the type is null, undefined, or null|undefined. */
  public static boolean isNullOrUndefined(SoyType type) {
    return NULL_OR_UNDEFINED.isAssignableFromStrict(type);
  }

  public static boolean isNumericOrUnknown(SoyType type) {
    return type.isOfKind(Kind.UNKNOWN) || NumberType.getInstance().isAssignableFromStrict(type);
  }

  public static Optional<SoyType> computeStricterType(SoyType t0, SoyType t1) {
    if (t0.isAssignableFromStrictWithoutCoercions(t1)) {
      return Optional.of(t1);
    } else if (t1.isAssignableFromStrictWithoutCoercions(t0)) {
      return Optional.of(t0);
    } else {
      return Optional.empty();
    }
  }

  /**
   * Compute the most specific type that is assignable from both t0 and t1.
   *
   * @param typeRegistry Type registry.
   * @param t0 A type.
   * @param t1 Another type.
   * @return A type that is assignable from both t0 and t1.
   */
  public static SoyType computeLowestCommonType(TypeInterner typeRegistry, SoyType t0, SoyType t1) {
    if (t0.isAssignableFromStrictWithoutCoercions(t1)) {
      return t0;
    } else if (t1.isAssignableFromStrictWithoutCoercions(t0)) {
      return t1;
    } else {
      // Create a union.  This preserves the most information.
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
      TypeInterner typeRegistry, Collection<SoyType> types) {
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
    SoyType left = excludeNull(t0).getEffectiveType();
    SoyType right = excludeNull(t1).getEffectiveType();
    // If either of the types isn't numeric or unknown, then this isn't valid for an arithmetic
    // operation.
    if (!isNumericOrUnknown(left) || !isNumericOrUnknown(right)) {
      return Optional.empty();
    }

    if (left.equals(right)) {
      return Optional.of(left);
    }
    if (left.isOfKind(Kind.UNKNOWN) || right.isOfKind(Kind.UNKNOWN)) {
      return Optional.of(UnknownType.getInstance());
    }

    // Return one of: number, float, number|int, float|int.
    Set<SoyType> unionMembers = new HashSet<>();
    if (SoyTypes.containsKind(left, Kind.NUMBER) || SoyTypes.containsKind(right, Kind.NUMBER)) {
      unionMembers.add(NumberType.getInstance());
    } else if (SoyTypes.containsKind(left, Kind.FLOAT)
        || SoyTypes.containsKind(right, Kind.FLOAT)) {
      unionMembers.add(FloatType.getInstance());
    }
    if (SoyTypes.containsIntegerPrimitives(left) && SoyTypes.containsIntegerPrimitives(right)) {
      unionMembers.add(IntType.getInstance());
    }
    checkState(!unionMembers.isEmpty()); // should be impossible due to isNumericOrUnknown.
    return Optional.of(UnionType.of(unionMembers));
  }

  /**
   * Helper method used by {@link #getSoyTypeForBinaryOperator} for handling {@code UnionType}
   * instances.
   */
  @Nullable
  private static SoyType getSoyTypeFromUnionForBinaryOperator(
      UnionType t0, SoyType t1, SoyTypeBinaryOperator operator) {
    List<SoyType> subTypes = new ArrayList<>();
    for (SoyType unionMember : t0.getMembers()) {
      SoyType result = getSoyTypeForBinaryOperator(unionMember, t1, operator);
      if (result == null) {
        return null;
      }
      subTypes.add(result);
    }
    return UnionType.of(subTypes);
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
    // None of the operators covered by SoyTypeBinaryOperator can evaluate to null/undefined.
    SoyType left = tryExcludeNullish(normalizeUnion(t0));
    SoyType right = tryExcludeNullish(normalizeUnion(t1));
    if (left instanceof UnionType) {
      return getSoyTypeFromUnionForBinaryOperator((UnionType) left, right, operator);
    }
    if (right instanceof UnionType) {
      // When we calculate the return type of a binary operator, it should always be commutative so
      // the order should not matter.
      return getSoyTypeFromUnionForBinaryOperator((UnionType) right, left, operator);
    }
    return operator.resolve(left.getEffectiveType(), right.getEffectiveType());
  }

  private static final ImmutableSet<Kind> NOT_IN_FLATTENED_KINDS =
      ImmutableSet.of(
          Kind.UNION, Kind.NAMED, Kind.INDEXED, Kind.PICK, Kind.OMIT, Kind.INTERSECTION);

  /**
   * Returns true if the given type matches the given kind, or if the given type is a union of types
   * that all match the given kind.
   */
  public static boolean isKindOrUnionOfKind(SoyType type, Kind kind) {
    Preconditions.checkArgument(!NOT_IN_FLATTENED_KINDS.contains(kind));
    return flattenUnion(type).allMatch((t) -> kind == t.getKind());
  }

  /**
   * Returns true if the given type matches one of the given kinds, or if the given type is a union
   * of types that all match one of the given kinds.
   */
  public static boolean isKindOrUnionOfKinds(SoyType type, Set<Kind> kinds) {
    Preconditions.checkArgument(Sets.intersection(kinds, NOT_IN_FLATTENED_KINDS).isEmpty());
    return flattenUnion(type).allMatch((t) -> kinds.contains(t.getKind()));
  }

  public static ImmutableSet<SoyType> flattenUnionToSet(SoyType root) {
    return flattenUnion(root).collect(ImmutableSet.toImmutableSet());
  }

  /**
   * Recursively resolves unions, returning a single stream of distinct elements. The stream will
   * never contain UNION or any computed type.
   */
  public static Stream<? extends SoyType> flattenUnion(SoyType root) {
    if (!NOT_IN_FLATTENED_KINDS.contains(root.getKind())) {
      return Stream.of(root);
    }
    return TreeStreams.breadthFirst(
            root,
            type -> {
              if (type instanceof UnionType) {
                return ((UnionType) type).getMembers();
              } else if (type instanceof ComputedType) {
                return ImmutableList.of(type.getEffectiveType());
              }
              return ImmutableList.of();
            })
        .filter(t -> !(t instanceof UnionType || t instanceof ComputedType))
        .distinct();
  }

  private static SoyType normalizeUnion(SoyType t) {
    if (t instanceof UnionType || t instanceof NamedType || t instanceof IndexedType) {
      return UnionType.of(flattenUnionToSet(t));
    } else if (t instanceof ComputedType) {
      return t.getEffectiveType();
    }
    return t;
  }

  /**
   * Returns true if the given type matches the given kind, or if the given type transitively
   * contains a type of the given kind -- e.g., within a union, list, record, map, or template
   * argument.
   */
  public static boolean transitivelyContainsKind(SoyType type, Kind kind) {
    return allLogicalTypes(type, null).anyMatch(t -> t.getKind() == kind);
  }

  public static boolean isSanitizedType(SoyType type) {
    return SANITIZED_TYPE_KINDS.stream().anyMatch(type::isAssignableFromStrict);
  }

  public static boolean containsIntegerPrimitives(SoyType type) {
    return type.isAssignableFromStrictWithoutCoercions(IntType.getInstance())
        || type.isAssignableFromStrictWithoutCoercions(SoyProtoEnumType.UNKNOWN_ENUM);
  }

  /**
   * Returns true if the given type matches any of the given kinds, or is a union which includes any
   * of the given kinds
   */
  public static boolean containsKinds(SoyType type, Set<Kind> kinds) {
    Preconditions.checkArgument(Sets.intersection(kinds, NOT_IN_FLATTENED_KINDS).isEmpty());
    return flattenUnion(type).anyMatch(t -> kinds.contains(t.getKind()));
  }

  public static boolean containsKind(SoyType type, Kind kind) {
    Preconditions.checkArgument(!NOT_IN_FLATTENED_KINDS.contains(kind));
    return flattenUnion(type).anyMatch(t -> t.getKind() == kind);
  }

  public static SoyType undefinedToNull(SoyType type) {
    if (isUndefinable(type)) {
      return unionWithNull(excludeUndefined(type));
    }
    return type;
  }

  /**
   * A type resolver interface that can be passed into getSoyTypeForBinaryOperator method.
   *
   * <p>All operators modeled by this interface should evaluate to a type that is not nullable. e.g.
   * `==` is OK because its type is always `boolean` but `??` is not because it can be nullable if
   * the rhs is nullable. This is due to the logic in getSoyTypeForBinaryOperator, which ignores
   * nullability.
   *
   * <p>Note that the implementation of this resolver does not need to handle union types. The logic
   * for union type should be handled by the callers that take this resolver as an argument.
   */
  public interface SoyTypeBinaryOperator {
    @Nullable
    SoyType resolve(SoyType left, SoyType right);
  }

  /**
   * Type resolver for equal (==) and not equal (!=) operators. The resolver returns null if two
   * {@code SoyType} instances are not comparable, otherwise always return {@code BoolType}.
   *
   * <p>In particular,
   *
   * <ul>
   *   <li>Comparing anything with UNKNOWN, ANY, and NULL is legitimate.
   *   <li>If one is assignable from another, comparing them is legitimate.
   *   <li>If both are primitive types, comparing them is legitimate.
   *   <li>All other comparisons should have exactly the same types on both sides. Coercing is
   *       unsafe, especially in JS backend. An example is a jspb message that contains a single
   *       enum. Assuming that the enum is 1, the representation in JS is {@code [1]}, and this is
   *       equivalent to a number.
   * </ul>
   */
  public static final class SoyTypeEqualComparisonOp implements SoyTypeBinaryOperator {
    @Override
    @Nullable
    public SoyType resolve(SoyType left, SoyType right) {
      SoyType boolType = BoolType.getInstance();
      if (isAlwaysComparableKind(left) || isAlwaysComparableKind(right)) {
        return boolType;
      }
      if (bothAssignableFrom(left, right, ANY_PRIMITIVE)) {
        return boolType;
      }
      return left.equals(right) ? boolType : null;
    }
  }

  /**
   * Type resolver for <, >, <=, and >= operators. The resolver returns null if two {@code SoyType}
   * instances are not comparable, otherwise always return {@code BoolType}.
   *
   * <p>In particular,
   *
   * <ul>
   *   <li>Comparing anything with UNKNOWN and ANY is legitimate.
   *   <li>Comparing numeric types is legitimate.
   *   <li>Comparing string types is legtimate.
   *   <li>All other comparisons are invalid. It causes inconsistent behaviors in different
   *       backends.
   * </ul>
   *
   * <p>Note that string-number comparisons and string-string comparisons do NOT work with Java
   * backends (both tofu and jbcsrc). These comparisons yield to a {@code RuntimeException}. In
   * contrast, JS backend allows these comparisons.
   *
   * <ul>
   *   <li>For string-number comparisons, JS tries to convert string to number. If string is
   *       numeric, it compares them numerically. For example, '1' < 2 is true and '1' > 2 is false.
   *       If string is not numeric, it always return false. For example, both '1a' < 2 and '1a' > 2
   *       return false.
   *   <li>For string-string comparisons, JS compares them alphabetically.
   * </ul>
   */
  public static final class SoyTypeComparisonOp implements SoyTypeBinaryOperator {
    @Override
    @Nullable
    public SoyType resolve(SoyType left, SoyType right) {
      SoyType boolType = BoolType.getInstance();
      if (isAlwaysComparableKind(left) || isAlwaysComparableKind(right)) {
        return boolType;
      }
      if (bothAssignableFrom(left, right, NumberType.getInstance())) {
        return boolType;
      }
      if (bothAssignableFrom(left, right, STRINGISH)) {
        return boolType;
      }
      return null;
    }
  }

  private static final SoyType ILLEGAL_OPERAND_KINDS_PLUS_OP =
      UnionType.of(
          IterableType.ANY_ITERABLE,
          ListType.ANY_LIST,
          SetType.ANY_SET,
          LegacyObjectMapType.ANY_MAP,
          MapType.ANY_MAP,
          RecordType.EMPTY_RECORD);

  /**
   * Type resolver for plus operators.
   *
   * <p>A plus operator can be an arithmetic operator or a string concat operator.
   *
   * <ul>
   *   <li>If both operands are numbers, returns the number type calculated by {@link
   *       #computeLowestCommonTypeArithmetic}.
   *   <li>If any of the operands is disallowed, returns null that indicates a compilation error.
   *   <li>If any of the operands is string type, returns string type.
   *   <li>Otherwise, returns null that indicates a compilation error.
   * </ul>
   */
  public static final class SoyTypePlusOperator implements SoyTypeBinaryOperator {
    @Override
    @Nullable
    public SoyType resolve(SoyType left, SoyType right) {
      Optional<SoyType> arithmeticType = SoyTypes.computeLowestCommonTypeArithmetic(left, right);
      if (arithmeticType.isPresent()) {
        return arithmeticType.get();
      } else if (eitherAssignableFrom(left, right, ILLEGAL_OPERAND_KINDS_PLUS_OP)) {
        // If any of the types is not allowed to be operands (for example, list and map), we return
        // null here. Returning null indicates a compilation error.
        return null;
      } else if (eitherAssignableFrom(left, right, STRINGISH)) {
        // If any of these types can be coerced to string, returns string type. In this case plus
        // operation means string concat (instead of arithmetic operation).
        return StringType.getInstance();
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
      return arithmeticType.orElse(null);
    }
  }

  /**
   * Returns a stream that traverses a soy type graph starting at {@code root} and following any
   * union, list, map, record, or other composite type.
   *
   * <p>The optional type registry parameter is used for resolving VE types.
   */
  public static Stream<? extends SoyType> allLogicalTypes(
      SoyType root, @Nullable SoyTypeRegistry registry) {
    return TreeStreams.breadthFirst(root, new SoyTypeSuccessorsFunction(registry));
  }

  /**
   * A stream that does not contain computed types, and only contains the types used by the computed
   * types. So instead of NAMED, this stream contains the alias value. And instead of OMIT, the
   * stream contains the effective record type.
   */
  public static Stream<? extends SoyType> allConcreteTypes(
      SoyType root, @Nullable SoyTypeRegistry registry) {
    return TreeStreams.breadthFirst(root, new SoyTypeEffectiveSuccessorsFunction(registry))
        .filter(t -> !(t instanceof ComputedType));
  }

  /**
   * Resolves a local symbol to a fully-qualified name. Supports dotted local symbols for module
   * imports or nested types. e.g.: {@code localToFqn("A", ImmutableMap.of("A", "pkg.A")) ==
   * "pkg.A"} and {@code localToFqn("A.B", ImmutableMap.of("A", "pkg.A")) == "pkg.A.B"}.
   *
   * @return {@code null} if no match exists in {@code localToFqn}.
   */
  @Nullable
  public static String localToFqn(String localSymbol, Map<String, String> localToFqn) {
    // If the local symbol is an imported top-level message, or an "ImportedModule.TopLevelMessage",
    // then we can just look up the fqn directly.
    if (localToFqn.containsKey(localSymbol)) {
      return localToFqn.get(localSymbol);
    }

    // Support nested messages by resolving the top level proto (e.g. "FooProto" or
    // "myProtosModule.FooProto") against the map, and then appending subsequent tokens.
    String localRoot = getFirstSegment(localSymbol);
    if (!localToFqn.containsKey(localRoot)) {
      // Module import case.
      localRoot = getFirstTwoSegments(localSymbol);
    }

    String fqnRoot = localToFqn.get(localRoot);
    if (fqnRoot == null) {
      return null;
    }
    return localSymbol.replaceFirst(localRoot, fqnRoot);
  }

  /**
   * Gets the first segment in a dotted string (e.g. "foo" in "foo.bar.baz"), or the whole string if
   * there are not dots.
   */
  private static String getFirstSegment(String symbol) {
    int index = symbol.indexOf('.');
    return index >= 0 ? symbol.substring(0, index) : symbol;
  }

  /**
   * Gets the first two segments in a dotted string (e.g. "foo.bar" in "foo.bar.baz"), or the whole
   * string if there are fewer than 2 dots.
   */
  private static String getFirstTwoSegments(String symbol) {
    int firstDot = symbol.indexOf('.');
    int secondDot = symbol.indexOf('.', firstDot + 1);
    if (secondDot >= 0) {
      return symbol.substring(0, secondDot);
    }
    return symbol;
  }

  /** Implementation of SuccessorsFunction that traverses a graph rooted at a SoyType. */
  private static class SoyTypeSuccessorsFunction
      implements Function<SoyType, Iterable<? extends SoyType>> {

    private final SoyTypeRegistry typeRegistry;

    public SoyTypeSuccessorsFunction(@Nullable SoyTypeRegistry typeRegistry) {
      this.typeRegistry = typeRegistry;
    }

    @Override
    public Iterable<? extends SoyType> apply(SoyType type) {
      // For any type that contains nested types, return the list of nested types. E.g. the LIST
      // type contains the list element type, the MAP type contains both the key and value types,
      // etc.
      switch (type.getKind()) {
        case UNION:
          return ((UnionType) type).getMembers();
        case INTERSECTION:
          return ((IntersectionType) type).getMembers();
        case INDEXED:
          return ImmutableList.of(((IndexedType) type).getType());
        case PICK:
          return ImmutableList.of(((PickType) type).getType(), ((PickType) type).getKeys());
        case OMIT:
          return ImmutableList.of(((OmitType) type).getType(), ((OmitType) type).getKeys());
        case NAMED:
          // Use SoyTypeDepsSuccessorsFunction below if you need to navigate to the effective record
          // type.
          return ImmutableList.of();

        case ITERABLE:
        case LIST:
        case SET:
          return ImmutableList.of(((AbstractIterableType) type).getElementType());

        case MAP:
        case LEGACY_OBJECT_MAP:
          AbstractMapType mapType = (AbstractMapType) type;
          return ImmutableList.of(mapType.getKeyType(), mapType.getValueType());

        case RECORD:
          return ((RecordType) type)
              .getMembers().stream()
                  .map(RecordType.Member::declaredType)
                  .collect(Collectors.toList());

        case VE:
          VeType veType = (VeType) type;
          if (typeRegistry != null && veType.getDataType().isPresent()) {
            String protoFqn = veType.getDataType().get();
            SoyType protoType = typeRegistry.getProtoRegistry().getProtoType(protoFqn);
            if (protoType == null) {
              throw new IllegalArgumentException(protoFqn);
            }
            return ImmutableList.of(protoType);
          }
        // fall through
        default:
          return ImmutableList.of();
      }
    }
  }

  /** Implementation of SuccessorsFunction that traverses a graph rooted at a SoyType. */
  private static class SoyTypeEffectiveSuccessorsFunction extends SoyTypeSuccessorsFunction {

    public SoyTypeEffectiveSuccessorsFunction(@Nullable SoyTypeRegistry typeRegistry) {
      super(typeRegistry);
    }

    @Override
    public Iterable<? extends SoyType> apply(SoyType type) {
      if (type instanceof ComputedType) {
        return ImmutableList.of(type.getEffectiveType());
      }
      return super.apply(type);
    }
  }

  public static boolean hasProtoDep(SoyType type) {
    return SoyTypes.allConcreteTypes(type, null)
        .anyMatch(t -> t.getKind() == Kind.PROTO || t.getKind() == Kind.PROTO_ENUM);
  }

  public static SoyType getIterableElementType(SoyType type) {
    return UnionType.of(
        flattenUnion(type)
            .map(member -> ((AbstractIterableType) member).getElementType())
            .collect(toImmutableList()));
  }

  public static SoyType getMapKeysType(SoyType type) {
    return UnionType.of(
        flattenUnion(type).map(t -> ((MapType) t).getKeyType()).collect(toImmutableSet()));
  }

  public static SoyType getMapValuesType(SoyType type) {
    return UnionType.of(
        flattenUnion(type).map(t -> ((MapType) t).getValueType()).collect(toImmutableSet()));
  }

  @Nullable
  public static TemplateType getTemplateType(SoyType type) {
    if (type instanceof TemplateType) {
      return (TemplateType) type;
    } else if (type instanceof TemplateImportType) {
      return ((TemplateImportType) type).getBasicTemplateType();
    }
    return null;
  }

  public static boolean isValidInstanceOfOperand(SoyType type) {
    type = type.getEffectiveType();
    switch (type.getKind()) {
      case STRING:
      case BOOL:
      case HTML:
      case JS:
      case URI:
      case TRUSTED_RESOURCE_URI:
      case CSS:
      case ATTRIBUTES:
      case MESSAGE:
      case PROTO:
      case NUMBER:
        return true;
      case RECORD:
        return type.equals(RecordType.EMPTY_RECORD);
      case ITERABLE:
      case LIST:
      case SET:
        return ((AbstractIterableType) type).getElementType().equals(AnyType.getInstance());
      case MAP:
        return ((MapType) type).getKeyType().equals(AnyType.getInstance())
            && ((MapType) type).getValueType().equals(AnyType.getInstance());
      default:
        return false;
    }
  }

  public static SoyType getRecordMembersType(RecordType type) {
    return UnionType.of(
        type.getMembers().stream().map(RecordType.Member::checkedType).collect(toImmutableList()));
  }

  public static SoyType getFunctionReturnType(SoyType soyType) {
    SoyType rv = null;
    for (SoyType member : flattenUnionToSet(soyType)) {
      if (member instanceof FunctionType) {
        SoyType returnType = ((FunctionType) member).getReturnType();
        rv =
            rv != null
                ? computeLowestCommonType(TypeRegistries.newTypeInterner(), rv, returnType)
                : returnType;
      } else {
        return UnknownType.getInstance();
      }
    }
    return checkNotNull(rv);
  }
}
