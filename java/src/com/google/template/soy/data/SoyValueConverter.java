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

import javax.annotation.Nonnull;


/**
 * <p> Important: Until this API is more stable and this note is removed, users must not directly
 * use this class.
 *
 * A general converter that knows how to convert all expected Java objects to SoyValues (or
 * SoyValueProviders).
 *
 */
public interface SoyValueConverter {


  /**
   * Converts the given object into a corresponding SoyValue or SoyValueProvider.
   *
   * Throws SoyDataException if an unexpected object is encountered.
   *
   * @param obj The object to convert.
   * @return A provider for the converted value (often the converted value itself).
   */
  @Nonnull SoyValueProvider convert(Object obj);

}
