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
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.BOOLEAN_DATA_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.NUMBER_DATA_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_ITERABLE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_LIST_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_MAP_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_PROTO_VALUE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_RECORD_IMPL_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_SET_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_PROVIDER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.STRING_DATA_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.STRING_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.isDefinitelyAssignableFrom;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.isNumericPrimitive;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.newLabel;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.numericConversion;

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
import com.google.template.soy.types.SetType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UndefinedType;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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
      SoyExpression soyExpression = (SoyExpression) delegate;
      SoyRuntimeType runtimeType =
          soyExpression.isBoxed() ? SoyRuntimeType.getBoxedType(type) : getUnboxedType(type);
      return new SoyExpression(runtimeType, soyExpression.delegate);
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

  public static SoyExpression forSet(SetType setType, Expression delegate) {
    return new SoyExpression(getUnboxedType(setType), delegate);
  }

  @DoNotCall
  public static SoyExpression forSet(SetType setType, SoyExpression delegate) {
    throw new UnsupportedOperationException();
  }

  public static SoyExpression forBoxedList(SoyType listType, Expression delegate) {
    Preconditions.checkArgument(ListType.ANY_LIST.isAssignableFromStrict(listType));
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
        || !isDefinitelyAssignableFrom(BytecodeUtils.SOY_VALUE_TYPE, delegate.resultType())) {
      delegate = delegate.invoke(MethodRefs.SOY_VALUE_PROVIDER_RESOLVE);
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
        != isDefinitelyAssignableFrom(SOY_VALUE_PROVIDER_TYPE, delegate.resultType())) {
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
  public SoyExpression toConstantExpression() {
    return withSource(delegate.toConstantExpression());
  }

  @Override
  public SoyExpression withConstantValue(ConstantValue constantValue) {
    return withSource(delegate.withConstantValue(constantValue));
  }

  @Override
  public boolean isConstant() {
    return delegate.isConstant();
  }

  @Override
  public ConstantValue constantValue() {
    return delegate.constantValue();
  }

  @Override
  public Object constantBytecodeValue() {
    return delegate.constantBytecodeValue();
  }

  @Override
  public SoyExpression toMaybeConstant() {
    Expression newDelegate = delegate.toMaybeConstant();
    if (newDelegate != delegate) {
      return withSource(newDelegate);
    }
    return this;
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

  public boolean isRuntimeInt() {
    return soyRuntimeType.runtimeType().getSort() == Type.INT
        || soyRuntimeType.runtimeType().getSort() == Type.LONG;
  }

  public boolean isRuntimeFloat() {
    return soyRuntimeType.runtimeType().getSort() == Type.FLOAT
        || soyRuntimeType.runtimeType().getSort() == Type.DOUBLE;
  }

  public boolean isRuntimeNumber() {
    return isRuntimeInt() || isRuntimeFloat();
  }

  public boolean isKnownString() {
    return soyRuntimeType.isKnownString();
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
      if (delegate.isConstant() && delegate.constantValue().hasJavaValue()) {
        boolean value = delegate.constantValue().getJavaValueAsType(Boolean.class).get();
        if (value) {
          return asBoxed(FieldRef.BOOLEAN_DATA_TRUE.accessor());
        } else {
          return asBoxed(FieldRef.BOOLEAN_DATA_FALSE.accessor());
        }
      }
      return asBoxed(MethodRefs.BOOLEAN_DATA_FOR_VALUE.invoke(delegate).toMaybeConstant());
    }
    if (soyRuntimeType.isKnownInt()) {
      return asBoxed(MethodRefs.INTEGER_DATA_FOR_VALUE.invoke(delegate).toMaybeConstant());
    }
    if (soyRuntimeType.isKnownFloat()) {
      return asBoxed(MethodRefs.FLOAT_DATA_FOR_VALUE.invoke(delegate).toMaybeConstant());
    }
    // If null is expected and it is a reference type we want to propagate null through the boxing
    // operation
    boolean nonNullable = delegate.isNonJavaNullable();
    Features features = features().plus(Feature.NON_JAVA_NULLABLE);
    if (nonNullable) {
      features = features.plus(Feature.NON_SOY_NULLISH);
    }
    return asBoxed(
        nonNullable
            ? doBoxNonJavaNullable(soyRuntimeType)
            : new Expression(soyRuntimeType.box().runtimeType(), features) {
              @Override
              protected void doGen(CodeBuilder adapter) {
                Label end = newLabel();
                delegate.gen(adapter);
                BytecodeUtils.coalesceSoyNullishToSoyNull(adapter, delegate.resultType(), end);
                doBox(adapter, soyRuntimeType.asNonSoyNullish());
                adapter.mark(end);
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
      return withSource(MethodRefs.SOY_NULLISH_TO_JAVA_NULL.invoke(delegate));
    }
    return asBoxed(
        new Expression(soyRuntimeType.box().runtimeType()) {
          @Override
          protected void doGen(CodeBuilder adapter) {
            Label end = newLabel();
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
      return withSource(MethodRefs.SOY_NULL_TO_JAVA_NULL.invoke(delegate));
    }
    return asBoxed(
        new Expression(soyRuntimeType.box().runtimeType()) {
          @Override
          protected void doGen(CodeBuilder adapter) {
            Label end = newLabel();
            delegate.gen(adapter);
            BytecodeUtils.coalesceSoyNullToJavaNull(adapter, delegate.resultType(), end);
            doBox(adapter, soyRuntimeType.asNonSoyNullish());
            adapter.mark(end);
          }
        });
  }

  /**
   * Generates code to box the expression assuming that it is non-java nullable and on the top of
   * the stack.
   */
  public static void doBox(CodeBuilder adapter, SoyRuntimeType type) {
    if (type.isKnownSanitizedContent()) {
      ContentKind kind =
          Converters.toContentKind(type.soyType().asType(SanitizedType.class).getContentKind());
      checkState(kind != ContentKind.TEXT); // sanity check
      FieldRef.enumReference(kind).accessStaticUnchecked(adapter);
      MethodRefs.ORDAIN_AS_SAFE.invokeUnchecked(adapter);
    } else if (type.isKnownString()) {
      MethodRefs.STRING_DATA_FOR_VALUE.invokeUnchecked(adapter);
    } else if (type.isKnownGbigint()) {
      MethodRefs.GBIGINT_DATA_FOR_VALUE.invokeUnchecked(adapter);
    } else if (type.isKnownListOrUnionOfLists()) {
      MethodRefs.LIST_IMPL_FOR_PROVIDER_LIST.invokeUnchecked(adapter);
    } else if (type.isKnownSet()) {
      MethodRefs.SET_IMPL_FOR_PROVIDER_SET.invokeUnchecked(adapter);
    } else if (type.isKnownLegacyObjectMapOrUnionOfMaps()) {
      FieldRef.enumReference(RuntimeMapTypeTracker.Type.LEGACY_OBJECT_MAP_OR_RECORD)
          .putUnchecked(adapter);
      MethodRefs.DICT_IMPL_FOR_PROVIDER_MAP.invokeUnchecked(adapter);
    } else if (type.isKnownMapOrUnionOfMaps()) {
      MethodRefs.MAP_IMPL_FOR_PROVIDER_MAP.invokeUnchecked(adapter);
    } else if (type.isKnownProtoOrUnionOfProtos()) {
      MethodRefs.SOY_PROTO_VALUE_CREATE.invokeUnchecked(adapter);
    } else {
      throw new IllegalStateException("Can't box soy expression of type " + type);
    }
  }

  /**
   * Generates code to box the expression assuming that it is non-java nullable and on the top of
   * the stack.
   */
  private Expression doBoxNonJavaNullable(SoyRuntimeType type) {
    if (type.isKnownSanitizedContent()) {
      ContentKind kind =
          Converters.toContentKind(type.soyType().asType(SanitizedType.class).getContentKind());
      checkState(kind != ContentKind.TEXT); // sanity check
      return MethodRefs.ORDAIN_AS_SAFE
          .invoke(delegate, FieldRef.enumReference(kind).accessor())
          .toMaybeConstant();
    } else if (type.isKnownString()) {
      if (delegate.isConstant()) {
        String constantValue =
            delegate.constantValue().getJavaValueAsType(String.class).orElse(null);
        if (constantValue != null && constantValue.isEmpty()) {
          // Special case the boxed empty string
          return FieldRef.EMPTY_STRING_DATA.accessor().withSourceLocation(delegate.location);
        }
      }
      return MethodRefs.STRING_DATA_FOR_VALUE.invoke(delegate).toMaybeConstant();
    } else if (type.isKnownGbigint()) {
      return MethodRefs.GBIGINT_DATA_FOR_VALUE.invoke(delegate).toMaybeConstant();
    } else if (type.isKnownListOrUnionOfLists()) {
      return MethodRefs.LIST_IMPL_FOR_PROVIDER_LIST.invoke(delegate);
    } else if (type.isKnownSet()) {
      return MethodRefs.SET_IMPL_FOR_PROVIDER_SET.invoke(delegate);
    } else if (type.isKnownLegacyObjectMapOrUnionOfMaps()) {
      return MethodRefs.DICT_IMPL_FOR_PROVIDER_MAP.invoke(
          delegate,
          FieldRef.enumReference(RuntimeMapTypeTracker.Type.LEGACY_OBJECT_MAP_OR_RECORD)
              .accessor());
    } else if (type.isKnownMapOrUnionOfMaps()) {
      return MethodRefs.MAP_IMPL_FOR_PROVIDER_MAP.invoke(delegate);
    } else if (type.isKnownProtoOrUnionOfProtos()) {
      // no builder methods are 'constable' but `getDefaultInstance` is, so this is safe
      return MethodRefs.SOY_PROTO_VALUE_CREATE.invoke(delegate).toMaybeConstant();
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
        return Branch.ifTrue(
            MethodRefs.RUNTIME_COERCE_DOUBLE_TO_BOOLEAN.invoke(delegate).toMaybeConstant());
      } else if (resultType().equals(Type.LONG_TYPE)) {
        if (delegate.isConstant()) {
          Long maybeLong = delegate.constantValue().getJavaValueAsType(Long.class).orElse(null);
          if (maybeLong == null) {
            return maybeLong == 0L ? Branch.never() : Branch.never().negate();
          }
        }
        return Branch.ifNotZero(delegate);
      } else {
        throw new AssertionError(
            "resultType(): " + resultType() + " is not a valid type for a SoyExpression");
      }
    }
    if (SoyTypes.isNullOrUndefined(soyType())) {
      return Branch.never();
    }
    if (isBoxed()) {
      // If we are boxed, just call the SoyValue method
      return Branch.ifTrue(
          delegate.invoke(MethodRefs.SOY_VALUE_COERCE_TO_BOOLEAN).toMaybeConstant());
    }
    // unboxed non-primitive types.  This would be strings, protos or lists
    if (resultType().equals(STRING_TYPE)) {
      return isNonJavaNullable()
          ? Branch.ifTrue(delegate.invoke(MethodRefs.STRING_IS_EMPTY).toMaybeConstant()).negate()
          : Branch.ifTrue(
              MethodRefs.RUNTIME_COERCE_STRING_TO_BOOLEAN.invoke(delegate).toMaybeConstant());
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
        return forString(MethodRefs.STRING_VALUE_OF.invoke(delegate));
      }
    }
    if (BytecodeUtils.isPrimitive(resultType())) {
      if (resultType().equals(Type.BOOLEAN_TYPE)) {
        return forString(MethodRefs.BOOLEAN_TO_STRING.invoke(delegate).toMaybeConstant());
      } else if (resultType().equals(Type.DOUBLE_TYPE)) {
        return forString(MethodRefs.DOUBLE_TO_STRING.invoke(delegate).toMaybeConstant());
      } else if (resultType().equals(Type.LONG_TYPE)) {
        return forString(MethodRefs.LONG_TO_STRING.invoke(delegate).toMaybeConstant());
      } else {
        throw new AssertionError(
            "resultType(): " + resultType() + " is not a valid type for a SoyExpression");
      }
    }
    if (!isBoxed()) {
      // Box values before coercing to string so we don't depend on toString() that we don't
      // control.
      return this.box().coerceToString();
    }
    return forString(MethodRefs.SOY_VALUE_COERCE_TO_STRING.invoke(delegate).toMaybeConstant());
  }

  /** Coerce this expression to a double value. Useful for float-int comparisons. */
  public SoyExpression coerceToDouble() {
    if (!isBoxed()) {
      if (soyRuntimeType.isKnownFloat()) {
        return this;
      }
      if (soyRuntimeType.isKnownInt()) {
        return forFloat(
            BytecodeUtils.numericConversion(delegate, Type.DOUBLE_TYPE).toMaybeConstant());
      }
      throw new UnsupportedOperationException("Can't convert " + resultType() + " to a double");
    }
    return forFloat(delegate.invoke(MethodRefs.SOY_VALUE_FLOAT_VALUE).toMaybeConstant());
  }

  public SoyExpression coerceToLong() {
    if (!isBoxed() && soyRuntimeType.isKnownFloat()) {
      return forInt(numericConversion(delegate, Type.LONG_TYPE));
    }
    return unboxAsLong();
  }

  /**
   * Returns an expression of type {@link Number}. Appropriate when preparing a parameter for an
   * extern or plugin implementation, which both expect Java null rather than NullData or
   * UndefinedData.
   */
  public Expression unboxAsNumberOrJavaNull() {
    if (!isBoxed()) {
      if (soyRuntimeType.isKnownFloat()) {
        return MethodRefs.BOX_DOUBLE.invoke(this).toMaybeConstant();
      }
      if (soyRuntimeType.isKnownInt()) {
        return MethodRefs.BOX_LONG.invoke(this).toMaybeConstant();
      }
      throw new UnsupportedOperationException("Can't convert " + resultType() + " to a Number");
    }
    if (isNonSoyNullish()) {
      return MethodRefs.SOY_VALUE_JAVA_NUMBER_VALUE.invoke(this);
    }
    return new Expression(BytecodeUtils.NUMBER_TYPE, featuresAfterUnboxing()) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        Label end = newLabel();
        delegate.gen(adapter);
        BytecodeUtils.coalesceSoyNullishToJavaNull(adapter, delegate.resultType(), end);

        MethodRefs.SOY_VALUE_JAVA_NUMBER_VALUE.invokeUnchecked(adapter);
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

    return forBool(
        delegateWithoutCast().invoke(MethodRefs.SOY_VALUE_BOOLEAN_VALUE).toMaybeConstant());
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

    return forInt(delegateWithoutCast().invoke(MethodRefs.SOY_VALUE_LONG_VALUE));
  }

  public SoyExpression coerceToIndex() {
    if (alreadyUnboxed(long.class)) {
      return this;
    } else {
      return forInt(box().invoke(MethodRefs.SOY_VALUE_ITEM_INDEX_VALUE));
    }
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
      return checkedCastLongToInt(this);
    }
    assertBoxed(long.class);
    return delegateWithoutCast().invoke(MethodRefs.SOY_VALUE_INTEGER_VALUE);
  }

  public Expression coerceToInt() {
    if (soyRuntimeType.isKnownInt()) {
      return unboxAsInt();
    }
    return checkedCastLongToInt(
        BytecodeUtils.numericConversion(
            soyRuntimeType.isKnownFloat() ? unboxAsDouble() : coerceToDouble(), Type.LONG_TYPE));
  }

  private static Expression checkedCastLongToInt(Expression delegate) {
    if (delegate.isConstant()) {
      Long javaValue = delegate.constantValue().getJavaValueAsType(Long.class).orElse(null);
      if (javaValue != null) {
        long longValue = javaValue;
        if (longValue == (int) longValue) {
          return constant((int) longValue);
        }
        // otherwise fall through and generate code that issues a runtime exception
      }
    }
    return MethodRefs.INTS_CHECKED_CAST.invoke(delegate);
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

    return forFloat(delegateWithoutCast().invoke(MethodRefs.SOY_VALUE_FLOAT_VALUE));
  }

  private Features featuresAfterUnboxing() {
    Features features = features();
    if (!features.has(Feature.NON_SOY_NULLISH)) {
      features = features.minus(Feature.NON_JAVA_NULLABLE);
    }
    return features;
  }

  /** Removes a soy cast, useful if/when we are generating a checked unboxing operation. */
  private Expression delegateWithoutCast() {
    if (delegate instanceof Expression.SoyCastExpression) {
      return ((Expression.SoyCastExpression) delegate).getOriginal();
    }
    return delegate;
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

    var delegate = delegateWithoutCast();
    return forString(
        delegate.invoke(
            delegate.isNonSoyNullish()
                ? MethodRefs.SOY_VALUE_STRING_VALUE
                : MethodRefs.SOY_VALUE_STRING_VALUE_OR_NULL));
  }

  public Expression unboxAsIteratorUnchecked() {
    if (alreadyUnboxed(Iterator.class)) {
      return this;
    }
    if (alreadyUnboxed(Iterable.class)) {
      return delegateWithoutCast().invoke(MethodRefs.GET_ITERATOR);
    }
    assertBoxed(SoyValue.class);
    return delegateWithoutCast().invoke(MethodRefs.SOY_VALUE_AS_JAVA_ITERATOR);
  }

  /** Unboxes a list and its items. Throws an exception if the boxed list value is nullish. */
  public Expression unboxAsListUnchecked() {
    return this.asNonSoyNullish().unboxAsListOrJavaNull();
  }

  /**
   * Unboxes a list; a Soy nullish value is returned as Java null. Appropriate when preparing a
   * parameter for an extern or plugin implementation, which both expect Java null rather than
   * NullData or UndefinedData.
   */
  public Expression unboxAsListOrJavaNull() {
    if (alreadyUnboxed(List.class)) {
      return this;
    }
    assertBoxed(List.class);
    return delegateWithoutCast()
        .invoke(
            delegate.isNonSoyNullish()
                ? MethodRefs.SOY_VALUE_AS_JAVA_LIST
                : MethodRefs.SOY_VALUE_AS_JAVA_LIST_OR_NULL);
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
    if (SoyTypes.isNullOrUndefined(soyType())) {
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
    var delegate = delegateWithoutCast();
    return delegate
        .invoke(
            delegate.isNonSoyNullish()
                ? MethodRefs.SOY_VALUE_GET_PROTO
                : MethodRefs.SOY_VALUE_GET_PROTO_OR_NULL)
        .checkedCast(runtimeType);
  }

  private SoyExpression instanceOfUnboxed(SoyType soyType) {
    // The optimization pass removes some but not all of these.
    Boolean staticValue = null;
    switch (soyType.getKind()) {
      case STRING:
        staticValue = alreadyUnboxed(String.class);
        break;
      case BOOL:
        staticValue = Type.BOOLEAN_TYPE == soyRuntimeType.runtimeType();
        break;
      case NUMBER:
        staticValue = isNumericPrimitive(soyRuntimeType.runtimeType());
        break;
      case LIST:
        staticValue = alreadyUnboxed(List.class);
        break;
      case SET:
        staticValue = alreadyUnboxed(Set.class);
        break;
      case GBIGINT:
        staticValue = alreadyUnboxed(BigInteger.class);
        break;
      default:
        break;
    }
    if (staticValue == null) {
      throw new IllegalArgumentException("" + soyRuntimeType);
    }
    return staticValue ? TRUE : FALSE;
  }

  /**
   * JBCSRC implementation of the `instanceof` operator.
   *
   * @param soyType must be a valid `instanceof` type operand.
   */
  public SoyExpression instanceOf(SoyType soyType) {
    if (!isBoxed()) {
      return instanceOfUnboxed(soyType);
    }
    Type type = null;
    switch (soyType.getKind()) {
      case STRING:
        type = STRING_DATA_TYPE;
        break;
      case BOOL:
        type = BOOLEAN_DATA_TYPE;
        break;
      case NUMBER:
        type = NUMBER_DATA_TYPE;
        break;
      case RECORD:
        type = SOY_RECORD_IMPL_TYPE;
        break;
      case LIST:
        type = SOY_LIST_TYPE;
        break;
      case SET:
        type = SOY_SET_TYPE;
        break;
      case ITERABLE:
        type = SOY_ITERABLE_TYPE;
        break;
      case MAP:
        type = SOY_MAP_TYPE;
        break;
      case MESSAGE:
        type = SOY_PROTO_VALUE_TYPE;
        break;
      case PROTO:
        Type protoType = SoyRuntimeType.protoType(((SoyProtoType) soyType).getDescriptor());
        return forBool(MethodRefs.IS_PROTO_INSTANCE.invoke(delegate, constant(protoType)));
      case HTML:
      case URI:
      case CSS:
      case JS:
      case TRUSTED_RESOURCE_URI:
      case ATTRIBUTES:
        ContentKind kind = Converters.toContentKind(((SanitizedType) soyType).getContentKind());
        return forBool(MethodRefs.IS_SANITIZED_CONTENT_KIND.invoke(delegate, constant(kind)));
      default:
        break;
    }
    if (type != null) {
      return forBool(BytecodeUtils.instanceOf(delegate, type));
    }
    throw new IllegalArgumentException("" + soyType);
  }

  private boolean alreadyUnboxed(Class<?> asType) {
    return isDefinitelyAssignableFrom(Type.getType(asType), soyRuntimeType.runtimeType());
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
    return withSource(delegate.asNonJavaNullable());
  }

  @Override
  public SoyExpression asJavaNullable() {
    return withSource(delegate.asJavaNullable());
  }

  @Override
  public SoyExpression asNonSoyNullish() {
    return new SoyExpression(soyRuntimeType.asNonSoyNullish(), delegate.asNonSoyNullish());
  }

  public SoyExpression asSoyUndefinable() {
    return new SoyExpression(soyRuntimeType.asSoyUndefinable(), delegate.asSoyNullish());
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

  /** Generic coercion to a runtime type, possibly boxed or unboxed. */
  public SoyExpression coerceTo(Type runtimeType) {
    switch (runtimeType.getSort()) {
      case Type.LONG:
        if (isBoxed()) {
          return forInt(MethodRefs.SOY_VALUE_LONG_VALUE.invoke(delegate));
        } else {
          return forInt(BytecodeUtils.numericConversion(delegate, Type.LONG_TYPE));
        }
      case Type.INT:
        if (isBoxed()) {
          return forInt(MethodRefs.SOY_VALUE_INTEGER_VALUE.invoke(delegate));
        } else {
          return forInt(BytecodeUtils.numericConversion(delegate, Type.INT_TYPE));
        }
      case Type.DOUBLE:
        return coerceToDouble();
      case Type.BOOLEAN:
        return unboxAsBoolean();
      case Type.OBJECT:
        if (BytecodeUtils.isDefinitelyAssignableFrom(BytecodeUtils.SOY_VALUE_TYPE, runtimeType)) {
          SoyExpression boxed = box();
          // Field may be a subclass of SoyValue, so need to perform a cast.
          return SoyExpression.forSoyValue(
              boxed.soyType(), boxed.delegate.checkedCast(runtimeType));
        }
        // Unhandled cases where we have a boxed value (string, list, message) but require an
        // unboxed value.
    }
    return this;
  }
}
