/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.passes;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/**
 * A pass that ensures that experimental features are only used when enabled.
 *
 * <p>In general access to experimental features should be controlled by the passes that implement
 * their behavior, but if the new behavior is a change to syntax that might not be feasible, or if
 * the new behavior is a change scattered across the compiler then it might be impractical. In those
 * cases this pass is a reasonable place to put the enforcement code.
 */
final class EnforceExperimentalFeaturesPass implements CompilerFilePass {

  private static final SoyErrorKind NON_NULL_ASSERTION_BANNED =
      SoyErrorKind.of(
          "Non-null assertion operator not supported, use the ''checkNotNull'' function instead.");

  private final ImmutableSet<String> features;
  private final ErrorReporter reporter;

  EnforceExperimentalFeaturesPass(ImmutableSet<String> features, ErrorReporter reporter) {
    this.features = checkNotNull(features);
    this.reporter = checkNotNull(reporter);
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    // TOOD(b/22389927): enable the non-null assertion operator once we're ready to use for
    // fixing proto nullability.
    if (!features.contains("enableNonNullAssertionOperator")) {
      for (AssertNonNullOpNode assertNonNullOpNode :
          SoyTreeUtils.getAllNodesOfType(file, AssertNonNullOpNode.class)) {
        reporter.report(assertNonNullOpNode.getSourceLocation(), NON_NULL_ASSERTION_BANNED);
      }
    }
  }
}
