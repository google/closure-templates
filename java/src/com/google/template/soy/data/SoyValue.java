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

import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.RenderResult;

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
   * @param other The other value to compare against.
   * @return True if this value is equal to the other in the sense of Soy.
   */
  public boolean equals(SoyValue other);


  /**
   * Coerces this value into a boolean.
   * @return This value coerced into a boolean.
   */
  public boolean coerceToBoolean();


  /**
   * Coerces this value into a string.
   * @return This value coerced into a string.
   */
  public String coerceToString();

  /**
   * Renders this value to the given appendable.
   *
   * <p>This should behave identically to {@code appendable.append(coerceToString())} but is
   * provided separately to allow more incremental approaches.
   *
   * @param appendable The appendable to render to.
   * @throws IOException
   */
  public void render(Appendable appendable) throws IOException;

  /**
   * Renders this value to the given {@link AdvisingAppendable}, possibly partially.
   *
   * <p>This should render the exact same content as {@link #render(Appendable)} but may optionally
   * detach part of the way through rendering.  Note, this means that this method is
   * <em>stateful</em> and if it returns something besides {@link RenderResult#done()} then the
   * next call to this method will resume rendering from the previous point.
   *
   * <p>It is expected that most implementations will simply delegate to
   * {@link #render(Appendable)}, since the renderable content is small/trivial.
   *
   * @param appendable The appendable to render to.
   * @param isLast True if this is <em>definitely</em> the last time this value will be rendered.
   *     Used as a hint to implementations to not optimize for later calls (for example, by storing
   *     render results in a buffer for faster re-renders).  The value of this parameter should not
   *     affect behavior of this method, only performance.
   * @returns A {@link RenderResult} that describes whether or not rendering completed.  If the
   *     returned result is not {@link RenderResult#done() done}, then to complete rendering you
   *     must call this method again.
   * @throws IOException If the appendable throws an IOException
   */
  public RenderResult render(AdvisingAppendable appendable, boolean isLast) throws IOException;

  // -----------------------------------------------------------------------------------------------
  // Convenience methods for retrieving a known primitive type.


  /**
   * Precondition: Only call this method if you know that this SoyValue object is a boolean.
   * This method gets the boolean value of this boolean object.
   * @return The boolean value of this boolean object.
   * @throws SoyDataException If this object is not actually a boolean.
   */
  public boolean booleanValue();


  /**
   * Precondition: Only call this method if you know that this SoyValue object is a 32-bit integer.
   * This method gets the integer value of this integer object.
   * @return The integer value of this integer object.
   * @throws SoyDataException If this object is not actually an integer.
   */
  public int integerValue();


  /**
   * Precondition: Only call this method if you know that this SoyValue object is an integer
   * or long. This method gets the integer value of this integer object.
   * @return The integer value of this integer object.
   * @throws SoyDataException If this object is not actually an integer.
   */
  public long longValue();


  /**
   * Precondition: Only call this method if you know that this SoyValue object is a float.
   * This method gets the float value of this float object.
   * @return The float value of this float object.
   * @throws SoyDataException If this object is not actually a float.
   */
  public double floatValue();


  /**
   * Precondition: Only call this method if you know that this SoyValue object is a number.
   * This method gets the float value of this number object (converting integer to float if
   * necessary).
   * @return The float value of this number object.
   * @throws SoyDataException If this object is not actually a number.
   */
  public double numberValue();


  /**
   * Precondition: Only call this method if you know that this SoyValue object is a string.
   * This method gets the string value of this string object.
   * @return The string value of this string object.
   * @throws SoyDataException If this object is not actually a string.
   */
  public String stringValue();

}
