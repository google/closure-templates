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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A mutable list with additional methods for ease-of-use.
 *
 * @deprecated Do not use. Use java.util.List instead for mutability. SoyList for everything else.
 */
@Deprecated
@ParametersAreNonnullByDefault
public final class SoyEasyList extends SoyList {

  private final List<SoyValueProvider> providerList;

  public SoyEasyList() {
    this.providerList = new ArrayList<>();
  }

  /**
   * Adds a value to the end of this SoyList.
   *
   * @param valueProvider A provider of the value to add. Note that this is often just the value
   *     itself, since all values are also providers.
   * @deprecated SoyValues should never be mutated, it invalidates assumptions made by the runtime.
   */
  @Deprecated
  public void add(SoyValueProvider valueProvider) {
    providerList.add(checkNotNull(valueProvider));
  }

  @Override
  public int length() {
    return providerList.size();
  }

  @Nullable
  @Override
  public SoyValueProvider getProvider(int index) {
    return index < 0 || index >= providerList.size() ? null : providerList.get(index);
  }

  @Override
  public List<SoyValueProvider> asJavaList() {
    return Collections.unmodifiableList(providerList);
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public boolean equals(Object other) {
    return other == this;
  }
}
