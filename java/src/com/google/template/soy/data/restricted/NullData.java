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
 * Null data.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
 */
public class NullData extends PrimitiveData {


  /** Singleton instance of NullData. */
  public static final NullData INSTANCE = new NullData();


  private NullData() {}


  @Override public String toString() {
    return "null";
  }


  /**
   * {@inheritDoc}
   *
   * <p> Null is falsy.
   */
  @Override public boolean toBoolean() {
    return false;
  }


  @Override public boolean equals(Object other) {
    return other == NullData.INSTANCE;
  }


  @Override public int hashCode() {
    return 827;
  }

}
