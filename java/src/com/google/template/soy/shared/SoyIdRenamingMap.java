/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.shared;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * An interface for a one-to-one string mapping function used to rename identifiers. Renaming can be
 * used for minimization, obfuscation, normalization, etc.
 *
 */
@ParametersAreNonnullByDefault
public interface SoyIdRenamingMap {

  /**
   * Gets the string that should be substituted for {@code key}. The same value must be consistently
   * returned for any particular {@code key}, and the returned value must not be returned for any
   * other {@code key} value.
   *
   * @param key The text to be replaced, never null.
   * @return The value to substitute for {@code key}, or null if not found in map.
   */
  @Nullable
  String get(String key);
}
