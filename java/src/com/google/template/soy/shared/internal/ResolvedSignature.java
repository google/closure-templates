/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.shared.internal;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.types.SoyType;

/**
 * A Soy function signature. This contains type information and therefore allows us to perform
 * strict type checking for Soy functions.
 *
 * <p>Generic types are not supported in general. Some built-in functions have special logic in
 * {@link com.google.template.soy.passes.ResolveExpressionTypesPass} for additional checks.
 */
@AutoValue
public abstract class ResolvedSignature {

  /** A list of parameter types. */
  public abstract ImmutableList<SoyType> parameterTypes();

  /** Return type. */
  public abstract SoyType returnType();

  public static ResolvedSignature create(
      ImmutableList<SoyType> parameterTypes, SoyType returnType) {
    return new AutoValue_ResolvedSignature(parameterTypes, returnType);
  }
}
