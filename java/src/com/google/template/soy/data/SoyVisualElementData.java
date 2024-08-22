/*
 * Copyright 2019 Google Inc.
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
import com.google.protobuf.Message;
import java.io.IOException;
import javax.annotation.Nullable;

/** Soy's runtime representation of objects of the Soy {@code ve_data} type. */
@AutoValue
public abstract class SoyVisualElementData extends SoyValue {
  public static SoyVisualElementData create(SoyValue ve) {
    return create(ve, null);
  }

  public static SoyVisualElementData create(SoyValue ve, @Nullable Message data) {
    if (ve.isNullish()) {
      throw new NullPointerException();
    }
    return new AutoValue_SoyVisualElementData((SoyVisualElement) ve, data);
  }

  public abstract SoyVisualElement ve();

  @Nullable
  public abstract Message data();

  @Override
  public boolean coerceToBoolean() {
    return true;
  }

  @Override
  public String coerceToString() {
    return "**FOR DEBUGGING ONLY ve_data("
        + ve().getDebugString()
        + (data() == null ? "" : ", " + data())
        + ")**";
  }

  @Override
  public void render(LoggingAdvisingAppendable appendable) throws IOException {
    appendable.append(coerceToString());
  }

  @Override
  public final boolean equals(Object other) {
    return this == other;
  }

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String getSoyTypeName() {
    return "ve_data";
  }
}
