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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.passes.CompilerFileSetPass.Result;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.TemplatesPerFile;
import com.google.template.soy.soytree.TemplatesPerFile.TemplateName;
import com.google.template.soy.soytree.defn.ImportedVar;

/**
 * Resolves Soy template imports; verifies that the imports are valid and populates a local template
 * registry that maps the imported symbols to their types.
 */
abstract class ResolveTemplateImportsPass extends ImportsPass implements CompilerFileSetPass {
  private TemplateRegistry fileSetTemplateRegistry;
  private final ErrorReporter errorReporter;

  ResolveTemplateImportsPass(ErrorReporter errorReporter) {
    this.fileSetTemplateRegistry = null;
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(
      ImmutableList<SoyFileNode> sourceFiles,
      IdGenerator nodeIdGen,
      TemplateRegistry fileSetTemplateRegistry) {
    this.fileSetTemplateRegistry = fileSetTemplateRegistry;
    for (SoyFileNode sourceFile : sourceFiles) {
      visitFile(sourceFile);
    }
    return Result.CONTINUE;
  }

  ErrorReporter errorReporter() {
    return errorReporter;
  }

  TemplateRegistry getFileSetTemplateRegistry() {
    return fileSetTemplateRegistry;
  }

  @Override
  abstract TemplateImportVisitor createImportVisitorForFile(SoyFileNode file);

  abstract static class TemplateImportVisitor extends ImportVisitor {
    private final TemplateRegistry fileSetTemplateRegistry;
    final ImmutableMap.Builder<String, TemplateName> symbolsToTemplatesMap =
        new ImmutableMap.Builder<>();

    TemplateImportVisitor(
        SoyFileNode file, TemplateRegistry fileSetTemplateRegistry, ErrorReporter errorReporter) {
      super(file, ImmutableSet.of(ImportType.TEMPLATE), errorReporter);
      this.fileSetTemplateRegistry = fileSetTemplateRegistry;
    }

    /**
     * Visits a template import node: verifies that the template names are valid and stores a
     * mapping from the imported symbol to the template info. Note that this is only called after
     * we've verified that the import path exists and any alias symbols are valid.
     */
    @Override
    void visitImportNodeWithValidPathAndSymbol(ImportNode node) {
      TemplatesPerFile templatesPerFile =
          fileSetTemplateRegistry.getTemplatesPerFile(node.getPath());
      for (ImportedVar symbol : node.getIdentifiers()) {
        String name = symbol.name();
        // Report an error if the template name is invalid.
        if (!templatesPerFile.hasTemplateWithUnqualifiedName(name)) {
          reportUnknownSymbolError(
              symbol.nameLocation(),
              name,
              node.getPath(),
              /* validSymbols= */ templatesPerFile.getUnqualifiedTemplateNames());
          continue;
        }
        symbolsToTemplatesMap.put(symbol.aliasOrName(), templatesPerFile.getFullTemplateName(name));
      }
      node.setIsResolved(); // Node has been validated
    }

    @Override
    boolean importExists(ImportType type, String path) {
      // We can ignore the type param because this visitor only visits template imports.
      return fileSetTemplateRegistry.getTemplatesPerFile().containsKey(path);
    }

    @Override
    ImmutableSet<String> getValidImportPathsForType(ImportType type) {
      // Get the names of all Soy files registered in the file set (including its deps). We can
      // ignore the type param because this visitor only visits template imports.
      return fileSetTemplateRegistry.getAllFileNames();
    }
  }
}
