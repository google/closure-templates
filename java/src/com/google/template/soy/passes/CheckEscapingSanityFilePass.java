/*
 * Copyright 2012 Google Inc.
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
import com.google.template.soy.soytree.EscapingMode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/**
 * Visitor performing escaping sanity checks over all input -- not just input affected by the
 * contextual autoescaping inference engine.
 *
 * <p>Checks that internal-only directives such as {@code |text} are not used.
 *
 * <p>{@link #exec} should be called on a full parse tree.
 *
 */
final class CheckEscapingSanityFilePass extends CompilerFilePass {

  private static final SoyErrorKind ILLEGAL_PRINT_DIRECTIVE =
      SoyErrorKind.of("{0} can only be used internally by the Soy compiler.");

  private final ErrorReporter errorReporter;

  CheckEscapingSanityFilePass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (PrintDirectiveNode node : SoyTreeUtils.getAllNodesOfType(file, PrintDirectiveNode.class)) {
      EscapingMode escapingMode = EscapingMode.fromDirective(node.getName());
      if (escapingMode != null && escapingMode.isInternalOnly) {
        errorReporter.report(node.getSourceLocation(), ILLEGAL_PRINT_DIRECTIVE, node.getName());
      }
    }
  }
}
