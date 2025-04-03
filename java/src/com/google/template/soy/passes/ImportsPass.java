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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.defn.SymbolVar;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TypeRegistries;
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
  ResolveDeclaredTypesPass.class,
  ResolveExpressionTypesPass.class, // To resolve extensions.
  RewriteGlobalsPass.class, // To resolve extensions.
  ResolveTemplateNamesPass.class,
  ResolveUseVariantTypePass.class,
})
final class ImportsPass implements CompilerFileSetPass {

  private static final SoyErrorKind IMPORT_NOT_IN_DEPS =
      SoyErrorKind.of("Unknown import dep {0}.{1}{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind RELATIVE_IMPORT =
      SoyErrorKind.of(
          "Relative imports are not supported, use the fully qualified name of the file.");
  private static final SoyErrorKind UNKNOWN_SYMBOL =
      SoyErrorKind.of("Unknown symbol {0} in {1}.{2}", StyleAllowance.NO_PUNCTUATION);

  // Naming conflict errors:
  private static final SoyErrorKind IMPORT_CONFLICTS_WITH_TYPE_NAME =
      SoyErrorKind.of("Import ''{0}'' conflicts with a builtin type of the same name.");
  private static final SoyErrorKind IMPORT_SAME_FILE =
      SoyErrorKind.of("Importing from the same file is not allowed.");

  interface ImportProcessor {
    boolean handlesPath(SourceLogicalPath path);

    void handle(SoyFileNode file, ImmutableCollection<ImportNode> imports);

    ImmutableCollection<SourceLogicalPath> getAllPaths();

    void init(ImmutableList<SoyFileNode> sourceFiles);
  }

  private final ErrorReporter errorReporter;
  private final boolean disableAllTypeChecking;
  private final ImmutableList<ImportProcessor> processors;

  public ImportsPass(
      ErrorReporter errorReporter, boolean disableAllTypeChecking, ImportProcessor... processors) {
    this.errorReporter = errorReporter;
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

      checkState(!importNode.getIdentifiers().isEmpty());

      boolean foundSymbolErrors = false;
      for (SymbolVar symbol : importNode.getIdentifiers()) {
        // Import naming collisions. Report errors but continue checking the other symbols so we
        // can report all the errors at once.
        if (reportErrorIfSymbolInvalid(file, symbol.name(), symbol.nameLocation())) {
          foundSymbolErrors = true;
        }
      }
      if (foundSymbolErrors) {
        continue;
      }

      for (ImportProcessor processor : processors) {
        if (processor.handlesPath(importNode.getSourceFilePath())) {
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

  static final ImmutableSet<String> NEW_TYPES =
      ImmutableSet.of("list", "set", "map", "record", "iterable");

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

    // Name conflicts with a built-in type.
    SoyType type = TypeRegistries.builtinTypeRegistry().getType(importSymbolName);
    if (type != null) {
      if (NEW_TYPES.contains(importSymbolName)) {
        errorReporter.warn(nameLocation, IMPORT_CONFLICTS_WITH_TYPE_NAME, importSymbolName);
      } else {
        foundErrors = true;
        errorReporter.report(nameLocation, IMPORT_CONFLICTS_WITH_TYPE_NAME, importSymbolName);
      }
    }

    return foundErrors;
  }

  private void reportUnknownImport(SoyFileNode file, ImportNode node) {
    if (disableAllTypeChecking) {
      return;
    }
    String nodePath = node.getPath();
    if (nodePath.startsWith(".")) {
      errorReporter.report(node.getPathSourceLocation(), RELATIVE_IMPORT);
      return;
    }

    Set<SourceLogicalPath> allPaths =
        processors.stream()
            .flatMap(p -> p.getAllPaths().stream())
            .collect(toCollection(TreeSet::new));
    allPaths.remove(file.getFilePath().asLogicalPath());

    errorReporter.report(
        node.getPathSourceLocation(),
        IMPORT_NOT_IN_DEPS,
        nodePath,
        SoyErrors.getDidYouMeanMessage(
            allPaths.stream().map(SourceLogicalPath::path).collect(toImmutableList()), nodePath),
        BuildCleanerUtil.getBuildCleanerCommand(node));
  }
}
