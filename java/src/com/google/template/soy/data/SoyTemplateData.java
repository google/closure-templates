/*
 * Copyright 2019 Google Inc.
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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.data.internal.SoyRecordImpl;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A collection of Soy template parameters and their values. Used in APIs that accept "injected"
 * parameters.
 */
public final class SoyTemplateData {

  public static final SoyTemplateData EMPTY = SoyTemplateData.builder().build();

  /** Returns a new builder. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link SoyTemplateData}. */
  public static final class Builder {

    private IdentityHashMap<RecordProperty, SoyValueProvider> data;
    private boolean copyOnWrite;

    private Builder() {
      data = new IdentityHashMap<>();
    }

    private Builder setParamInternal(SoyTemplateParam<?> param, Object value) {
      if (copyOnWrite) {
        this.data = new IdentityHashMap<>(data);
        copyOnWrite = false;
      }
      data.put(param.getSymbol(), SoyValueConverter.INSTANCE.convert(value));
      return this;
    }

    @CanIgnoreReturnValue
    public <T> Builder setParam(SoyTemplateParam<? super T> param, T value) {
      return setParamInternal(param, value);
    }

    @CanIgnoreReturnValue
    public <T> Builder setParamFuture(
        SoyTemplateParam<? super T> param, ListenableFuture<T> value) {
      return setParamInternal(param, value);
    }

    public SoyTemplateData build() {
      this.copyOnWrite = true;
      return new SoyTemplateData(this);
    }
  }

  private final SoyRecordImpl data;

  private SoyTemplateData(Builder builder) {
    this.data = new SoyRecordImpl(builder.data);
  }

  /**
   * Returns the parameters as a map. This method is only intended to be called by the Soy
   * framework.
   */
  public Map<String, ?> getParamsAsMap() {
    ImmutableMap.Builder<String, SoyValueProvider> params = ImmutableMap.builder();
    data.forEach((k, v) -> params.put(k.getName(), v));
    return params.buildOrThrow();
  }

  /**
   * Returns the parameters as a map. Values are not wrapped with SoyValueProvider. This method is
   * intended to be called only by test code.
   */
  public Map<String, Object> getRawParamsAsMap() {
    ImmutableMap.Builder<String, Object> params = ImmutableMap.builder();
    data.forEach((k, v) -> params.put(k.getName(), SoyValueUnconverter.unconvert(v)));
    return params.buildOrThrow();
  }

  /** Returns the parameters as a record. Intended only for soy internal usecases. */
  public Object getParamsAsRecord() {
    return data;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof SoyTemplateData && soyRecordEquals(data, ((SoyTemplateData) o).data);
  }

  @Override
  public int hashCode() {
    return soyRecordHashCode(data);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass()).add("data", getParamsAsMap()).toString();
  }

  /** An equals operator for SoyRecord */
  static boolean soyRecordEquals(SoyRecordImpl o1, SoyRecordImpl o2) {
    if (o1.recordSize() != o2.recordSize()) {
      return false;
    }
    for (var key : o1.keys()) {
      if (!o1.getFieldProvider(key).equals(o2.getFieldProvider(key))) {
        return false;
      }
    }
    return true;
  }

  /** A hash function for SoyRecord */
  static int soyRecordHashCode(SoyRecordImpl record) {
    int result = 0;
    for (var key : record.keys()) {
      // We accumulate with + to ensure we are associative (insensitive to ordering)
      result += System.identityHashCode(key) ^ record.getFieldProvider(key).hashCode();
    }
    return result;
  }
}
