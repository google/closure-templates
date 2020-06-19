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

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;

/**
 * Throws errors if we try to print certain expression types. Currently only template types are
 * banned.
 */
@RunAfter(ResolveExpressionTypesPass.class)
final class ValidatePrintExpressionTypes implements CompilerFilePass {
  private static final SoyErrorKind CANNOT_PRINT_TEMPLATES =
      SoyErrorKind.of(
          "Printing template-type expressions is not allowed (found expression of type: `{0}`)."
              + " Did you mean to '{'call'}' it instead?");

  private final ErrorReporter errorReporter;

  ValidatePrintExpressionTypes(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (PrintNode printNode : SoyTreeUtils.getAllNodesOfType(file, PrintNode.class)) {
      if (SoyTypes.isKindOrUnionOfKind(printNode.getExpr().getType(), SoyType.Kind.NAMED_TEMPLATE)
          || SoyTypes.isKindOrUnionOfKind(printNode.getExpr().getType(), SoyType.Kind.TEMPLATE)) {
        errorReporter.report(
            printNode.getExpr().getSourceLocation(),
            CANNOT_PRINT_TEMPLATES,
            printNode.getExpr().getType());
      }
    }
  }
}
