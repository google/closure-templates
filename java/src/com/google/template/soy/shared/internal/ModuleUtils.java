/*
 * Copyright 2009 Google Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;

import java.util.Map;
import java.util.Set;


/**
 * Utilities for Guice modules (SharedModule, TofuModule, JsSrcModule, JavaSrcModule, etc).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class ModuleUtils {

  private ModuleUtils() {}


  /**
   * Given a specific Soy function type (subtype of SoyFunction) and the set of all Soy
   * function implementations, finds the Soy functions that implement the specific type and
   * returns them in the form of a map from function name to function.
   *
   * @param specificSoyFunctionType The specific Soy function type to filter for.
   * @param soyFunctionsSet The set of all Soy functions.
   * @return A map of the relevant specific Soy functions (name to function).
   */
  public static <T extends SoyFunction> Map<String, T> buildSpecificSoyFunctionsMap(
      Class<T> specificSoyFunctionType, Set<SoyFunction> soyFunctionsSet) {

    ImmutableMap.Builder<String, T> mapBuilder = ImmutableMap.builder();

    Set<String> seenFnNames = Sets.newHashSetWithExpectedSize(soyFunctionsSet.size());

    for (SoyFunction fn : soyFunctionsSet) {
      if (specificSoyFunctionType.isAssignableFrom(fn.getClass())) {
        String fnName = fn.getName();

        if (seenFnNames.contains(fnName) || NonpluginFunction.forFunctionName(fnName) != null) {
          throw new IllegalStateException(
              "Found two implementations of " + specificSoyFunctionType.getSimpleName() +
              " with the same function name '" + fnName + "'.");
        }
        seenFnNames.add(fnName);

        mapBuilder.put(fnName, specificSoyFunctionType.cast(fn));
      }
    }

    return mapBuilder.build();
  }


  /**
   * Given a specific Soy directive type (subtype of SoyPrintDirective) and the set of all Soy
   * directive implementations, finds the Soy directives that implement the specific type and
   * returns them in the form of a map from directive name to directive.
   *
   * @param specificSoyDirectiveType The specific Soy directive type to filter for.
   * @param soyDirectivesSet The set of all Soy directives.
   * @return A map of the relevant specific Soy directives (name to directive).
   */
  public static <T extends SoyPrintDirective> Map<String, T> buildSpecificSoyDirectivesMap(
      Class<T> specificSoyDirectiveType, Set<SoyPrintDirective> soyDirectivesSet) {

    ImmutableMap.Builder<String, T> mapBuilder = ImmutableMap.builder();

    Set<String> seenDirectiveNames = Sets.newHashSetWithExpectedSize(soyDirectivesSet.size());

    for (SoyPrintDirective directive : soyDirectivesSet) {
      if (specificSoyDirectiveType.isAssignableFrom(directive.getClass())) {
        String directiveName = directive.getName();

        if (seenDirectiveNames.contains(directiveName)) {
          throw new IllegalStateException(
              "Found two implementations of " + specificSoyDirectiveType.getSimpleName() +
              " with the same directive name '" + directiveName + "'.");
        }
        seenDirectiveNames.add(directiveName);

        mapBuilder.put(directiveName, specificSoyDirectiveType.cast(directive));
      }
    }

    return mapBuilder.build();
  }

}
