/*
 * Copyright 2025 Google Inc.
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

import com.google.auto.value.AutoValue;
import java.io.IOException;
import javax.annotation.Nonnull;

/** A signal wrapping a value. */
@AutoValue
public abstract class SignalValue extends SoyValue {
  @Nonnull
  public static SignalValue create(SoyValue data) {
    return new AutoValue_SignalValue(data);
  }

  public abstract SoyValue getData();

  @Override
  public final boolean coerceToBoolean() {
    return true;
  }

  @Override
  public final String coerceToString() {
    return getData().coerceToString();
  }

  @Override
  public final void render(LoggingAdvisingAppendable appendable) throws IOException {
    getData().render(appendable);
  }

  @Override
  public String getSoyTypeName() {
    return "Signal<" + getData().getSoyTypeName() + ">";
  }
}
