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

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A list containing values. Each value is a SoyValue (can be unresolved).
 *
 * <p>A list also supports the map interface. In that usage, the item keys are the list indices in
 * the form of IntegerData.
 *
 * <p>Important: Until this API is more stable and this note is removed, users must not define
 * classes that implement this interface.
 *
 */
@ParametersAreNonnullByDefault
public interface SoyList extends SoyMap {

  /**
   * Gets the length of this SoyList.
   *
   * @return The length.
   */
  public int length();

  /**
   * Gets a Java list of all value providers in this SoyList. Note that value providers are often
   * just the values themselves, since all values are also providers.
   *
   * @return A Java list of all value providers.
   */
  @Nonnull
  public List<? extends SoyValueProvider> asJavaList();

  /**
   * Gets a Java list all values in this SoyList. All value providers will be eagerly resolved.
   *
   * @return A Java list of all resolved values.
   */
  @Nonnull
  public List<? extends SoyValue> asResolvedJavaList();

  /**
   * Gets a value of this SoyList.
   *
   * @param index The index to get.
   * @return The value for the given index, or null if no such index.
   */
  public SoyValue get(int index);

  /**
   * Gets a provider of a value of this SoyList.
   *
   * @param index The index to get.
   * @return A provider of the value for the given index, or null if no such index.
   */
  public SoyValueProvider getProvider(int index);
}
