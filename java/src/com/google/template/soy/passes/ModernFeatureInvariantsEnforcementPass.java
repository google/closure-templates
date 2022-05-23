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
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.SoyFileNode;
import java.util.List;

/**
 * Enforces certain conditions on Soy files that contain constants.
 *
 * <ol>
 *   <li>They must be topologically sortable (no cycles) because of how constant types are resolved.
 *   <li>They must have a unique namespace because of how the jbcsrc code is generated.
 * </ol>
 */
@RunAfter(FileDependencyOrderPass.class)
class ModernFeatureInvariantsEnforcementPass implements CompilerFileSetPass {

  private static final SoyErrorKind UNIQUE_NS_REQUIRED =
      SoyErrorKind.of("Feature {0} is only allowed in files with unique namespaces.");

  private final ErrorReporter errorReporter;

  public ModernFeatureInvariantsEnforcementPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    boolean error = false;
    for (SoyFileNode file : sourceFiles) {
      if (!NamespaceExemptions.isKnownDuplicateNamespace(file.getNamespace())) {
        continue;
      }

      List<ConstNode> constants = file.getConstants();
      if (!constants.isEmpty()) {
        error = true;
        constants.forEach(
            c -> errorReporter.report(c.getSourceLocation(), UNIQUE_NS_REQUIRED, "{const}"));
      }

      List<ExternNode> externs = file.getExterns();
      if (!externs.isEmpty()) {
        error = true;
        externs.forEach(
            c -> errorReporter.report(c.getSourceLocation(), UNIQUE_NS_REQUIRED, "{extern}"));
      }
    }

    return error ? Result.STOP : Result.CONTINUE;
  }
}
