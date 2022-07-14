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
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TemplateType;

/** Checks modifiable templates. */
@RunAfter(ResolveExpressionTypesPass.class)
final class CheckModifiableTemplatesPass implements CompilerFilePass {

  private static final SoyErrorKind MODIFIES_WITHOUT_MODNAME =
      SoyErrorKind.of(
          "\"modifies\" can only be used in a file with a '{'modName'}' command, unless it is used "
              + "on a variant template. If this is a non-variant template, did you forget to add a "
              + "'{'modName'}'? Or did you forget to mark this template as a variant?");

  private static final SoyErrorKind MODIFIABLE_WITH_MODNAME =
      SoyErrorKind.of(
          "\"modifiable\" templates cannot be placed in files with a '{'modName'}' command.");

  private static final SoyErrorKind INCOMPATIBLE_SIGNATURE =
      SoyErrorKind.of(
          "Template with signature {0} cannot be modified by template with "
              + "incompatible signature {1}.");

  private static final SoyErrorKind BAD_VARIANT_TYPE =
      SoyErrorKind.of("Expected variant of type {0}, found type {1}.");

  private final ErrorReporter errorReporter;

  CheckModifiableTemplatesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode templateNode : file.getTemplates()) {
      if (templateNode instanceof TemplateBasicNode) {
        TemplateBasicNode templateBasicNode = (TemplateBasicNode) templateNode;
        if (templateBasicNode.getModifiable() && file.getDelPackageName() != null) {
          errorReporter.report(templateNode.getSourceLocation(), MODIFIABLE_WITH_MODNAME);
        }
        if (templateBasicNode.getModifiesExpr() != null
            && templateBasicNode.getVariantExpr() == null
            && file.getDelPackageName() == null) {
          errorReporter.report(templateNode.getSourceLocation(), MODIFIES_WITHOUT_MODNAME);
        }
        validateModifiesAttribute(templateBasicNode);
        validateVariantExpr(templateBasicNode);
      }
    }
  }

  private void validateModifiesAttribute(TemplateBasicNode templateBasicNode) {
    if (templateBasicNode.getModifiesExpr() == null) {
      return;
    }
    SoyType modifiedTemplateType = templateBasicNode.getModifiesExpr().getRoot().getType();
    SoyType modifyingType = TemplateMetadata.buildTemplateType(templateBasicNode);
    if (!modifyingType.isAssignableFromStrict(modifiedTemplateType)) {
      errorReporter.report(
          templateBasicNode.getSourceLocation(),
          INCOMPATIBLE_SIGNATURE,
          modifiedTemplateType.toString(),
          modifyingType.toString());
    }
  }

  private void validateVariantExpr(TemplateBasicNode templateBasicNode) {
    if (templateBasicNode.getVariantExpr() == null) {
      return;
    }
    TemplateType modifiedTemplateType =
        (TemplateType) templateBasicNode.getModifiesExpr().getRoot().getType();
    SoyType variantType = templateBasicNode.getVariantExpr().getRoot().getType();
    if (!modifiedTemplateType.getUseVariantType().isAssignableFromStrict(variantType)) {
      errorReporter.report(
          templateBasicNode.getVariantExpr().getSourceLocation(),
          BAD_VARIANT_TYPE,
          modifiedTemplateType.getUseVariantType(),
          variantType);
    }
  }
}
