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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TemplateType;
import java.util.Set;
import java.util.TreeSet;

/** Checks modifiable templates. */
@RunAfter(ResolveExpressionTypesPass.class)
final class CheckModifiableTemplatesPass implements CompilerFilePass {

  private static final SoyErrorKind MODIFIES_WITHOUT_MODNAME =
      SoyErrorKind.of(
          "\"modifies\" can only be used in a file with a '{'modname'}' command, unless it is used "
              + "on a variant template. If this is a non-variant template, did you forget to add a "
              + "'{'modname'}'? Or did you forget to mark this template as a variant?");

  private static final SoyErrorKind MODNAME_WITHOUT_MODIFIES =
      SoyErrorKind.of("'{'modname'}' can only be used in a file with a \"modifies\" template.");

  private static final SoyErrorKind MODIFIABLE_WITH_MODNAME =
      SoyErrorKind.of(
          "\"modifiable\" templates cannot be placed in files with a '{'modname'}' command.");

  private static final SoyErrorKind INCOMPATIBLE_SIGNATURE =
      SoyErrorKind.of(
          "Template with signature {0} cannot be modified by template with "
              + "incompatible signature {1}.");

  private static final SoyErrorKind BAD_VARIANT_TYPE =
      SoyErrorKind.of("Expected variant of type {0}, found type {1}.");

  private static final SoyErrorKind MODDING_MULTIPLE_FILES =
      SoyErrorKind.of(
          "A single Soy file can only modify templates from a single external namespace. "
              + "Namespaces: {0}.");

  private static final SoyErrorKind UNRESOLVED_MODIFIES_EXPR =
      SoyErrorKind.of(
          "The \"modifies\" expression could not be statically resolved to a valid template "
              + "literal.");

  private static final SoyErrorKind MODIFIES_NON_MODIFIABLE =
      SoyErrorKind.of("Template in \"modifies\" expression must have `modifiable=\"true\"` set.");

  private final ErrorReporter errorReporter;

  CheckModifiableTemplatesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    TreeSet<String> modifiedNamespaces = new TreeSet<>();
    boolean foundModTemplate = false;
    for (TemplateNode templateNode : file.getTemplates()) {
      if (templateNode instanceof TemplateDelegateNode) {
        foundModTemplate = true;
      }
      if (templateNode instanceof TemplateBasicNode) {
        TemplateBasicNode templateBasicNode = (TemplateBasicNode) templateNode;
        if (templateBasicNode.isModifiable() && file.getModName() != null) {
          errorReporter.report(templateNode.getSourceLocation(), MODIFIABLE_WITH_MODNAME);
        }
        if (templateBasicNode.getModifiesExpr() != null) {
          foundModTemplate = true;
          if (templateBasicNode.getVariantExpr() == null && file.getModName() == null) {
            errorReporter.report(templateNode.getSourceLocation(), MODIFIES_WITHOUT_MODNAME);
          }
        }
        validateModifiesAttribute(templateBasicNode, file, modifiedNamespaces);
        validateVariantExpr(templateBasicNode);
      }
    }
    if (file.getModName() != null && !foundModTemplate) {
      errorReporter.report(file.getModNameDeclaration().location(), MODNAME_WITHOUT_MODIFIES);
    }
  }

  private void validateModifiesAttribute(
      TemplateBasicNode templateBasicNode, SoyFileNode file, Set<String> modifiedNamespaces) {
    if (templateBasicNode.getModifiesExpr() == null) {
      return;
    }
    if (!(templateBasicNode.getModifiesExpr().getRoot() instanceof TemplateLiteralNode)) {
      errorReporter.report(
          templateBasicNode.getModifiesExpr().getSourceLocation(), UNRESOLVED_MODIFIES_EXPR);
      return;
    }
    TemplateType modifiedTemplateType =
        (TemplateType) templateBasicNode.getModifiesExpr().getRoot().getType();
    if (!modifiedTemplateType.isModifiable()) {
      errorReporter.report(
          templateBasicNode.getModifiesExpr().getSourceLocation(), MODIFIES_NON_MODIFIABLE);
      return;
    }
    SoyType modifyingType = TemplateMetadata.buildTemplateType(templateBasicNode);
    if (!modifyingType.isAssignableFromStrict(modifiedTemplateType)) {
      errorReporter.report(
          templateBasicNode.getSourceLocation(),
          INCOMPATIBLE_SIGNATURE,
          modifiedTemplateType.toString(),
          modifyingType.toString());
    }
    validateSingleFileIsModded(templateBasicNode, file, modifiedNamespaces);
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

  private void validateSingleFileIsModded(
      TemplateBasicNode templateBasicNode, SoyFileNode file, Set<String> modifiedNamespaces) {
    // Invariants checked in validateModifiesAttribute().
    Preconditions.checkNotNull(templateBasicNode.getModifiesExpr());
    Preconditions.checkState(
        templateBasicNode.getModifiesExpr().getRoot() instanceof TemplateLiteralNode);
    TemplateLiteralNode literal =
        (TemplateLiteralNode) templateBasicNode.getModifiesExpr().getRoot();
    String namespace =
        literal.getResolvedName().substring(0, literal.getResolvedName().lastIndexOf("."));
    if (!namespace.equals(file.getNamespace())) {
      modifiedNamespaces.add(namespace);
      if (modifiedNamespaces.size() > 1) {
        errorReporter.report(
            templateBasicNode.getModifiesExpr().getSourceLocation(),
            MODDING_MULTIPLE_FILES,
            Joiner.on(", ").join(modifiedNamespaces));
      }
    }
  }
}
