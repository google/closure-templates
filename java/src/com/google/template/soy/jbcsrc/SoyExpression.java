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

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.template.soy.jbcsrc.BytecodeUtils.classFromAsmType;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.primitive.BoolType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.NullType;
import com.google.template.soy.types.primitive.StringType;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.util.List;
import java.util.Map;

/**
 * An Expression involving a soy value.
 *
 * <p>SoyExpressions can be {@link #box() boxed} into SoyValue subtypes and they also support some
 * implicit conversions.
 *
 * <p>All soy expressions are convertable to {@code boolean} or {@link String} valued expressions,
 * but depending on the type they may also support additional unboxing conversions.
 */
class SoyExpression extends Expression {
  private static final ImmutableSet<Kind> STRING_KINDS = 
      Sets.immutableEnumSet(Kind.STRING, Kind.HTML, Kind.ATTRIBUTES, Kind.JS, Kind.CSS, Kind.URI);

  static SoyExpression forSoyValue(SoyType type, Expression delegate) {
    return new SoyExpression(type, type.javaType(), delegate);
  }

  static SoyExpression forBool(Expression delegate) {
    return new SoyExpression(BoolType.getInstance(), boolean.class, delegate);
  }

  static SoyExpression forFloat(Expression delegate) {
    return new SoyExpression(FloatType.getInstance(), double.class, delegate);
  }

  static SoyExpression forInt(Expression delegate) {
    return new SoyExpression(IntType.getInstance(), long.class, delegate);
  }

  static SoyExpression forString(Expression delegate) {
    return new SoyExpression(StringType.getInstance(), String.class, delegate);
  }

  static SoyExpression forList(ListType listType, Expression delegate) {
    return new SoyExpression(listType, List.class, delegate);
  }

  static SoyExpression forMap(MapType mapType, Expression delegate) {
    return new SoyExpression(mapType, Map.class, delegate);
  }

  static SoyExpression forRecord(RecordType recordType, Expression delegate) {
    return new SoyExpression(recordType, Map.class, delegate);
  }

  static final SoyExpression NULL =
      new SoyExpression(NullType.getInstance(), Object.class, 
          new SimpleExpression(Type.getType(Object.class), true) {
            @Override void doGen(GeneratorAdapter adapter) {
              adapter.visitInsn(Opcodes.ACONST_NULL);
            }
          });

  static final SoyExpression TRUE =
      new SoyExpression(BoolType.getInstance(), boolean.class, BytecodeUtils.constant(true)) {
        @Override
        SoyExpression box() {
          return new DefaultBoxed(BoolType.getInstance(), this,
              FieldRef.BOOLEAN_DATA_TRUE.accessor());
        }
      };

  static final SoyExpression FALSE =
      new SoyExpression(BoolType.getInstance(), boolean.class, BytecodeUtils.constant(false)) {
        @Override
        SoyExpression box() {
          return new DefaultBoxed(BoolType.getInstance(), this,
              FieldRef.BOOLEAN_DATA_FALSE.accessor());
        }
      };

  private final Class<?> clazz;
  private final SoyType soyType;
  private final Expression delegate;

  private SoyExpression(SoyType soyType, Class<?> clazz, Expression delegate) {
    checkArgument(
        clazz.isAssignableFrom(classFromAsmType(delegate.resultType())),
        "delegate with type %s isn't compatible with asserted SoyExpression type %s", 
        delegate.resultType(), 
        clazz);
    // If this is a boxed type, make sure the declared clazz is compatible
    // TODO(lukes): support this check for unboxed types as well.
    if (SoyValue.class.isAssignableFrom(clazz) && !soyType.javaType().isAssignableFrom(clazz)) {
      throw new IllegalArgumentException(clazz + " is not compatible with soy type: " + soyType);
    }
    this.soyType = soyType;
    this.clazz = clazz;
    this.delegate = delegate;
  }

  @Override final Type resultType() {
    return delegate.resultType();
  }

  @Override final boolean isConstant() {
    return delegate.isConstant();
  }

  @Override final void doGen(GeneratorAdapter adapter) {
    delegate.gen(adapter);
  }

  /**
   * Returns {@code true} if the expression is known to be a string at compile time.
   *
   * <p>Note: If this returns {@code false}, there is no guarantee that this expression is
   * <em>not</em> a string, just that it is not <em>known</em> to be a string at compile time. For
   * example, {@code $b ? 'hello' : 2} is a valid soy expression that will be typed as 'any' at
   * compile time. So {@link #isKnownString()} on that soy expression will return false even though
   * it may in fact be a string.
   */
  boolean isKnownString() {
    // It 'is' a string if it is unboxed or is one of our string types
    return STRING_KINDS.contains(soyType.getKind());
  }

  /**
   * Returns {@code true} if the expression is known to be an int at compile time.
   *
   * <p>Note: If this returns {@code false}, there is no guarantee that this expression is
   * <em>not</em> a int, just that it is not <em>known</em> to be a int at compile time.
   */
  boolean isKnownInt() {
    return soyType.getKind() == Kind.INT;
  }

  /**
   * Returns {@code true} if the expression is known to be a float at compile time.
   *
   * <p>Note: If this returns {@code false}, there is no guarantee that this expression is
   * <em>not</em> a float, just that it is not <em>known</em> to be a float at compile time.
   */
  boolean isKnownFloat() {
    return soyType.getKind() == Kind.FLOAT;
  }

  boolean isKnownList() {
    return soyType.getKind() == Kind.LIST;
  }

  boolean isKnownMap() {
    return soyType.getKind() == Kind.MAP;
  }

  boolean isKnownRecord() {
    return soyType.getKind() == Kind.RECORD;
  }

  private boolean isBoxed() {
    return SoyValue.class.isAssignableFrom(clazz);
  }

  /**
   * Returns {@code true} if the expression is known to be an {@linkplain #isKnownInt() int} or a
   * {@linkplain #isKnownFloat() float} at compile time.
   *
   * <p>Note: If this returns {@code false}, there is no guarantee that this expression is
   * <em>not</em> a number, just that it is not <em>known</em> to be a number at compile time.
   */
  final boolean isKnownNumber() {
    return isKnownFloat() || isKnownInt();
  }

  /** Returns a SoyExpression that evaluates to a subtype of {@link SoyValue}. */
  SoyExpression box() {
    if (isBoxed()) {
      return this;
    }
    if (isKnownInt()) {
      return asBoxed(MethodRef.INTEGER_DATA_FOR_VALUE.invoke(delegate));
    }
    if (isKnownFloat()) {
      return asBoxed(MethodRef.FLOAT_DATA_FOR_VALUE.invoke(delegate));
    }
    if (isKnownString()) {
      // TODO(lukes): we are losing some type information when we do string conversions. Use the
      // SoyType
      return asBoxed(MethodRef.STRING_DATA_FOR_VALUE.invoke(delegate));
    }
    if (isKnownList()) {
      return asBoxed(MethodRef.LIST_IMPL_FOR_PROVIDER_LIST.invoke(delegate));
    }
    if (isKnownMap()) {
      return asBoxed(MethodRef.DICT_IMPL_FOR_PROVIDER_MAP.invoke(delegate));
    }
    if (soyType.getKind() == Kind.NULL) {
      return asBoxed(FieldRef.NULL_DATA_INSTANCE.accessor());
    }
    throw new IllegalStateException("cannot box expression of type " + clazz);
  }

  private DefaultBoxed asBoxed(Expression expr) {
    return new DefaultBoxed(soyType, this, expr);
  }

  /**
   * Converts this to a {@link SoyExpression} with a runtime type of {@code asType} if possible.
   *
   * <p>This will either be a type coercion or an unboxing operation (or return {@code this} if the
   * type already matches). Note: type coercions may throw exceptions at runtime.
   */
  SoyExpression convert(Class<?> asType) {
    checkArgument(!SoyValue.class.isAssignableFrom(asType),
        "Cannot use convert() to convert to a  SoyValue: %s", asType);
    // no op conversion
    if (asType.equals(clazz)) {
      return this;
    }

    if (isKnownInt()) {
      Expression intExpr = delegate;
      if (isBoxed()) {
        // unbox first
        intExpr = MethodRef.SOY_VALUE_LONG_VALUE.invoke(intExpr);
      }
      if (asType.equals(double.class)) {
        return forFloat(BytecodeUtils.numericConversion(intExpr, Type.DOUBLE_TYPE));
      }
      if (asType.equals(boolean.class)) {
        return forBool(BytecodeUtils.compare(Opcodes.IFNE, intExpr, BytecodeUtils.constant(0L)));
      }
      if (asType.equals(String.class)) {
        return forString(MethodRef.LONG_TO_STRING.invoke(intExpr));
      }
    }
    if (isKnownFloat()) {
      Expression floatExpr = delegate;
      if (isBoxed()) {
        // unbox first
        floatExpr = MethodRef.SOY_VALUE_LONG_VALUE.invoke(floatExpr);
      }
      if (asType.equals(long.class)) {
        throw new IllegalArgumentException("Cannot convert float to int");
      }
      if (asType.equals(boolean.class)) {
        return forBool(MethodRef.RUNTIME_COERCE_DOUBLE_TO_BOOLEAN.invoke(floatExpr));
      }
      if (asType.equals(String.class)) {
        return forString(MethodRef.DOUBLE_TO_STRING.invoke(floatExpr));
      }
    }
    if (isKnownString()) {
      Expression stringExpr = delegate;
      if (isBoxed()) {
        // unbox first
        stringExpr = MethodRef.SOY_VALUE_STRING_VALUE.invoke(stringExpr);
      }
      if (asType.equals(double.class) || asType.equals(long.class)) {
        throw new IllegalArgumentException("Cannot convert string to " + asType);
      }
      if (asType.equals(boolean.class)) {
        return forBool(MethodRef.STRING_IS_EMPTY.invoke(stringExpr));
      }
    }
    // TODO(lukes): implement specializations for lists/maps/records/objects

    // SoyValue conversions, we first box ourselves and then call a SoyValue method
    if (asType.equals(long.class)) {
      return forInt(MethodRef.SOY_VALUE_LONG_VALUE.invoke(box()));
    }
    if (asType.equals(double.class)) {
      return forFloat(MethodRef.SOY_VALUE_FLOAT_VALUE.invoke(box()));
    }
    if (asType.equals(String.class)) {
      // string coercion is performed via the toString method
      return forString(MethodRef.TO_STRING.invoke(box()));
    }
    if (asType.equals(boolean.class)) {
      return forBool(MethodRef.SOY_VALUE_COERCE_TO_BOOLEAN.invoke(box()));
    }
    throw new UnsupportedOperationException("Can't unbox " + clazz + " as " + asType);
  }

  /**
   * Default subtype of {@link SoyExpression} used by our core expression implementations.
   */
  private static final class DefaultBoxed extends SoyExpression {
    private final SoyExpression unboxed;

    DefaultBoxed(SoyType soyType, SoyExpression unboxed, Expression delegate) {
      super(soyType, soyType.javaType(), delegate);
      this.unboxed = unboxed;
    }

    @Override final SoyExpression convert(Class<?> asType) {
      return unboxed.convert(asType);
    }

    @Override final SoyExpression box() {
      return this;
    }
  }
}
