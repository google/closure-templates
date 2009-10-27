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


/**
 * Abstract superclass for number data (integers and floats).
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
 */
public abstract class NumberData extends PrimitiveData {


  /**
   * Gets the float value of this number data object. If this object is actually an integer, its
   * value will be converted to a float before being returned.
   * @return The float value of this number data object.
   */
  public abstract double toFloat();


  @Override public double numberValue() {
    return toFloat();
  }


  @Override public boolean equals(Object other) {
    return other != null && other instanceof NumberData &&
           ((NumberData) other).toFloat() == this.toFloat();
  }


  @Override public int hashCode() {
    return (new Double(toFloat())).hashCode();
  }

}
