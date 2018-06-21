/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.conformance;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.basetree.Node;

/**
 * A tuple of a {@link Rule rule} and a list of whitelisted paths that are exempt from the rule.
 *
 * @author brndn@google.com (Brendan Linn)
 */
@AutoValue
abstract class RuleWithWhitelists {
  static RuleWithWhitelists create(
      Rule<? extends Node> rule,
      ImmutableList<String> whitelistedPaths,
      ImmutableList<String> onlyApplyToPaths) {
    return new AutoValue_RuleWithWhitelists(rule, whitelistedPaths, onlyApplyToPaths);
  }

  abstract Rule<? extends Node> getRule();

  abstract ImmutableList<String> getWhitelistedPaths();

  abstract ImmutableList<String> getOnlyApplyToPaths();

  /** A file should be checked against a rule unless it contains one of the whitelisted paths. */
  boolean shouldCheckConformanceFor(String filePath) {
    for (String whitelistedPath : getWhitelistedPaths()) {
      if (filePath.contains(whitelistedPath)) {
        return false;
      }
    }
    ImmutableList<String> onlyApplyToPaths = getOnlyApplyToPaths();
    if (onlyApplyToPaths.isEmpty()) {
      return true;
    }
    // If only_apply_to field is presented in the configuration, check it.
    for (String onlyApplyToPath : onlyApplyToPaths) {
      if (filePath.contains(onlyApplyToPath)) {
        return true;
      }
    }
    return false;
  }
}
