/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.sharedpasses.render;

import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.plugin.java.restricted.JavaValue;

/** Wraps a {@link SoyValue} into a {@link JavaValue}. */
final class TofuJavaValue implements JavaValue {
  static TofuJavaValue forSoyValue(SoyValue soyValue) {
    return new TofuJavaValue(soyValue);
  }

  private final SoyValue soyValue;

  private TofuJavaValue(SoyValue soyValue) {
    this.soyValue = soyValue;
  }

  SoyValue soyValue() {
    return soyValue;
  }

  @Override
  public ValueSoyType soyType() {
    // Tofu works in SoyValues, so we interpret the runtime type as the type of the SoyValue
    // and rely on the TofuValueFactory to cast appropriately before calling methods.
    if (soyValue instanceof StringData) {
      return ValueSoyType.STRING;
    } else if (soyValue instanceof BooleanData) {
      return ValueSoyType.BOOLEAN;
    } else if (soyValue instanceof NullData || soyValue instanceof UndefinedData) {
      return ValueSoyType.NULL;
    } else if (soyValue instanceof FloatData) {
      return ValueSoyType.FLOAT;
    } else if (soyValue instanceof IntegerData) {
      return ValueSoyType.INTEGER;
    } else if (soyValue instanceof SoyList) {
      return ValueSoyType.LIST;
    } else {
      return ValueSoyType.OTHER;
    }
  }

  @Override
  public String toString() {
    return "TofuJavaValue[soyValue=" + soyValue + "]";
  }
}
