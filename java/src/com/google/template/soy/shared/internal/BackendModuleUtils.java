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
 * Utilities for backend modules (TofuModule, JsSrcModule, JavaSrcModule).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class BackendModuleUtils {

  private BackendModuleUtils() {}


  /**
   * Given a backend-specific Soy function type and the set of all Soy function implementations,
   * finds the Soy functions that are implemented for the specific backend and returns them in the
   * form of a map from function name to function.
   *
   * @param backendSpecificSoyFunctionType The backend-specific Soy function type to filter for.
   * @param soyFunctionsSet The set of all Soy functions.
   * @return A map of the relevant backend-specific Soy functions (name to function).
   */
  public static <T extends SoyFunction> Map<String, T> buildBackendSpecificSoyFunctionsMap(
      Class<T> backendSpecificSoyFunctionType, Set<SoyFunction> soyFunctionsSet) {

    ImmutableMap.Builder<String, T> mapBuilder = ImmutableMap.builder();

    Set<String> seenFnNames = Sets.newHashSetWithExpectedSize(soyFunctionsSet.size());

    for (SoyFunction fn : soyFunctionsSet) {
      if (backendSpecificSoyFunctionType.isAssignableFrom(fn.getClass())) {
        String fnName = fn.getName();

        if (seenFnNames.contains(fnName) || ImpureFunction.forFunctionName(fnName) != null) {
          throw new IllegalStateException(
              "Found two implementations of " + backendSpecificSoyFunctionType.getSimpleName() +
              " with the same function name '" + fnName + "'.");
        }
        seenFnNames.add(fnName);

        mapBuilder.put(fnName, backendSpecificSoyFunctionType.cast(fn));
      }
    }

    return mapBuilder.build();
  }


  /**
   * Given a backend-specific Soy directive type and the set of all Soy directive implementations,
   * finds the Soy directives that are implemented for the specific backend and returns them in the
   * form of a map from directive name to directive.
   *
   * @param backendSpecificSoyDirectiveType The backend-specific Soy directive type to filter for.
   * @param soyDirectivesSet The set of all Soy directives.
   * @return A map of the relevant backend-specific Soy directives (name to directive).
   */
  public static <T extends SoyPrintDirective> Map<String, T> buildBackendSpecificSoyDirectivesMap(
      Class<T> backendSpecificSoyDirectiveType, Set<SoyPrintDirective> soyDirectivesSet) {

    ImmutableMap.Builder<String, T> mapBuilder = ImmutableMap.builder();

    Set<String> seenDirectiveNames = Sets.newHashSetWithExpectedSize(soyDirectivesSet.size());

    for (SoyPrintDirective directive : soyDirectivesSet) {
      if (backendSpecificSoyDirectiveType.isAssignableFrom(directive.getClass())) {
        String directiveName = directive.getName();

        if (seenDirectiveNames.contains(directiveName)) {
          throw new IllegalStateException(
              "Found two implementations of " + backendSpecificSoyDirectiveType.getSimpleName() +
              " with the same directive name '" + directiveName + "'.");
        }
        seenDirectiveNames.add(directiveName);

        mapBuilder.put(directiveName, backendSpecificSoyDirectiveType.cast(directive));
      }
    }

    return mapBuilder.build();
  }

}
