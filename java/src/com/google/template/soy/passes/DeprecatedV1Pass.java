/*
 * Copyright 2008 Google Inc.
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
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;

/**
 * Ensures that the {@code deprecatedV1} attribute is only set in compilations that set the compile
 * mode to 1.0. Also asserts that the {@code v1Expression} function is only used in {@code
 * deprecatedV1} templates.
 */
final class DeprecatedV1Pass extends CompilerFilePass {

  private static final SoyErrorKind INCORRECT_V1_EXPRESSION_USE =
      SoyErrorKind.of(
          "The ''v1Expression'' function can only be used in templates that set "
              + "''deprecatedV1=\"true\"''.");
  private static final SoyErrorKind INCORRECT_DEPRECATED_V1_SYNTAX =
      SoyErrorKind.of(
          "''deprecatedV1=\"true\"'' can only be set if you enable the "
              + "--shouldAllowDeprecatedSyntax flag or set "
              + "SoyJsSrcOptions.shouldAllowDeprecatedSyntax to true. This is only supported in "
              + "the JavaScript backend.",
          StyleAllowance.NO_CAPS);

  private final SyntaxVersion syntaxVersion;
  private final ErrorReporter errorReporter;

  /**
   * @param syntaxVersion The configured syntax version.
   * @param errorReporter For reporting errors.
   */
  DeprecatedV1Pass(SyntaxVersion syntaxVersion, ErrorReporter errorReporter) {
    this.syntaxVersion = syntaxVersion;
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode template : file.getChildren()) {
      if (template.isDeprecatedV1()) {
        if (syntaxVersion != SyntaxVersion.V1_0) {
          // ideally this would point at the deprecatedV1 attribute, but we don't currently record
          // that information.
          errorReporter.report(template.getSourceLocation(), INCORRECT_DEPRECATED_V1_SYNTAX);
        }
      } else {
        // ban uses of the v1Expression function
        for (FunctionNode function : SoyTreeUtils.getAllNodesOfType(template, FunctionNode.class)) {
          if (function.getSoyFunction() == BuiltinFunction.V1_EXPRESSION) {
            errorReporter.report(function.getSourceLocation(), INCORRECT_V1_EXPRESSION_USE);
          }
        }
      }
    }
  }
}
