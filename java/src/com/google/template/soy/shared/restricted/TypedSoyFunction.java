/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.shared.restricted;

import com.google.common.collect.ImmutableSortedSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** A soy function that carries type information. */
public abstract class TypedSoyFunction implements SoyFunction {

  @Override
  public final Set<Integer> getValidArgsSizes() {
    if (!this.getClass().isAnnotationPresent(SoyFunctionSignature.class)) {
      throw new IllegalStateException(
          "TypedSoyFunction must set @SoyFunctionSignature annotation.");
    }
    Map<Integer, Signature> validArgs = new HashMap<>();
    for (Signature signature : this.getClass().getAnnotation(SoyFunctionSignature.class).value()) {
      int argSize = signature.parameterTypes().length;
      if (validArgs.containsKey(argSize)) {
        throw new IllegalArgumentException(
            String.format(
                "TypedSoyFunction can only have exactly one signature for a given"
                    + " number of parameters. Found more than one signatures that specify "
                    + "%s parameters:\n  %s\n  %s",
                argSize, validArgs.get(argSize), signature));
      }
      validArgs.put(argSize, signature);
    }
    return ImmutableSortedSet.copyOf(validArgs.keySet());
  }
}
