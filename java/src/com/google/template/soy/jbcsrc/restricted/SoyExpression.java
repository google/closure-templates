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

package com.google.template.soy.jbcsrc.restricted;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_PROVIDER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.STRING_TYPE;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.DoNotCall;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.internal.Converters;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.LegacyObjectMapType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UndefinedType;
import com.google.template.soy.types.UnknownType;
import java.util.List;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

/**
 * An Expression involving a soy value.
 *
 * <p>SoyExpressions can be {@link #box() boxed} into SoyValue subtypes and they also support some
 * implicit conversions.
 *
 * <p>All soy expressions are convertible to {@code boolean} or {@link String} valued expressions,
 * but depending on the type they may also support additional unboxing conversions.
 */
public final class SoyExpression extends Expression {

  public static SoyExpression forSoyValue(SoyType type, Expression delegate) {
    if (delegate instanceof SoyExpression) {
      return forSoyValue(type, ((SoyExpression) delegate).delegate);
    }
    return new SoyExpression(SoyRuntimeType.getBoxedType(type), delegate);
  }

  public static SoyExpression forBool(Expression delegate) {
    return new SoyExpression(getUnboxedType(BoolType.getInstance()), delegate);
  }

  @DoNotCall
  public static SoyExpression forBool(SoyExpression delegate) {
    throw new UnsupportedOperationException();
  }

  public static SoyExpression forFloat(Expression delegate) {
    return new SoyExpression(getUnboxedType(FloatType.getInstance()), delegate);
  }

  @DoNotCall
  public static SoyExpression forFloat(SoyExpression delegate) {
    throw new UnsupportedOperationException();
  }

  public static SoyExpression forInt(Expression delegate) {
    return new SoyExpression(getUnboxedType(IntType.getInstance()), delegate);
  }

  @DoNotCall
  public static SoyExpression forInt(SoyExpression delegate) {
    throw new UnsupportedOperationException();
  }

  public static SoyExpression forString(Expression delegate) {
    return new SoyExpression(getUnboxedType(StringType.getInstance()), delegate);
  }

  @DoNotCall
  public static SoyExpression forString(SoyExpression delegate) {
    throw new UnsupportedOperationException();
  }

  public static SoyExpression forList(ListType listType, Expression delegate) {
    return new SoyExpression(getUnboxedType(listType), delegate);
  }

  @DoNotCall
  public static SoyExpression forList(ListType listType, SoyExpression delegate) {
    throw new UnsupportedOperationException();
  }

  public static SoyExpression forBoxedList(SoyType listType, Expression delegate) {
    Preconditions.checkArgument(SoyTypes.isKindOrUnionOfKind(listType, Kind.LIST));
    return new SoyExpression(SoyRuntimeType.getBoxedType(listType), delegate);
  }

  @DoNotCall
  public static SoyExpression forBoxedList(ListType listType, SoyExpression delegate) {
    throw new UnsupportedOperationException();
  }

  public static SoyExpression forLegacyObjectMap(LegacyObjectMapType mapType, Expression delegate) {
    return new SoyExpression(SoyRuntimeType.getBoxedType(mapType), delegate);
  }

  @DoNotCall
  public static SoyExpression forLegacyObjectMap(
      LegacyObjectMapType mapType, SoyExpression delegate) {
    throw new UnsupportedOperationException();
  }

  public static SoyExpression forMap(MapType mapType, Expression delegate) {
    return new SoyExpression(SoyRuntimeType.getBoxedType(mapType), delegate);
  }

  @DoNotCall
  public static SoyExpression forMap(MapType mapType, SoyExpression delegate) {
    throw new UnsupportedOperationException();
  }

  public static SoyExpression forProto(SoyRuntimeType type, Expression delegate) {
    checkArgument(type.soyType().getKind() == Kind.PROTO);
    return new SoyExpression(type, delegate);
  }

  @DoNotCall
  public static SoyExpression forProto(SoyRuntimeType type, SoyExpression delegate) {
    throw new UnsupportedOperationException();
  }

  public static SoyExpression forRuntimeType(SoyRuntimeType type, Expression delegate) {
    return new SoyExpression(type, delegate);
  }

  @DoNotCall
  public static SoyExpression forRuntimeType(SoyRuntimeType type, SoyExpression delegate) {
    throw new UnsupportedOperationException();
  }

  public static SoyExpression resolveSoyValueProvider(SoyType type, Expression delegate) {
    checkArgument(
        BytecodeUtils.isPossiblyAssignableFrom(
            BytecodeUtils.SOY_VALUE_PROVIDER_TYPE, delegate.resultType()));
    if (delegate.isNonJavaNullable()
        || !BytecodeUtils.isDefinitelyAssignableFrom(
            BytecodeUtils.SOY_VALUE_TYPE, delegate.resultType())) {
      delegate = delegate.invoke(MethodRef.SOY_VALUE_PROVIDER_RESOLVE).checkedSoyCast(type);
    }
    return forSoyValue(type, delegate);
  }

  /**
   * Returns an Expression that evaluates to a list containing all the items as boxed soy values,
   * with Soy nullish values converted to Java null.
   */
  public static Expression boxListWithSoyNullishAsJavaNull(List<SoyExpression> items) {
    return BytecodeUtils.asList(
        items.stream().map(SoyExpression::boxWithSoyNullishAsJavaNull).collect(toImmutableList()));
  }

  public static Expression boxListWithSoyNullAsJavaNull(List<SoyExpression> items) {
    return BytecodeUtils.asList(
        items.stream().map(SoyExpression::boxWithSoyNullAsJavaNull).collect(toImmutableList()));
  }

  public static Expression asBoxedValueProviderList(List<SoyExpression> items) {
    return BytecodeUtils.asImmutableList(
        items.stream().map(SoyExpression::box).collect(toImmutableList()));
  }

  public static final SoyExpression SOY_NULL =
      new SoyExpression(
          SoyRuntimeType.getBoxedType(NullType.getInstance()), BytecodeUtils.soyNull());

  public static final SoyExpression SOY_UNDEFINED =
      new SoyExpression(
          SoyRuntimeType.getBoxedType(UndefinedType.getInstance()), BytecodeUtils.soyUndefined());

  public static final SoyExpression TRUE = forBool(BytecodeUtils.constant(true));

  public static final SoyExpression FALSE = forBool(BytecodeUtils.constant(false));

  private static SoyRuntimeType getUnboxedType(SoyType soyType) {
    return SoyRuntimeType.getUnboxedType(soyType).get();
  }

  private final SoyRuntimeType soyRuntimeType;
  final Expression delegate;

  private SoyExpression(SoyRuntimeType soyRuntimeType, Expression delegate) {
    super(delegate.resultType(), delegate.features());
    checkState(!(delegate instanceof SoyExpression));
    checkArgument(
        BytecodeUtils.isPossiblyAssignableFrom(soyRuntimeType.runtimeType(), delegate.resultType()),
        "Expecting SoyExpression type of %s for soy type %s, found delegate with type of %s",
        soyRuntimeType.runtimeType(),
        soyRuntimeType.soyType(),
        delegate.resultType());
    if (soyRuntimeType.isBoxed()
        != BytecodeUtils.isDefinitelyAssignableFrom(
            SOY_VALUE_PROVIDER_TYPE, delegate.resultType())) {
      throw new IllegalArgumentException(
          "boxed=" + soyRuntimeType.isBoxed() + " for type " + delegate.resultType());
    }
    this.soyRuntimeType = soyRuntimeType;
    this.delegate = delegate;
  }

  /** Returns the {@link SoyType} of the expression. */
  public SoyType soyType() {
    return soyRuntimeType.soyType();
  }

  /** Returns the {@link SoyRuntimeType} of the expression. */
  public SoyRuntimeType soyRuntimeType() {
    return soyRuntimeType;
  }

  @Override
  protected void doGen(CodeBuilder adapter) {
    delegate.gen(adapter);
  }

  @Override
  public SoyExpression withSourceLocation(SourceLocation location) {
    return withSource(delegate.withSourceLocation(location));
  }

  // Need to override since we also overrode `withSourceLocation` otherwise all SoyExpression
  // objects have `unknown` locations.
  @Override
  public SourceLocation location() {
    return delegate.location();
  }

  public boolean assignableToNullableInt() {
    return soyRuntimeType.assignableToNullableInt();
  }

  public boolean assignableToNullableFloat() {
    return soyRuntimeType.assignableToNullableFloat();
  }

  public boolean assignableToNullableNumber() {
    return soyRuntimeType.assignableToNullableNumber();
  }

  public boolean assignableToNullableString() {
    return soyRuntimeType.assignableToNullableString();
  }

  public boolean isBoxed() {
    return soyRuntimeType.isBoxed();
  }

  /** Returns a SoyExpression that evaluates to a subtype of {@link SoyValue}. */
  public SoyExpression box() {
    if (isBoxed()) {
      return this;
    }
    // since we aren't boxed and these must be primitives so we don't need to worry about
    // nullability
    if (soyRuntimeType.isKnownBool()) {
      return asBoxed(MethodRef.BOOLEAN_DATA_FOR_VALUE.invoke(delegate));
    }
    if (soyRuntimeType.isKnownInt()) {
      return asBoxed(MethodRef.INTEGER_DATA_FOR_VALUE.invoke(delegate));
    }
    if (soyRuntimeType.isKnownFloat()) {
      return asBoxed(MethodRef.FLOAT_DATA_FOR_VALUE.invoke(delegate));
    }
    // If null is expected and it is a reference type we want to propagate null through the boxing
    // operation
    boolean nonNullable = delegate.isNonJavaNullable();
    Features features = features().plus(Feature.NON_JAVA_NULLABLE);
    if (nonNullable) {
      features = features.plus(Feature.NON_SOY_NULLISH);
    }
    return asBoxed(
        new Expression(soyRuntimeType.box().runtimeType(), features) {
          @Override
          protected void doGen(CodeBuilder adapter) {
            Label end = null;
            delegate.gen(adapter);
            if (!nonNullable) {
              end = new Label();
              BytecodeUtils.coalesceSoyNullishToSoyNull(adapter, delegate.resultType(), end);
            }
            doBox(adapter, soyRuntimeType.asNonSoyNullish());
            if (end != null) {
              adapter.mark(end);
            }
          }
        });
  }

  /**
   * Boxes a value, converting Soy nullish to Java null. Appropriate when preparing a parameter for
   * an extern or plugin implementation, which both expect Java null rather than NullData or
   * UndefinedData.
   */
  public SoyExpression boxWithSoyNullishAsJavaNull() {
    if (isNonSoyNullish()) {
      return this.box();
    }
    if (this.isBoxed()) {
      return withSource(MethodRef.SOY_NULLISH_TO_JAVA_NULL.invoke(delegate));
    }
    return asBoxed(
        new Expression(soyRuntimeType.box().runtimeType()) {
          @Override
          protected void doGen(CodeBuilder adapter) {
            Label end = new Label();
            delegate.gen(adapter);
            BytecodeUtils.coalesceSoyNullishToJavaNull(adapter, delegate.resultType(), end);
            doBox(adapter, soyRuntimeType.asNonSoyNullish());
            adapter.mark(end);
          }
        });
  }

  public SoyExpression boxWithSoyNullAsJavaNull() {
    if (isNonSoyNullish()) {
      return this.box();
    }
    if (this.isBoxed()) {
      return withSource(MethodRef.SOY_NULL_TO_JAVA_NULL.invoke(delegate));
    }
    return asBoxed(
        new Expression(soyRuntimeType.box().runtimeType()) {
          @Override
          protected void doGen(CodeBuilder adapter) {
            Label end = new Label();
            delegate.gen(adapter);
            BytecodeUtils.coalesceSoyNullToJavaNull(adapter, delegate.resultType(), end);
            doBox(adapter, soyRuntimeType.asNonSoyNullish());
            adapter.mark(end);
          }
        });
  }

  /**
   * Generates code to box the expression assuming that it is non-nullable and on the top of the
   * stack.
   */
  public static void doBox(CodeBuilder adapter, SoyRuntimeType type) {
    if (type.isKnownSanitizedContent()) {
      ContentKind kind =
          Converters.toContentKind(((SanitizedType) type.soyType()).getContentKind());
      checkState(kind != ContentKind.TEXT); // sanity check
      FieldRef.enumReference(kind).accessStaticUnchecked(adapter);
      MethodRef.ORDAIN_AS_SAFE.invokeUnchecked(adapter);
    } else if (type.isKnownString()) {
      MethodRef.STRING_DATA_FOR_VALUE.invokeUnchecked(adapter);
    } else if (type.isKnownListOrUnionOfLists()) {
      MethodRef.LIST_IMPL_FOR_PROVIDER_LIST.invokeUnchecked(adapter);
    } else if (type.isKnownLegacyObjectMapOrUnionOfMaps()) {
      FieldRef.enumReference(RuntimeMapTypeTracker.Type.LEGACY_OBJECT_MAP_OR_RECORD)
          .putUnchecked(adapter);
      MethodRef.DICT_IMPL_FOR_PROVIDER_MAP.invokeUnchecked(adapter);
    } else if (type.isKnownMapOrUnionOfMaps()) {
      MethodRef.MAP_IMPL_FOR_PROVIDER_MAP.invokeUnchecked(adapter);
    } else if (type.isKnownProtoOrUnionOfProtos()) {
      MethodRef.SOY_PROTO_VALUE_CREATE.invokeUnchecked(adapter);
    } else {
      throw new IllegalStateException("Can't box soy expression of type " + type);
    }
  }

  private SoyExpression asBoxed(Expression expr) {
    return new SoyExpression(soyRuntimeType.box(), expr);
  }

  /** Compiles this expression to be a boolean condition for a branch. */
  public Branch compileToBranch() {
    // First deal with primitives which don't have to care about null.
    if (BytecodeUtils.isPrimitive(resultType())) {
      if (resultType().equals(Type.BOOLEAN_TYPE)) {
        return Branch.ifTrue(this);
      } else if (resultType().equals(Type.DOUBLE_TYPE)) {
        return Branch.ifTrue(MethodRef.RUNTIME_COERCE_DOUBLE_TO_BOOLEAN.invoke(delegate));
      } else if (resultType().equals(Type.LONG_TYPE)) {
        return Branch.ifNotZero(delegate);
      } else {
        throw new AssertionError(
            "resultType(): " + resultType() + " is not a valid type for a SoyExpression");
      }
    }
    if (soyType().equals(NullType.getInstance())) {
      return Branch.never();
    }
    if (isBoxed()) {
      // If we are boxed, just call the SoyValue method
      if (delegate.isNonJavaNullable()) {
        return Branch.ifTrue(delegate.invoke(MethodRef.SOY_VALUE_COERCE_TO_BOOLEAN));
      } else {
        return Branch.ifTrue(MethodRef.RUNTIME_COERCE_TO_BOOLEAN.invoke(delegate));
      }
    }
    // unboxed non-primitive types.  This would be strings, protos or lists
    if (resultType().equals(STRING_TYPE)) {
      return isNonJavaNullable()
          ? Branch.ifTrue(delegate.invoke(MethodRef.STRING_IS_EMPTY)).negate()
          : Branch.ifTrue(MethodRef.RUNTIME_COERCE_STRING_TO_BOOLEAN.invoke(delegate));
    }
    // All other types are always truthy unless null
    return Branch.ifNonSoyNullish(delegate);
  }

  /** Coerce this expression to a boolean value. */
  public SoyExpression coerceToBoolean() {
    return forBool(compileToBranch().asBoolean());
  }

  /** Coerce this expression to a string value. */
  public SoyExpression coerceToString() {
    if (soyRuntimeType.isKnownString() && !isBoxed()) {
      if (isNonJavaNullable()) {
        return this;
      } else {
        return forString(MethodRef.STRING_VALUE_OF.invoke(delegate));
      }
    }
    if (BytecodeUtils.isPrimitive(resultType())) {
      if (resultType().equals(Type.BOOLEAN_TYPE)) {
        return forString(MethodRef.BOOLEAN_TO_STRING.invoke(delegate));
      } else if (resultType().equals(Type.DOUBLE_TYPE)) {
        return forString(MethodRef.DOUBLE_TO_STRING.invoke(delegate));
      } else if (resultType().equals(Type.LONG_TYPE)) {
        return forString(MethodRef.LONG_TO_STRING.invoke(delegate));
      } else {
        throw new AssertionError(
            "resultType(): " + resultType() + " is not a valid type for a SoyExpression");
      }
    }
    if (!isBoxed()) {
      // this is for unboxed reference types (strings, lists, protos) String.valueOf handles null
      // implicitly
      return forString(MethodRef.STRING_VALUE_OF.invoke(delegate));
    }
    if (isNonJavaNullable()) {
      return forString(MethodRef.SOY_VALUE_COERCE_TO_STRING.invoke(delegate));
    }
    return forString(MethodRef.RUNTIME_COERCE_TO_STRING.invoke(delegate));
  }

  /** Coerce this expression to a double value. Useful for float-int comparisons. */
  public SoyExpression coerceToDouble() {
    if (!isBoxed()) {
      if (soyRuntimeType.isKnownFloat()) {
        return this;
      }
      if (soyRuntimeType.isKnownInt()) {
        return forFloat(BytecodeUtils.numericConversion(delegate, Type.DOUBLE_TYPE));
      }
      throw new UnsupportedOperationException("Can't convert " + resultType() + " to a double");
    }
    if (soyRuntimeType.isKnownFloat()) {
      return forFloat(delegate.invoke(MethodRef.SOY_VALUE_FLOAT_VALUE));
    }
    return forFloat(delegate.invoke(MethodRef.SOY_VALUE_NUMBER_VALUE));
  }

  /**
   * Returns an expression of type {@link Number}. Appropriate when preparing a parameter for an
   * extern or plugin implementation, which both expect Java null rather than NullData or
   * UndefinedData.
   */
  public Expression unboxAsNumberOrJavaNull() {
    if (!isBoxed()) {
      if (soyRuntimeType.isKnownFloat()) {
        return MethodRef.BOX_DOUBLE.invoke(this);
      }
      if (soyRuntimeType.isKnownInt()) {
        return MethodRef.BOX_LONG.invoke(this);
      }
      throw new UnsupportedOperationException("Can't convert " + resultType() + " to a Number");
    }
    if (isNonSoyNullish()) {
      return MethodRef.SOY_VALUE_JAVA_NUMBER_VALUE.invoke(
          this.checkedCast(BytecodeUtils.NUMBER_DATA_TYPE));
    }
    return new Expression(BytecodeUtils.NUMBER_TYPE, featuresAfterUnboxing()) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        Label end = new Label();
        delegate.gen(adapter);
        BytecodeUtils.coalesceSoyNullishToJavaNull(adapter, delegate.resultType(), end);
        adapter.checkCast(BytecodeUtils.NUMBER_DATA_TYPE);
        MethodRef.SOY_VALUE_JAVA_NUMBER_VALUE.invokeUnchecked(adapter);
        adapter.mark(end);
      }
    };
  }

  /**
   * Unboxes this to a {@link SoyExpression} with a boolean runtime type.
   *
   * <p>This method is appropriate when you know (likely via inspection of the {@link #soyType()},
   * or other means) that the value does have the appropriate type but you prefer to interact with
   * it as its unboxed representation. If you simply want to 'coerce' the given value to a new type
   * consider {@link #coerceToBoolean()} which is designed for that use case.
   */
  public SoyExpression unboxAsBoolean() {
    if (alreadyUnboxed(boolean.class)) {
      return this;
    }
    assertBoxed(boolean.class);

    return forBool(delegate.invoke(MethodRef.SOY_VALUE_BOOLEAN_VALUE));
  }

  /**
   * Unboxes this to a {@link SoyExpression} with a long runtime type.
   *
   * <p>This method is appropriate when you know (likely via inspection of the {@link #soyType()},
   * or other means) that the value does have the appropriate type but you prefer to interact with
   * it as its unboxed representation. If you simply want to 'coerce' the given value to a new type
   * consider {@link #coerceToDouble()} which is designed for that use case.
   */
  public SoyExpression unboxAsLong() {
    if (alreadyUnboxed(long.class)) {
      return this;
    }
    assertBoxed(long.class);

    return forInt(delegate.invoke(MethodRef.SOY_VALUE_LONG_VALUE));
  }

  /**
   * Unboxes this to a {@link SoyExpression} with an `int` runtime type.
   *
   * <p>This method is appropriate when you know (likely via inspection of the {@link #soyType()},
   * or other means) that the value does have the appropriate type but you prefer to interact with
   * it as its unboxed representation. If you simply want to 'coerce' the given value to a new type
   * consider {@link #coerceToDouble()} which is designed for that use case.
   */
  public Expression unboxAsInt() {
    if (alreadyUnboxed(long.class)) {
      return MethodRef.INTS_CHECKED_CAST.invoke(this);
    }
    return box().invoke(MethodRef.SOY_VALUE_INTEGER_VALUE);
  }

  /**
   * Unboxes this to a {@link SoyExpression} with a double runtime type.
   *
   * <p>This method is appropriate when you know (likely via inspection of the {@link #soyType()},
   * or other means) that the value does have the appropriate type but you prefer to interact with
   * it as its unboxed representation. If you simply want to 'coerce' the given value to a new type
   * consider {@link #coerceToDouble()} which is designed for that use case.
   */
  public SoyExpression unboxAsDouble() {
    if (alreadyUnboxed(double.class)) {
      return this;
    }
    assertBoxed(double.class);

    return forFloat(delegate.invoke(MethodRef.SOY_VALUE_FLOAT_VALUE));
  }

  private Features featuresAfterUnboxing() {
    Features features = features();
    if (!features.has(Feature.NON_SOY_NULLISH)) {
      features = features.minus(Feature.NON_JAVA_NULLABLE);
    }
    return features;
  }

  /** Unboxes a string. Throws an exception if the boxed value is nullish. */
  public SoyExpression unboxAsStringUnchecked() {
    return this.asNonSoyNullish().unboxAsStringOrJavaNull();
  }

  /**
   * Unboxes a value, converting Soy nullish to Java null. Appropriate when preparing a parameter
   * for an extern or plugin implementation, which both expect Java null rather than NullData or
   * UndefinedData.
   */
  public SoyExpression unboxAsStringOrJavaNull() {
    if (alreadyUnboxed(String.class)) {
      return this;
    }
    assertBoxed(String.class);

    return forString(
        delegate.invoke(
            delegate.isNonSoyNullish()
                ? MethodRef.SOY_VALUE_STRING_VALUE
                : MethodRef.SOY_VALUE_STRING_VALUE_OR_NULL));
  }

  /** Unboxes a list and its items. Throws an exception if the boxed list value is nullish. */
  public SoyExpression unboxAsListUnchecked() {
    return this.asNonSoyNullish().unboxAsListOrJavaNull();
  }

  /**
   * Unboxes a list; a Soy nullish value is returned as Java null. Appropriate when preparing a
   * parameter for an extern or plugin implementation, which both expect Java null rather than
   * NullData or UndefinedData.
   */
  public SoyExpression unboxAsListOrJavaNull() {
    if (alreadyUnboxed(List.class)) {
      return this;
    }
    assertBoxed(List.class);

    Expression unboxedList =
        delegate.invoke(
            delegate.isNonSoyNullish()
                ? MethodRef.SOY_VALUE_AS_JAVA_LIST
                : MethodRef.SOY_VALUE_AS_JAVA_LIST_OR_NULL);

    ListType asListType;
    SoyRuntimeType nonNullRuntimeType =
        SoyRuntimeType.getBoxedType(SoyTypes.tryRemoveNullish(soyType()));
    if (!SoyTypes.isNullOrUndefined(soyType()) && nonNullRuntimeType.isKnownListOrUnionOfLists()) {
      asListType = nonNullRuntimeType.asListType();
    } else {
      if (soyType().getKind() == Kind.UNKNOWN || soyType().isNullOrUndefined()) {
        asListType = ListType.of(UnknownType.getInstance());
      } else {
        // The type checker should have already rejected all of these
        throw new UnsupportedOperationException("Can't convert " + soyRuntimeType + " to List");
      }
    }
    return forList(asListType, unboxedList);
  }

  /** Unboxes a proto message. Throws an exception if the boxed value is nullish. */
  public Expression unboxAsMessageUnchecked(Type runtimeType) {
    return this.asNonSoyNullish().unboxAsMessageOrJavaNull(runtimeType);
  }

  /**
   * Unboxes a proto message, converting Soy nullish to Java null. Appropriate when preparing a
   * parameter for an extern or plugin implementation, which both expect Java null rather than
   * NullData or UndefinedData.
   */
  public Expression unboxAsMessageOrJavaNull(Type runtimeType) {
    if (soyType().isNullOrUndefined()) {
      // If this is a null literal, return a Messaged-typed null literal.
      return BytecodeUtils.constantNull(runtimeType);
    }
    // Attempting to unbox an unboxed proto
    // (We compare the non-nullable type because being null doesn't impact unboxability,
    //  and if we didn't remove null then isKnownProtoOrUnionOfProtos would fail.)
    if (soyRuntimeType.asNonSoyNullish().isKnownProtoOrUnionOfProtos() && !isBoxed()) {
      // Any unboxed proto must be either a concrete proto or a message.
      // if we are unboxing to Message then there is no need to cast.
      if (delegate.resultType().equals(runtimeType)
          || runtimeType.equals(BytecodeUtils.MESSAGE_TYPE)) {
        return this;
      } else {
        return this.delegate.checkedCast(runtimeType);
      }
    }
    return delegate
        .invoke(
            delegate.isNonSoyNullish()
                ? MethodRef.SOY_VALUE_GET_PROTO
                : MethodRef.SOY_VALUE_GET_PROTO_OR_NULL)
        .checkedCast(runtimeType);
  }

  private boolean alreadyUnboxed(Class<?> asType) {
    return BytecodeUtils.isDefinitelyAssignableFrom(
        Type.getType(asType), soyRuntimeType.runtimeType());
  }

  private void assertBoxed(Class<?> asType) {
    if (!isBoxed()) {
      throw new IllegalStateException(
          "Trying to unbox an unboxed value ("
              + soyRuntimeType
              + ") into "
              + asType
              + " doesn't make sense. Should you be using a type coercion? e.g. coerceToBoolean()");
    }
  }

  /** Returns a new {@link SoyExpression} with the same type but a new delegate expression. */
  public SoyExpression withSource(Expression expr) {
    if (expr == delegate) {
      return this;
    }
    return new SoyExpression(soyRuntimeType, expr);
  }

  @Override
  public SoyExpression asCheap() {
    return withSource(delegate.asCheap());
  }

  @Override
  public SoyExpression asNonJavaNullable() {
    return new SoyExpression(soyRuntimeType, delegate.asNonJavaNullable());
  }

  @Override
  public SoyExpression asJavaNullable() {
    return new SoyExpression(soyRuntimeType, delegate.asJavaNullable());
  }

  @Override
  public SoyExpression asNonSoyNullish() {
    return new SoyExpression(soyRuntimeType.asNonSoyNullish(), delegate.asNonSoyNullish());
  }

  @Override
  public SoyExpression asSoyNullish() {
    return new SoyExpression(soyRuntimeType.asSoyNullish(), delegate.asSoyNullish());
  }

  @Override
  public SoyExpression labelStart(Label label) {
    return withSource(delegate.labelStart(label));
  }

  @Override
  public SoyExpression labelEnd(Label label) {
    return withSource(delegate.labelEnd(label));
  }

  @Override
  protected void extraToStringProperties(MoreObjects.ToStringHelper helper) {
    helper.add("soyType", soyType());
  }
}
