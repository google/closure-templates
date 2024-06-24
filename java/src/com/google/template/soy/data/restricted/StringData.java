/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.data.restricted;

import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyValue;
import java.io.IOException;
import javax.annotation.Nonnull;

/**
 * String data.
 *
 * <p>Important: This class may only be used in implementing plugins (e.g. functions, directives).
 */
@Immutable
public abstract class StringData extends PrimitiveData {

  /** Static instance of StringData with value "". */
  public static final StringData EMPTY_STRING = new Impl("");

  /**
   * Gets a StringData instance for the given value.
   *
   * @param value The desired value.
   * @return A StringData instance with the given value.
   */
  @Nonnull
  public static StringData forValue(String value) {
    return (value.length() == 0) ? EMPTY_STRING : new Impl(value);
  }

  @Nonnull
  public static StringData forValue(LoggingAdvisingAppendable.CommandBuffer value) {
    return new BufferedImpl(value);
  }

  /** Returns the string value. */
  public abstract String getValue();

  @Override
  public String stringValue() {
    return getValue();
  }

  @Override
  public String toString() {
    return getValue();
  }

  /**
   * {@inheritDoc}
   *
   * <p>The empty string is falsy.
   */
  @Override
  public boolean coerceToBoolean() {
    return getValue().length() > 0;
  }

  @Override
  public String coerceToString() {
    return toString();
  }

  @Override
  public final boolean equals(Object other) {
    return other instanceof StringData && getValue().equals(((StringData) other).getValue());
  }

  @Override
  public final int hashCode() {
    return stringValue().hashCode();
  }

  @Override
  public SoyValue checkNullishString() {
    return this;
  }

  private static final class Impl extends StringData {
    private final String value;

    Impl(String value) {
      this.value = value;
    }

    @Override
    public String getValue() {
      return value;
    }

    @Override
    public void render(LoggingAdvisingAppendable appendable) throws IOException {
      appendable.append(value);
    }
  }

  private static final class BufferedImpl extends StringData {
    @LazyInit private String value;
    private final LoggingAdvisingAppendable.CommandBuffer buffer;

    BufferedImpl(LoggingAdvisingAppendable.CommandBuffer value) {
      this.buffer = value;
    }

    @Override
    public String getValue() {
      var value = this.value;
      if (value == null) {
        value = buffer.toString();
        this.value = value;
      }
      return value;
    }

    @Override
    public void render(LoggingAdvisingAppendable appendable) throws IOException {
      buffer.replayOn(appendable);
    }
  }
}
