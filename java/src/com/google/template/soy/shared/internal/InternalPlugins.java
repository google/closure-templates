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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;

/** Lists all functions & directives shipped with Soy. */
public final class InternalPlugins {
  private InternalPlugins() {}

  /** Returns a map (whose key is the name of the function) of the functions shipped with Soy. */
  public static ImmutableMap<String, SoySourceFunction> internalFunctionMap() {
    // TODO(sameb): Include the actual functions when they're converted.
    // (Something like Iterables.concat(BasicFunctions.functions(), BidiFunctions.functions()))
    return fromFunctions(ImmutableList.of());
  }

  public static ImmutableMap<String, SoySourceFunction> fromFunctions(
      Iterable<? extends SoySourceFunction> functions) {
    ImmutableMap.Builder<String, SoySourceFunction> builder = ImmutableMap.builder();
    for (SoySourceFunction fn : functions) {
      SoyFunctionSignature sig = fn.getClass().getAnnotation(SoyFunctionSignature.class);
      checkState(sig != null, "Missing @SoyFunctionSignature on %s", fn.getClass());
      builder.put(sig.name(), fn);
    }
    return builder.build();
  }
}
