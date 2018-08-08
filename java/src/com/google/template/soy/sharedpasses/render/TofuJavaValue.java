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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.StringType;
import com.ibm.icu.util.ULocale;
import javax.annotation.Nullable;

/** Wraps a {@link SoyValue} into a {@link JavaValue}. */
final class TofuJavaValue implements JavaValue {
  static TofuJavaValue forSoyValue(SoyValue soyValue, SourceLocation sourceLocation) {
    return new TofuJavaValue(checkNotNull(soyValue), null, null, checkNotNull(sourceLocation));
  }

  static TofuJavaValue forULocale(ULocale locale) {
    return new TofuJavaValue(null, checkNotNull(locale), null, null);
  }

  static JavaValue forBidiDir(BidiGlobalDir bidiGlobalDir) {
    return new TofuJavaValue(null, null, checkNotNull(bidiGlobalDir), null);
  }

  @Nullable private final SoyValue soyValue;
  @Nullable private final SourceLocation sourceLocation;
  @Nullable private final ULocale locale;
  @Nullable private final BidiGlobalDir bidiGlobalDir;

  private TofuJavaValue(
      SoyValue soyValue,
      ULocale locale,
      BidiGlobalDir bidiGlobalDir,
      SourceLocation sourceLocation) {
    this.soyValue = soyValue;
    this.locale = locale;
    this.bidiGlobalDir = bidiGlobalDir;
    this.sourceLocation = sourceLocation;
  }

  boolean hasSoyValue() {
    return soyValue != null;
  }

  SoyValue soyValue() {
    checkState(soyValue != null);
    return soyValue;
  }

  BidiGlobalDir bidiGlobalDir() {
    checkState(bidiGlobalDir != null);
    return bidiGlobalDir;
  }

  ULocale locale() {
    checkState(locale != null);
    return locale;
  }

  @Override
  public TofuJavaValue isNonNull() {
    if (soyValue == null) {
      throw RenderException.create(
          "isNonNull is only supported on the 'args' parameters of JavaValueFactory methods");
    }
    return forSoyValue(
        BooleanData.forValue(!(soyValue instanceof UndefinedData || soyValue instanceof NullData)),
        sourceLocation);
  }

  @Override
  public TofuJavaValue isNull() {
    if (soyValue == null) {
      throw RenderException.create(
          "isNull is only supported on the 'args' parameters of JavaValueFactory methods");
    }
    return forSoyValue(
        BooleanData.forValue(soyValue instanceof UndefinedData || soyValue instanceof NullData),
        sourceLocation);
  }

  @Override
  public TofuJavaValue asSoyBoolean() {
    checkType(BoolType.getInstance());
    return this;
  }

  @Override
  public TofuJavaValue asSoyFloat() {
    checkType(StringType.getInstance());
    return this;
  }

  @Override
  public TofuJavaValue asSoyInt() {
    checkType(IntType.getInstance());
    return this;
  }

  @Override
  public TofuJavaValue asSoyString() {
    checkType(StringType.getInstance());
    return this;
  }

  @Override
  public JavaValue coerceToSoyBoolean() {
    return TofuJavaValue.forSoyValue(
        BooleanData.forValue(soyValue.coerceToBoolean()), sourceLocation);
  }

  @Override
  public JavaValue coerceToSoyString() {
    return TofuJavaValue.forSoyValue(
        StringData.forValue(soyValue.coerceToString()), sourceLocation);
  }

  private void checkType(SoyType type) {
    if (!TofuTypeChecks.isInstance(type, soyValue, sourceLocation)) {
      throw RenderException.create(
          "SoyValue["
              + soyValue
              + "] of type: "
              + soyValue.getClass()
              + " is incompatible with soy type: "
              + type);
    }
  }

  @Override
  public String toString() {
    if (soyValue != null) {
      return "TofuJavaValue[soyValue=" + soyValue + "]";
    } else if (locale != null) {
      return "TofuJavaValue[locale=" + locale + "]";
    } else {
      return "TofuJavaValue[bidiGlobalDir=" + bidiGlobalDir + "]";
    }
  }
}
