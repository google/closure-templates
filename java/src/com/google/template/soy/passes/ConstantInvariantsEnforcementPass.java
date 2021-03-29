/*
 * Copyright 2021 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.internal.exemptions.NamespaceExemptions;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.SoyFileNode;
import java.util.List;
import java.util.function.Supplier;

/**
 * Enforces certain conditions on Soy files that contain constants.
 *
 * <ol>
 *   <li>They must be topologically sortable (no cycles) because of how constant types are resolved.
 *   <li>They must have a unique namespace because of how the jbcsrc code is generated.
 * </ol>
 */
@RunAfter(FileDependencyOrderPass.class)
class ConstantInvariantsEnforcementPass implements CompilerFileSetPass {

  private static final SoyErrorKind TOPO_SORT_REQUIRED =
      SoyErrorKind.of("Constants are only allowed in non-cyclical file sets.");

  private static final SoyErrorKind UNIQUE_NS_REQUIRED =
      SoyErrorKind.of("Constants are only allowed in files with unique namespaces.");

  private final ErrorReporter errorReporter;
  private final Supplier<Boolean> topologicalSortSucceeded;

  public ConstantInvariantsEnforcementPass(
      ErrorReporter errorReporter, Supplier<Boolean> topologicalSortSucceeded) {
    this.errorReporter = errorReporter;
    this.topologicalSortSucceeded = topologicalSortSucceeded;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    boolean error = false;
    boolean cycleError = !topologicalSortSucceeded.get();
    for (SoyFileNode file : sourceFiles) {
      boolean duplicateNs = NamespaceExemptions.isKnownDuplicateNamespace(file.getNamespace());
      if (!cycleError && !duplicateNs) {
        continue;
      }

      List<ConstNode> constants = file.getConstants();
      if (constants.isEmpty()) {
        continue;
      }

      error = true;
      SoyErrorKind errorKind = cycleError ? TOPO_SORT_REQUIRED : UNIQUE_NS_REQUIRED;
      constants.forEach(c -> errorReporter.report(c.getSourceLocation(), errorKind));
    }

    return error ? Result.STOP : Result.CONTINUE;
  }
}
