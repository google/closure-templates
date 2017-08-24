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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.util.Set;

final class ValidatePrintDirectivesPass extends CompilerFilePass {
  private static final SoyErrorKind UNKNOWN_PRINT_DIRECTIVE =
      SoyErrorKind.of("Unknown print directive.{0}", StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind INCORRECT_NUM_ARGS =
      SoyErrorKind.of("Print directive ''{0}'' called with {1} arguments (expected {2}).");
  private final ImmutableMap<String, ? extends SoyPrintDirective> directives;
  private final ErrorReporter reporter;
  private final boolean allowUnknownFunctionsAndPrintDirectives;

  public ValidatePrintDirectivesPass(
      ErrorReporter reporter,
      ImmutableMap<String, ? extends SoyPrintDirective> soyPrintDirectives,
      boolean allowUnknownFunctionsAndPrintDirectives) {
    this.reporter = reporter;
    this.directives = soyPrintDirectives;
    this.allowUnknownFunctionsAndPrintDirectives = allowUnknownFunctionsAndPrintDirectives;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (PrintDirectiveNode directiveNode :
        SoyTreeUtils.getAllNodesOfType(file, PrintDirectiveNode.class)) {
      String name = directiveNode.getName();
      SoyPrintDirective soyPrintDirective = directives.get(name);
      if (soyPrintDirective != null) {
        directiveNode.setPrintDirective(soyPrintDirective);
        checkNumArgs(soyPrintDirective, directiveNode);
      } else if (!allowUnknownFunctionsAndPrintDirectives) {
        reporter.report(
            directiveNode.getSourceLocation(),
            UNKNOWN_PRINT_DIRECTIVE,
            SoyErrors.getDidYouMeanMessage(directives.keySet(), name));
      }
    }
  }

  private void checkNumArgs(SoyPrintDirective printDirective, PrintDirectiveNode node) {
    int numArgs = node.getArgs().size();
    Set<Integer> arities = printDirective.getValidArgsSizes();
    if (!arities.contains(numArgs)) {
      reporter.report(
          node.getSourceLocation(),
          INCORRECT_NUM_ARGS,
          printDirective.getName(),
          numArgs,
          Joiner.on(" or ").join(arities));
    }
  }
}
