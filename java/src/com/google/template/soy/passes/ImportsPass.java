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

import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TypeRegistries;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Abstract base class for an imports pass. Verifies that import paths are valid and symbols are
 * unique, before delegating to implementations for what to do when we visit each {@link
 * ImportNode}.
 */
@RunBefore({
  // Basically anything that needs types...
  ResolveExpressionTypesPass.class,
  ResolvePluginsPass.class, // Needs all local variables in scope.
  ResolveTemplateParamTypesPass.class,
  ResolveExpressionTypesPass.class, // To resolve extensions.
  RewriteGlobalsPass.class, // To resolve extensions.
  ResolveTemplateNamesPass.class,
})
public final class ImportsPass implements CompilerFileSetPass {

  private static final SoyErrorKind IMPORT_NOT_IN_DEPS =
      SoyErrorKind.of(
          "Unknown import dep {0}.{1}"
          ,
          StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind UNKNOWN_SYMBOL =
      SoyErrorKind.of("Unknown symbol {0} in {1}.{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind SYMBOLS_REQUIRED =
      SoyErrorKind.of("One or more imported symbols are required for import.");

  // Naming conflict errors:
  private static final SoyErrorKind IMPORT_CONFLICTS_WITH_GLOBAL =
      SoyErrorKind.of("Import ''{0}'' conflicts with a global of the same name.");
  private static final SoyErrorKind IMPORT_CONFLICTS_WITH_GLOBAL_PREFIX =
      SoyErrorKind.of("Import ''{0}'' conflicts with namespace for global ''{1}''.");
  private static final SoyErrorKind IMPORT_CONFLICTS_WITH_TYPE_NAME =
      SoyErrorKind.of("Import ''{0}'' conflicts with a builtin type of the same name.");
  private static final SoyErrorKind IMPORT_SAME_FILE =
      SoyErrorKind.of("Importing from the same file is not allowed.");

  interface ImportProcessor {
    boolean handlesPath(String path);

    void handle(SoyFileNode file, ImmutableCollection<ImportNode> imports);

    ImmutableCollection<String> getAllPaths();

    void init(ImmutableList<SoyFileNode> sourceFiles);
  }

  private final ErrorReporter errorReporter;
  private final SoyGeneralOptions options;
  private final boolean disableAllTypeChecking;
  private final ImmutableList<ImportProcessor> processors;

  // LazyInit
  private Map<String, String> globalPrefixToFullNameMap = null;

  public ImportsPass(
      ErrorReporter errorReporter,
      SoyGeneralOptions options,
      boolean disableAllTypeChecking,
      ImportProcessor... processors) {
    this.errorReporter = errorReporter;
    this.options = options;
    this.disableAllTypeChecking = disableAllTypeChecking;
    this.processors = ImmutableList.copyOf(processors);
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (ImportProcessor processor : processors) {
      processor.init(sourceFiles);
    }
    for (SoyFileNode sourceFile : sourceFiles) {
      run(sourceFile);
    }
    return Result.CONTINUE;
  }

  private void run(SoyFileNode file) {
    ImmutableMultimap.Builder<ImportProcessor, ImportNode> builder = ImmutableMultimap.builder();
    OUTER:
    for (ImportNode importNode : file.getImports()) {
      String path = importNode.getPath();
      if (path.equals(file.getFilePath().path())) {
        errorReporter.report(importNode.getPathSourceLocation(), IMPORT_SAME_FILE);
        continue;
      }

      if (importNode.getIdentifiers().isEmpty()) {
        errorReporter.report(importNode.getSourceLocation(), SYMBOLS_REQUIRED);
        continue;
      }

      boolean foundSymbolErrors = false;
      for (ImportedVar symbol : importNode.getIdentifiers()) {
        // Import naming collisions. Report errors but continue checking the other symbols so we
        // can report all of the errors at once.
        if (reportErrorIfSymbolInvalid(file, symbol.name(), symbol.nameLocation())) {
          foundSymbolErrors = true;
        }
      }
      if (foundSymbolErrors) {
        continue;
      }

      for (ImportProcessor processor : processors) {
        if (processor.handlesPath(importNode.getPath())) {
          builder.put(processor, importNode);
          continue OUTER;
        }
      }

      reportUnknownImport(file, importNode);
    }
    ImmutableMultimap<ImportProcessor, ImportNode> nodesByProc = builder.build();

    for (ImportProcessor processor : processors) {
      ImmutableCollection<ImportNode> nodes = nodesByProc.get(processor);
      // Must be called even if nodes is empty so that processors can set state for every file.
      processor.handle(file, nodes);
    }
  }

  /** Reports an error when an invalid symbol is imported from a valid file. */
  static void reportUnknownSymbolError(
      ErrorReporter errorReporter,
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
      // Don't report here. A better error message is generated later in ResolveNamesPass.
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

    return foundErrors;
  }

  /**
   * Builds a map that contains, for each compile time global, the first dotted prefix mapped to the
   * full global name (e.g. "foo." -> "foo.bar.Baz"). If multiple types have the same prefix, the
   * map will store the first one.
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

  private void reportUnknownImport(SoyFileNode file, ImportNode node) {
    if (disableAllTypeChecking) {
      return;
    }

    String nodePath = node.getPath();
    Set<String> allPaths =
        processors.stream()
            .flatMap(p -> p.getAllPaths().stream())
            .collect(toCollection(TreeSet::new));
    allPaths.remove(file.getFilePath().path());

    errorReporter.report(
        node.getPathSourceLocation(),
        IMPORT_NOT_IN_DEPS,
        nodePath,
        SoyErrors.getDidYouMeanMessage(allPaths, nodePath));
  }
}
