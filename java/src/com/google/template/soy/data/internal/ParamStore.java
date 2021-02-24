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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import java.io.IOException;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;

/**
 * Internal-use param store for passing data in subtemplate calls.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class ParamStore extends SoyAbstractValue implements SoyRecord {

  /** The internal map holding the fields (params). */
  private final Map<String, SoyValueProvider> localStore;

  public ParamStore(SoyRecord backingStore, int size) {
    this.localStore = Maps.newHashMapWithExpectedSize(backingStore.recordSize() + size);
    backingStore.forEach(localStore::put);
  }

  // private constructor for the empty instance
  private ParamStore(boolean unused) {
    this.localStore = ImmutableMap.of();
  }

  public ParamStore(int size) {
    this.localStore = Maps.newHashMapWithExpectedSize(size);
  }

  /**
   * Sets a field (i.e. param) in this ParamStore.
   *
   * @param name The field name to set.
   * @param valueProvider A provider of the field value.
   */
  public ParamStore setField(String name, @Nonnull SoyValueProvider valueProvider) {
    Preconditions.checkNotNull(valueProvider);
    localStore.put(name, valueProvider);
    return this;
  }

  /**
   * Sets a field (i.e. param) in this ParamStore. Failing if it is already present.
   *
   * <p>This is implemented for {@code bind()} calls
   *
   * @param name The field name to set.
   * @param valueProvider A provider of the field value.
   */
  public ParamStore setFieldCritical(String name, @Nonnull SoyValueProvider valueProvider) {
    Preconditions.checkNotNull(valueProvider);
    SoyValueProvider previous = localStore.put(name, valueProvider);
    checkState(previous == null, "value already set for param %s", name);
    return this;
  }

  @Override
  public boolean hasField(String name) {
    return localStore.containsKey(name);
  }

  @Override
  public SoyValueProvider getFieldProvider(String name) {
    return localStore.get(name);
  }

  @Override
  public ImmutableMap<String, SoyValueProvider> recordAsMap() {
    return ImmutableMap.copyOf(localStore);
  }

  @Override
  public void forEach(BiConsumer<String, ? super SoyValueProvider> action) {
    localStore.forEach(action);
  }

  @Override
  public int recordSize() {
    return localStore.size();
  }

  @Override
  public SoyValue getField(String name) {
    SoyValueProvider valueProvider = getFieldProvider(name);
    return (valueProvider != null) ? valueProvider.resolve() : null;
  }

  // -----------------------------------------------------------------------------------------------
  // SoyValue.

  @Override
  public boolean coerceToBoolean() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String coerceToString() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void render(LoggingAdvisingAppendable appendable) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public final boolean equals(Object other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public final int hashCode() {
    throw new UnsupportedOperationException();
  }

  /**
   * Arbitrary method override to allow toString to be called without throwing
   * UnsupportedOperationException (since {@link Object#toString} uses {@link #hashCode}.
   */
  @Override
  public String toString() {
    return getClass().toString();
  }

  // -----------------------------------------------------------------------------------------------
  // Empty instance.

  public static final ParamStore EMPTY_INSTANCE = new ParamStore(true);
}
