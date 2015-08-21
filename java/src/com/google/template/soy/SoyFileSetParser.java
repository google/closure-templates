/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.parsepasses.CheckCallsVisitor;
import com.google.template.soy.parsepasses.CheckDelegatesVisitor;
import com.google.template.soy.parsepasses.InferRequiredSyntaxVersionVisitor;
import com.google.template.soy.parsepasses.ParsePasses;
import com.google.template.soy.shared.SoyAstCache;
import com.google.template.soy.shared.SoyAstCache.VersionedFile;
import com.google.template.soy.sharedpasses.CheckCallingParamTypesVisitor;
import com.google.template.soy.sharedpasses.CheckTemplateParamsVisitor;
import com.google.template.soy.sharedpasses.CheckTemplateVisibility;
import com.google.template.soy.sharedpasses.ReportSyntaxVersionErrorsVisitor;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyTypeRegistry;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Static functions for parsing a set of Soy files into a {@link SoyFileSetNode}.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SoyFileSetParser {
  /** A simple tuple for the result of a parse operation. */
  @AutoValue
  public abstract static class ParseResult {
    static ParseResult create(SoyFileSetNode soyTree, TemplateRegistry registry) {
      return new AutoValue_SoyFileSetParser_ParseResult(soyTree, registry);
    }

    public abstract SoyFileSetNode fileSet();

    public abstract TemplateRegistry registry();
  }

  /** The type registry to resolve type names. */
  private final SoyTypeRegistry typeRegistry;

  /** Optional file cache. */
  @Nullable private final SoyAstCache cache;

  /** User-declared syntax version. */
  private final SyntaxVersion declaredSyntaxVersion;

  /** The suppliers of the Soy files to parse. */
  private final List<? extends SoyFileSupplier> soyFileSuppliers;

  /** Parsing passes. null means that they are disabled.*/
  @Nullable private final ParsePasses parsingPasses;

  /** Whether to run checking passes. */
  private final boolean doRunCheckingPasses;

  /** For reporting parse errors. */
  private final ErrorReporter errorReporter;

  /**
   * @param typeRegistry The type registry to resolve type names.
   * @param astCache The AST cache to use, if any.
   * @param declaredSyntaxVersion User-declared syntax version.
   * @param soyFileSuppliers The suppliers for the Soy files. Each must have a unique file name.
   */
  public SoyFileSetParser(
      SoyTypeRegistry typeRegistry,
      @Nullable SoyAstCache astCache,
      SyntaxVersion declaredSyntaxVersion,
      List<? extends SoyFileSupplier> soyFileSuppliers,
      ParsePasses parsePasses,
      ErrorReporter errorReporter) {
    // By default, run all the parsing and checking passes.
    this(
        typeRegistry,
        astCache,
        declaredSyntaxVersion,
        soyFileSuppliers,
        errorReporter,
        checkNotNull(parsePasses),
        true);
  }

  /**
   * @param typeRegistry The type registry to resolve type names.
   * @param astCache The AST cache to use, if any.
   * @param declaredSyntaxVersion User-declared syntax version.
   * @param soyFileSuppliers The suppliers for the Soy files. Each must have a unique file name.
   * @param errorReporter For reporting errors during parsing.
   * @param parsingPasses The parsing passes to run.
   * @param doRunCheckingPasses Whether to run checking passes.
   */
  public SoyFileSetParser(
      SoyTypeRegistry typeRegistry,
      @Nullable SoyAstCache astCache,
      SyntaxVersion declaredSyntaxVersion,
      List<? extends SoyFileSupplier> soyFileSuppliers,
      ErrorReporter errorReporter,
      @Nullable ParsePasses parsingPasses,
      boolean doRunCheckingPasses) {
    Preconditions.checkArgument(
        (astCache == null) || (parsingPasses != null && doRunCheckingPasses),
        "AST caching is only allowed when all parsing and checking passes are enabled, to avoid "
            + "caching inconsistent versions");
    this.typeRegistry = typeRegistry;
    this.cache = astCache;
    this.declaredSyntaxVersion = declaredSyntaxVersion;
    this.soyFileSuppliers = soyFileSuppliers;
    this.errorReporter = errorReporter;
    verifyUniquePaths(soyFileSuppliers);

    this.parsingPasses = parsingPasses;
    this.doRunCheckingPasses = doRunCheckingPasses;
  }


  /**
   * Parses a set of Soy files, returning a structure containing the parse tree and any errors.
   */
  public ParseResult parse() {
    try {
      return parseWithVersions();
    } catch (IOException e) {
      // parse has 9 callers in SoyFileSet, and those are public API methods,
      // whose signatures it is infeasible to change.
      throw Throwables.propagate(e);
    }
  }


  /**
   * Ensures all SoyFileSuppliers have unique paths.
   */
  private static void verifyUniquePaths(Iterable<? extends SoyFileSupplier> soyFileSuppliers) {
    Set<String> paths = Sets.newHashSet();
    for (SoyFileSupplier supplier : soyFileSuppliers) {
      Preconditions.checkArgument(
          !paths.contains(supplier.getFilePath()), "Two file suppliers have the same path: %s",
          supplier.getFilePath());
      paths.add(supplier.getFilePath());
    }
  }


  /**
   * Parses a set of Soy files, returning a structure containing the parse tree and template
   * registry.
   */
  private ParseResult parseWithVersions() throws IOException {
    Preconditions.checkState(
        (cache == null) || (parsingPasses != null && doRunCheckingPasses),
        "AST caching is only allowed when all parsing and checking passes are enabled, to avoid "
            + "caching inconsistent versions");
    IdGenerator nodeIdGen =
        (cache != null) ? cache.getNodeIdGenerator() : new IncrementingIdGenerator();
    SoyFileSetNode soyTree = new SoyFileSetNode(nodeIdGen.genId(), nodeIdGen);
    boolean filesWereSkipped = false;
    for (SoyFileSupplier fileSupplier : soyFileSuppliers) {
      SoyFileSupplier.Version version = fileSupplier.getVersion();
      VersionedFile cachedFile = cache != null
          ? cache.get(fileSupplier.getFilePath(), version)
          : null;
      SoyFileNode node;
      if (cachedFile == null) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter IntelliJ
        synchronized (nodeIdGen) {  // Avoid using the same ID generator in multiple threads.
          node = parseSoyFileHelper(fileSupplier, nodeIdGen, typeRegistry);
          // TODO(user): implement error recovery and keep on trucking in order to display
          // as many errors as possible. Currently, the later passes just spew NPEs if run on
          // a malformed parse tree.
          if (node == null) {
            filesWereSkipped = true;
            continue;
          }
          if (parsingPasses != null) {
            // Run passes that are considered part of initial parsing.
            parsingPasses.run(node, nodeIdGen);
          }
        }
        if (doRunCheckingPasses) {
          // Run passes that check the tree.
          runSingleFileCheckingPasses(node);
        }
        if (cache != null) {
          cache.put(fileSupplier.getFilePath(), VersionedFile.of(node, version));
        }
      } else {
        node = cachedFile.file();
      }
      soyTree.addChild(node);
    }

    TemplateRegistry registry = new TemplateRegistry(soyTree, errorReporter);
    // Run passes that check the tree iff we successfully parsed every file.
    if (!filesWereSkipped && doRunCheckingPasses) {
      runWholeFileSetCheckingPasses(registry, soyTree);
    }
    return ParseResult.create(soyTree, registry);
  }

  /**
   * Private helper for {@code parseWithVersions()} to parse one Soy file.
   *
   * @param soyFileSupplier Supplier of the Soy file content and path.
   * @param nodeIdGen The generator of node ids.
   * @return The resulting parse tree for one Soy file and the version from which it was parsed.
   */
  private SoyFileNode parseSoyFileHelper(
      SoyFileSupplier soyFileSupplier, IdGenerator nodeIdGen, SoyTypeRegistry typeRegistry)
      throws IOException {
    try (Reader soyFileReader = soyFileSupplier.open()) {
      return new SoyFileParser(
          typeRegistry,
          nodeIdGen,
          soyFileReader,
          soyFileSupplier.getSoyFileKind(),
          soyFileSupplier.getFilePath(),
          errorReporter)
          .parseSoyFile();
    }
  }


  /**
   * Private helper for {@code parseWithVersion()} that operate on single files.
   */
  private void runSingleFileCheckingPasses(SoyFileNode fileNode) {
    new ReportSyntaxVersionErrorsVisitor(declaredSyntaxVersion, true, errorReporter)
        .exec(fileNode);
    // Check for errors based on inferred (as opposed to declared) required syntax version.
    SyntaxVersion inferredSyntaxVersion = new InferRequiredSyntaxVersionVisitor().exec(fileNode);
    if (inferredSyntaxVersion.num > declaredSyntaxVersion.num) {
      new ReportSyntaxVersionErrorsVisitor(inferredSyntaxVersion, false, errorReporter)
          .exec(fileNode);
    }
  }


  /**
   * Private helper for {@code parseWithVersions()} to run checking passes that require the whole
   * tree.
   */
  private void runWholeFileSetCheckingPasses(TemplateRegistry registry, SoyFileSetNode soyTree) {
    new CheckTemplateParamsVisitor(registry, declaredSyntaxVersion, errorReporter).exec(soyTree);
    new CheckDelegatesVisitor(registry, errorReporter).exec(soyTree);
    new CheckCallsVisitor(registry, errorReporter).exec(soyTree);
    new CheckCallingParamTypesVisitor(registry, errorReporter).exec(soyTree);
    new CheckTemplateVisibility(registry, errorReporter).exec(soyTree);
  }
}
