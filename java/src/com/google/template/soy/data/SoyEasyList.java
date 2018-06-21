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

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A mutable list with additional methods for ease-of-use.
 *
 * <p>Important: Do not use. Use java.util.List instead.
 *
 */
@Deprecated
@ParametersAreNonnullByDefault
public interface SoyEasyList extends SoyList, SoyLegacyObjectMap {

  /**
   * Adds a value to the end of this SoyList.
   *
   * @param valueProvider A provider of the value to add. Note that this is often just the value
   *     itself, since all values are also providers.
   */
  @Deprecated
  public void add(SoyValueProvider valueProvider);
}
