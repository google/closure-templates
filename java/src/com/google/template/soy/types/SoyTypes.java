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
  public static final SoyType NUMBER_TYPE =
      UnionType.of(IntType.getInstance(), FloatType.getInstance());

  public static final SoyType GBIGINT = GbigintType.getInstance();

  // TODO: b/319288438 - Remove these types once soy setters are migrated to gbigint.
  //
  // The gbigint or number/string types are for use by setters during the migration to gbigint. JSPB
  // setters in JS normally accept gbigint|number|string regardless of any jstype annotations. This
  // has not historically been the case in Soy, so we need to smooth over the difference.
  public static final SoyType GBIGINT_OR_NUMBER_FOR_MIGRATION =
      UnionType.of(GbigintType.getInstance(), NUMBER_TYPE);

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

  public static final ImmutableSet<Kind> SANITIZED_TYPE_KINDS =
      ImmutableSet.of(
          Kind.HTML,
          Kind.ELEMENT,
          Kind.ATTRIBUTES,
          Kind.JS,
          Kind.CSS,
          Kind.URI,
          Kind.TRUSTED_RESOURCE_URI);

  private static final ImmutableSet<Kind> ALWAYS_COMPARABLE_KINDS =
      Sets.immutableEnumSet(Kind.UNKNOWN, Kind.ANY, Kind.NULL, Kind.UNDEFINED);

  public static final ImmutableSet<Kind> ARITHMETIC_PRIMITIVES =
      Sets.immutableEnumSet(Kind.INT, Kind.FLOAT, Kind.NUMBER);

  public static final ImmutableSet<Kind> NULLISH_KINDS =
      Sets.immutableEnumSet(Kind.NULL, Kind.UNDEFINED);

  public static final ImmutableSet<Kind> NUMERIC_PRIMITIVES =
      new ImmutableSet.Builder<Kind>().addAll(ARITHMETIC_PRIMITIVES).add(Kind.PROTO_ENUM).build();

  public static final ImmutableSet<Kind> INTEGER_PRIMITIVES =
      Sets.immutableEnumSet(Kind.INT, Kind.PROTO_ENUM);

  private static final ImmutableSet<Kind> PRIMITIVE_KINDS =
      new ImmutableSet.Builder<Kind>()
          .addAll(NUMERIC_PRIMITIVES)
          .addAll(Kind.STRING_KINDS)
          .add(Kind.BOOL)
          .build();

  public static boolean isIntFloatOrNumber(SoyType type) {
    return SoyTypes.NUMBER_TYPE.isAssignableFromStrict(type);
  }

  /**
   * Returns true if the input type is a numeric primitive type, such as int, float, proto enum, and
   * number.
   */
  public static boolean isNumericPrimitive(SoyType type) {
    return isKindOrUnionOfKinds(type, NUMERIC_PRIMITIVES);
  }

  public static boolean bothOfKind(SoyType left, SoyType right, Kind kind) {
    ImmutableSet<Kind> kinds = ImmutableSet.of(kind);
    return isKindOrUnionOfKinds(left, kinds) && isKindOrUnionOfKinds(right, kinds);
  }

  public static boolean bothOfKind(SoyType left, SoyType right, Set<Kind> kinds) {
    return isKindOrUnionOfKinds(left, kinds) && isKindOrUnionOfKinds(right, kinds);
  }

  public static boolean eitherOfKind(SoyType left, SoyType right, Kind kind) {
    ImmutableSet<Kind> kinds = ImmutableSet.of(kind);
    return isKindOrUnionOfKinds(left, kinds) || isKindOrUnionOfKinds(right, kinds);
  }

  public static boolean eitherOfKind(SoyType left, SoyType right, Set<Kind> kinds) {
    return isKindOrUnionOfKinds(left, kinds) || isKindOrUnionOfKinds(right, kinds);
  }

  public static SoyType removeNull(SoyType type) {
    return modifyUnion(type, t -> t != NullType.getInstance());
  }

  public static SoyType removeUndefined(SoyType type) {
    return modifyUnion(type, t -> t != UndefinedType.getInstance());
  }

  public static SoyType removeNullish(SoyType type) {
    return modifyUnion(type, t -> !NULLISH_KINDS.contains(t.getKind()));
  }

  private static SoyType keepNullish(SoyType type) {
    return modifyUnion(type, t -> NULLISH_KINDS.contains(t.getKind()));
  }

  private static SoyType modifyUnion(SoyType type, Predicate<SoyType> filter) {
    checkNotNull(type);
    SoyType effective = type.getEffectiveType();
    if (effective.getKind() == Kind.UNION) {
      SoyType newUnion = ((UnionType) effective).filter(filter);
      if (newUnion != effective) {
        return newUnion;
      }
    }
    return type;
  }

  /**
   * You probably want to use {@link #tryRemoveNullish}.
   *
   * <p>If the type is nullable, makes it non-nullable. If the type is the null type, then it
   * returns the null type.
   */
  public static SoyType tryRemoveNull(SoyType type) {
    if (type == NullType.getInstance()) {
      return type;
    }
    return removeNull(type);
  }

  public static SoyType tryRemoveUndefined(SoyType type) {
    if (type == UndefinedType.getInstance()) {
      return type;
    }
    return removeUndefined(type);
  }

  public static SoyType tryRemoveNullish(SoyType type) {
    if (isNullOrUndefined(type)) {
      return type;
    }
    return removeNullish(type);
  }

  public static SoyType tryKeepNullish(SoyType type) {
    if (isNullOrUndefined(type)) {
      return type;
    }
    return keepNullish(type);
  }

  public static SoyType makeNullish(SoyType type) {
    checkNotNull(type);
    return UnionType.of(type, NullType.getInstance(), UndefinedType.getInstance());
  }

  public static SoyType makeNullable(SoyType type) {
    checkNotNull(type);
    return isNullable(type) ? type : UnionType.of(type, NullType.getInstance());
  }

  public static boolean isNullable(SoyType type) {
    return containsKind(type, Kind.NULL);
  }

  public static boolean isUndefinable(SoyType type) {
    return containsKind(type, Kind.UNDEFINED);
  }

  public static SoyType makeUndefinable(SoyType type) {
    return isUndefinable(type) ? type : UnionType.of(type, UndefinedType.getInstance());
  }

  /** Returns true if the type is null, undefined, or a union containing one of those kinds. */
  public static boolean isNullish(SoyType type) {
    return containsKinds(type, NULLISH_KINDS);
  }

  /** Return true if value can't be nullish. taking "any" into account. */
  public static boolean isDefinitelyNonNullish(SoyType type) {
    return !isNullish(type) && type.getKind() != Kind.UNKNOWN && type.getKind() != Kind.ANY;
  }

  /** Returns true if the type is null, undefined, or null|undefined. */
  public static boolean isNullOrUndefined(SoyType type) {
    return isKindOrUnionOfKinds(type, NULLISH_KINDS);
  }

  public static boolean isNumericOrUnknown(SoyType type) {
    return type.getKind() == Kind.UNKNOWN
        || SoyTypes.isKindOrUnionOfKinds(type, SoyTypes.NUMERIC_PRIMITIVES);
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
    SoyType left = tryRemoveNull(t0);
    SoyType right = tryRemoveNull(t1);
    // If either of the types isn't numeric or unknown, then this isn't valid for an arithmetic
    // operation.
    if (!isNumericOrUnknown(left) || !isNumericOrUnknown(right)) {
      return Optional.empty();
    }

    if (left.equals(right)) {
      return Optional.of(left);
    }
    if (left.getKind() == Kind.UNKNOWN || right.getKind() == Kind.UNKNOWN) {
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
    if (SoyTypes.containsKinds(left, INTEGER_PRIMITIVES)
        && SoyTypes.containsKinds(right, INTEGER_PRIMITIVES)) {
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
    SoyType left = tryRemoveNullish(t0.getEffectiveType()).getEffectiveType();
    SoyType right = tryRemoveNullish(t1.getEffectiveType()).getEffectiveType();
    if (left.getKind() == Kind.UNION) {
      return getSoyTypeFromUnionForBinaryOperator((UnionType) left, right, operator);
    }
    if (right.getKind() == Kind.UNION) {
      // When we calculate the return type of a binary operator, it should always be commutative so
      // the order should not matter.
      return getSoyTypeFromUnionForBinaryOperator((UnionType) right, left, operator);
    }
    return operator.resolve(left, right);
  }

  /**
   * Returns true if the given type matches the given kind, or if the given type is a union of types
   * that all match the given kind.
   */
  public static boolean isKindOrUnionOfKind(SoyType type, Kind kind) {
    return expandUnions(type).stream().allMatch((t) -> kind == t.getKind());
  }

  /**
   * Returns true if the given type matches one of the given kinds, or if the given type is a union
   * of types that all match one of the given kinds.
   */
  public static boolean isKindOrUnionOfKinds(SoyType type, Set<Kind> kinds) {
    return expandUnions(type).stream().allMatch((t) -> kinds.contains(t.getKind()));
  }

  /**
   * For union types, returns a list of member types; for all other types, returns a list with a
   * single element containing the type.
   */
  public static ImmutableSet<SoyType> expandUnions(SoyType type) {
    checkNotNull(type);
    if (type.getKind() == Kind.UNION) {
      return ((UnionType) type).getMembers();
    } else {
      return ImmutableSet.of(type);
    }
  }

  /**
   * Returns true if the given type matches the given kind, or if the given type transitively
   * contains a type of the given kind -- e.g., within a union, list, record, map, or template
   * argument.
   */
  public static boolean transitivelyContainsKind(SoyType type, Kind kind) {
    return TreeStreams.breadthFirst(type, new SoyTypeSuccessorsFunction(null))
        .anyMatch(t -> t.getKind() == kind);
  }

  public static boolean isSanitizedType(SoyType type) {
    return containsKinds(type, SANITIZED_TYPE_KINDS);
  }

  /**
   * Returns true if the given type matches any of the given kinds, or is a union which includes any
   * of the given kinds
   */
  public static boolean containsKinds(SoyType type, Set<Kind> kinds) {
    if (kinds.contains(type.getKind())) {
      return true;
    }

    if (type instanceof UnionType) {
      return ((UnionType) type)
          .getMembers().stream().map(SoyType::getKind).anyMatch(kinds::contains);
    }
    return false;
  }

  public static boolean containsKind(SoyType type, Kind kind) {
    if (kind == type.getKind()) {
      return true;
    }

    if (type instanceof UnionType) {
      return ((UnionType) type).getMembers().stream().anyMatch(t -> t.getKind() == kind);
    }

    return false;
  }

  public static SoyType undefinedToNull(SoyType type) {
    if (type == UndefinedType.getInstance()) {
      return NullType.getInstance();
    } else if (type instanceof UnionType) {
      if (isUndefinable(type)) {
        return makeNullable(removeUndefined(type));
      }
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
      if (eitherOfKind(left, right, ALWAYS_COMPARABLE_KINDS)) {
        return boolType;
      }
      if (bothOfKind(left, right, PRIMITIVE_KINDS)) {
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
      if (eitherOfKind(left, right, ALWAYS_COMPARABLE_KINDS)) {
        return boolType;
      }
      if (bothOfKind(left, right, NUMERIC_PRIMITIVES)) {
        return boolType;
      }
      if (bothOfKind(left, right, Kind.STRING_KINDS)) {
        return boolType;
      }
      return null;
    }
  }

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
      } else if (eitherOfKind(left, right, Kind.ILLEGAL_OPERAND_KINDS_PLUS_OP)) {
        // If any of the types is not allowed to be operands (for example, list and map), we return
        // null here. Returning null indicates a compilation error.
        return null;
      } else if (eitherOfKind(left, right, Kind.STRING_KINDS)) {
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
   * Returns an stream that traverses a soy type graph starting at {@code root} and following any
   * union, list, map, record, or other composite type.
   *
   * <p>The optional type registry parameter is used for resolving VE types.
   */
  public static Stream<? extends SoyType> allTypes(
      SoyType root, @Nullable SoyTypeRegistry registry) {
    return TreeStreams.breadthFirst(root, new SoyTypeSuccessorsFunction(registry));
  }

  /** Like {@link #allTypes} but navigates to the effective types of INDEXED and NAMED. */
  public static Stream<? extends SoyType> allTypesWithDeps(
      SoyType root, @Nullable SoyTypeRegistry registry) {
    return TreeStreams.breadthFirst(root, new SoyTypeDepsSuccessorsFunction(registry));
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
  private static class SoyTypeDepsSuccessorsFunction extends SoyTypeSuccessorsFunction {

    public SoyTypeDepsSuccessorsFunction(@Nullable SoyTypeRegistry typeRegistry) {
      super(typeRegistry);
    }

    @Override
    public Iterable<? extends SoyType> apply(SoyType type) {
      switch (type.getKind()) {
        case INDEXED:
        case NAMED:
          return ImmutableList.of(type.getEffectiveType());
        default:
          return super.apply(type);
      }
    }
  }

  public static boolean hasProtoDep(SoyType type) {
    return SoyTypes.allTypes(type, null)
        .anyMatch(t -> t.getKind() == Kind.PROTO || t.getKind() == Kind.PROTO_ENUM);
  }

  public static SoyType getIterableElementType(SoyType type) {
    if (type instanceof AbstractIterableType) {
      return ((AbstractIterableType) type).getElementType();
    }
    UnionType union = (UnionType) type;
    return UnionType.of(
        union.getMembers().stream()
            .map(member -> ((AbstractIterableType) member).getElementType())
            .collect(toImmutableList()));
  }

  public static SoyType getMapKeysType(SoyType type) {
    if (type instanceof MapType) {
      return ((MapType) type).getKeyType();
    }
    return UnionType.of(
        ((UnionType) type)
            .getMembers().stream().map(t -> ((MapType) t).getKeyType()).collect(toImmutableSet()));
  }

  public static SoyType getMapValuesType(SoyType type) {
    if (type instanceof MapType) {
      return ((MapType) type).getValueType();
    }
    return UnionType.of(
        ((UnionType) type)
            .getMembers().stream()
                .map(t -> ((MapType) t).getValueType())
                .collect(toImmutableSet()));
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
    for (SoyType member : expandUnions(soyType)) {
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
