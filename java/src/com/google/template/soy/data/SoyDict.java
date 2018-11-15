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

import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A SoyRecord that also implements the SoyLegacyObjectMap interface.
 *
 * <p>In map usage, the item keys are the record field names in the form of StringData.
 *
 * <p>Important: Until this API is more stable and this note is removed, users must not define
 * classes that implement this interface.
 *
 */
@ParametersAreNonnullByDefault
public interface SoyDict extends SoyRecord, SoyLegacyObjectMap {

  /**
   * Gets a Java map of all items in this SoyDict, where mappings are string to value provider. Note
   * that value providers are often just the values themselves, since all values are also providers.
   *
   * @return A Java map of all items, where mappings are string to value provider.
   */
  @Nonnull
  public Map<String, ? extends SoyValueProvider> asJavaStringMap();

  /**
   * Gets a Java map of all items in this SoyDict, where mappings are string to value. All value
   * providers will be eagerly resolved.
   *
   * @return A Java map of all items, where mappings are string to value.
   */
  @Nonnull
  public Map<String, ? extends SoyValue> asResolvedJavaStringMap();
}
