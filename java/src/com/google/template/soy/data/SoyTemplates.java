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

import java.lang.reflect.Method;

/** Reflective utilities related to {@link SoyTemplate}. */
public final class SoyTemplates {

  private SoyTemplates() {}

  /**
   * Reflectively obtains the default instance of a template type.
   *
   * @throws IllegalArgumentException if the template has one or more required parameters (i.e. the
   *     getDefaultInstance method does not exist on the class).
   */
  public static <T extends SoyTemplate> T getDefaultInstance(Class<T> type) {
    try {
      Method factory = type.getDeclaredMethod("getDefaultInstance");
      Object instance = factory.invoke(null);
      return type.cast(instance);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          "No default instance for template type " + type.getName(), e);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(
          "Unexpected error while calling getDefaultInstance() on " + type.getName(), e);
    }
  }

  /** Reflectively creates a builder of a template type. */
  public static <T extends SoyTemplate> SoyTemplate.Builder<T> getBuilder(Class<T> type) {
    try {
      Method factory = type.getDeclaredMethod("builder");
      @SuppressWarnings("unchecked")
      SoyTemplate.Builder<T> instance = (SoyTemplate.Builder<T>) factory.invoke(null);
      return instance;
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(
          "Unexpected error while calling builder() on " + type.getName(), e);
    }
  }
}
