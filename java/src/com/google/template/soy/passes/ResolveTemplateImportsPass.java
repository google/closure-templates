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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.ImportsContext.ImportsTemplateRegistry;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNameRegistry;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.TemplatesPerFile;
import com.google.template.soy.soytree.TemplatesPerFile.TemplateName;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.TemplateImportType;
import com.google.template.soy.types.TemplateModuleImportType;
import com.google.template.soy.types.UnknownType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves Soy template imports; verifies that the imports are valid and populates a local template
 * registry that maps the imported symbols to their types.
 */
@RunBefore({
  ResolveTemplateNamesPass.class,
})
public final class ResolveTemplateImportsPass extends ImportsPass implements CompilerFileSetPass {

  private TemplateNameRegistry templateNameRegistry;
  private final SoyGeneralOptions options;
  private final ErrorReporter errorReporter;

  ResolveTemplateImportsPass(SoyGeneralOptions options, ErrorReporter errorReporter) {
    this.templateNameRegistry = null;
    this.options = options;
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(
      ImmutableList<SoyFileNode> sourceFiles,
      IdGenerator nodeIdGen,
      TemplateNameRegistry templateNameRegistry,
      TemplateRegistry unusedPartialTemplateRegistry) {
    this.templateNameRegistry = templateNameRegistry;
    for (SoyFileNode sourceFile : sourceFiles) {
      visitFile(sourceFile);
    }
    return Result.CONTINUE;
  }

  ErrorReporter errorReporter() {
    return errorReporter;
  }

  @Override
  TemplateImportVisitor createImportVisitorForFile(SoyFileNode file) {
    return new TemplateImportVisitor(
        file, templateNameRegistry, options, errorReporter, file.getSoyTypeRegistry());
  }

  static final class TemplateImportVisitor extends ImportVisitor {
    // Names of the templates in each file (a lightweight template registry, without the metadata).
    private final TemplateNameRegistry templateNameRegistry;

    // Map of imported symbols to full template names.
    final Map<String, TemplateName> symbolsToTemplatesMap = new LinkedHashMap<>();
    private final ImmutableMap<String, String> symbolToTemplateName;
    private final SoyTypeRegistry typeRegistry;

    TemplateImportVisitor(
        SoyFileNode file,
        TemplateNameRegistry templateNameRegistry,
        SoyGeneralOptions options,
        ErrorReporter errorReporter,
        SoyTypeRegistry typeRegistry) {
      super(file, ImmutableSet.of(ImportType.TEMPLATE), options, errorReporter);

      this.templateNameRegistry = templateNameRegistry;
      // TODO(b/170213185): Come up with a unified vision for template resolution.
      this.symbolToTemplateName =
          file.getTemplates().stream()
              .collect(
                  toImmutableMap(
                      TemplateNode::getLocalTemplateSymbol,
                      TemplateNode::getPartialTemplateName,
                      (existing, replacement) -> existing));
      this.typeRegistry = typeRegistry;
    }

    /**
     * Registers the imported templates for a symbol-level import node (as opposed to a module-level
     * import node). Verifies that the template names are valid and stores a mapping from the
     * imported symbol to the template info. Note that this is only called after we've verified that
     * the import path exists and any alias symbols are valid.
     */
    @Override
    void processImportedSymbols(ImportNode node) {
      TemplatesPerFile templatesPerFile =
          templateNameRegistry.getTemplatesForFile(SourceFilePath.create(node.getPath()));
      for (ImportedVar symbol : node.getIdentifiers()) {
        String name = symbol.getSymbol();
        // Report an error if the template name is invalid.
        if (!templatesPerFile.hasTemplateWithUnqualifiedName(name)) {
          reportUnknownSymbolError(
              symbol.nameLocation(),
              name,
              node.getPath(),
              /* validSymbols= */ templatesPerFile.getUnqualifiedTemplateNames());
          symbol.setType(UnknownType.getInstance());
          continue;
        }

        // Consider moving this to ImportsPass.
        String partialTemplateName = symbolToTemplateName.get(symbol.name());
        if (partialTemplateName != null) {
          // Error will be reported in LocalVariables.
          symbol.setType(UnknownType.getInstance());
          continue;
        }

        // Needs to be able to handle duplicates, since the formatter fixes them, but it's not a
        // compiler error (if they have the same path).
        TemplateName templateName = templatesPerFile.getFullTemplateName(name);
        symbolsToTemplatesMap.put(symbol.name(), templateName);
        symbol.setType(
            typeRegistry.intern(TemplateImportType.create(templateName.fullyQualifiedName())));
      }
    }

    /**
     * Visits a template module import (e.g. "import * as fooTemplates from foo.soy"). Registers all
     * the templates in the imported file (e.g. "fooTemplates.render"). Note that this is only
     * called after we've verified that the import path exists and the module alias symbol is valid
     * (doesn't collide with other import symbol aliases).
     */
    @Override
    void processImportedModule(ImportNode node) {
      TemplatesPerFile templatesPerFile =
          templateNameRegistry.getTemplatesForFile(SourceFilePath.create(node.getPath()));
      Iterables.getOnlyElement(node.getIdentifiers())
          .setType(
              typeRegistry.intern(
                  TemplateModuleImportType.create(
                      templatesPerFile.getNamespace(),
                      templatesPerFile.getFilePath(),
                      templatesPerFile.getTemplateNames().stream()
                          .map(TemplateName::unqualifiedName)
                          .collect(toImmutableSet()))));
      // For each template, add a mapping from "ModuleName.templateName" -> templateFqn.
      templatesPerFile
          .getUnqualifiedTemplateNames()
          .forEach(
              template ->
                  symbolsToTemplatesMap.put(
                      node.getModuleAlias() + "." + template,
                      templatesPerFile.getFullTemplateName(template)));
    }

    @Override
    boolean importExists(ImportType type, String path) {
      // We can ignore the type param because this visitor only visits template imports.
      return templateNameRegistry.hasFile(SourceFilePath.create(path));
    }

    @Override
    ImmutableSet<String> getValidImportPathsForType(ImportType type) {
      // Get the names of all Soy files registered in the file set (including its deps). We can
      // ignore the type param because this visitor only visits template imports.
      return templateNameRegistry.allFiles().stream()
          .map(SourceFilePath::path)
          .collect(toImmutableSet());
    }

    @Override
    void updateImportsContext() {
      file.getImportsContext()
          .setTemplateRegistry(
              new ImportsTemplateRegistry(file, ImmutableMap.copyOf(symbolsToTemplatesMap)));
    }
  }
}
