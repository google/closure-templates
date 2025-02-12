/*
 * Copyright 2023 Google Inc.
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

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Message;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyValue;

/** Abstract superclass of null and undefined. */
@Immutable
public abstract class NullishData extends PrimitiveData {

  @Override
  public final int integerValue() {
    throw new SoyDataException("'" + this + "' cannot be coerced to integer");
  }

  @Override
  public final long longValue() {
    throw new SoyDataException("'" + this + "' cannot be coerced to long");
  }

  @Override
  public final double floatValue() {
    throw new SoyDataException("'" + this + "' cannot be coerced to float");
  }

  @Override
  public final double numberValue() {
    throw new SoyDataException("'" + this + "' cannot be coerced to number");
  }

  @Override
  public final boolean coerceToBoolean() {
    return false;
  }

  @Override
  public final String coerceToString() {
    return toString();
  }

  @Override
  public final SoyValue checkNullishType(Class<? extends SoyValue> type) {
    return this;
  }

  @Override
  public final SoyValue checkNullishNumber() {
    return this;
  }

  @Override
  public final SoyValue checkNullishBoolean() {
    return this;
  }

  @Override
  public final SoyValue checkNullishString() {
    return this;
  }

  @Override
  public final SoyValue checkNullishGbigint() {
    return this;
  }

  @Override
  public final SoyValue checkNullishSanitizedContent(ContentKind contentKind) {
    return this;
  }

  @Override
  public final SoyValue checkNullishProto(Class<? extends Message> messageType) {
    return this;
  }
}
