/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.jssrc.restricted;

import com.google.common.collect.ImmutableSet;

/**
 * Interface for a Soy function with more Closure Library requirements than typical Soy templates,
 * implemented for the JS Source backend.
 *
 * <p>Important: This may only be used in implementing function plugins.
 *
 */
public interface SoyLibraryAssistedJsSrcFunction extends SoyJsSrcFunction {

  /**
   * Returns a list of Closure library names to require when this function is used.
   *
   * <p>Note: Return the raw Closure library names, Soy will wrap them in goog.require for you.
   *
   * @return A collection of strings representing Closure JS library names
   */
  ImmutableSet<String> getRequiredJsLibNames();
}
