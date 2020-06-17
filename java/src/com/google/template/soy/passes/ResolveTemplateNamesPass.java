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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.soytree.ImportsContext.ImportsTemplateRegistry;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import java.util.Optional;

/**
 * Resolves template names in calls, checking against template names & imports. Since template
 * imports are resolved in two passes (once for deps and once for the current fileset), this pass
 * also needs to be run twice for the passes that require a partial template registry.
 */
public final class ResolveTemplateNamesPass implements CompilerFilePass, CompilerFileSetPass {
  private static final SoyErrorKind CALL_COLLIDES_WITH_NAMESPACE_ALIAS =
      SoyErrorKind.of("Call collides with namespace alias ''{0}''.");

  private static final SoyErrorKind MISSING_CALLEE_NAMESPACE =
      SoyErrorKind.of(
          "Callee ''{0}'' should be relative to a namespace (preceded by a \".\"), or it must be"
              + " imported. {1}",
          StyleAllowance.NO_PUNCTUATION);

  private final ErrorReporter errorReporter;
  private final boolean throwErrorIfCantResolve;

  public ResolveTemplateNamesPass(ErrorReporter errorReporter, boolean throwErrorIfCantResolve) {
    this.errorReporter = errorReporter;
    this.throwErrorIfCantResolve = throwErrorIfCantResolve;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      run(file, idGenerator);
    }

    return Result.CONTINUE;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator idGenerator) {
    visitFile(
        file,
        file.hasTemplateRegistry() ? Optional.of(file.getTemplateRegistry()) : Optional.empty());
  }

  private void visitFile(SoyFileNode file, Optional<ImportsTemplateRegistry> templateRegistry) {
    for (TemplateLiteralNode node :
        SoyTreeUtils.getAllNodesOfType(file, TemplateLiteralNode.class)) {
      resolveTemplateName(node, templateRegistry, file.getHeaderInfo());
    }
  }

  /** Attempts to resolve a template name, checking against aliases & imports. */
  private void resolveTemplateName(
      TemplateLiteralNode templateLiteralNode,
      Optional<ImportsTemplateRegistry> importsTemplateRegistry,
      SoyFileHeaderInfo header) {

    if (templateLiteralNode.isResolved()) {
      return;
    }

    Identifier unresolvedIdent = templateLiteralNode.getIdentifier();
    String name = unresolvedIdent.identifier();
    switch (unresolvedIdent.type()) {
      case DOT_IDENT:
        // Case 1: ".foo" Source callee name is partial.
        templateLiteralNode.resolveTemplateName(
            Identifier.create(header.getNamespace() + name, name, unresolvedIdent.location()));
        return;
      case DOTTED_IDENT:
        // Case 2: "foo.bar.baz" Source callee name is a proper dotted ident, which might start with
        // an alias.
        templateLiteralNode.resolveTemplateName(header.resolveAlias(unresolvedIdent));
        return;
      case SINGLE_IDENT:
        if (importsTemplateRegistry.isPresent()) {
          // Case 3: "foo" Source callee name is a single ident (not dotted). Check if it's a known
          // import:
          TemplateMetadata importedTemplate =
              importsTemplateRegistry.get().getBasicTemplateOrElement(name);
          if (importedTemplate != null) {
            templateLiteralNode.resolveTemplateName(
                Identifier.create(
                    importedTemplate.getTemplateName(), name, unresolvedIdent.location()));
            return;
          }

          // If this is the last time we're running the pass, throw an error if we
          // couldn't resolve the name.
          if (throwErrorIfCantResolve) {
            reportUnresolveableTemplateNameError(
                unresolvedIdent, header, importsTemplateRegistry.get());
          }
        }
        return;
    }
    throw new AssertionError(unresolvedIdent.type());
  }

  private void reportUnresolveableTemplateNameError(
      Identifier unresolvedTemplateNameIdent,
      SoyFileHeaderInfo header,
      ImportsTemplateRegistry importsTemplateRegistry) {
    String unresolvedName = unresolvedTemplateNameIdent.identifier();
    if (header.hasAlias(unresolvedName)) {
      // This callee collides with a namespace alias, which likely means the alias
      // incorrectly references a template.
      errorReporter.report(
          unresolvedTemplateNameIdent.location(),
          CALL_COLLIDES_WITH_NAMESPACE_ALIAS,
          unresolvedName);
    } else {
      //  The callee name needs a namespace, or should have been imported.
      String importSuggestion =
          SoyErrors.getClosest(importsTemplateRegistry.getImportedSymbols(), unresolvedName);
      if (!Strings.isNullOrEmpty(importSuggestion)) {
        importSuggestion = "'" + importSuggestion + "' (with no '.')";
      }
      errorReporter.report(
          unresolvedTemplateNameIdent.location(),
          MISSING_CALLEE_NAMESPACE,
          unresolvedName,
          SoyErrors.getDidYouMeanMessage("." + unresolvedName, importSuggestion));
    }
  }

  /**
   * Resolves aliases for Soy Shovel. Since shovel doesn't run any passes, we don't have a template
   * registry and can't resolve imports. This is a special util to allow call names for non-imports
   * to be resolved, and should not be called by anything other than Soy Shovel.
   */
  public void resolveForSoyShovel(SoyFileNode file) {
    visitFile(file, Optional.empty());
  }
}
