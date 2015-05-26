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

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.parsepasses.CheckCallsVisitor;
import com.google.template.soy.parsepasses.CheckDelegatesVisitor;
import com.google.template.soy.parsepasses.InferRequiredSyntaxVersionVisitor;
import com.google.template.soy.parsepasses.ReplaceHasDataFunctionVisitor;
import com.google.template.soy.parsepasses.RewriteGenderMsgsVisitor;
import com.google.template.soy.parsepasses.RewriteRemainderNodesVisitor;
import com.google.template.soy.parsepasses.SetDefaultForDelcallAllowsEmptyDefaultVisitor;
import com.google.template.soy.parsepasses.SetFullCalleeNamesVisitor;
import com.google.template.soy.parsepasses.VerifyPhnameAttrOnlyOnPlaceholdersVisitor;
import com.google.template.soy.shared.SoyAstCache;
import com.google.template.soy.shared.SoyAstCache.VersionedFile;
import com.google.template.soy.sharedpasses.CheckCallingParamTypesVisitor;
import com.google.template.soy.sharedpasses.CheckTemplateParamsVisitor;
import com.google.template.soy.sharedpasses.CheckTemplateVisibility;
import com.google.template.soy.sharedpasses.RemoveHtmlCommentsVisitor;
import com.google.template.soy.sharedpasses.ReportSyntaxVersionErrorsVisitor;
import com.google.template.soy.sharedpasses.ResolveExpressionTypesVisitor;
import com.google.template.soy.sharedpasses.ResolveNamesVisitor;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
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

  private static final SoyError VERSION_SKEW_IN_SOY_FILE =
      SoyError.of("Version skew in Soy file {0}");

  /** The type registry to resolve type names. */
  private final SoyTypeRegistry typeRegistry;

  /** Optional file cache. */
  @Nullable private final SoyAstCache cache;

  /** User-declared syntax version. */
  private final SyntaxVersion declaredSyntaxVersion;

  /** The suppliers of the Soy files to parse. */
  private final List<SoyFileSupplier> soyFileSuppliers;

  /** Whether to run initial parsing passes. */
  private final boolean doRunInitialParsingPasses;

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
      List<SoyFileSupplier> soyFileSuppliers,
      ErrorReporter errorReporter) {
    // By default, run all the parsing and checking passes.
    this(
        typeRegistry, astCache, declaredSyntaxVersion, soyFileSuppliers, errorReporter, true, true);
  }

  /**
   * @param typeRegistry The type registry to resolve type names.
   * @param astCache The AST cache to use, if any.
   * @param declaredSyntaxVersion User-declared syntax version.
   * @param soyFileSuppliers The suppliers for the Soy files. Each must have a unique file name.
   * @param errorReporter For reporting errors during parsing.
   * @param doRunInitialParsingPasses Whether to run initial parsing passes.
   * @param doRunCheckingPasses Whether to run checking passes.
   */
  SoyFileSetParser(
      SoyTypeRegistry typeRegistry,
      @Nullable SoyAstCache astCache,
      SyntaxVersion declaredSyntaxVersion,
      List<SoyFileSupplier> soyFileSuppliers,
      ErrorReporter errorReporter,
      boolean doRunInitialParsingPasses,
      boolean doRunCheckingPasses) {

    this.typeRegistry = typeRegistry;
    this.cache = astCache;
    this.declaredSyntaxVersion = declaredSyntaxVersion;
    this.soyFileSuppliers = soyFileSuppliers;
    this.errorReporter = errorReporter;
    verifyUniquePaths(soyFileSuppliers);

    this.doRunInitialParsingPasses = doRunInitialParsingPasses;
    this.doRunCheckingPasses = doRunCheckingPasses;
  }


  /**
   * Parses a set of Soy files, returning a structure containing the parse tree and any errors.
   */
  public SoyFileSetNode parse() {
    return parseWithVersions();
  }


  /**
   * Ensures all SoyFileSuppliers have unique paths.
   */
  private static void verifyUniquePaths(Iterable<SoyFileSupplier> soyFileSuppliers) {
    Set<String> paths = Sets.newHashSet();
    for (SoyFileSupplier supplier : soyFileSuppliers) {
      Preconditions.checkArgument(
          !paths.contains(supplier.getFilePath()), "Two file suppliers have the same path: %s",
          supplier.getFilePath());
      paths.add(supplier.getFilePath());
    }
  }


  /**
   * Parses a set of Soy files, returning a structure containing the parse tree and any errors.
   */
  private SoyFileSetNode parseWithVersions() {
    Preconditions.checkState((cache == null) || (doRunInitialParsingPasses && doRunCheckingPasses),
        "AST caching is only allowed when all parsing and checking passes are enabled, to avoid " +
            "caching inconsistent versions");
    IdGenerator nodeIdGen =
        (cache != null) ? cache.getNodeIdGenerator() : new IncrementingIdGenerator();
    SoyFileSetNode soyTree = new SoyFileSetNode(nodeIdGen.genId(), nodeIdGen);

    for (SoyFileSupplier soyFileSupplier : soyFileSuppliers) {
      VersionedFile fileAndVersion = (cache != null) ? cache.get(soyFileSupplier) : null;
      if (fileAndVersion == null) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter IntelliJ
        synchronized (nodeIdGen) {  // Avoid using the same ID generator in multiple threads.
          fileAndVersion = parseSoyFileHelper(soyFileSupplier, nodeIdGen, typeRegistry);
          // TODO(user): implement error recovery and keep on trucking in order to display
          // as many errors as possible. Currently, the later passes just spew NPEs if run on
          // a malformed parse tree.
          if (fileAndVersion.file() == null) {
            return soyTree;
          }
          if (doRunInitialParsingPasses) {
            // Run passes that are considered part of initial parsing.
            runSingleFileParsingPasses(fileAndVersion.file(), nodeIdGen);
          }
        }
        if (doRunCheckingPasses) {
          // Run passes that check the tree.
          runSingleFileCheckingPasses(fileAndVersion.file());
        }
        if (cache != null) {
          cache.put(soyFileSupplier, fileAndVersion);
        }
      }
      if (fileAndVersion.file() != null) {
        soyTree.addChild(fileAndVersion.file());
      }
    }

    // Run passes that check the tree.
    if (doRunCheckingPasses) {
      runWholeFileSetCheckingPasses(soyTree);
    }

    return soyTree;
  }


  /**
   * Private helper for {@code parseWithVersions()} to parse one Soy file.
   *
   * @param soyFileSupplier Supplier of the Soy file content and path.
   * @param nodeIdGen The generator of node ids.
   * @return The resulting parse tree for one Soy file and the version from which it was parsed.
   */
  private VersionedFile parseSoyFileHelper(
      SoyFileSupplier soyFileSupplier, IdGenerator nodeIdGen, SoyTypeRegistry typeRegistry) {

    String filePath = soyFileSupplier.getFilePath();

    SoyFileSupplier.Version version = soyFileSupplier.getVersion();
    try (Reader soyFileReader = soyFileSupplier.open()) {
      SoyFileNode soyFileNode = new SoyFileParser(
          typeRegistry,
          nodeIdGen,
          soyFileReader,
          soyFileSupplier.getSoyFileKind(),
          filePath,
          errorReporter)
          .parseSoyFile();
      if (soyFileSupplier.hasChangedSince(version)) {
        errorReporter.report(
            new SourceLocation(filePath, -1, -1, -1, -1), VERSION_SKEW_IN_SOY_FILE, filePath);
      }
      return VersionedFile.of(soyFileNode, version);
    } catch (IOException e) {
      throw SoySyntaxException.createCausedWithoutMetaInfo(
          "Error opening/closing Soy file " + soyFileSupplier.getFilePath(), e);
    }
  }


  /**
   * Runs initial parsing passes that work on a file-by-file basis.
   */
  private void runSingleFileParsingPasses(SoyFileNode fileNode, IdGenerator nodeIdGen) {
    // Note: RewriteGenderMsgsVisitor must be run first due to the assertion in
    // MsgNode.getAllExprUnions().
    new RewriteGenderMsgsVisitor(nodeIdGen, errorReporter)
        .exec(fileNode);
    new RewriteRemainderNodesVisitor(errorReporter)
        .exec(fileNode);
    new ReplaceHasDataFunctionVisitor(declaredSyntaxVersion, errorReporter)
        .exec(fileNode);
    new SetFullCalleeNamesVisitor(errorReporter)
        .exec(fileNode);
    new SetDefaultForDelcallAllowsEmptyDefaultVisitor(declaredSyntaxVersion, errorReporter)
        .exec(fileNode);
    if (declaredSyntaxVersion == SyntaxVersion.V1_0) {
      new RemoveHtmlCommentsVisitor(nodeIdGen, errorReporter).exec(fileNode);
    }
    new ResolveNamesVisitor(declaredSyntaxVersion, errorReporter)
        .exec(fileNode);
    new ResolveExpressionTypesVisitor(typeRegistry, declaredSyntaxVersion, errorReporter)
        .exec(fileNode);
  }


  /**
   * Private helper for {@code parseWithVersion()} that operate on single files.
   */
  private void runSingleFileCheckingPasses(SoyFileNode fileNode) {
    new VerifyPhnameAttrOnlyOnPlaceholdersVisitor(errorReporter)
        .exec(fileNode);
    new ReportSyntaxVersionErrorsVisitor(declaredSyntaxVersion, true, errorReporter)
        .exec(fileNode);
    // Check for errors based on inferred (as opposed to declared) required syntax version.
    SyntaxVersion inferredSyntaxVersion = new InferRequiredSyntaxVersionVisitor(errorReporter)
        .exec(fileNode);
    if (inferredSyntaxVersion.num > declaredSyntaxVersion.num) {
      new ReportSyntaxVersionErrorsVisitor(inferredSyntaxVersion, false, errorReporter)
          .exec(fileNode);
    }
  }


  /**
   * Private helper for {@code parseWithVersions()} to run checking passes that require the whole
   * tree.
   */
  private void runWholeFileSetCheckingPasses(SoyFileSetNode soyTree) {
    new CheckTemplateParamsVisitor(declaredSyntaxVersion, errorReporter)
        .exec(soyTree);
    new CheckDelegatesVisitor(errorReporter)
        .exec(soyTree);
    new CheckCallsVisitor(errorReporter)
        .exec(soyTree);
    new CheckCallingParamTypesVisitor(errorReporter)
        .exec(soyTree);
    new CheckTemplateVisibility(errorReporter)
        .exec(soyTree);
  }
}
