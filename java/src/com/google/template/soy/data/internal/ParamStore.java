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
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;

/**
 * Internal-use param store for passing data in subtemplate calls.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public class ParamStore extends SoyAbstractValue implements SoyRecord {

  /** The internal map holding the fields (params). */
  private final IdentityHashMap<RecordProperty, SoyValueProvider> localStore;

  public ParamStore(SoyRecord backingStore, int size) {
    this.localStore = new IdentityHashMap<>(backingStore.recordSize() + size);
    backingStore.forEach(localStore::put);
  }

  public ParamStore(int size) {
    this.localStore = new IdentityHashMap<>(size);
  }

  public ParamStore(IdentityHashMap<RecordProperty, SoyValueProvider> localStore) {
    this.localStore = localStore;
  }

  /**
   * Sets a field (i.e. param) in this ParamStore.
   *
   * @param name The field name to set.
   * @param valueProvider A provider of the field value.
   */
  @CanIgnoreReturnValue
  public ParamStore setField(RecordProperty name, @Nonnull SoyValueProvider valueProvider) {
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
  @CanIgnoreReturnValue
  public ParamStore setFieldCritical(RecordProperty name, @Nonnull SoyValueProvider valueProvider) {
    Preconditions.checkNotNull(valueProvider);
    SoyValueProvider previous = localStore.put(name, valueProvider);
    checkState(previous == null, "value already set for param %s", name);
    return this;
  }

  @Override
  public boolean hasField(RecordProperty name) {
    return localStore.containsKey(name);
  }

  @Override
  public SoyValueProvider getFieldProvider(RecordProperty name) {
    return localStore.get(name);
  }

  @Override
  public ImmutableMap<String, SoyValueProvider> recordAsMap() {
    return localStore.entrySet().stream()
        .collect(toImmutableMap(e -> e.getKey().getName(), Map.Entry::getValue));
  }

  @Override
  public void forEach(BiConsumer<RecordProperty, ? super SoyValueProvider> action) {
    localStore.forEach(action);
  }

  @Override
  public int recordSize() {
    return localStore.size();
  }

  @Override
  public SoyValue getField(RecordProperty name) {
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
  public void render(LoggingAdvisingAppendable appendable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int hashCode() {
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

  public static final ParamStore EMPTY_INSTANCE =
      new ParamStore(0) {
        @Override
        public ParamStore setField(RecordProperty name, SoyValueProvider valueProvider) {
          throw new UnsupportedOperationException();
        }

        @Override
        public ParamStore setFieldCritical(RecordProperty name, SoyValueProvider valueProvider) {
          throw new UnsupportedOperationException();
        }
      };
}
