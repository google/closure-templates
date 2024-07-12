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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.DoNotCall;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.UndefinedData;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;

/**
 * Internal-use param store for passing data in subtemplate calls.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class ParamStore extends IdentityHashMap<RecordProperty, SoyValueProvider>
    implements BiConsumer<RecordProperty, SoyValueProvider> {

  public static ParamStore merge(ParamStore store1, ParamStore store2) {
    // Merging with empty stores is common due to the way we bind template literals.
    var store1Size = store1.size();
    if (store1Size == 0) {
      return store2;
    }
    var store2Size = store2.size();
    if (store2Size == 0) {
      return store1;
    }
    var newStore = new ParamStore(store1Size + store2Size);
    store1.forEach(newStore);
    store2.forEach(newStore);
    return newStore.freeze();
  }

  public static ParamStore fromRecord(SoyValue value) {
    SoyRecord record = value.asSoyRecord();
    if (record instanceof SoyRecordImpl) {
      return ((SoyRecordImpl) record).getParamStore();
    }
    var newStore = new ParamStore(record.recordSize());
    record.forEach(newStore);
    return newStore.freeze();
  }

  private boolean frozen;

  public ParamStore(ParamStore backingStore, int size) {
    super(backingStore.size() + size);
    backingStore.forEach(this);
  }

  public ParamStore(int size) {
    super(size);
  }

  public ParamStore() {
    super();
  }

  @CanIgnoreReturnValue
  public ParamStore freeze() {
    frozen = true;
    return this;
  }

  public boolean isFrozen() {
    return frozen;
  }

  /**
   * Sets a field (i.e. param) in this ParamStore.
   *
   * @param name The field name to set.
   * @param valueProvider A provider of the field value.
   */
  @CanIgnoreReturnValue
  public ParamStore setField(RecordProperty name, @Nonnull SoyValueProvider valueProvider) {
    checkState(!frozen);
    Preconditions.checkNotNull(valueProvider);
    super.put(name, valueProvider);
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
    checkState(!frozen);
    Preconditions.checkNotNull(valueProvider);
    SoyValueProvider previous = super.put(name, valueProvider);
    checkState(previous == null, "value already set for param %s", name);
    return this;
  }

  @CanIgnoreReturnValue
  public ParamStore setAll(SoyValue record) {
    checkState(!frozen);
    record.asSoyRecord().forEach(this);
    return this;
  }

  public boolean hasField(RecordProperty name) {
    return super.containsKey(name);
  }

  public SoyValueProvider getFieldProvider(RecordProperty name) {
    return super.get(name);
  }

  public SoyValueProvider getParameter(RecordProperty name) {
    SoyValueProvider provider = super.get(name);
    return provider != null ? provider : UndefinedData.INSTANCE;
  }

  public SoyValueProvider getParameter(RecordProperty name, SoyValue defaultValue) {
    return SoyValueProvider.withDefault(super.get(name), defaultValue);
  }

  public ImmutableMap<String, SoyValueProvider> asStringMap() {
    ImmutableMap.Builder<String, SoyValueProvider> builder =
        ImmutableMap.builderWithExpectedSize(size());
    forEach((k, v) -> builder.put(k.getName(), v));
    return builder.buildOrThrow();
  }

  /**
   * Arbitrary method override to allow toString to be called without throwing
   * UnsupportedOperationException (since {@link Object#toString} uses {@link #hashCode}.
   */
  @Override
  public String toString() {
    return getClass().toString();
  }

  @Override
  public boolean equals(Object o) {
    checkState(frozen);
    if (!(o instanceof ParamStore)) {
      return false;
    }
    ParamStore otherStore = (ParamStore) o;
    if (size() != otherStore.size()) {
      return false;
    }
    for (var key : super.keySet()) {
      if (!getFieldProvider(key).equals(otherStore.getFieldProvider(key))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    checkState(frozen);
    int result = 0;
    for (var key : super.keySet()) {
      // We accumulate with + to ensure we are associative (insensitive to ordering)
      result += System.identityHashCode(key) ^ getFieldProvider(key).hashCode();
    }
    return result;
  }

  public Set<RecordProperty> properties() {
    return super.keySet();
  }

  // Override base methods methods to clarify our api... too bad java doesn't have private
  // inheritance

  @DoNotCall
  @Override
  public SoyValueProvider put(RecordProperty property, SoyValueProvider value) {
    throw new UnsupportedOperationException();
  }

  @DoNotCall
  @Override
  public void putAll(Map<? extends RecordProperty, ? extends SoyValueProvider> map) {
    throw new UnsupportedOperationException();
  }

  @DoNotCall
  @Override
  public SoyValueProvider remove(Object property) {
    throw new UnsupportedOperationException();
  }

  @DoNotCall
  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @DoNotCall
  @Override
  public Set<Map.Entry<RecordProperty, SoyValueProvider>> entrySet() {
    throw new UnsupportedOperationException();
  }

  @DoNotCall
  @Override
  public Set<RecordProperty> keySet() {
    throw new UnsupportedOperationException();
  }

  @DoNotCall
  @Override
  public Collection<SoyValueProvider> values() {
    throw new UnsupportedOperationException();
  }

  @DoNotCall
  @Override
  public SoyValueProvider get(Object key) {
    throw new UnsupportedOperationException();
  }

  // Implements BiConsumer.accept
  /**
   * @deprecated just so people don't call it directly or accidentally
   */
  @Override
  @DoNotCall
  @Deprecated
  @SuppressWarnings("Deprecated")
  public void accept(RecordProperty name, SoyValueProvider valueProvider) {
    super.put(name, valueProvider);
  }

  // -----------------------------------------------------------------------------------------------
  // Empty instance.

  public static final ParamStore EMPTY_INSTANCE = new ParamStore(0).freeze();
}
