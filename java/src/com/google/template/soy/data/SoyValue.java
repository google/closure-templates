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

import java.io.IOException;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Superinterface of all Soy value interfaces/classes. Replaces the old SoyData.
 *
 * <p>Important: Until this API is more stable and this note is removed, users must not define
 * classes that implement this interface.
 *
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
  String stringValue();
}
