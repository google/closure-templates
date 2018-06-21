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

package com.google.template.soy.passes;

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;

/**
 * Visitor that ensures files and templates use strict autoescaping. Backends such as Miso (Python)
 * can choose to disallow all other types of autoescaping besides strict.
 *
 */
final class AssertStrictAutoescapingPass extends CompilerFilePass {

  private static final SoyErrorKind INVALID_AUTOESCAPING =
      SoyErrorKind.of("Invalid use of non-strict when strict autoescaping is required.");
  private final ErrorReporter errorReporter;

  AssertStrictAutoescapingPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode node : file.getChildren()) {
      if (node.getAutoescapeMode() != AutoescapeMode.STRICT) {
        errorReporter.report(node.getSourceLocation(), INVALID_AUTOESCAPING);
      }
    }

  }
}
