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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Internal implementation of SoyList backed by a list of SoyValueProviders. Do not use directly;
 * instead, use {@link SoyValueConverter#convert}.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
@ParametersAreNonnullByDefault
public final class ListImpl extends ListBackedList {

  /** Creates a Soy list implementation backed by the given list. */
  @Nonnull
  public static ListImpl forProviderList(List<? extends SoyValueProvider> providerList) {
    return new ListImpl(ImmutableList.copyOf(providerList));
  }

  /** Creates a Soy list implementation backed by the given list. */
  @Nonnull
  public static ListImpl forProviderList(ImmutableList<? extends SoyValueProvider> providerList) {
    return new ListImpl(providerList);
  }

  private ListImpl(ImmutableList<? extends SoyValueProvider> providerList) {
    super(providerList);
  }

  // Override to avoid some indirection and unmodifiable list creation.
  @Override
  @Nonnull
  public List<? extends SoyValueProvider> asJavaList() {
    return providerList;
  }

  @Override
  public Iterator<? extends SoyValueProvider> javaIterator() {
    return providerList.iterator();
  }
}
