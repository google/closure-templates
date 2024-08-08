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
import com.google.template.soy.data.internal.ParamStore;
import java.util.HashMap;
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

    private ParamStore data;

    private Builder() {
      data = new ParamStore();
    }

    @CanIgnoreReturnValue
    private Builder setParamInternal(SoyTemplateParam<?> param, Object value) {
      if (data.isFrozen()) {
        this.data = new ParamStore(data, 1);
      }
      data.setField(param.getSymbol(), SoyValueConverter.INSTANCE.convert(value));
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
      return new SoyTemplateData(data.freeze());
    }
  }

  private final ParamStore data;

  private SoyTemplateData(ParamStore data) {
    this.data = data;
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
    Map<String, Object> rawValues = new HashMap<>();
    data.forEach((k, v) -> rawValues.put(k.getName(), SoyValueUnconverter.unconvert(v)));
    return rawValues;
  }

  /** Returns the parameters as a record. Intended only for soy internal usecases. */
  public Object getParamsAsRecord() {
    return data;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof SoyTemplateData && data.equals(((SoyTemplateData) o).data);
  }

  @Override
  public int hashCode() {
    return data.hashCode();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass()).add("data", getParamsAsMap()).toString();
  }
}
