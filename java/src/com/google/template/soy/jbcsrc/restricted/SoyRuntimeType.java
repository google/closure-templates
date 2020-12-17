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

package com.google.template.soy.jbcsrc.restricted;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_STRING_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_TYPE;

import com.google.common.base.Objects;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.objectweb.asm.Type;

/**
 * The 'runtime type' of a {@link SoyType}.
 *
 * <p>In {@code jbcsrc} all types have a corresponding runtime type, and often distinct 'boxed' and
 * 'unboxed' forms.
 */
public abstract class SoyRuntimeType {

  /**
   * Returns the unboxed {@code jbcsrc} representation of the given type, or absent if no such
   * representation exists.
   *
   * <p>Types will fail to have unboxed representations mostly for unknown, any and union types.
   */
  public static Optional<SoyRuntimeType> getUnboxedType(SoyType soyType) {
    return Optional.ofNullable(unboxedTypeImpl(soyType));
  }

  /** Returns the boxed representation of the given type. */
  public static SoyRuntimeType getBoxedType(SoyType soyType) {
    PrimitiveSoyType primitive = unboxedTypeImpl(soyType);
    if (primitive != null) {
      return primitive.box();
    }
    switch (soyType.getKind()) {
      case ATTRIBUTES:
      case CSS:
      case URI:
      case ELEMENT:
      case HTML:
      case JS:
      case TRUSTED_RESOURCE_URI:
        return new BoxedSoyType(soyType, BytecodeUtils.SANITIZED_CONTENT_TYPE);
      case LIST:
        return new BoxedSoyType(soyType, BytecodeUtils.SOY_LIST_TYPE);
      case LEGACY_OBJECT_MAP:
        return new BoxedSoyType(soyType, BytecodeUtils.SOY_LEGACY_OBJECT_MAP_TYPE);
      case MAP:
        return new BoxedSoyType(soyType, BytecodeUtils.SOY_MAP_TYPE);
      case RECORD:
        return new BoxedSoyType(soyType, BytecodeUtils.SOY_RECORD_TYPE);
      case VE:
        return new BoxedSoyType(soyType, BytecodeUtils.SOY_VISUAL_ELEMENT_TYPE);
      case VE_DATA:
        return new BoxedSoyType(soyType, BytecodeUtils.SOY_VISUAL_ELEMENT_DATA_TYPE);
      case TEMPLATE:
        return new BoxedSoyType(soyType, BytecodeUtils.COMPILED_TEMPLATE_FACTORY_VALUE_TYPE);
      case UNION:
        {
          // unions generally don't have a runtime type except in 2 special cases
          // 1. nullable types can use the normal reference type.
          // 2. if all members of the union have the same runtimeType then we can use that
          SoyType nonNullType = SoyTypes.removeNull(soyType);
          if (!nonNullType.equals(soyType)) {
            BoxedSoyType boxedType = (BoxedSoyType) getBoxedType(nonNullType);
            return new BoxedSoyType(soyType, boxedType.runtimeType());
          }
          BoxedSoyType memberType = null;
          for (SoyType member : ((UnionType) soyType).getMembers()) {
            BoxedSoyType boxed = (BoxedSoyType) getBoxedType(member);
            if (memberType == null) {
              memberType = boxed;
            } else if (!memberType.runtimeType().equals(boxed.runtimeType())) {
              memberType = null;
              break;
            }
          }
          if (memberType != null) {
            return new BoxedSoyType(soyType, memberType.runtimeType());
          }
        }
        // fall-through
      case UNKNOWN:
      case ANY:
        // SoyValue
        return new BoxedSoyType(soyType, BytecodeUtils.SOY_VALUE_TYPE);
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
            StringType.getInstance(), BytecodeUtils.STRING_TYPE, SOY_STRING_TYPE);
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
      case ELEMENT:
      case HTML:
      case JS:
      case TRUSTED_RESOURCE_URI:
        // sanitized strings cannot be unboxed
        return null;
      case MESSAGE:
        return new PrimitiveSoyType(
            soyType, BytecodeUtils.MESSAGE_TYPE, BytecodeUtils.SOY_PROTO_VALUE_TYPE);
      case PROTO:
        return soyTypeFromProto((SoyProtoType) soyType);
      case LIST:
        // We have some minor support for unboxed lists
        return new PrimitiveSoyType(soyType, BytecodeUtils.LIST_TYPE, BytecodeUtils.SOY_LIST_TYPE);
      case LEGACY_OBJECT_MAP:
      case MAP:
      case RECORD:
      case NAMED_TEMPLATE:
      case TEMPLATE:
      case VE:
      case VE_DATA:
        // no unboxed representation at all.  We could add something for these, but there is
        // currently not much point.
        return null;
      case UNION:
        {
          // unions generally don't have a unique unboxed runtime type except in 2 special cases
          // 1. nullable reference types can be unboxed.
          // 2. if all members of the union have the same runtimeType then we can use that
          SoyType nonNullType = SoyTypes.removeNull(soyType);
          if (!nonNullType.equals(soyType)) {
            PrimitiveSoyType primitive =
                (PrimitiveSoyType) getUnboxedType(nonNullType).orElse(null);
            if (primitive != null && !BytecodeUtils.isPrimitive(primitive.runtimeType())) {
              return new PrimitiveSoyType(
                  soyType, primitive.runtimeType(), primitive.box().runtimeType());
            }
          }
          PrimitiveSoyType memberType = null;
          for (SoyType member : ((UnionType) soyType).getMembers()) {
            PrimitiveSoyType primitive = (PrimitiveSoyType) getUnboxedType(member).orElse(null);
            if (primitive == null) {
              return null;
            }
            if (memberType == null) {
              memberType = primitive;
            } else if (!memberType.runtimeType().equals(primitive.runtimeType())
                || !memberType.box().runtimeType().equals(primitive.box().runtimeType())) {
              memberType = null;
              break;
            }
          }
          if (memberType != null) {
            return new PrimitiveSoyType(
                soyType, memberType.runtimeType(), memberType.box().runtimeType());
          }
        }
        // fall-through
      case UNKNOWN:
      case ANY:
        // no unique unboxed representation
        return null;
      case PROTO_TYPE:
      case PROTO_ENUM_TYPE:
      case PROTO_EXTENSION:
      case PROTO_MODULE:
      case TEMPLATE_TYPE:
      case TEMPLATE_MODULE:
    }
    throw new AssertionError("can't map " + soyType + " to an unboxed soy runtime type");
  }

  private static PrimitiveSoyType soyTypeFromProto(SoyProtoType soyType) {
    return new PrimitiveSoyType(
        soyType, protoType(soyType.getDescriptor()), BytecodeUtils.SOY_PROTO_VALUE_TYPE);
  }

  /** Returns the runtime type for the message correspdoning to the given descriptor.. */
  public static Type protoType(Descriptor descriptor) {
    return Type.getType('L' + JavaQualifiedNames.getClassName(descriptor).replace('.', '/') + ';');
  }

  private static PrimitiveSoyType enumType(SoyProtoEnumType enumType) {
    return new PrimitiveSoyType(enumType, Type.LONG_TYPE, BytecodeUtils.INTEGER_DATA_TYPE);
  }

  private final SoyType soyType;
  private final Type runtimeType;

  private SoyRuntimeType(SoyType soyType, Type runtimeType) {
    this.soyType = checkNotNull(soyType);
    this.runtimeType = checkNotNull(runtimeType);
  }

  public final SoyType soyType() {
    return soyType;
  }

  public final Type runtimeType() {
    return runtimeType;
  }

  public boolean assignableToNullableInt() {
    return assignableToNullableType(IntType.getInstance());
  }

  public boolean assignableToNullableFloat() {
    return assignableToNullableType(FloatType.getInstance());
  }

  public boolean assignableToNullableNumber() {
    return assignableToNullableType(SoyTypes.NUMBER_TYPE);
  }

  public boolean assignableToNullableString() {
    return soyType.getKind().isKnownStringOrSanitizedContent()
        || (soyType.getKind() == Kind.UNION
            && SoyTypes.removeNull(soyType).getKind().isKnownStringOrSanitizedContent());
  }

  private boolean assignableToNullableType(SoyType type) {
    return type.isAssignableFromStrict(soyType)
        || (soyType.getKind() == Kind.UNION
            && type.isAssignableFromStrict(SoyTypes.removeNull(soyType)));
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
  public boolean isKnownString() {
    return soyType.getKind() == Kind.STRING;
  }

  public boolean isKnownStringOrSanitizedContent() {
    if (soyType.getKind().isKnownStringOrSanitizedContent()) {
      return true;
    }
    if (soyType.getKind() == Kind.UNION) {
      for (SoyType member : ((UnionType) soyType).getMembers()) {
        if (!member.getKind().isKnownStringOrSanitizedContent()) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public boolean isKnownSanitizedContent() {
    return soyType.getKind().isKnownSanitizedContent();
  }

  /**
   * Returns {@code true} if the expression is known to be an int at compile time.
   *
   * <p>Note: If this returns {@code false}, there is no guarantee that this expression is
   * <em>not</em> a int, just that it is not <em>known</em> to be a int at compile time.
   */
  public boolean isKnownInt() {
    return soyType.getKind() == Kind.INT || SoyTypes.isKindOrUnionOfKind(soyType, Kind.PROTO_ENUM);
  }

  /**
   * Returns {@code true} if the expression is known to be a float at compile time.
   *
   * <p>Note: If this returns {@code false}, there is no guarantee that this expression is
   * <em>not</em> a float, just that it is not <em>known</em> to be a float at compile time.
   */
  public final boolean isKnownFloat() {
    return soyType.getKind() == Kind.FLOAT;
  }

  public final boolean isKnownListOrUnionOfLists() {
    return SoyTypes.isKindOrUnionOfKind(soyType, Kind.LIST);
  }

  public final ListType asListType() {
    checkState(isKnownListOrUnionOfLists());
    if (soyType instanceof ListType) {
      return (ListType) soyType;
    }
    List<SoyType> members = new ArrayList<>();
    for (SoyType member : ((UnionType) soyType).getMembers()) {
      ListType memberAsList = (ListType) member;
      if (memberAsList.getElementType() != null) {
        members.add(memberAsList.getElementType());
      }
    }
    return ListType.of(UnionType.of(members));
  }

  public final boolean isKnownLegacyObjectMapOrUnionOfMaps() {
    return SoyTypes.isKindOrUnionOfKind(soyType, Kind.LEGACY_OBJECT_MAP);
  }

  public final boolean isKnownMapOrUnionOfMaps() {
    return SoyTypes.isKindOrUnionOfKind(soyType, Kind.MAP);
  }

  public final boolean isKnownBool() {
    return soyType.getKind() == Kind.BOOL;
  }

  public final boolean isKnownProtoOrUnionOfProtos() {
    return SoyTypes.isKindOrUnionOfKind(soyType, Kind.PROTO);
  }

  /**
   * Returns {@code true} if the expression is known to be an {@linkplain #isKnownInt() int} or a
   * {@linkplain #isKnownFloat() float} at compile time.
   *
   * <p>Note: If this returns {@code false}, there is no guarantee that this expression is
   * <em>not</em> a number, just that it is not <em>known</em> to be a number at compile time.
   */
  public final boolean isKnownNumber() {
    return SoyTypes.NUMBER_TYPE.isAssignableFromStrict(soyType);
  }

  public final SoyRuntimeType asNonNullable() {
    return withNewSoyType(SoyTypes.removeNull(soyType));
  }

  public final SoyRuntimeType asNullable() {
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

  public abstract boolean isBoxed();

  public abstract SoyRuntimeType box();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SoyRuntimeType)) {
      return false;
    }
    SoyRuntimeType that = (SoyRuntimeType) o;
    return Objects.equal(soyType, that.soyType)
        && Objects.equal(runtimeType, that.runtimeType)
        && isBoxed() == that.isBoxed();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(soyType, runtimeType, isBoxed());
  }

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
    public BoxedSoyType box() {
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
    public SoyRuntimeType box() {
      return this;
    }
  }
}
