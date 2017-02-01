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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueProvider;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Implementation of ParamStore that represents a backing store augmented with additional fields
 * (params). The additional fields may hide fields from the backing store.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class AugmentedParamStore extends ParamStore {

  /** The backing store (can be any SoyRecord, not necessarily ParamStore) being augmented. */
  private final SoyRecord backingStore;

  /** The internal map holding the additional fields (params). */
  private final Map<String, SoyValueProvider> localStore;

  public AugmentedParamStore(@Nullable SoyRecord backingStore, int expectedKeys) {
    this.backingStore = backingStore;
    this.localStore = Maps.newHashMapWithExpectedSize(expectedKeys);
  }

  @Override
  public AugmentedParamStore setField(String name, @Nonnull SoyValueProvider valueProvider) {
    Preconditions.checkNotNull(valueProvider);
    localStore.put(name, valueProvider);
    return this;
  }

  @Override
  public boolean hasField(String name) {
    return localStore.containsKey(name) || backingStore.hasField(name);
  }

  @Override
  public SoyValueProvider getFieldProvider(String name) {
    SoyValueProvider val = localStore.get(name);
    return (val != null) ? val : backingStore.getFieldProvider(name);
  }
}
