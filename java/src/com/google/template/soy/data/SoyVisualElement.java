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
import com.google.template.soy.logging.LoggableElementMetadata;
import java.io.IOException;

/** Soy's runtime representation of objects of the Soy {@code ve} type. */
@AutoValue
public abstract class SoyVisualElement extends SoyAbstractValue {

  private static final LoggableElementMetadata EMPTY_METADATA =
      LoggableElementMetadata.getDefaultInstance();

  public static SoyVisualElement create(long id, String name) {
    return create(id, name, EMPTY_METADATA);
  }

  public static SoyVisualElement create(long id, String name, LoggableElementMetadata metadata) {
    return new AutoValue_SoyVisualElement(id, name, metadata);
  }

  public abstract long id();

  public abstract String name();

  public abstract LoggableElementMetadata metadata();

  @Override
  public boolean coerceToBoolean() {
    return true;
  }

  String getDebugString() {
    return String.format("ve(%s)", name());
  }

  @Override
  public String coerceToString() {
    return String.format("**FOR DEBUGGING ONLY %s, id: %s**", getDebugString(), id());
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
}
