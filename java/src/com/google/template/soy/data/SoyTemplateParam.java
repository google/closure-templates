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
  /** Creates a standard optional or required param with the given name. */
  public static <T> SoyTemplateParam<T> standard(String name, boolean required, TypeToken<T> type) {
    return new AutoValue_SoyTemplateParam<>(
        name, required, /* indirect= */ false, /* injected= */ false, type);
  }

  /** Creates an indirect param with the given name. Indirect params are always optional. */
  public static <T> SoyTemplateParam<T> indirect(String name, boolean required, TypeToken<T> type) {
    return new AutoValue_SoyTemplateParam<>(
        name, required, /* indirect= */ true, /* injected= */ false, type);
  }

  /** Creates an injected param with the given name. */
  public static <T> SoyTemplateParam<T> injected(String name, boolean required, TypeToken<T> type) {
    return new AutoValue_SoyTemplateParam<>(
        name, required, /* indirect= */ false, /* injected= */ true, type);
  }

  public abstract String getName();

  /**
   * Returns whether the parameter is declared as required. All required, non-indirect parameters
   * must be set for {@link SoyTemplate.Builder#build} to succeed.
   *
   * <p>If a parameter is indirect then {@link #isRequired} may return `true`. But missing indirect
   * parameters never cause {@link SoyTemplate.Builder#build} to fail.
   */
  public abstract boolean isRequired();

  /**
   * Returns whether the parameter is indirectly included from another template via a `{call
   * data="all"}`.
   */
  public abstract boolean isIndirect();

  /** Returns whether the parameter is an injected parameter declared with `@inject`. */
  public abstract boolean isInjected();

  public abstract TypeToken<T> getType();

  boolean isRequiredAndNotIndirect() {
    return isRequired() && !isIndirect();
  }
}
