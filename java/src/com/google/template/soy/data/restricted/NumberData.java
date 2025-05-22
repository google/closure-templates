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

import com.google.common.primitives.Longs;
import com.google.template.soy.data.SoyValue;

/** Abstract superclass for number data (integers and floats). */
public abstract class NumberData extends PrimitiveData {

  @Override
  public final SoyValue checkNullishInt() {
    return this;
  }

  @Override
  public final SoyValue checkNullishFloat() {
    return this;
  }

  /**
   * Returns true if this value is a whole integer in the range representable in JavaScript without
   * a loss of precision.
   */
  public abstract boolean isSafeJsInteger();

  @Override
  @Deprecated
  public final double numberValue() {
    return floatValue();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof NumberData && ((NumberData) other).floatValue() == this.floatValue();
  }

  @Override
  public int hashCode() {
    return Longs.hashCode(Double.doubleToLongBits(floatValue()));
  }

  @Override
  public final SoyValue checkNullishNumber() {
    return this;
  }
}
