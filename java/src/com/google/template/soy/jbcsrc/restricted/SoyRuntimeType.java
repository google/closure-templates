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
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_TYPE;

import com.google.common.base.Objects;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.types.AbstractIterableType;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.IterableType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.MessageType;
import com.google.template.soy.types.SanitizedType.AttributesType;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.SetType;
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
    return new BoxedSoyType(soyType, SOY_VALUE_TYPE);
  }

  @Nullable
  private static PrimitiveSoyType unboxedTypeImpl(SoyType soyType) {
    switch (soyType.getKind()) {
      case BOOL:
        return new PrimitiveSoyType(BoolType.getInstance(), Type.BOOLEAN_TYPE);
      case STRING:
        return new PrimitiveSoyType(StringType.getInstance(), BytecodeUtils.STRING_TYPE);
      case INT:
        return new PrimitiveSoyType(IntType.getInstance(), Type.LONG_TYPE);
      case FLOAT:
        return new PrimitiveSoyType(FloatType.getInstance(), Type.DOUBLE_TYPE);
      case PROTO_ENUM:
        return new PrimitiveSoyType(soyType, Type.LONG_TYPE);
      case MESSAGE:
        return new PrimitiveSoyType(soyType, BytecodeUtils.MESSAGE_TYPE);
      case PROTO:
        return new PrimitiveSoyType(soyType, protoType(((SoyProtoType) soyType).getDescriptor()));
      case LIST:
        // We have some minor support for unboxed lists
        return new PrimitiveSoyType(soyType, BytecodeUtils.LIST_TYPE);
      case SET:
        // We have some minor support for unboxed sets
        return new PrimitiveSoyType(soyType, BytecodeUtils.SET_TYPE);
      case UNION:
        {
          // unions generally don't have a unique unboxed runtime type except in 2 special cases
          // 1. nullable reference types can be unboxed.
          // 2. if all members of the union have the same runtimeType then we can use that
          SoyType nonNullType = SoyTypes.tryRemoveNullish(soyType);
          if (!nonNullType.equals(soyType)) {
            return null;
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
              return null;
            }
          }
          if (memberType != null) {
            return new PrimitiveSoyType(soyType, memberType.runtimeType());
          }
        }
      // fall-through
      case GBIGINT:
      case NULL:
      case UNDEFINED:
      case ATTRIBUTES:
      case CSS:
      case URI:
      case ELEMENT:
      case HTML:
      case JS:
      case TRUSTED_RESOURCE_URI:
      case ITERABLE:
      case LEGACY_OBJECT_MAP:
      case MAP:
      case RECORD:
      case TEMPLATE:
      case VE:
      case VE_DATA:
      case UNKNOWN:
      case ANY:
      case FUNCTION:
        // no unique unboxed representation
        return null;
      case NAMESPACE:
      case PROTO_TYPE:
      case PROTO_ENUM_TYPE:
      case PROTO_EXTENSION:
      case PROTO_MODULE:
      case TEMPLATE_TYPE:
      case TEMPLATE_MODULE:
      case INTERSECTION:
      case NAMED:
      case INDEXED:
      case NEVER:
    }
    throw new AssertionError("can't map " + soyType + " to an unboxed soy runtime type");
  }

  /** Returns the runtime type for the message correspdoning to the given descriptor.. */
  public static Type protoType(Descriptor descriptor) {
    return BytecodeUtils.getTypeForClassName(JavaQualifiedNames.getClassName(descriptor));
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

  public boolean assignableToNullableString() {
    return soyType.getKind().isKnownStringOrSanitizedContent()
        || (soyType.getKind() == Kind.UNION
            && SoyTypes.tryRemoveNullish(soyType).getKind().isKnownStringOrSanitizedContent());
  }

  public boolean assignableToNullableHtmlOrAttributes() {
    var type = SoyTypes.tryRemoveNullish(soyType);
    return type.isAssignableFromLoose(HtmlType.getInstance())
        || type.isAssignableFromLoose(AttributesType.getInstance());
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

  public boolean isKnownGbigint() {
    return soyType.getKind() == Kind.GBIGINT;
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
    return ListType.ANY_LIST.isAssignableFromStrict(soyType);
  }

  public final boolean isKnownSet() {
    return SetType.ANY_SET.isAssignableFromStrict(soyType);
  }

  public final boolean isKnownIterable() {
    return IterableType.ANY_ITERABLE.isAssignableFromStrict(soyType);
  }

  public final ListType asListType() {
    checkState(isKnownIterable());
    if (soyType instanceof ListType) {
      return (ListType) soyType;
    } else if (soyType instanceof AbstractIterableType) {
      return ListType.of(((AbstractIterableType) soyType).getElementType());
    }
    List<SoyType> members = new ArrayList<>();
    for (SoyType member : ((UnionType) soyType).getMembers()) {
      AbstractIterableType memberAsList = (AbstractIterableType) member;
      if (!memberAsList.isEmpty()) {
        members.add(memberAsList.getElementType());
      }
    }
    return ListType.of(UnionType.of(members));
  }

  public final boolean isKnownLegacyObjectMapOrUnionOfMaps() {
    return SoyTypes.isKindOrUnionOfKind(soyType, Kind.LEGACY_OBJECT_MAP);
  }

  public final boolean isKnownMapOrUnionOfMaps() {
    return MapType.ANY_MAP.isAssignableFromStrict(soyType);
  }

  public final boolean isKnownBool() {
    return soyType.getKind() == Kind.BOOL;
  }

  public final boolean isKnownProtoOrUnionOfProtos() {
    return MessageType.getInstance().isAssignableFromStrict(soyType);
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

  public final SoyRuntimeType asNonSoyNullish() {
    // Use tryRemoveNull instead of removeNull because there are times where the jbcsrc backend
    // infers stronger types than Soy proper (e.g. for `@state` params initialized to `null` but
    // declared with a different type)
    return withNewSoyType(SoyTypes.tryRemoveNullish(soyType));
  }

  public final SoyRuntimeType asSoyUndefinable() {
    return withNewSoyType(SoyTypes.makeUndefinable(soyType));
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

  public SoyRuntimeType box() {
    if (isBoxed()) {
      return this;
    } else {
      return getBoxedType(soyType());
    }
  }

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

    PrimitiveSoyType(SoyType soyType, Type runtimeType) {
      super(soyType, runtimeType);
    }

    @Override
    public boolean isBoxed() {
      return false;
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
  }
}
