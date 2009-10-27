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

import com.google.common.base.Preconditions;


/**
 * String data.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
 */
public class StringData extends PrimitiveData {


  /** The string value. */
  private final String value;


  /**
   * @param value The string value.
   */
  public StringData(String value) {
    Preconditions.checkNotNull(value);
    this.value = value;
  }


  /** Returns the string value. */
  public String getValue() {
    return value;
  }


  @Override public String stringValue() {
    return value;
  }


  @Override public String toString() {
    return value;
  }


  /**
   * {@inheritDoc}
   *
   * <p> The empty string is falsy.
   */
  @Override public boolean toBoolean() {
    return value.length() > 0;
  }


  @Override public boolean equals(Object other) {
    return other != null && other.getClass() == StringData.class &&
           ((StringData) other).getValue().equals(value);
  }


  @Override public int hashCode() {
    return value.hashCode();
  }

}
