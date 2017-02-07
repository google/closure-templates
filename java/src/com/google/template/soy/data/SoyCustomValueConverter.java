/*
 * Copyright 2013 Google Inc.
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

/**
 * Important: Until this API is more stable and this note is removed, users must not directly use
 * this class.
 *
 * <p>A custom converter that knows how to convert some specific Java objects to SoyValues (or
 * SoyValueProviders).
 *
 *     <p>TODO(user): Going away soon. Please perform data conversions outside of Soy code.
 */
public interface SoyCustomValueConverter {

  /**
   * Converts the given object into a corresponding SoyValue or SoyValueProvider. If this converter
   * is not intended to handle the given object, then returns null.
   *
   * @param valueConverter The converter to use for internal arbitrary object conversions (if
   *     needed). This should be a general converter that knows how to handle all object types.
   * @param obj The object to convert.
   * @return A provider for the converted value (often the converted value itself).
   */
  SoyValueProvider convert(SoyValueConverter valueConverter, Object obj);
}
