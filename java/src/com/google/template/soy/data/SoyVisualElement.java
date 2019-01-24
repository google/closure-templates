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

package com.google.template.soy.data;

import com.google.auto.value.AutoValue;
import java.io.IOException;

/** Soy's runtime representation of objects of the Soy {@code ve} type. */
@AutoValue
public abstract class SoyVisualElement extends SoyAbstractValue {

  public static SoyVisualElement create(long id, String name) {
    return new AutoValue_SoyVisualElement(id, name);
  }

  public abstract long id();

  public abstract String name();

  @Override
  public boolean coerceToBoolean() {
    return true;
  }

  @Override
  public String coerceToString() {
    return String.format("**FOR DEBUGGING ONLY ve(%s), id: %s**", name(), id());
  }

  @Override
  public void render(LoggingAdvisingAppendable appendable) throws IOException {
    appendable.append(coerceToString());
  }
}
