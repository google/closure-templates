/*
 * Copyright 2016 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.template.soy.jbcsrc.BytecodeUtils.SOY_VALUE_TYPE;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.SoyString;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.BoolType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.NullType;
import com.google.template.soy.types.primitive.SanitizedType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.proto.SoyProtoEnumType;
import com.google.template.soy.types.proto.SoyProtoType;
import javax.annotation.Nullable;
import org.objectweb.asm.Type;

/**
 * The 'runtime type' of a {@link SoyType}.
 *
 * <p>In {@code jbcsrc} all types have a corresponding runtime type, and often distinct 'boxed' and
 * 'unboxed' forms.
 */
abstract class SoyRuntimeType {

  /**
   * Returns the unboxed {@code jbcsrc} representation of the given type, or absent if no such
   * representation exists.
   *
   * <p>Types will fail to have unboxed representations mostly for unknown, any and union types.
   */
  static Optional<SoyRuntimeType> getUnboxedType(SoyType soyType) {
    // Optional is immutable so Optional<Subclass> can always be safely cast to Optional<SuperClass>
    @SuppressWarnings("unchecked")
    Optional<SoyRuntimeType> typed = (Optional) primitiveTypeCache.getUnchecked(soyType);
    return typed;
  }

  /** Returns the boxed representation of the given type. */
  static SoyRuntimeType getBoxedType(SoyType soyType) {
    return boxedTypeCache.getUnchecked(soyType);
  }

  // Thse caches should have a relatively fixed size (the universe of SoyTypes).  One potential
  // source of concern is that in the case of protos, if a user is hot swapping in new class
  // definitions and adding/removing fields.  We won't modify the SoyType definitions (or
  // SoyRuntimeType) definitions in these caches.  This is a limitation in the design of the proto
  // type definition (because we never try to re-read the proto descriptors).  In theory this could
  // be fixed by flushing the whole type registry between compiles.  This would solve the problem,
  // but then these caches would start leaking!  Any easy fix would be to give these caches weak
  // keys.

  private static final LoadingCache<SoyType, Optional<PrimitiveSoyType>> primitiveTypeCache =
      CacheBuilder.newBuilder()
          .build(
              new CacheLoader<SoyType, Optional<PrimitiveSoyType>>() {
                @Override
                public Optional<PrimitiveSoyType> load(SoyType key) throws Exception {
                  return Optional.fromNullable(unboxedTypeImpl(key));
                }
              });

  private static final LoadingCache<SoyType, BoxedSoyType> boxedTypeCache =
      CacheBuilder.newBuilder()
          .build(
              new CacheLoader<SoyType, BoxedSoyType>() {
                @Override
                public BoxedSoyType load(SoyType key) throws Exception {
                  return boxedSoyTypeImpl(key);
                }
              });

  @Nullable
  private static BoxedSoyType boxedSoyTypeImpl(SoyType soyType) {
    Optional<PrimitiveSoyType> primitive = primitiveTypeCache.getUnchecked(soyType);
    if (primitive.isPresent()) {
      return primitive.get().box();
    }
    switch (soyType.getKind()) {
      case LIST:
        return new BoxedSoyType(soyType, BytecodeUtils.SOY_LIST_TYPE);
      case MAP:
        return new BoxedSoyType(soyType, BytecodeUtils.SOY_MAP_TYPE);
      case RECORD:
        return new BoxedSoyType(soyType, BytecodeUtils.SOY_RECORD_TYPE);
      case UNION:
        // for nullable types we can get more specific runtime types.
        SoyType nonNullType = ((UnionType) soyType).removeNullability();
        if (nonNullType != soyType) {
          BoxedSoyType boxedType = boxedSoyTypeImpl(nonNullType);
          return new BoxedSoyType(soyType, boxedType.runtimeType());
        }
        // fall-through
      case UNKNOWN:
      case ANY:
        // SoyValue
        return new BoxedSoyType(soyType, BytecodeUtils.SOY_VALUE_TYPE);
      case ERROR:
      default:
        throw new AssertionError("can't map " + soyType + " to a boxed soy runtime type");
    }
  }

  @Nullable
  private static PrimitiveSoyType unboxedTypeImpl(SoyType soyType) {
    switch (soyType.getKind()) {
      case NULL:
        return new PrimitiveSoyType(
            NullType.getInstance(), BytecodeUtils.OBJECT.type(), SOY_VALUE_TYPE);
      case BOOL:
        return new PrimitiveSoyType(
            BoolType.getInstance(), Type.BOOLEAN_TYPE, Type.getType(BooleanData.class));
      case STRING:
        return new PrimitiveSoyType(
            StringType.getInstance(), BytecodeUtils.STRING_TYPE, Type.getType(SoyString.class));
      case INT:
        return new PrimitiveSoyType(
            IntType.getInstance(), Type.LONG_TYPE, BytecodeUtils.INTEGER_DATA_TYPE);
      case FLOAT:
        return new PrimitiveSoyType(
            FloatType.getInstance(), Type.DOUBLE_TYPE, Type.getType(FloatData.class));
      case PROTO_ENUM:
        return enumType((SoyProtoEnumType) soyType);
      case ATTRIBUTES:
      case CSS:
      case URI:
      case HTML:
      case JS:
      case TRUSTED_RESOURCE_URI:
        return sanitizedType((SanitizedType) soyType);
      case PROTO:
        return protoType((SoyProtoType) soyType);
      case LIST:
        // We have some minor support for unboxed lists
        return new PrimitiveSoyType(soyType, BytecodeUtils.LIST_TYPE, BytecodeUtils.SOY_LIST_TYPE);
      case MAP:
      case RECORD:
        // no unboxed representation at all.  We could add something for these, but there is
        // currently not much point.
        return null;
      case UNION:
        // unions generally don't have a unique unboxed type except in the special case of nullable
        // nullable reference types can be unboxed.
        SoyType nonNullType = ((UnionType) soyType).removeNullability();
        if (nonNullType != soyType) {
          PrimitiveSoyType primitive = unboxedTypeImpl(nonNullType);
          if (primitive != null && !BytecodeUtils.isPrimitive(primitive.runtimeType())) {
            return new PrimitiveSoyType(
                soyType, primitive.runtimeType(), primitive.box().runtimeType());
          }
        }
        // fall-through
      case UNKNOWN:
      case ANY:
        // no unique unboxed representation
        return null;
      case ERROR:
      default:
        throw new AssertionError("can't map " + soyType + " to an unboxed soy runtime type");
    }
  }

  private static PrimitiveSoyType protoType(SoyProtoType soyType) {
    return new PrimitiveSoyType(
        soyType,
        Type.getType(
            'L' + soyType.getNameForBackend(SoyBackendKind.JBC_SRC).replace('.', '/') + ';'),
        BytecodeUtils.SOY_PROTO_VALUE_TYPE);
  }

  private static PrimitiveSoyType sanitizedType(SanitizedType soyType) {
    return new PrimitiveSoyType(
        soyType, BytecodeUtils.STRING_TYPE, Type.getType(SanitizedContent.class));
  }

  private static PrimitiveSoyType enumType(SoyProtoEnumType enumType) {
    return new PrimitiveSoyType(enumType, Type.INT_TYPE, BytecodeUtils.INTEGER_DATA_TYPE);
  }

  private final SoyType soyType;
  private final Type runtimeType;

  private SoyRuntimeType(SoyType soyType, Type runtimeType) {
    this.soyType = checkNotNull(soyType);
    this.runtimeType = checkNotNull(runtimeType);
  }

  final SoyType soyType() {
    return soyType;
  }

  final Type runtimeType() {
    return runtimeType;
  }

  boolean assignableToNullableInt() {
    return assignableToNullableType(IntType.getInstance());
  }

  boolean assignableToNullableFloat() {
    return assignableToNullableType(FloatType.getInstance());
  }

  boolean assignableToNullableNumber() {
    return assignableToNullableType(SoyTypes.NUMBER_TYPE);
  }

  private boolean assignableToNullableType(SoyType type) {
    return type.isAssignableFrom(soyType)
        || (soyType.getKind() == Kind.UNION && type.isAssignableFrom(SoyTypes.removeNull(soyType)));
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
    return soyType.getKind() == Kind.STRING;
  }

  boolean isKnownStringOrSanitizedContent() {
    // It 'is' a string if it is unboxed or is one of our string types
    return soyType.getKind().isKnownStringOrSanitizedContent();
  }

  boolean isKnownSanitizedContent() {
    return soyType.getKind().isKnownSanitizedContent();
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
  final boolean isKnownFloat() {
    return soyType.getKind() == Kind.FLOAT;
  }

  final boolean isKnownList() {
    return soyType.getKind() == Kind.LIST;
  }

  final boolean isKnownMap() {
    return soyType.getKind() == Kind.MAP;
  }

  final boolean isKnownRecord() {
    return soyType.getKind() == Kind.RECORD;
  }

  final boolean isKnownBool() {
    return soyType.getKind() == Kind.BOOL;
  }

  final boolean isKnownProto() {
    return soyType.getKind() == Kind.PROTO;
  }

  /**
   * Returns {@code true} if the expression is known to be an {@linkplain #isKnownInt() int} or a
   * {@linkplain #isKnownFloat() float} at compile time.
   *
   * <p>Note: If this returns {@code false}, there is no guarantee that this expression is
   * <em>not</em> a number, just that it is not <em>known</em> to be a number at compile time.
   */
  final boolean isKnownNumber() {
    return SoyTypes.NUMBER_TYPE.isAssignableFrom(soyType);
  }

  final SoyRuntimeType asNonNullable() {
    return withNewSoyType(SoyTypes.removeNull(soyType));
  }

  final SoyRuntimeType asNullable() {
    return withNewSoyType(SoyTypes.makeNullable(soyType));
  }

  private SoyRuntimeType withNewSoyType(SoyType newSoyType) {
    if (newSoyType != soyType) {
      if (isBoxed()) {
        return getBoxedType(newSoyType);
      } else {
        // no need to check the return value. This is only used for adding/removing null.
        // If we are a primitive type then there must be another primitive type that is the same
        // but non-nullable (or nullable).
        return getUnboxedType(newSoyType).get();
      }
    }
    return this;
  }

  abstract boolean isBoxed();

  abstract SoyRuntimeType box();

  // NOTE: we have identity semantics.  This is fine because our caches ensure we never produce
  // two otherwise identical objects

  @Override
  public String toString() {
    return "SoyRuntimeType{" + soyType + ", " + runtimeType + "}";
  }

  private static final class PrimitiveSoyType extends SoyRuntimeType {
    private final BoxedSoyType boxedType;

    PrimitiveSoyType(SoyType soyType, Type runtimeType, Type boxedType) {
      super(soyType, runtimeType);
      this.boxedType = new BoxedSoyType(soyType, boxedType);
    }

    @Override
    public boolean isBoxed() {
      return false;
    }

    @Override
    BoxedSoyType box() {
      return boxedType;
    }
  }

  private static final class BoxedSoyType extends SoyRuntimeType {
    BoxedSoyType(SoyType soyType, Type runtimeType) {
      super(soyType, runtimeType);
    }

    @Override
    public boolean isBoxed() {
      return true;
    }

    @Override
    SoyRuntimeType box() {
      return this;
    }
  }
}
