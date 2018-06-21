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

import com.google.template.soy.data.LoggingAdvisingAppendable;
import java.io.IOException;
import javax.annotation.concurrent.Immutable;

/**
 * String data.
 *
 * <p>Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 */
@Immutable
public final class StringData extends PrimitiveData implements SoyString {

  /** Static instance of StringData with value "". */
  public static final StringData EMPTY_STRING = new StringData("");

  private final String value;

  private StringData(String value) {
    this.value = value;
  }

  /**
   * Gets a StringData instance for the given value.
   *
   * @param value The desired value.
   * @return A StringData instance with the given value.
   */
  public static StringData forValue(String value) {
    return (value.length() == 0) ? EMPTY_STRING : new StringData(value);
  }

  /** Returns the string value. */
  public String getValue() {
    return value;
  }

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
  public void render(LoggingAdvisingAppendable appendable) throws IOException {
    appendable.append(value);
  }

  @Override
  public boolean equals(Object other) {
    // Keep this in sync with UnsanitizedString#equals so that StringData and UnsanitizedString can
    // be used interchangeably.
    return other != null && getValue().equals(other.toString());
  }

  @Override
  public int hashCode() {
    // Keep this in sync with UnsanitizedString#hashCode so that StringData and UnsanitizedString
    // can be used interchangeably.
    return stringValue().hashCode();
  }
}
