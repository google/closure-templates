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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A soy function that carries type information. */
public abstract class TypedSoyFunction implements SoyFunction {

  /**
   * Returns a list of {@link Signature}s, each {@link Signature} contains a list of parameter types
   * and a return type. For any given number of parameters, we only allow up to one signature. For
   * example, if a soy function says it can either take a IntType or take a StringType, a runtime
   * exception will be thrown.
   *
   * @return List of {@link Signature}s that is allowed for this function.
   */
  public abstract List<Signature> signatures();

  @Override
  public final Set<Integer> getValidArgsSizes() {
    Map<Integer, Signature> validArgs = new HashMap<>();
    for (Signature signature : signatures()) {
      int argSize = signature.parameterTypes().size();
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
    return validArgs.keySet();
  }
}
