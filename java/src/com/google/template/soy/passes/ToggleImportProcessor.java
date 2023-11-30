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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.types.ToggleImportType;
import com.google.template.soy.types.ToggleRegistry;

/**
 * Resolves Soy toggle imports; verifies that the imports are valid and populates a local type
 * registry that maps the imported symbols to their types.
 */
final class ToggleImportProcessor implements ImportsPass.ImportProcessor {
  private final ErrorReporter errorReporter;
  private final ToggleRegistry toggleRegistry;

  private static final SoyErrorKind TOGGLE_MODULE_IMPORT =
      SoyErrorKind.of(
          "Module-level toggle imports are forbidden. Import individual toggles by name.");

  ToggleImportProcessor(ToggleRegistry toggleRegistry, ErrorReporter errorReporter) {
    this.toggleRegistry = toggleRegistry;
    this.errorReporter = errorReporter;
  }

  @Override
  public boolean handlesPath(String path) {
    return this.toggleRegistry.getPaths().contains(SourceLogicalPath.create(path));
  }

  @Override
  public ImmutableSet<String> getAllPaths() {
    return toggleRegistry.getPaths().stream()
        .map(SourceLogicalPath::path)
        .collect(toImmutableSet());
  }

  @Override
  public void init(ImmutableList<SoyFileNode> sourceFiles) {}

  @Override
  public void handle(SoyFileNode file, ImmutableCollection<ImportNode> imports) {
    for (ImportNode importNode : imports) {
      importNode.setImportType(ImportType.TOGGLE);
      if (importNode.isModuleImport()) {
        errorReporter.report(importNode.getSourceLocation(), TOGGLE_MODULE_IMPORT);
      } else {
        processImportedSymbols(importNode);
      }
    }
  }

  /**
   * Registers the imported toggles for a symbol-level import node (as opposed to a module-level
   * import node). Note that this is only called after we've verified that the import path exists
   * and any alias symbols are valid.
   */
  public void processImportedSymbols(ImportNode node) {
    for (ImportedVar symbol : node.getIdentifiers()) {
      String name = symbol.getSymbol();
      SourceLogicalPath path = node.getSourceFilePath();

      // Toggle name doesn't exist in registry
      if (!toggleRegistry.getToggles(path).contains(name)) {
        ImportsPass.reportUnknownSymbolError(
            errorReporter,
            symbol.nameLocation(),
            name,
            node.getPath(),
            toggleRegistry.getToggles(path));
      }
      ToggleImportType nodeType = ToggleImportType.create(name, path);
      symbol.setType(nodeType);
    }
  }
}
