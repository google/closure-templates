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
package com.google.template.soy.passes;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.FooLogNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

final class FooLogValidationPass extends CompilerFilePass {
  private static final SoyErrorKind LOGGING_IS_EXPERIMENTAL =
      SoyErrorKind.of("The '{'foolog ...'}' command is disabled in this configuration.");

  private final ErrorReporter reporter;
  private final boolean enabled;

  FooLogValidationPass(ErrorReporter reporter, ImmutableSet<String> experimentalFeatures) {
    this.reporter = reporter;
    this.enabled = experimentalFeatures.contains("logging_support");
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    if (!enabled) {
      for (FooLogNode node : SoyTreeUtils.getAllNodesOfType(file, FooLogNode.class)) {
        reporter.report(node.getSourceLocation(), LOGGING_IS_EXPERIMENTAL);
      }
    }
  }
}
