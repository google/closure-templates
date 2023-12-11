/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.data;

import com.google.protobuf.Message;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.UndefinedData;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Superinterface of all Soy value interfaces/classes.
 *
 * <p>Important: Until this API is more stable and this note is removed, users must not define
 * classes that implement this interface.
 */
@ParametersAreNonnullByDefault
public interface SoyValue extends SoyValueProvider {

  /**
   * Compares this value against another for equality in the sense of the '==' operator of Soy.
   *
   * @param other The other value to compare against.
   * @return True if this value is equal to the other in the sense of Soy.
   */
  @Override
  boolean equals(Object other);

  /**
   * Coerces this value into a boolean.
   *
   * @return This value coerced into a boolean.
   */
  boolean coerceToBoolean();

  /**
   * Coerces this value into a string.
   *
   * @return This value coerced into a string.
   */
  String coerceToString();

  /**
   * Returns whether this value is empty. For SanitizedContent, this checks if the content is empty.
   *
   * @return This value coerced into a boolean.
   */
  default boolean isEmpty() {
    return !coerceToBoolean();
  }

  /**
   * Renders this value to the given appendable.
   *
   * <p>This should behave identically to {@code appendable.append(coerceToString())} but is
   * provided separately to allow more incremental approaches.
   *
   * @param appendable The appendable to render to.
   * @throws IOException
   */
  void render(LoggingAdvisingAppendable appendable) throws IOException;

  /**
   * Renders this value to the given appendable.
   *
   * <p>This should behave identically to {@code appendable.append(coerceToString())} but is
   * provided separately to allow more incremental approaches.
   *
   * @param appendable The appendable to render to.
   * @throws IOException
   */
  void render(Appendable appendable) throws IOException;

  // -----------------------------------------------------------------------------------------------
  // Convenience methods for retrieving a known primitive type.

  /**
   * Precondition: Only call this method if you know that this SoyValue object is a boolean. This
   * method gets the boolean value of this boolean object.
   *
   * @return The boolean value of this boolean object.
   * @throws SoyDataException If this object is not actually a boolean.
   */
  boolean booleanValue();

  /**
   * Precondition: Only call this method if you know that this SoyValue object is a 32-bit integer.
   * This method gets the integer value of this integer object.
   *
   * @return The integer value of this integer object.
   * @throws SoyDataException If this object is not actually an integer.
   */
  int integerValue();

  /**
   * Precondition: Only call this method if you know that this SoyValue object is an integer or
   * long. This method gets the integer value of this integer object.
   *
   * @return The integer value of this integer object.
   * @throws SoyDataException If this object is not actually an integer.
   */
  long longValue();

  /**
   * Precondition: Only call this method if you know that this SoyValue object is a float. This
   * method gets the float value of this float object.
   *
   * @return The float value of this float object.
   * @throws SoyDataException If this object is not actually a float.
   */
  double floatValue();

  /**
   * Precondition: Only call this method if you know that this SoyValue object is a number. This
   * method gets the float value of this number object (converting integer to float if necessary).
   *
   * @return The float value of this number object.
   * @throws SoyDataException If this object is not actually a number.
   */
  double numberValue();

  /**
   * Precondition: Only call this method if you know that this SoyValue object is a string. This
   * method gets the string value of this string object.
   *
   * @return The string value of this string object.
   * @throws SoyDataException If this object is not actually a string.
   */
  @Nonnull
  String stringValue();

  /** Returns null is this value is nullish, otherwise {@link #stringValue()}. */
  @Nullable
  default String stringValueOrNull() {
    return isNullish() ? null : stringValue();
  }

  /** If this is Soy null (NullData). */
  default boolean isNull() {
    return this == NullData.INSTANCE;
  }

  /** If this is Soy undefined (UndefinedData). */
  default boolean isUndefined() {
    return this == UndefinedData.INSTANCE;
  }

  /** If this is Soy null or Soy undefined. */
  default boolean isNullish() {
    return isNull() || isUndefined();
  }

  /** Returns this value, coalescing UndefinedData to NullData. */
  default SoyValue nullishToNull() {
    return this == UndefinedData.INSTANCE ? NullData.INSTANCE : this;
  }

  /** Returns this value, coalescing NullData to UndefinedData. */
  default SoyValue nullishToUndefined() {
    return this == NullData.INSTANCE ? UndefinedData.INSTANCE : this;
  }

  @Nonnull
  default Message getProto() {
    throw new SoyDataException(
        "Expecting proto value but instead encountered type " + getClass().getSimpleName());
  }

  /** Returns null is this value is nullish, otherwise {@link #getProto()}. */
  @Nullable
  default Message getProtoOrNull() {
    return isNullish() ? null : getProto();
  }

  @Nonnull
  default List<? extends SoyValueProvider> asJavaList() {
    throw new SoyDataException(
        "Expecting list value but instead encountered type " + getClass().getSimpleName());
  }

  @Nullable
  default List<? extends SoyValueProvider> asJavaListOrNull() {
    return isNullish() ? null : asJavaList();
  }

  @Nonnull
  default Map<? extends SoyValue, ? extends SoyValueProvider> asJavaMap() {
    throw new SoyDataException(
        "Expecting map value but instead encountered type " + getClass().getSimpleName());
  }

  /**
   * A runtime type check for this boxed Soy value.
   *
   * @throws ClassCastException if this value is not null, undefined, or an instance of {@code
   *     type}.
   */
  default SoyValue checkNullishType(Class<? extends SoyValue> type) {
    return type.cast(this);
  }

  /** A runtime type check for this boxed Soy value. */
  default SoyValue checkNullishInt() {
    throw new ClassCastException(getClass().getSimpleName() + " cannot be cast to IntegerData");
  }

  /** A runtime type check for this boxed Soy value. */
  default SoyValue checkNullishFloat() {
    throw new ClassCastException(getClass().getSimpleName() + " cannot be cast to FloatData");
  }

  /** A runtime type check for this boxed Soy value. */
  default SoyValue checkNullishNumber() {
    throw new ClassCastException(getClass().getSimpleName() + " cannot be cast to NumberData");
  }

  /** A runtime type check for this boxed Soy value. */
  default SoyValue checkNullishBoolean() {
    throw new ClassCastException(getClass().getSimpleName() + " cannot be cast to BooleanData");
  }

  /** A runtime type check for this boxed Soy value. */
  default SoyValue checkNullishString() {
    throw new ClassCastException(getClass().getSimpleName() + " cannot be cast to StringData");
  }

  /**
   * A runtime type check for this boxed Soy value.
   *
   * @throws ClassCastException if this value is not null, undefined, or an instance of {@link
   *     SanitizedContent} whose content kind is equal to {@code contentKind}.
   */
  default SoyValue checkNullishSanitizedContent(ContentKind contentKind) {
    throw new ClassCastException(getClass().getSimpleName() + " cannot be cast to " + contentKind);
  }

  /**
   * A runtime type check for this boxed Soy value.
   *
   * @throws ClassCastException if this value is not null, undefined, or an instance of {@link
   *     SoyProtoValue} whose unboxed proto is an instance of {@code messageType}.
   */
  default SoyValue checkNullishProto(Class<? extends Message> messageType) {
    throw new ClassCastException(
        getClass().getSimpleName() + " cannot be cast to " + messageType.getName());
  }
}
