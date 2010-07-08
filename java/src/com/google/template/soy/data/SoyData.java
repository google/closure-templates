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

package com.google.template.soy.data;

import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;

import java.util.List;
import java.util.Map;


/**
 * Abstract base class for all nodes in a Soy data tree.
 *
 * @author Kai Huang
 */
public abstract class SoyData {


  /**
   * Creation function for creating a SoyData object out of any existing primitive, data object, or
   * data structure.
   *
   * <p> Important: Avoid using this function if you know the type of the object at compile time.
   * For example, if the object is a primitive, it can be passed directly to methods such as
   * {@code SoyMapData.put()} or {@code SoyListData.add()}. If the object is a Map or an Iterable,
   * you can directly create the equivalent SoyData object using the constructor of
   * {@code SoyMapData} or {@code SoyListData}. 
   *
   * <p> If the given object is already a SoyData object, then it is simply returned.
   * Otherwise a new SoyData object will be created that is equivalent to the given primitive, data
   * object, or data structure (even if the given object is null!).
   *
   * <p> Note that in order for the conversion process to succeed, the given data structure must
   * correspond to a valid SoyData tree. Some requirements include:
   * (a) all Maps within your data structure must have string keys that are identifiers,
   * (b) all non-leaf nodes must be Maps or Lists,
   * (c) all leaf nodes must be null, boolean, int, double, or String (corresponding to Soy
   *     primitive data types null, boolean, integer, float, string).
   *
   * @param obj The existing object or data structure to convert.
   * @return A SoyData object or tree that corresponds to the given object.
   * @throws SoyDataException If the given object cannot be converted to SoyData.
   */
  public static SoyData createFromExistingData(Object obj) {

    if (obj == null) {
      return NullData.INSTANCE;
    } else if (obj instanceof SoyData) {
      return (SoyData) obj;
    } else if (obj instanceof String) {
      return new StringData((String) obj);
    } else if (obj instanceof Boolean) {
      return new BooleanData((Boolean) obj);
    } else if (obj instanceof Integer) {
      return new IntegerData((Integer) obj);
    } else if (obj instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, ?> objCast = (Map<String, ?>) obj;
      return new SoyMapData(objCast);
    } else if (obj instanceof List) {
      return new SoyListData((List<?>) obj);
    } else if (obj instanceof Double) {
      return new FloatData((Double) obj);
    } else if (obj instanceof Float) {
      // Automatically convert float to double.
      return new FloatData((Float) obj);
    } else {
      throw new SoyDataException(
          "Attempting to convert unrecognized object to Soy data (object type " +
          obj.getClass().getSimpleName() + ").");
    }
  }


  /**
   * Converts this data object into a string (e.g. when used in a string context).
   * @return The value of this data object if coerced into a string.
   */
  @Override public abstract String toString();


  /**
   * Converts this data object into a boolean (e.g. when used in a boolean context). In other words,
   * this method tells whether this object is truthy.
   * @return The value of this data object if coerced into a boolean. I.e. true if this object is
   *     truthy, false if this object is falsy.
   */
  public abstract boolean toBoolean();


  /**
   * Compares this data object against another for equality in the sense of the operator '==' for
   * Soy expressions.
   *
   * @param other The other data object to compare against.
   * @return True if the two objects are equal.
   */
  @Override public abstract boolean equals(Object other);


  /**
   * Precondition: Only call this method if you know that this SoyData object is a boolean.
   * This method gets the boolean value of this boolean object.
   * @return The boolean value of this boolean object.
   * @throws SoyDataException If this object is not actually a boolean.
   */
  public boolean booleanValue() {
    throw new SoyDataException("Non-boolean found when expecting boolean value.");
  }


  /**
   * Precondition: Only call this method if you know that this SoyData object is an integer.
   * This method gets the integer value of this integer object.
   * @return The integer value of this integer object.
   * @throws SoyDataException If this object is not actually an integer.
   */
  public int integerValue() {
    throw new SoyDataException("Non-integer found when expecting integer value.");
  }


  /**
   * Precondition: Only call this method if you know that this SoyData object is a float.
   * This method gets the float value of this float object.
   * @return The float value of this float object.
   * @throws SoyDataException If this object is not actually a float.
   */
  public double floatValue() {
    throw new SoyDataException("Non-float found when expecting float value.");
  }


  /**
   * Precondition: Only call this method if you know that this SoyData object is a number.
   * This method gets the float value of this number object (converting integer to float if
   * necessary).
   * @return The float value of this number object.
   * @throws SoyDataException If this object is not actually a number.
   */
  public double numberValue() {
    throw new SoyDataException("Non-number found when expecting number value.");
  }


  /**
   * Precondition: Only call this method if you know that this SoyData object is a string.
   * This method gets the string value of this string object.
   * @return The string value of this string object.
   * @throws SoyDataException If this object is not actually a string.
   */
  public String stringValue() {
    throw new SoyDataException("Non-string found when expecting string value.");
  }

}
