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
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/** Superinterface of all Soy value interfaces/classes. */
@ParametersAreNonnullByDefault
public abstract class SoyValue extends SoyValueProvider {

  /** Convenience method for testing nullishness. */
  @SuppressWarnings("ReferenceEquality") // This is safe since we own the definition of nullish
  public static boolean isNullish(@Nullable SoyValue value) {
    return value == null || value == UndefinedData.INSTANCE || value == NullData.INSTANCE;
  }

  /** If this is Soy null or Soy undefined. */
  @SuppressWarnings("ReferenceEquality") // This is safe since we own the definition of nullish
  public final boolean isNullish() {
    return this == UndefinedData.INSTANCE || this == NullData.INSTANCE;
  }

  /**
   * Compares this value against another for equality in the sense of the '==' operator of Soy.
   *
   * @param other The other value to compare against.
   * @return True if this value is equal to the other in the sense of Soy.
   */
  @Override
  public abstract boolean equals(Object other);

  // Force subtypes to implement hashCode
  @Override
  public abstract int hashCode();

  /**
   * Coerces this value into a boolean.
   *
   * @return This value coerced into a boolean.
   */
  public abstract boolean coerceToBoolean();

  /**
   * Coerces this value into a string.
   *
   * @return This value coerced into a string.
   */
  public abstract String coerceToString();

  @Override
  public SoyValueProvider coerceToBooleanProvider() {
    return BooleanData.forValue(this.coerceToBoolean());
  }

  /**
   * Performs a Java number to `long` coercion on the wrapped value. Compared with {@link
   * #longValue()} this method is expected to succeed for any {@link
   * com.google.template.soy.data.restricted.NumberData}.
   */
  public long coerceToLong() {
    throw new SoyDataException("'" + this + "' cannot be coerced to long");
  }

  public int coerceToInt() {
    throw new SoyDataException("'" + this + "' cannot be coerced to int");
  }

  /**
   * Returns whether this value is truthy. For SanitizedContent, this checks if the content is
   * empty.
   *
   * @return This value coerced into a boolean.
   */
  public boolean isTruthyNonEmpty() {
    return coerceToBoolean();
  }

  /**
   * Returns whether this value is truthy. For SanitizedContent, this checks if the content is not
   * empty.
   *
   * @return This value coerced into a boolean.
   */
  public boolean hasContent() {
    return coerceToBoolean();
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
  public abstract void render(LoggingAdvisingAppendable appendable) throws IOException;

  // -----------------------------------------------------------------------------------------------
  // Convenience methods for retrieving a known primitive type.

  /**
   * Precondition: Only call this method if you know that this SoyValue object is a boolean. This
   * method gets the boolean value of this boolean object.
   *
   * @return The boolean value of this boolean object.
   * @throws SoyDataException If this object is not actually a boolean.
   */
  public boolean booleanValue() {
    throw new SoyDataException(classCastErrorMessage(this, "bool"));
  }

  /**
   * Precondition: Only call this method if you know that this SoyValue object is a 32-bit integer.
   * This method gets the integer value of this integer object.
   *
   * @return The integer value of this integer object.
   * @throws SoyDataException If this object is not actually an integer.
   */
  public int integerValue() {
    throw new SoyDataException(classCastErrorMessage(this, "int"));
  }

  /**
   * Precondition: Only call this method if you know that this SoyValue object is an integer or
   * long. This method gets the integer value of this integer object.
   *
   * @return The integer value of this integer object.
   * @throws SoyDataException If this object is not actually an integer.
   */
  public long longValue() {
    throw new SoyDataException(classCastErrorMessage(this, "long"));
  }

  /**
   * Precondition: Only call this method if you know that this SoyValue object is a float. This
   * method gets the float value of this float object.
   *
   * @return The float value of this float object.
   * @throws SoyDataException If this object is not actually a float.
   */
  public double floatValue() {
    throw new SoyDataException(classCastErrorMessage(this, "float"));
  }

  /**
   * Precondition: Only call this method if you know that this SoyValue object is a number. This
   * method gets the float value of this number object (converting integer to float if necessary).
   *
   * @return The float value of this number object.
   * @throws SoyDataException If this object is not actually a number.
   */
  public double numberValue() {
    throw new SoyDataException(classCastErrorMessage(this, "number"));
  }

  /**
   * Precondition: Only call this method if you know that this SoyValue object is a string. This
   * method gets the string value of this string object.
   *
   * @return The string value of this string object.
   * @throws SoyDataException If this object is not actually a string.
   */
  @Nonnull
  public String stringValue() {
    throw new SoyDataException(classCastErrorMessage(this, "string"));
  }

  /** Returns null is this value is nullish, otherwise {@link #stringValue()}. */
  @Nullable
  public final String stringValueOrNull() {
    return isNullish() ? null : stringValue();
  }

  /** If this is Soy null (NullData). */
  public final boolean isNull() {
    return this == NullData.INSTANCE;
  }

  /** If this is Soy undefined (UndefinedData). */
  public final boolean isUndefined() {
    return this == UndefinedData.INSTANCE;
  }

  /** Returns this value, coalescing UndefinedData to NullData. */
  public final SoyValue nullishToNull() {
    return this == UndefinedData.INSTANCE ? NullData.INSTANCE : this;
  }

  /** Returns this value, coalescing NullData to UndefinedData. */
  public final SoyValue nullishToUndefined() {
    return this == NullData.INSTANCE ? UndefinedData.INSTANCE : this;
  }

  @Nonnull
  public Message getProto() {
    throw new SoyDataException(classCastErrorMessage(this, "Message"));
  }

  /** Returns null is this value is nullish, otherwise {@link #getProto()}. */
  @Nullable
  public final Message getProtoOrNull() {
    return isNullish() ? null : getProto();
  }

  @Nonnull
  public List<? extends SoyValueProvider> asJavaList() {
    throw new SoyDataException(classCastErrorMessage(this, "list"));
  }

  @Nullable
  public final List<? extends SoyValueProvider> asJavaListOrNull() {
    return isNullish() ? null : asJavaList();
  }

  @Nonnull
  public Iterator<? extends SoyValueProvider> javaIterator() {
    return asJavaList().iterator();
  }

  @Nonnull
  public Map<? extends SoyValue, ? extends SoyValueProvider> asJavaMap() {
    throw new SoyDataException(classCastErrorMessage(this, "map"));
  }

  @Nonnull
  public SoyRecord asSoyRecord() {
    throw new ClassCastException(classCastErrorMessage(this, "record"));
  }

  /**
   * A runtime type check for this boxed Soy value.
   *
   * @throws ClassCastException if this value is not null, undefined, or an instance of {@code
   *     type}.
   */
  public SoyValue checkNullishType(Class<? extends SoyValue> type) {
    return type.cast(this);
  }

  /** A runtime type check for this boxed Soy value. */
  public SoyValue checkNullishInt() {
    throw new ClassCastException(classCastErrorMessage(this, "int"));
  }

  /** A runtime type check for this boxed Soy value. */
  public SoyValue checkNullishFloat() {
    throw new ClassCastException(classCastErrorMessage(this, "float"));
  }

  /** A runtime type check for this boxed Soy value. */
  public SoyValue checkNullishNumber() {
    throw new ClassCastException(classCastErrorMessage(this, "number"));
  }

  public SoyValue checkNullishGbigint() {
    throw new ClassCastException(classCastErrorMessage(this, "gbigint"));
  }

  /** A runtime type check for this boxed Soy value. */
  public SoyValue checkNullishBoolean() {
    throw new ClassCastException(classCastErrorMessage(this, "bool"));
  }

  /** A runtime type check for this boxed Soy value. */
  public SoyValue checkNullishString() {
    throw new ClassCastException(classCastErrorMessage(this, "string"));
  }

  public SoyValue checkNullishFunction() {
    throw new ClassCastException(classCastErrorMessage(this, "function"));
  }

  /**
   * A runtime type check for this boxed Soy value.
   *
   * @throws ClassCastException if this value is not null, undefined, or an instance of {@link
   *     SanitizedContent} whose content kind is equal to {@code contentKind}.
   */
  public SoyValue checkNullishSanitizedContent(ContentKind contentKind) {
    throw new ClassCastException(classCastErrorMessage(this, contentKind.getSoyTypeName()));
  }

  /**
   * A runtime type check for this boxed Soy value.
   *
   * @throws ClassCastException if this value is not null, undefined, or an instance of {@link
   *     SoyProtoValue} whose unboxed proto is an instance of {@code messageType}.
   */
  public SoyValue checkNullishProto(Class<? extends Message> messageType) {
    throw new ClassCastException(
        classCastErrorMessage(this, ProtoUtils.getProtoTypeName(messageType)));
  }

  /** Returns true if this value is a sanitized content with kind equal to `contentKind`. */
  public boolean isSanitizedContentKind(ContentKind contentKind) {
    return false;
  }

  /** Returns true if this value is a proto of type `messageType`. */
  public boolean isProtoInstance(Class<? extends Message> messageType) {
    return false;
  }

  /**
   * Returns a human-readable string representation of the Soy type contained by this value, for use
   * in error messages.
   */
  public abstract String getSoyTypeName();

  // SoyValueProvider methods
  @Override
  @Nonnull
  public final SoyValue resolve() {
    return this;
  }

  @Override
  @Nonnull
  public final RenderResult status() {
    return RenderResult.done();
  }

  @Override
  public final RenderResult renderAndResolve(LoggingAdvisingAppendable appendable)
      throws IOException {
    render(appendable);
    return RenderResult.done();
  }

  private static String classCastErrorMessage(SoyValue value, String requiredSoyType) {
    return String.format("expected %s, got %s", requiredSoyType, value.getSoyTypeName());
  }
}
