/*
 * Copyright 2020 Google Inc.
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
import com.google.template.soy.data.ProtoFieldInterpreter;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import java.util.List;

/**
 * A SoyList interface to a native Java List (from a proto) that lazily converts values to {@link
 * SoyValue}s as they're accessed.
 */
public final class LazyProtoToSoyValueList<E> extends AbstractSoyList {

  private final ImmutableList<E> rawValues;
  private final ProtoFieldInterpreter valueInterpreter;
  /**
   * A cache of the values after they're converted to SoyValues. The values are in the same indices
   * as in rawValues. If a given value in this array is null that means it hasn't been converted to
   * a SoyValue yet.
   */
  private final SoyValue[] wrappedValues;

  /** Uses {@code valueInterpreter} to lazily convert the native values to {@link SoyValue}s. */
  public static <E> LazyProtoToSoyValueList<E> forList(
      List<E> list, ProtoFieldInterpreter valueInterpreter) {
    return new LazyProtoToSoyValueList<>(list, valueInterpreter);
  }

  private LazyProtoToSoyValueList(List<E> list, ProtoFieldInterpreter valueInterpreter) {
    rawValues = ImmutableList.copyOf(list);
    this.valueInterpreter = valueInterpreter;
    wrappedValues = new SoyValue[rawValues.size()];
  }

  @Override
  public int length() {
    return rawValues.size();
  }

  @Override
  public ImmutableList<SoyValue> asJavaList() {
    return asResolvedJavaList();
  }

  @Override
  public ImmutableList<SoyValue> asResolvedJavaList() {
    ImmutableList.Builder<SoyValue> list = ImmutableList.builder();
    for (int i = 0; i < length(); i++) {
      list.add(get(i));
    }
    return list.build();
  }

  @Override
  public SoyValue get(int index) {
    SoyValue wrapped = wrappedValues[index];
    if (wrapped != null) {
      return wrapped;
    }
    wrapped = valueInterpreter.soyFromProto(rawValues.get(index));
    wrappedValues[index] = wrapped;
    return wrapped;
  }

  @Override
  public SoyValueProvider getProvider(int index) {
    return get(index);
  }
}
