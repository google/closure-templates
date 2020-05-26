/*
 * Copyright 2018 Google Inc.
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
import com.google.template.soy.css.CssRegistry;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;

/** Checks for required css names being valid. */
final class ValidateRequiredCssPass implements CompilerFilePass {

  private static final SoyErrorKind CSS_REQUIRED_NOT_PROVIDED =
      SoyErrorKind.of("CSS namespace ''{0}'' not provided in css deps. ''{1}''");

  private final ErrorReporter errorReporter;
  private final CssRegistry cssRegistry;

  public ValidateRequiredCssPass(ErrorReporter errorReporter, CssRegistry cssRegistry) {
    this.errorReporter = errorReporter;
    this.cssRegistry = cssRegistry;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (String cssNamespace : file.getRequiredCssNamespaces()) {
      // TODO(tomnguyen) Use ImportDeclaration location when appropriate
      if (!cssRegistry.isInRegistry(cssNamespace)) {
        errorReporter.report(
            file.getNamespaceDeclaration().getSourceLocation(),
            CSS_REQUIRED_NOT_PROVIDED,
            cssNamespace,
            SoyErrors.getDidYouMeanMessage(cssRegistry.providedSymbols(), cssNamespace));
      }
    }
    for (TemplateNode node : file.getTemplates()) {
      for (String cssNamespace : node.getRequiredCssNamespaces()) {
        if (!cssRegistry.isInRegistry(cssNamespace)) {
          errorReporter.report(
              node.getOpenTagLocation(),
              CSS_REQUIRED_NOT_PROVIDED,
              cssNamespace,
              SoyErrors.getDidYouMeanMessage(cssRegistry.providedSymbols(), cssNamespace));
        }
      }
    }
  }
}
