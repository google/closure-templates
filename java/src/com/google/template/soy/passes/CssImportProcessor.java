/*
 * Copyright 2023 Google Inc.
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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.css.CssRegistry;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.types.CssImportType;
import com.google.template.soy.types.CssModuleImportType;

/**
 * Resolves Soy proto imports; verifies that the imports are valid and populates a local type
 * registry that maps the imported symbols to their types.
 */
final class CssImportProcessor implements ImportsPass.ImportProcessor {
  private final CssRegistry cssRegistry;
  private final ErrorReporter errorReporter;

  private static final SoyErrorKind CSS_MODULE_IMPORT =
      SoyErrorKind.of(
          "Module-level css imports are forbidden. Import the classes object explicitly.");

  CssImportProcessor(CssRegistry cssRegistry, ErrorReporter errorReporter) {
    this.cssRegistry = cssRegistry;
    this.errorReporter = errorReporter;
  }

  @Override
  public void init(ImmutableList<SoyFileNode> sourceFiles) {}

  @Override
  public boolean handlesPath(String path) {
    return cssRegistry.filePathToShortClassMap().containsKey(path);
  }

  @Override
  public ImmutableSet<String> getAllPaths() {
    return cssRegistry.filePathToShortClassMap().keySet();
  }

  @Override
  public void handle(SoyFileNode file, ImmutableCollection<ImportNode> imports) {
    for (ImportNode importNode : imports) {
      importNode.setImportType(ImportType.CSS);
      if (importNode.isModuleImport()) {
        errorReporter.report(importNode.getSourceLocation(), CSS_MODULE_IMPORT);
      } else {
        processImportedSymbols(importNode);
      }
    }
  }

  private void processImportedSymbols(ImportNode node) {
    String path = node.getPath();
    CssModuleImportType moduleType = CssModuleImportType.create(SourceFilePath.create(path));
    node.setModuleType(moduleType);

    for (ImportedVar symbol : node.getIdentifiers()) {
      String name = symbol.getSymbol();
      if (!moduleType.getNestedSymbolNames().contains(name)) {
        ImportsPass.reportUnknownSymbolError(
            errorReporter,
            symbol.nameLocation(),
            name,
            node.getPath(),
            moduleType.getNestedSymbolNames());
      }

      ImmutableMap<String, String> shortClassMap = cssRegistry.filePathToShortClassMap().get(path);
      symbol.setType(CssImportType.create(node.getSourceFilePath(), shortClassMap));
    }
  }
}
