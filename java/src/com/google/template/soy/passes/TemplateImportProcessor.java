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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNameRegistry;
import com.google.template.soy.soytree.TemplatesPerFile;
import com.google.template.soy.soytree.TemplatesPerFile.TemplateName;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.TemplateImportType;
import com.google.template.soy.types.TemplateModuleImportType;
import com.google.template.soy.types.UnknownType;
import java.util.function.Supplier;

/**
 * Resolves Soy template imports; verifies that the imports are valid and populates a local template
 * registry that maps the imported symbols to their types.
 */
public final class TemplateImportProcessor implements ImportsPass.ImportProcessor {

  private final ErrorReporter errorReporter;
  private final Supplier<TemplateNameRegistry> templateNameRegistry;

  private SoyTypeRegistry typeRegistry;

  TemplateImportProcessor(
      ErrorReporter errorReporter, Supplier<TemplateNameRegistry> templateNameRegistry) {
    this.templateNameRegistry = templateNameRegistry;
    this.errorReporter = errorReporter;
  }

  @Override
  public void handle(SoyFileNode file, ImmutableCollection<ImportNode> imports) {
    typeRegistry = file.getSoyTypeRegistry();

    for (ImportNode anImport : imports) {
      anImport.setImportType(ImportType.TEMPLATE);
      if (anImport.isModuleImport()) {
        processImportedModule(anImport);
      } else {
        processImportedSymbols(anImport);
      }
    }
  }

  /**
   * Registers the imported templates for a symbol-level import node (as opposed to a module-level
   * import node). Verifies that the template names are valid and stores a mapping from the imported
   * symbol to the template info. Note that this is only called after we've verified that the import
   * path exists and any alias symbols are valid.
   */
  private void processImportedSymbols(ImportNode node) {
    TemplatesPerFile templatesPerFile =
        templateNameRegistry.get().getTemplatesForFile(SourceFilePath.create(node.getPath()));
    for (ImportedVar symbol : node.getIdentifiers()) {
      String name = symbol.getSymbol();
      // Report an error if the template name is invalid.
      if (!templatesPerFile.hasTemplateWithUnqualifiedName(name)) {
        ImportsPass.reportUnknownSymbolError(
            errorReporter,
            symbol.nameLocation(),
            name,
            node.getPath(),
            /* validSymbols= */ templatesPerFile.getUnqualifiedTemplateNames());
        symbol.setType(UnknownType.getInstance());
        continue;
      }

      // Needs to be able to handle duplicates, since the formatter fixes them, but it's not a
      // compiler error (if they have the same path).
      TemplateName templateName = templatesPerFile.getFullTemplateName(name);
      symbol.setType(
          typeRegistry.intern(TemplateImportType.create(templateName.fullyQualifiedName())));
    }
  }

  /**
   * Visits a template module import (e.g. "import * as fooTemplates from foo.soy"). Registers all
   * the templates in the imported file (e.g. "fooTemplates.render"). Note that this is only called
   * after we've verified that the import path exists and the module alias symbol is valid (doesn't
   * collide with other import symbol aliases).
   */
  private void processImportedModule(ImportNode node) {
    TemplatesPerFile templatesPerFile =
        templateNameRegistry.get().getTemplatesForFile(SourceFilePath.create(node.getPath()));
    Iterables.getOnlyElement(node.getIdentifiers())
        .setType(
            typeRegistry.intern(
                TemplateModuleImportType.create(
                    templatesPerFile.getNamespace(),
                    templatesPerFile.getFilePath(),
                    templatesPerFile.getTemplateNames().stream()
                        .map(TemplateName::unqualifiedName)
                        .collect(toImmutableSet()))));
  }

  @Override
  public boolean handlesPath(String path) {
    return templateNameRegistry.get().hasFile(SourceFilePath.create(path));
  }

  @Override
  public ImmutableCollection<String> getAllPaths() {
    return templateNameRegistry.get().allFiles().stream()
        .map(SourceFilePath::path)
        .collect(toImmutableSet());
  }
}
