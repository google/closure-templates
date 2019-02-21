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

package com.google.template.soy.msgs.restricted;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import javax.annotation.Nullable;

/**
 * Represents a part of a message (i.e. raw text or placeholder).
 *
 */
@Immutable
public abstract class SoyMsgPart {
  // TODO(lukes): there is a fair bit of code inspecting this type hierarchy via cascading
  // instanceof tests.  Consider introducing a visitor api or an enum for fast switching.

  /** A case in a plural or 'select' msg part. */
  @AutoValue
  @Immutable(containerOf = "T")
  public abstract static class Case<T> {
    public static <T> Case<T> create(T spec, Iterable<? extends SoyMsgPart> parts) {
      return new AutoValue_SoyMsgPart_Case<>(spec, ImmutableList.copyOf(parts));
    }

    Case() {}

    // null means default case
    @Nullable
    public abstract T spec();

    public abstract ImmutableList<SoyMsgPart> parts();
  }

  // force subtypes to implement this.
  @Override
  public abstract String toString();
}
