/*
 * Copyright 2025 Google Inc.
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.soytree.FileMetadata.Extern;
import com.google.template.soy.types.SoyType;
import java.util.List;

/** Runtime type for function pointers. */
@AutoValue
public abstract class FunctionValue<T> extends SoyValue {

  public static FunctionValue<?> create(Extern impl) {
    return new AutoValue_FunctionValue<>(impl, ImmutableList.of());
  }

  public abstract Extern getImpl();

  /**
   * Typed broadly as Object until this is working in JBCSRC. TOFU uses TofuJavaValue. Each backend
   * just needs to agree between binding and calling.
   */
  public abstract ImmutableList<T> getBoundArgs();

  public FunctionValue<T> bind(List<? extends T> params) {
    if (params.isEmpty()) {
      return this;
    }
    ImmutableList<T> existing = getBoundArgs();
    ImmutableList<T> newParams =
        existing.isEmpty()
            ? ImmutableList.copyOf(params)
            : ImmutableList.<T>builder().addAll(existing).addAll(params).build();
    return new AutoValue_FunctionValue<T>(getImpl(), newParams);
  }

  public int getBoundArgsCount() {
    return getBoundArgs().size();
  }

  public int getParamCount() {
    return getImpl().getSignature().getArity();
  }

  public SoyType getReturnType() {
    return getImpl().getSignature().getReturnType();
  }

  @Override
  public SoyValue checkNullishFunction() {
    return this;
  }

  @Override
  public final boolean coerceToBoolean() {
    return true;
  }

  @Override
  public final String coerceToString() {
    return String.format("** FOR DEBUGGING ONLY: %s **", getImpl());
  }

  @Override
  public final void render(LoggingAdvisingAppendable appendable) {
    throw new IllegalStateException(
        "Cannot print function types; this should have been caught during parsing.");
  }

  @Override
  public String getSoyTypeName() {
    return "function";
  }
}
