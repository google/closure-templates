/*
 * Copyright 2022 Google Inc.
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
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateNode;

/** Checks modifiable templates. */
@RunAfter(ResolveExpressionTypesPass.class)
final class CheckModifiableTemplatesPass implements CompilerFilePass {

  private static final SoyErrorKind MODIFIES_WITHOUT_MODNAME =
      SoyErrorKind.of(
          "\"modifies\" can only be used in a file with a '{'modName'}' command, unless it is used "
              + "on a variant template. If this is a non-variant template, did you forget to add a "
              + "'{'modName'}'? Or did you forget to mark this template as a variant?");

  private final ErrorReporter errorReporter;

  CheckModifiableTemplatesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode templateNode : file.getTemplates()) {
      if (templateNode instanceof TemplateBasicNode) {
        TemplateBasicNode templateBasicNode = (TemplateBasicNode) templateNode;
        if (templateBasicNode.getModifiesExpr() != null) {
          if (templateBasicNode.getVariantExpr() == null && file.getDelPackageName() == null) {
            errorReporter.report(templateNode.getSourceLocation(), MODIFIES_WITHOUT_MODNAME);
          }
        }
      }
    }
  }
}
