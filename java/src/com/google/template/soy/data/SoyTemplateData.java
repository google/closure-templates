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

import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
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

    private final Map<String, SoyValueProvider> data;
    private final SoyValueConverter soyValueConverter;

    private Builder() {
      data = new HashMap<>();
      soyValueConverter = SoyValueConverter.INSTANCE;
    }

    public <T> Builder setParam(SoyTemplateParam<? super T> param, T value) {
      data.put(param.getName(), soyValueConverter.convert(value));
      return this;
    }

    public <T> Builder setParamFuture(
        SoyTemplateParam<? super T> param, ListenableFuture<T> value) {
      data.put(param.getName(), soyValueConverter.convert(value));
      return this;
    }

    public SoyTemplateData build() {
      return new SoyTemplateData(this);
    }
  }

  private final ImmutableMap<String, SoyValueProvider> data;

  private SoyTemplateData(Builder builder) {
    this.data = ImmutableMap.copyOf(builder.data);
  }

  /**
   * Returns the parameters as a map. This method is only intended to be called by the Soy
   * framework.
   */
  public Map<String, ?> getParamsAsMap() {
    return data;
  }

  /**
   * Returns the parameters as a map. Values are not wrapped with SoyValueProvider. This method is
   * intended to be called only by test code.
   */
  public Map<String, Object> getRawParamsAsMap() {
    return data.entrySet().stream()
        .collect(toMap(Map.Entry::getKey, e -> SoyValueUnconverter.unconvert(e.getValue())));
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof SoyTemplateData && data.equals(((SoyTemplateData) o).data);
  }

  @Override
  public int hashCode() {
    return data.hashCode();
  }
}
