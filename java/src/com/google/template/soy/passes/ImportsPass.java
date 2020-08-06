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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TypeRegistries;
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
  private static final SoyErrorKind SYMBOLS_NOT_ALLOWED =
      SoyErrorKind.of("Imported symbols are not allowed from import type {0}.");
  private static final SoyErrorKind SYMBOLS_REQUIRED =
      SoyErrorKind.of("One or more imported symbols are required for import type {0}.");

  // Naming conflict errors:
  private static final SoyErrorKind IMPORT_COLLISION =
      SoyErrorKind.of("Imported symbol {0} conflicts with previously imported symbol.");
  private static final SoyErrorKind IMPORT_CONFLICTS_WITH_GLOBAL =
      SoyErrorKind.of("Import ''{0}'' conflicts with a global of the same name.");
  private static final SoyErrorKind IMPORT_CONFLICTS_WITH_GLOBAL_PREFIX =
      SoyErrorKind.of("Import ''{0}'' conflicts with namespace for global ''{1}''.");
  private static final SoyErrorKind IMPORT_CONFLICTS_WITH_TYPE_NAME =
      SoyErrorKind.of("Import ''{0}'' conflicts with a builtin type of the same name.");

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
    private final SoyGeneralOptions options;
    final ErrorReporter errorReporter;
    private final ImmutableSet<ImportType> importTypesToVisit;
    /**
     * Map from unique imported symbol (aliasOrName) to "path//name". Used to determine whether
     * imported symbol collisions are real collisions or just duplicates.
     */
    private final Map<String, String> uniqueImports = new HashMap<>();

    private Map<String, String> globalPrefixToFullNameMap = null;

    ImportVisitor(
        SoyFileNode file,
        ImmutableSet<ImportType> importTypesToVisit,
        SoyGeneralOptions options,
        ErrorReporter errorReporter) {
      this.file = file;
      this.options = options;
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

        boolean foundSymbolErrors = false;
        for (ImportedVar symbol : node.getIdentifiers()) {
          String name = symbol.aliasOrName();

          // Ignore duplicate imports. The formatter will dedupe these and it's more convenient
          // to not have a compilation error on duplicates.
          String path = node.getPath() + "//" + symbol.name();
          String duplicatePath = uniqueImports.put(name, path);
          if (path.equals(duplicatePath)) {
            continue;
          }

          // Import naming collisions. Report errors but continue checking the other symbols so we
          // can report all of the errors at once.
          if (reportErrorIfSymbolInvalid(file, name, symbol.nameLocation())) {
            foundSymbolErrors = true;
            continue;
          }
        }
        if (foundSymbolErrors) {
          return;
        }
      } else if (!node.getIdentifiers().isEmpty()) {
        errorReporter.report(
            node.getIdentifiers().get(0).nameLocation(), SYMBOLS_NOT_ALLOWED, node.getImportType());
        return;
      }

      if (node.isModuleImport()) {
        processImportedModule(node);
      } else {
        processImportedSymbols(node);
      }
    }

    /**
     * Whether the path exists and is valid for the given import type. Will only be called for nodes
     * of type {@link #importTypesToVisit}.
     */
    abstract boolean importExists(ImportType importType, String path);

    /**
     * Registers a module-level import (e.g. import * as fooTemplates from 'my_foo.soy'); This will
     * only be called after the node's path has been verified, and them module alias has been
     * checked for collisions against other imports. Will only be called for nodes of type {@link
     * #importTypesToVisit}.
     */
    abstract void processImportedModule(ImportNode node);

    /**
     * Registers the symbols in a symbol-level import node (e.g. import {foo,bar as myBar} from
     * '...';). This will only be called after the node's path has been verified, and the symbol
     * aliases have been checked for collisions against other imports. Will only be called for nodes
     * of type {@link #importTypesToVisit}.
     */
    abstract void processImportedSymbols(ImportNode node);

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

    /**
     * Reports naming collisions with built-in types, globals, VEs, etc.
     *
     * <p>Note that collisions between aliases and imports are reported in {@link
     * ValidateAliasesPass}.
     *
     * <p>Returns true if any errors were reported.
     */
    boolean reportErrorIfSymbolInvalid(
        SoyFileNode file, String importSymbolName, SourceLocation nameLocation) {
      boolean foundErrors = false;

      // Name collides with another import symbol.
      if (!file.getImportsContext().addImportedSymbol(importSymbolName)) {
        errorReporter.report(nameLocation, IMPORT_COLLISION, importSymbolName);
        foundErrors = true;
      }

      // Name conflicts with a global.
      if (options.getCompileTimeGlobals().containsKey(importSymbolName)) {
        foundErrors = true;
        errorReporter.report(nameLocation, IMPORT_CONFLICTS_WITH_GLOBAL, importSymbolName);
      }

      // Name conflicts with a built-in type.
      SoyType type = TypeRegistries.builtinTypeRegistry().getType(importSymbolName);
      if (type != null) {
        foundErrors = true;
        errorReporter.report(nameLocation, IMPORT_CONFLICTS_WITH_TYPE_NAME, importSymbolName);
      }

      // Name conflicts with the namespace for a global.
      String prefix = importSymbolName + ".";
      if (globalPrefixToFullNameMap == null) {
        globalPrefixToFullNameMap = buildGlobalPrefixToFullNameMap();
      }
      if (globalPrefixToFullNameMap.containsKey(prefix)) {
        foundErrors = true;
        errorReporter.report(
            nameLocation,
            IMPORT_CONFLICTS_WITH_GLOBAL_PREFIX,
            importSymbolName,
            globalPrefixToFullNameMap.get(prefix));
      }

      // TODO(b/161005145): Add VE naming collision check.
      return foundErrors;
    }

    /**
     * Builds a map that contains, for each compile time global, the first dotted prefix mapped to
     * the full global name (e.g. "foo." -> "foo.bar.Baz"). If multiple types have the same prefix,
     * the map will store the first one.
     */
    private ImmutableMap<String, String> buildGlobalPrefixToFullNameMap() {
      Map<String, String> prefixesToGlobalNamesBuilder = new HashMap<>();

      for (String fullName : options.getCompileTimeGlobals().keySet()) {
        String prefix = fullName;
        int indexOfFirstDot = fullName.indexOf(".");
        // If there was no dot, or a dot was the last char, return the whole string.
        // Otherwise, return "foo." in "foo.bar.baz".
        if (indexOfFirstDot >= 0 && indexOfFirstDot < fullName.length() - 1) {
          prefix = fullName.substring(0, indexOfFirstDot + 1);
        }
        prefixesToGlobalNamesBuilder.putIfAbsent(prefix, fullName);
      }
      return ImmutableMap.copyOf(prefixesToGlobalNamesBuilder);
    }
  }
}
