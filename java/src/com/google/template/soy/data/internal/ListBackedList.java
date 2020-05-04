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

package com.google.template.soy.data.internal;

import com.google.common.collect.Lists;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Internal implementation of SoyList in terms of a list. Do not use directly.
 *
 * <p>There are two implementations - SoyEasyList which creates its own list and supports
 * modification via the API, and ListImpl, which wraps existing lists.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@ParametersAreNonnullByDefault
abstract class ListBackedList extends AbstractSoyList {

  /** Backing list of providers. */
  protected final List<? extends SoyValueProvider> providerList;

  /** Constructs as a view of the given list. */
  protected ListBackedList(List<? extends SoyValueProvider> providerList) {
    this.providerList = providerList;
  }

  @Override
  public final int length() {
    return providerList.size();
  }

  @Override
  @Nonnull
  public final List<? extends SoyValueProvider> asJavaList() {
    return Collections.unmodifiableList(providerList);
  }

  @Override
  @Nonnull
  public final List<SoyValue> asResolvedJavaList() {
    return Lists.transform(asJavaList(), SoyValueProvider::resolve);
  }

  @Override
  public final SoyValue get(int index) {
    SoyValueProvider valueProvider = getProvider(index);
    return (valueProvider != null) ? valueProvider.resolve() : null;
  }

  @Override
  public final SoyValueProvider getProvider(int index) {
    try {
      return providerList.get(index);
    } catch (IndexOutOfBoundsException e) {
      return null;
    }
  }
}
