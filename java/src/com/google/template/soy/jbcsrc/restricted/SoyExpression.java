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
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.OBJECT;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_LIST_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.logicalNot;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
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
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnknownType;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
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
    return new SoyExpression(SoyRuntimeType.getBoxedType(type), delegate);
  }

  public static SoyExpression forBool(Expression delegate) {
    return new SoyExpression(getUnboxedType(BoolType.getInstance()), delegate);
  }

  public static SoyExpression forFloat(Expression delegate) {
    return new SoyExpression(getUnboxedType(FloatType.getInstance()), delegate);
  }

  public static SoyExpression forInt(Expression delegate) {
    return new SoyExpression(getUnboxedType(IntType.getInstance()), delegate);
  }

  public static SoyExpression forString(Expression delegate) {
    return new SoyExpression(getUnboxedType(StringType.getInstance()), delegate);
  }

  public static SoyExpression forSanitizedString(Expression delegate, SanitizedContentKind kind) {
    return new SoyExpression(getUnboxedType(SanitizedType.getTypeForContentKind(kind)), delegate);
  }

  public static SoyExpression forList(ListType listType, Expression delegate) {
    return new SoyExpression(getUnboxedType(listType), delegate);
  }

  public static SoyExpression forLegacyObjectMap(LegacyObjectMapType mapType, Expression delegate) {
    return new SoyExpression(SoyRuntimeType.getBoxedType(mapType), delegate);
  }

  public static SoyExpression forMap(MapType mapType, Expression delegate) {
    return new SoyExpression(SoyRuntimeType.getBoxedType(mapType), delegate);
  }

  public static SoyExpression forProto(SoyRuntimeType type, Expression delegate) {
    checkArgument(type.soyType().getKind() == Kind.PROTO);
    return new SoyExpression(type, delegate);
  }

  /**
   * Returns an Expression that evaluates to a list containing all the items as boxed soy values.
   */
  public static Expression asBoxedList(List<SoyExpression> items) {
    List<Expression> childExprs = new ArrayList<>(items.size());
    for (SoyExpression child : items) {
      childExprs.add(child.box());
    }
    return BytecodeUtils.asList(childExprs);
  }

  public static final SoyExpression NULL =
      new SoyExpression(
          getUnboxedType(NullType.getInstance()),
          new Expression(OBJECT.type(), Feature.CHEAP) {
            @Override
            protected void doGen(CodeBuilder cb) {
              cb.pushNull();
            }
          });

  public static final SoyExpression NULL_BOXED =
      new SoyExpression(
          SoyRuntimeType.getBoxedType(NullType.getInstance()),
          new Expression(SOY_VALUE_TYPE, Feature.CHEAP) {
            @Override
            protected void doGen(CodeBuilder cb) {
              cb.pushNull();
            }
          });

  public static final SoyExpression TRUE = forBool(BytecodeUtils.constant(true));

  public static final SoyExpression FALSE = forBool(BytecodeUtils.constant(false));

  private static SoyRuntimeType getUnboxedType(SoyType soyType) {
    return SoyRuntimeType.getUnboxedType(soyType).get();
  }

  private final SoyRuntimeType soyRuntimeType;
  private final Expression delegate;

  private SoyExpression(SoyRuntimeType soyRuntimeType, Expression delegate) {
    super(delegate.resultType(), delegate.features());
    checkArgument(
        BytecodeUtils.isPossiblyAssignableFrom(soyRuntimeType.runtimeType(), delegate.resultType()),
        "Expecting SoyExpression type of %s for soy type %s, found delegate with type of %s",
        soyRuntimeType.runtimeType(),
        soyRuntimeType.soyType(),
        delegate.resultType());
    this.soyRuntimeType = soyRuntimeType;
    this.delegate = delegate;
  }

  /** Returns the {@link SoyType} of the expression. */
  public final SoyType soyType() {
    return soyRuntimeType.soyType();
  }

  /** Returns the {@link SoyRuntimeType} of the expression. */
  public final SoyRuntimeType soyRuntimeType() {
    return soyRuntimeType;
  }

  @Override
  protected final void doGen(CodeBuilder adapter) {
    delegate.gen(adapter);
  }

  @Override
  public SoyExpression withSourceLocation(SourceLocation location) {
    return withSource(delegate.withSourceLocation(location));
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

  /** Returns an Expression of a non-null {@link SoyValueProvider} providing this value. */
  public Expression boxAsSoyValueProvider() {
    if (soyType().equals(NullType.getInstance())) {
      if (delegate == NULL || delegate == NULL_BOXED) {
        return FieldRef.NULL_PROVIDER.accessor();
      }
      // otherwise this expression might have side effects,  evaluate it as a statement then return
      // the NULL_PROVIDER
      return toStatement().then(FieldRef.NULL_PROVIDER.accessor());
    }
    if (delegate.isNonNullable()) {
      // Every SoyValue is-a SoyValueProvider, so if it is non-null
      return box();
    }
    if (isBoxed()) {
      return new Expression(
          BytecodeUtils.SOY_VALUE_PROVIDER_TYPE, delegate.features().plus(Feature.NON_NULLABLE)) {
        @Override
        protected void doGen(CodeBuilder adapter) {
          Label end = new Label();
          delegate.gen(adapter);
          adapter.dup();
          adapter.ifNonNull(end);
          adapter.pop();
          FieldRef.NULL_PROVIDER.accessStaticUnchecked(adapter);
          adapter.mark(end);
        }
      };
    }
    return new Expression(
        BytecodeUtils.SOY_VALUE_PROVIDER_TYPE, delegate.features().plus(Feature.NON_NULLABLE)) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        Label end = new Label();
        delegate.gen(adapter);
        adapter.dup();
        Label nonNull = new Label();
        adapter.ifNonNull(nonNull);
        adapter.pop(); // pop the null value and replace with the nullprovider
        FieldRef.NULL_PROVIDER.accessStaticUnchecked(adapter);
        adapter.goTo(end);
        adapter.mark(nonNull);
        doBox(adapter, soyRuntimeType);
        adapter.mark(end);
      }
    };
  }

  /** Returns a SoyExpression that evaluates to a subtype of {@link SoyValue}. */
  public SoyExpression box() {
    if (isBoxed()) {
      return this;
    }
    if (soyType().equals(NullType.getInstance())) {
      if (delegate == NULL) {
        return NULL_BOXED;
      }
      return asBoxed(toStatement().then(NULL_BOXED));
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
    final boolean isNonNullable = delegate.isNonNullable();
    return asBoxed(
        new Expression(soyRuntimeType.box().runtimeType(), features()) {
          @Override
          protected void doGen(CodeBuilder adapter) {
            Label end = null;
            delegate.gen(adapter);
            if (!isNonNullable) {
              end = new Label();
              BytecodeUtils.nullCoalesce(adapter, end);
            }
            doBox(adapter, soyRuntimeType.asNonNullable());
            if (end != null) {
              adapter.mark(end);
            }
          }
        });
  }

  /**
   * Generates code to box the expression assuming that it is non-nullable and on the top of the
   * stack.
   */
  private static void doBox(CodeBuilder adapter, SoyRuntimeType type) {
    if (type.isKnownSanitizedContent()) {
      FieldRef.enumReference(
              ContentKind.valueOf(((SanitizedType) type.soyType()).getContentKind().name()))
          .accessStaticUnchecked(adapter);
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

  /** Coerce this expression to a boolean value. */
  public SoyExpression coerceToBoolean() {
    // First deal with primitives which don't have to care about null.
    if (BytecodeUtils.isPrimitive(resultType())) {
      return coercePrimitiveToBoolean();
    }
    if (soyType().equals(NullType.getInstance())) {
      return FALSE;
    }
    if (delegate.isNonNullable()) {
      return coerceNonNullableReferenceTypeToBoolean();
    } else {
      // If we are potentially nullable, then map null to false and run the normal logic recursively
      // for the non-nullable branch.
      final Label end = new Label();
      return withSource(
              new Expression(delegate.resultType(), delegate.features()) {
                @Override
                protected void doGen(CodeBuilder adapter) {
                  delegate.gen(adapter);
                  adapter.dup();
                  Label nonNull = new Label();
                  adapter.ifNonNull(nonNull);
                  adapter.pop();
                  adapter.pushBoolean(false);
                  adapter.goTo(end);
                  adapter.mark(nonNull);
                }
              })
          .asNonNullable()
          .coerceToBoolean()
          .labelEnd(end);
    }
  }

  private SoyExpression coercePrimitiveToBoolean() {
    if (resultType().equals(Type.BOOLEAN_TYPE)) {
      return this;
    } else if (resultType().equals(Type.DOUBLE_TYPE)) {
      return forBool(MethodRef.RUNTIME_COERCE_DOUBLE_TO_BOOLEAN.invoke(delegate));
    } else if (resultType().equals(Type.LONG_TYPE)) {
      return forBool(BytecodeUtils.compare(Opcodes.IFNE, delegate, BytecodeUtils.constant(0L)));
    } else {
      throw new AssertionError(
          "resultType(): " + resultType() + " is not a valid type for a SoyExpression");
    }
  }

  private SoyExpression coerceNonNullableReferenceTypeToBoolean() {
    if (isBoxed()) {
      // If we are boxed, just call the SoyValue method
      return forBool(delegate.invoke(MethodRef.SOY_VALUE_COERCE_TO_BOOLEAN));
    }
    // unboxed non-primitive types.  This would be strings, protos or lists
    if (soyRuntimeType.isKnownString()) {
      return forBool(logicalNot(delegate.invoke(MethodRef.STRING_IS_EMPTY)));
    }
    // All other types are always truthy, but we still need to eval the delegate in case it has
    // side effects or contains a null exit branch.
    return forBool(
        new Expression(Type.BOOLEAN_TYPE, delegate.features()) {
          @Override
          protected void doGen(CodeBuilder adapter) {
            delegate.gen(adapter);
            adapter.pop();
            adapter.pushBoolean(true);
          }
        });
  }

  /** Coerce this expression to a string value. */
  public SoyExpression coerceToString() {
    if (soyRuntimeType.isKnownString() && !isBoxed()) {
      return this;
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

  /**
   * Unboxes this to a {@link SoyExpression} with a String runtime type.
   *
   * <p>This method is appropriate when you know (likely via inspection of the {@link #soyType()},
   * or other means) that the value does have the appropriate type but you prefer to interact with
   * it as its unboxed representation. If you simply want to 'coerce' the given value to a new type
   * consider {@link #coerceToString()} which is designed for that use case.
   */
  public SoyExpression unboxAsString() {
    if (alreadyUnboxed(String.class)) {
      return this;
    }
    assertBoxed(String.class);

    Expression unboxedString;
    if (delegate.isNonNullable()) {
      unboxedString = delegate.invoke(MethodRef.SOY_VALUE_STRING_VALUE);
    } else {
      unboxedString =
          new Expression(BytecodeUtils.STRING_TYPE, features()) {
            @Override
            protected void doGen(CodeBuilder adapter) {
              Label end = new Label();
              delegate.gen(adapter);
              BytecodeUtils.nullCoalesce(adapter, end);
              MethodRef.SOY_VALUE_STRING_VALUE.invokeUnchecked(adapter);
              adapter.mark(end);
            };
          };
    }
    // We need to ensure that santized types don't lose their content kinds
    return soyRuntimeType.isKnownSanitizedContent()
        ? forSanitizedString(unboxedString, ((SanitizedType) soyType()).getContentKind())
        : forString(unboxedString);
  }

  /**
   * Unboxes this to a {@link SoyExpression} with a List runtime type.
   *
   * <p>This method is appropriate when you know (likely via inspection of the {@link #soyType()},
   * or other means) that the value does have the appropriate type but you prefer to interact with
   * it as its unboxed representation.
   */
  public SoyExpression unboxAsList() {
    if (alreadyUnboxed(List.class)) {
      return this;
    }
    assertBoxed(List.class);

    Expression unboxedList;
    if (delegate.isNonNullable()) {
      unboxedList = delegate.checkedCast(SOY_LIST_TYPE).invoke(MethodRef.SOY_LIST_AS_JAVA_LIST);
    } else {
      unboxedList =
          new Expression(BytecodeUtils.LIST_TYPE, features()) {
            @Override
            protected void doGen(CodeBuilder adapter) {
              Label end = new Label();
              delegate.gen(adapter);
              BytecodeUtils.nullCoalesce(adapter, end);
              adapter.checkCast(SOY_LIST_TYPE);
              MethodRef.SOY_LIST_AS_JAVA_LIST.invokeUnchecked(adapter);
              adapter.mark(end);
            };
          };
    }

    ListType asListType;
    if (soyType().getKind() != Kind.NULL
        && soyRuntimeType.asNonNullable().isKnownListOrUnionOfLists()) {
      asListType = soyRuntimeType.asNonNullable().asListType();
    } else {
      Kind kind = soyType().getKind();
      if (kind == Kind.UNKNOWN || kind == Kind.NULL) {
        asListType = ListType.of(UnknownType.getInstance());
      } else {
        // The type checker should have already rejected all of these
        throw new UnsupportedOperationException("Can't convert " + soyRuntimeType + " to List");
      }
    }
    return forList(asListType, unboxedList);
  }

  /**
   * Unboxes this to a {@link SoyExpression} with a Message runtime type.
   *
   * <p>This method is appropriate when you know (likely via inspection of the {@link #soyType()},
   * or other means) that the value does have the appropriate type but you prefer to interact with
   * it as its unboxed representation.
   */
  public Expression unboxAsMessage() {
    if (soyType().getKind() == Kind.NULL) {
      // If this is a null literal, return a Messaged-typed null literal.
      return BytecodeUtils.constantNull(BytecodeUtils.MESSAGE_TYPE);
    }
    // Attempting to unbox an unboxed proto
    // (We compare the non-nullable type because being null doesn't impact unboxability,
    //  and if we didn't remove null then isKnownProtoOrUnionOfProtos would fail.)
    if (soyRuntimeType.asNonNullable().isKnownProtoOrUnionOfProtos() && !isBoxed()) {
      return this;
    }
    if (delegate.isNonNullable()) {
      return delegate.invoke(MethodRef.SOY_PROTO_VALUE_GET_PROTO);
    }

    return new Expression(BytecodeUtils.MESSAGE_TYPE, features()) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        Label end = new Label();
        delegate.gen(adapter);
        BytecodeUtils.nullCoalesce(adapter, end);
        MethodRef.SOY_PROTO_VALUE_GET_PROTO.invokeUnchecked(adapter);
        adapter.mark(end);
      }
    };
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
  public SoyExpression asNonNullable() {
    return new SoyExpression(soyRuntimeType.asNonNullable(), delegate.asNonNullable());
  }

  @Override
  public SoyExpression asNullable() {
    return new SoyExpression(soyRuntimeType.asNullable(), delegate.asNullable());
  }

  @Override
  public SoyExpression labelStart(Label label) {
    return withSource(delegate.labelStart(label));
  }

  @Override
  public SoyExpression labelEnd(Label label) {
    return withSource(delegate.labelEnd(label));
  }
}
