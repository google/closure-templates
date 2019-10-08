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
import com.google.common.reflect.TypeToken;

/**
 * A representation of a parameter to a Soy template.
 *
 * @see SoyTemplate
 */
@AutoValue
public abstract class SoyTemplateParam<T> {
  /** Creates an optional param with the given name. */
  public static <T> SoyTemplateParam<T> optional(String name, TypeToken<T> type) {
    return new AutoValue_SoyTemplateParam<>(name, false, false, false, type);
  }

  /** Creates a required param with the given name. */
  public static <T> SoyTemplateParam<T> required(String name, TypeToken<T> type) {
    return new AutoValue_SoyTemplateParam<>(name, true, false, false, type);
  }

  /** Creates an indirect param with the given name. Indirect params are always optional. */
  public static <T> SoyTemplateParam<T> indirect(String name, TypeToken<T> type) {
    return new AutoValue_SoyTemplateParam<>(name, false, true, false, type);
  }

  /** Creates an injected param with the given name. */
  public static <T> SoyTemplateParam<T> injected(String name, TypeToken<T> type) {
    return new AutoValue_SoyTemplateParam<>(name, false, false, true, type);
  }

  public abstract String getName();

  abstract boolean isRequired();

  abstract boolean isIndirect();

  abstract boolean isInjected();

  public abstract TypeToken<T> getType();
}
