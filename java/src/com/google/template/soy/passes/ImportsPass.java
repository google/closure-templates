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

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.defn.ImportedVar;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for an imports pass. Verifies that import paths are valid and symbols are
 * unique, before delegating to implementations for what to do when we visit each {@link
 * ImportNode}.
 */
abstract class ImportsPass {

  private static final SoyErrorKind IMPORT_NOT_IN_DEPS =
      SoyErrorKind.of("Unknown import dep {0}.{1}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind UNKNOWN_SYMBOL =
      SoyErrorKind.of("Unknown symbol {0} in {1}.{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind IMPORT_COLLISION =
      SoyErrorKind.of("Imported symbol {0} conflicts with previously imported symbol.");
  private static final SoyErrorKind SYMBOLS_NOT_ALLOWED =
      SoyErrorKind.of("Imported symbols are not allowed from import type {0}.");
  private static final SoyErrorKind SYMBOLS_REQUIRED =
      SoyErrorKind.of("One or more imported symbols are required for import type {0}.");

  /**
   * Visits a Soy file, validating its imports and updating the file's {@link
   * com.google.template.soy.soytree.ImportsContext}.
   */
  void visitFile(SoyFileNode file) {
    createImportVisitorForFile(file).exec();
  }

  /** Constructs an {@link ImportVisitor} for the given soy file. */
  abstract ImportVisitor createImportVisitorForFile(SoyFileNode file);

  /** Visitor for imports in a single soy file. */
  abstract static class ImportVisitor {
    final SoyFileNode file;
    final ErrorReporter errorReporter;
    private final ImmutableSet<ImportType> importTypesToVisit;
    /**
     * Map from unique imported symbol (aliasOrName) to "path//name". Used to determine whether
     * imported symbol collisions are real collisions or just duplicates.
     */
    private final Map<String, String> uniqueImports = new HashMap<>();

    ImportVisitor(
        SoyFileNode file,
        ImmutableSet<ImportType> importTypesToVisit,
        ErrorReporter errorReporter) {
      this.file = file;
      this.importTypesToVisit = importTypesToVisit;
      this.errorReporter = errorReporter;
    }

    /**
     * Visits all of the file's imports, and then updates the file's {@link
     * com.google.template.soy.soytree.ImportsContext}.
     */
    final void exec() {
      for (ImportNode importNode : file.getImports()) {
        visit(importNode);
      }
      updateImportsContext();
    }

    /**
     * Updates the {@link SoyFileNode}'s {@link com.google.template.soy.soytree.ImportsContext}
     * after the visitor has been executed.
     */
    abstract void updateImportsContext();

    /**
     * Whether to visit the node. Will only be called if the nodes has a type in {@link
     * #importTypesToVisit}. This can be used if there are additional criteria for whether to visit
     * a node or not (e.g. template imports are visited in two phases, one for deps and one for the
     * current file set).
     */
    boolean shouldVisit(ImportNode node) {
      return true;
    }

    /**
     * Visits an import node. First, validates that the import path exists and the symbol names
     * and/or optional aliases do not collide with other import symbols. Then, delegates to the
     * abstract {@link #visitImportNodeWithValidPathAndSymbol}.
     */
    private void visit(ImportNode node) {
      if (!importTypesToVisit.contains(node.getImportType()) || !shouldVisit(node)) {
        return;
      }

      if (!importExists(node.getImportType(), node.getPath())) {
        errorReporter.report(
            node.getPathSourceLocation(),
            IMPORT_NOT_IN_DEPS,
            node.getPath(),
            SoyErrors.getDidYouMeanMessage(
                getValidImportPathsForType(node.getImportType()), node.getPath()));
        return;
      }

      if (node.getImportType().allowsSymbols()) {
        if (node.getImportType().requiresSymbols() && node.getIdentifiers().isEmpty()) {
          errorReporter.report(node.getSourceLocation(), SYMBOLS_REQUIRED, node.getImportType());
          return;
        }

        for (ImportedVar symbol : node.getIdentifiers()) {
          String name = symbol.aliasOrName();

          // Ignore duplicate imports. The formatter will dedupe these and it's more convenient
          // to not have a compilation error on duplicates.
          String path = node.getPath() + "//" + symbol.name();
          String duplicatePath = uniqueImports.put(name, path);
          if (path.equals(duplicatePath)) {
            continue;
          }

          if (!file.getImportsContext().addImportedSymbol(name)) {
            errorReporter.report(symbol.nameLocation(), IMPORT_COLLISION, name);
            return;
          }
        }
      } else if (!node.getIdentifiers().isEmpty()) {
        errorReporter.report(
            node.getIdentifiers().get(0).nameLocation(), SYMBOLS_NOT_ALLOWED, node.getImportType());
        return;
      }

      visitImportNodeWithValidPathAndSymbol(node);
    }

    /**
     * Whether the path exists and is valid for the given import type. Will only be called for nodes
     * of type {@link #importTypesToVisit}.
     */
    abstract boolean importExists(ImportType importType, String path);

    /**
     * Visits an import node that has already been verified to have a valid import path and symbol
     * (+ optional alias) that doesn't collide with other imports (yet). Will only be called for
     * nodes of type {@link #importTypesToVisit}.
     */
    abstract void visitImportNodeWithValidPathAndSymbol(ImportNode node);

    /**
     * Gets the list of valid paths for a given import type, used for "Did you mean?" error
     * messages. Will only be called for nodes of type {@link #importTypesToVisit}.
     */
    abstract ImmutableSet<String> getValidImportPathsForType(ImportType importType);

    /** Reports an error when an invalid symbol is imported from a valid file. */
    void reportUnknownSymbolError(
        SourceLocation symbolLocation,
        String incorrectName,
        String importPath,
        Iterable<String> validSymbols) {
      errorReporter.report(
          symbolLocation,
          UNKNOWN_SYMBOL,
          incorrectName,
          importPath,
          SoyErrors.getDidYouMeanMessage(validSymbols, incorrectName));
    }
  }
}
