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

package com.google.template.soy.soyparse;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.parsepasses.CheckCallsVisitor;
import com.google.template.soy.parsepasses.CheckDelegatesVisitor;
import com.google.template.soy.parsepasses.CheckOverridesVisitor;
import com.google.template.soy.parsepasses.InferRequiredSyntaxVersionVisitor;
import com.google.template.soy.parsepasses.ReplaceHasDataFunctionVisitor;
import com.google.template.soy.parsepasses.RewriteGenderMsgsVisitor;
import com.google.template.soy.parsepasses.RewriteNullCoalescingOpVisitor;
import com.google.template.soy.parsepasses.RewriteRemainderNodesVisitor;
import com.google.template.soy.parsepasses.SetDefaultForDelcallAllowsEmptyDefaultVisitor;
import com.google.template.soy.parsepasses.SetFullCalleeNamesVisitor;
import com.google.template.soy.parsepasses.VerifyPhnameAttrOnlyOnPlaceholdersVisitor;
import com.google.template.soy.shared.SoyAstCache;
import com.google.template.soy.sharedpasses.CheckCallingParamTypesVisitor;
import com.google.template.soy.sharedpasses.CheckSoyDocVisitor;
import com.google.template.soy.sharedpasses.CheckTemplateVisibility;
import com.google.template.soy.sharedpasses.RemoveHtmlCommentsVisitor;
import com.google.template.soy.sharedpasses.ReportSyntaxVersionErrorsVisitor;
import com.google.template.soy.sharedpasses.ResolveExpressionTypesVisitor;
import com.google.template.soy.sharedpasses.ResolveNamesVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.types.SoyTypeRegistry;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;


/**
 * Static functions for parsing a set of Soy files into a {@link SoyFileSetNode}.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class SoyFileSetParser {


  /** The type registry to resolve type names. */
  private final SoyTypeRegistry typeRegistry;

  /** Optional file cache. */
  private SoyAstCache cache;

  /** User-declared syntax version. */
  private SyntaxVersion declaredSyntaxVersion;

  /** The suppliers of the Soy files to parse. */
  private final List<SoyFileSupplier> soyFileSuppliers;

  /** Whether to run initial parsing passes. */
  private boolean doRunInitialParsingPasses;

  /** Whether to run checking passes. */
  private boolean doRunCheckingPasses;

  /** Whether to check overrides. */
  private boolean doCheckOverrides;


  /**
   * @param typeRegistry The type registry to resolve type names.
   * @param astCache The AST cache to use, if any.
   * @param declaredSyntaxVersion User-declared syntax version.
   * @param soyFileSuppliers The suppliers for the Soy files.
   */
  public SoyFileSetParser(
      SoyTypeRegistry typeRegistry, @Nullable SoyAstCache astCache,
      SyntaxVersion declaredSyntaxVersion, SoyFileSupplier... soyFileSuppliers) {
    this(typeRegistry, astCache, declaredSyntaxVersion, Arrays.asList(soyFileSuppliers));
  }


  /**
   * @param typeRegistry The type registry to resolve type names.
   * @param astCache The AST cache to use, if any.
   * @param declaredSyntaxVersion User-declared syntax version.
   * @param soyFileSuppliers The suppliers for the Soy files. Each must have a unique file name.
   */
  public SoyFileSetParser(
      SoyTypeRegistry typeRegistry, @Nullable SoyAstCache astCache,
      SyntaxVersion declaredSyntaxVersion, List<SoyFileSupplier> soyFileSuppliers) {

    this.typeRegistry = typeRegistry;
    this.cache = astCache;
    this.declaredSyntaxVersion = declaredSyntaxVersion;
    this.soyFileSuppliers = soyFileSuppliers;
    verifyUniquePaths(soyFileSuppliers);

    // By default, do everything.
    this.doRunInitialParsingPasses = true;
    this.doRunCheckingPasses = true;
    this.doCheckOverrides = true;
  }


  /**
   * Sets whether to run initial parsing passes. Returns self.
   */
  public SoyFileSetParser setDoRunInitialParsingPasses(boolean doRunInitialParsingPasses) {
    this.doRunInitialParsingPasses = doRunInitialParsingPasses;
    if (! doRunInitialParsingPasses) {
      this.doRunCheckingPasses = false;
      this.doCheckOverrides = false;
    }
    return this;
  }


  /**
   * Sets whether to run checking passes. Returns self.
   */
  public SoyFileSetParser setDoRunCheckingPasses(boolean doRunCheckingPasses) {
    this.doRunCheckingPasses = doRunCheckingPasses;
    if (doRunCheckingPasses) {
      Preconditions.checkState(doRunInitialParsingPasses);
    } else {
      this.doCheckOverrides = false;
    }
    return this;
  }


  /**
   * Sets whether to check overrides. Returns self.
   */
  public SoyFileSetParser setDoCheckOverrides(boolean doCheckOverrides) {
    this.doCheckOverrides = doCheckOverrides;
    if (doCheckOverrides) {
      Preconditions.checkState(doRunCheckingPasses);
    }
    return this;
  }


  /**
   * Parses a set of Soy files and returns the parse tree.
   *
   * @return The resulting parse tree.
   * @throws SoySyntaxException If there is an error reading the file or a syntax error is found.
   */
  public SoyFileSetNode parse() throws SoySyntaxException {
    return parseWithVersions().first;
  }


  /**
   * Ensures all SoyFileSuppliers have unique paths.
   */
  private static void verifyUniquePaths(Iterable<SoyFileSupplier> soyFileSuppliers) {
    Set<String> paths = Sets.newHashSet();
    for (SoyFileSupplier supplier : soyFileSuppliers) {
      Preconditions.checkArgument(!paths.contains(supplier.getFilePath()),
          "Two file suppliers have the same path: " + supplier.getFilePath());
      paths.add(supplier.getFilePath());
    }
  }


  /**
   * Parses a set of Soy files and returns the parse tree and versions of the files.
   *
   * @return The resulting parse tree and the versions of the files read.
   * @throws SoySyntaxException If there is an error reading the file or a syntax error is found.
   */
  public Pair<SoyFileSetNode, List<SoyFileSupplier.Version>> parseWithVersions()
      throws SoySyntaxException {

    Preconditions.checkState((cache == null) || (doRunInitialParsingPasses && doRunCheckingPasses),
        "AST caching is only allowed when all parsing and checking passes are enabled, to avoid " +
            "caching inconsistent versions");
    IdGenerator nodeIdGen =
        (cache != null) ? cache.getNodeIdGenerator() : new IncrementingIdGenerator();
    SoyFileSetNode soyTree = new SoyFileSetNode(nodeIdGen.genId(), nodeIdGen);
    ImmutableList.Builder<SoyFileSupplier.Version> versions = ImmutableList.builder();

    for (SoyFileSupplier soyFileSupplier : soyFileSuppliers) {
      Pair<SoyFileNode, SoyFileSupplier.Version> fileAndVersion =
          (cache != null) ? cache.get(soyFileSupplier) : null;
      if (fileAndVersion == null) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter IntelliJ
        synchronized (nodeIdGen) {  // Avoid using the same ID generator in multiple threads.
          fileAndVersion = parseSoyFileHelper(soyFileSupplier, nodeIdGen, typeRegistry);
          if (doRunInitialParsingPasses) {
            // Run passes that are considered part of initial parsing.
            runSingleFileParsingPasses(fileAndVersion.first, nodeIdGen);
          }
        }
        if (doRunCheckingPasses) {
          // Run passes that check the tree.
          runSingleFileCheckingPasses(fileAndVersion.first);
        }
        if (cache != null) {
          cache.put(soyFileSupplier, fileAndVersion.second, fileAndVersion.first);
        }
      }
      soyTree.addChild(fileAndVersion.first);
      versions.add(fileAndVersion.second);
    }

    // Run passes that check the tree.
    if (doRunCheckingPasses) {
      runWholeFileSetCheckingPasses(soyTree);
    }

    return Pair.of(soyTree, (List<SoyFileSupplier.Version>) versions.build());
  }


  /**
   * Private helper for {@code parseWithVersions()} to parse one Soy file.
   *
   * @param soyFileSupplier Supplier of the Soy file content and path.
   * @param nodeIdGen The generator of node ids.
   * @return The resulting parse tree for one Soy file and the version from which it was parsed.
   * @throws SoySyntaxException If there is an error reading the file or a syntax error is found.
   */
  private static Pair<SoyFileNode, SoyFileSupplier.Version> parseSoyFileHelper(
      SoyFileSupplier soyFileSupplier, IdGenerator nodeIdGen, SoyTypeRegistry typeRegistry)
      throws SoySyntaxException {

    String filePath = soyFileSupplier.getFilePath();

    Reader soyFileReader;
    SoyFileSupplier.Version version;
    try {
      Pair<Reader, SoyFileSupplier.Version> readerAndVersion = soyFileSupplier.open();
      soyFileReader = readerAndVersion.first;
      version = readerAndVersion.second;
    } catch (IOException ioe) {
      throw SoySyntaxException.createWithoutMetaInfo(
          "Error opening Soy file " + filePath + ": " + ioe);
    }

    try {
      SoyFileNode soyFile = (new SoyFileParser(
          typeRegistry, nodeIdGen, soyFileReader, soyFileSupplier.getSoyFileKind(), filePath))
          .parseSoyFile();
      if (soyFileSupplier.hasChangedSince(version)) {
        throw SoySyntaxException.createWithoutMetaInfo("Version skew in Soy file " + filePath);
      }
      return Pair.of(soyFile, version);

    } catch (TokenMgrError tme) {
      throw SoySyntaxException.createCausedWithMetaInfo(
          null, tme, null, soyFileSupplier.getFilePath(), null);
    } catch (ParseException pe) {
      throw SoySyntaxException.createCausedWithMetaInfo(
          null, pe, null, soyFileSupplier.getFilePath(), null);
    } catch (SoySyntaxException sse) {
      throw sse.associateMetaInfo(null, soyFileSupplier.getFilePath(), null);

    } finally {
      // Close the Reader.
      try {
        soyFileReader.close();
      } catch (IOException ioe) {
        //noinspection ThrowFromFinallyBlock IntelliJ
        throw SoySyntaxException.createWithoutMetaInfo(
            "Error closing Soy file " + soyFileSupplier.getFilePath() + ": " + ioe);
      }
    }
  }


  /**
   * Runs initial parsing passes that work on a file-by-file basis.
   */
  private void runSingleFileParsingPasses(SoyFileNode fileNode, IdGenerator nodeIdGen) {
    // Note: RewriteGenderMsgsVisitor must be run first due to the assertion in
    // MsgNode.getAllExprUnions().
    (new RewriteGenderMsgsVisitor(nodeIdGen)).exec(fileNode);
    (new RewriteRemainderNodesVisitor()).exec(fileNode);
    (new ReplaceHasDataFunctionVisitor(declaredSyntaxVersion)).exec(fileNode);
    (new RewriteNullCoalescingOpVisitor()).exec(fileNode);
    (new SetFullCalleeNamesVisitor()).exec(fileNode);
    (new SetDefaultForDelcallAllowsEmptyDefaultVisitor(declaredSyntaxVersion)).exec(fileNode);
    if (declaredSyntaxVersion == SyntaxVersion.V1_0) {
      (new RemoveHtmlCommentsVisitor(nodeIdGen)).exec(fileNode);
    }
    (new ResolveNamesVisitor(declaredSyntaxVersion)).exec(fileNode);
    (new ResolveExpressionTypesVisitor(typeRegistry, declaredSyntaxVersion)).exec(fileNode);
  }


  /**
   * Private helper for {@code parseWithVersion()} that operate on single files.
   */
  private void runSingleFileCheckingPasses(SoyFileNode fileNode) {

    (new VerifyPhnameAttrOnlyOnPlaceholdersVisitor()).exec(fileNode);
    (new ReportSyntaxVersionErrorsVisitor(declaredSyntaxVersion, true)).exec(fileNode);

    // Check for errors based on inferred (as opposed to declared) required syntax version.
    SyntaxVersion inferredSyntaxVersion = (new InferRequiredSyntaxVersionVisitor()).exec(fileNode);
    if (inferredSyntaxVersion.num > declaredSyntaxVersion.num) {
      (new ReportSyntaxVersionErrorsVisitor(inferredSyntaxVersion, false)).exec(fileNode);
    }
  }


  /**
   * Private helper for {@code parseWithVersions()} to run checking passes that require the whole
   * tree.
   */
  private void runWholeFileSetCheckingPasses(SoyFileSetNode soyTree) {
    (new CheckSoyDocVisitor(declaredSyntaxVersion)).exec(soyTree);
    if (doCheckOverrides) {
      (new CheckOverridesVisitor()).exec(soyTree);
    }
    (new CheckDelegatesVisitor()).exec(soyTree);
    (new CheckCallsVisitor()).exec(soyTree);
    (new CheckCallingParamTypesVisitor()).exec(soyTree);
    (new CheckTemplateVisibility()).exec(soyTree);
  }
}
