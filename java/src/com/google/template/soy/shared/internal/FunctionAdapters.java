/*
 * Copyright 2015 Google Inc.
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
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.util.Set;

/**
 * Functions for adapting the legacy Java {@link SoyFunction} implementations ({@link
 * SoyJavaRuntimeFunction} and {@link com.google.template.soy.tofu.restricted.SoyTofuFunction}) to
 * the canonical implementation ({@link SoyJavaFunction}).
 *
 * <p>TODO(user): migrate the legacy impls and remove this class.
 */
public final class FunctionAdapters {
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
  public static <T extends SoyPrintDirective> ImmutableMap<String, T> buildSpecificSoyDirectivesMap(
      Set<SoyPrintDirective> soyDirectivesSet, Class<T> specificSoyDirectiveType) {

    ImmutableMap.Builder<String, T> mapBuilder = ImmutableMap.builder();

    Set<String> seenDirectiveNames = Sets.newHashSetWithExpectedSize(soyDirectivesSet.size());

    for (SoyPrintDirective directive : soyDirectivesSet) {
      if (specificSoyDirectiveType.isAssignableFrom(directive.getClass())) {
        String directiveName = directive.getName();

        if (seenDirectiveNames.contains(directiveName)) {
          throw new IllegalStateException(
              "Found two implementations of "
                  + specificSoyDirectiveType.getSimpleName()
                  + " with the same directive name '"
                  + directiveName
                  + "'.");
        }
        seenDirectiveNames.add(directiveName);

        mapBuilder.put(directiveName, specificSoyDirectiveType.cast(directive));
      }
    }

    return mapBuilder.build();
  }
}
