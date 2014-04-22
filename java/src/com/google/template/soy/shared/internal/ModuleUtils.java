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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;

import java.util.Map;
import java.util.Set;


/**
 * Utilities for Guice modules (SharedModule, TofuModule, JsSrcModule, etc).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class ModuleUtils {


  private ModuleUtils() {}


  /**
   * Given the set of all Soy function implementations and a specific Soy function type (subtype
   * of SoyFunction) to look for, finds the Soy functions that implement the specific type
   * and returns them in the form of a map from function name to function.
   *
   * @param <T> The specific Soy function type to look for.
   * @param soyFunctionsSet The set of all Soy functions.
   * @param specificSoyFunctionType The class of the specific Soy function type to look for.
   * @return A map of the relevant specific Soy functions (name to function).
   */
  public static <T extends SoyFunction> Map<String, T> buildSpecificSoyFunctionsMap(
      Set<SoyFunction> soyFunctionsSet, Class<T> specificSoyFunctionType) {

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
   * Given the set of all Soy function implementations, a specific Soy function type (subtype of
   * SoyFunction) to look for, another Soy function type to look for that is an equivalent
   * deprecated version of the specific Soy function type, and an adapt function for adapting the
   * deprecated type to the specific type, finds the Soy functions that implement either type and
   * returns them in the form of a map from function name to function, where the functions with
   * the deprecated type have been adapted using the adapt function.
   *
   * @param <T> The specific Soy function type to look for.
   * @param <D> The equivalent deprecated Soy function type to also look for.
   * @param soyFunctionsSet The set of all Soy functions.
   * @param specificSoyFunctionType The class of the specific Soy function type to look for.
   * @param equivDeprecatedSoyFunctionType The class of the equivalent deprecated Soy function
   *     type to also look for.
   * @param adaptFn The adapt function that adapts the deprecated type to the specific type.
   * @return A map of the relevant specific Soy functions (name to function).
   */
  public static <T extends SoyFunction, D extends SoyFunction>
      Map<String, T> buildSpecificSoyFunctionsMapWithAdaptation(
      Set<SoyFunction> soyFunctionsSet, Class<T> specificSoyFunctionType,
      Class<D> equivDeprecatedSoyFunctionType, Function<D, T> adaptFn) {

    Map<String, T> tMap =
        buildSpecificSoyFunctionsMap(soyFunctionsSet, specificSoyFunctionType);
    Map<String, D> dMap =
        buildSpecificSoyFunctionsMap(soyFunctionsSet, equivDeprecatedSoyFunctionType);

    ImmutableMap.Builder<String, T> resultMapBuilder = ImmutableMap.builder();
    resultMapBuilder.putAll(tMap);
    for (String functionName : dMap.keySet()) {
      if (tMap.containsKey(functionName)) {
        if (tMap.get(functionName).equals(dMap.get(functionName))) {
          throw new IllegalStateException(String.format(
              "Found function named '%s' that implements both %s and" +
                  " %s -- please remove the latter deprecated interface.",
              functionName, specificSoyFunctionType.getSimpleName(),
              equivDeprecatedSoyFunctionType.getSimpleName()));
        } else {
          throw new IllegalStateException(String.format(
              "Found two functions with the same name '%s', one implementing %s and the" +
                  " other implementing %s",
              functionName, specificSoyFunctionType.getSimpleName(),
              equivDeprecatedSoyFunctionType.getSimpleName()));
        }
      }
      resultMapBuilder.put(functionName, adaptFn.apply(dMap.get(functionName)));
    }
    return resultMapBuilder.build();
  }


  /**
   * Given the set of all Soy directive implementations and a specific Soy directive type (subtype
   * of SoyPrintDirective) to look for, finds the Soy directives that implement the specific type
   * and returns them in the form of a map from directive name to directive.
   *
   * @param <T> The specific Soy directive type to look for.
   * @param soyDirectivesSet The set of all Soy directives.
   * @param specificSoyDirectiveType The class of the specific Soy directive type to look for.
   * @return A map of the relevant specific Soy directives (name to directive).
   */
  public static <T extends SoyPrintDirective> Map<String, T> buildSpecificSoyDirectivesMap(
      Set<SoyPrintDirective> soyDirectivesSet, Class<T> specificSoyDirectiveType) {

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


  /**
   * Given the set of all Soy directive implementations, a specific Soy directive type (subtype of
   * SoyPrintDirective) to look for, another Soy directive type to look for that is an equivalent
   * deprecated version of the specific Soy directive type, and an adapt function for adapting the
   * deprecated type to the specific type, finds the Soy directives that implement either type and
   * returns them in the form of a map from directive name to directive, where the directives with
   * the deprecated type have been adapted using the adapt function.
   *
   * @param <T> The specific Soy directive type to look for.
   * @param <D> The equivalent deprecated Soy directive type to also look for.
   * @param soyDirectivesSet The set of all Soy directives.
   * @param specificSoyDirectiveType The class of the specific Soy directive type to look for.
   * @param equivDeprecatedSoyDirectiveType The class of the equivalent deprecated Soy directive
   *     type to also look for.
   * @param adaptFn The adapt function that adapts the deprecated type to the specific type.
   * @return A map of the relevant specific Soy directives (name to directive).
   */
  public static <T extends SoyPrintDirective, D extends SoyPrintDirective>
      Map<String, T> buildSpecificSoyDirectivesMapWithAdaptation(
      Set<SoyPrintDirective> soyDirectivesSet, Class<T> specificSoyDirectiveType,
      Class<D> equivDeprecatedSoyDirectiveType, Function<D, T> adaptFn) {

    Map<String, T> tMap =
        buildSpecificSoyDirectivesMap(soyDirectivesSet, specificSoyDirectiveType);
    Map<String, D> dMap =
        buildSpecificSoyDirectivesMap(soyDirectivesSet, equivDeprecatedSoyDirectiveType);

    ImmutableMap.Builder<String, T> resultMapBuilder = ImmutableMap.builder();
    resultMapBuilder.putAll(tMap);
    for (String directiveName : dMap.keySet()) {
      if (tMap.containsKey(directiveName)) {
        if (tMap.get(directiveName).equals(dMap.get(directiveName))) {
          throw new IllegalStateException(String.format(
              "Found print directive named '%s' that implements both %s and" +
                  " %s -- please remove the latter deprecated interface.",
              directiveName, specificSoyDirectiveType.getSimpleName(),
              equivDeprecatedSoyDirectiveType.getSimpleName()));
        } else {
          throw new IllegalStateException(String.format(
              "Found two print directives with the same name '%s', one implementing %s and the" +
                  " other implementing %s",
              directiveName, specificSoyDirectiveType.getSimpleName(),
              equivDeprecatedSoyDirectiveType.getSimpleName()));
        }
      }
      resultMapBuilder.put(directiveName, adaptFn.apply(dMap.get(directiveName)));
    }
    return resultMapBuilder.build();
  }

}
