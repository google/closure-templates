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
import com.google.common.collect.Lists;
import com.google.template.soy.base.IdGenerator;
import com.google.template.soy.base.IncrementingIdGenerator;
import com.google.template.soy.base.SoyFileSupplier;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.parsepasses.CheckDelegatesVisitor;
import com.google.template.soy.parsepasses.CheckOverridesVisitor;
import com.google.template.soy.parsepasses.PrependNamespacesToCalleeNamesVisitor;
import com.google.template.soy.parsepasses.RewriteRemainderNodesVisitor;
import com.google.template.soy.parsepasses.VerifyPhnameAttrOnlyOnPlaceholdersVisitor;
import com.google.template.soy.sharedpasses.AssertSyntaxVersionV2Visitor;
import com.google.template.soy.sharedpasses.CheckSoyDocVisitor;
import com.google.template.soy.sharedpasses.RemoveHtmlCommentsVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;

import java.io.IOException;
import java.io.Reader;
import java.util.List;


/**
 * Static functions for parsing a set of Soy files into a {@link SoyFileSetNode}.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class SoyFileSetParser {


  /** The suppliers of the Soy files to parse. */
  private final List<SoyFileSupplier> soyFileSuppliers;

  /** Whether to run initial parsing passes. */
  private boolean doRunInitialParsingPasses;

  /** Whether to run checking passes. */
  private boolean doRunCheckingPasses;

  /** Whether to enforce V2 syntax. */
  private boolean doEnforceSyntaxVersionV2;

  /** Whether to check overrides. */
  private boolean doCheckOverrides;


  /**
   * @param soyFileSuppliers The suppliers for the Soy files.
   */
  public SoyFileSetParser(SoyFileSupplier... soyFileSuppliers) {
    this(Lists.newArrayList(soyFileSuppliers));
  }


  /**
   * @param soyFileSuppliers The suppliers for the Soy files.
   */
  public SoyFileSetParser(List<SoyFileSupplier> soyFileSuppliers) {
    this.soyFileSuppliers = soyFileSuppliers;
    this.doRunInitialParsingPasses = true;
    this.doRunCheckingPasses = true;
    this.doEnforceSyntaxVersionV2 = true;
    this.doCheckOverrides = true;
  }


  /**
   * Sets whether to run initial parsing passes. Returns self.
   */
  public SoyFileSetParser setDoRunInitialParsingPasses(boolean doRunInitialParsingPasses) {
    this.doRunInitialParsingPasses = doRunInitialParsingPasses;
    if (! doRunInitialParsingPasses) {
      this.doRunCheckingPasses = false;
      this.doEnforceSyntaxVersionV2 = false;
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
      this.doEnforceSyntaxVersionV2 = false;
      this.doCheckOverrides = false;
    }
    return this;
  }


  /**
   * Sets whether to enforce V2 syntax. Returns self.
   */
  public SoyFileSetParser setDoEnforceSyntaxVersionV2(boolean doEnforceSyntaxVersionV2) {
    this.doEnforceSyntaxVersionV2 = doEnforceSyntaxVersionV2;
    if (doEnforceSyntaxVersionV2) {
      Preconditions.checkState(doRunCheckingPasses);
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
   * Parses a set of Soy files and returns the parse tree and versions of the files.
   *
   * @return The resulting parse tree and the versions of the files read.
   * @throws SoySyntaxException If there is an error reading the file or a syntax error is found.
   */
  public Pair<SoyFileSetNode, List<SoyFileSupplier.Version>> parseWithVersions()
      throws SoySyntaxException {

    IdGenerator nodeIdGen = new IncrementingIdGenerator();
    SoyFileSetNode soyTree = new SoyFileSetNode(nodeIdGen.genId(), nodeIdGen);
    ImmutableList.Builder<SoyFileSupplier.Version> versions = ImmutableList.builder();

    for (SoyFileSupplier soyFileSupplier : soyFileSuppliers) {
      Pair<SoyFileNode, SoyFileSupplier.Version> fileAndVersion = parseSoyFileHelper(
          soyFileSupplier, nodeIdGen, soyFileSupplier.getPath());
      soyTree.addChild(fileAndVersion.first);
      versions.add(fileAndVersion.second);
    }

    // Run passes that are considered part of initial parsing.
    if (doRunInitialParsingPasses) {
      (new RewriteRemainderNodesVisitor()).exec(soyTree);
      (new PrependNamespacesToCalleeNamesVisitor()).exec(soyTree);

      // Run passes that check the tree.
      if (doRunCheckingPasses) {
        runCheckingPasses(soyTree);
      }
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
      SoyFileSupplier soyFileSupplier, IdGenerator nodeIdGen, String sourcePath)
      throws SoySyntaxException {

    Reader soyFileReader;
    SoyFileSupplier.Version version;
    try {
      Pair<Reader, SoyFileSupplier.Version> readerAndVersion = soyFileSupplier.open();
      soyFileReader = readerAndVersion.first;
      version = readerAndVersion.second;
    } catch (IOException ioe) {
      throw new SoySyntaxException(
          "Error opening Soy file " + soyFileSupplier.getPath() + ": " + ioe);
    }

    try {
      SoyFileNode soyFile =
          (new SoyFileParser(soyFileReader, nodeIdGen, sourcePath)).parseSoyFile();
      soyFile.setFilePath(soyFileSupplier.getPath());
      if (soyFileSupplier.hasChangedSince(version)) {
        throw new SoySyntaxException("Version skew in Soy file " + soyFileSupplier.getPath());
      }
      return Pair.of(soyFile, version);

    } catch (TokenMgrError tme) {
      throw (new SoySyntaxException(tme)).setFilePath(soyFileSupplier.getPath());
    } catch (ParseException pe) {
      throw (new SoySyntaxException(pe)).setFilePath(soyFileSupplier.getPath());
    } catch (SoySyntaxException sse) {
      throw sse.setFilePath(soyFileSupplier.getPath());

    } finally {
      // Close the Reader.
      try {
        soyFileReader.close();
      } catch (IOException ioe) {
        throw new SoySyntaxException(
            "Error closing Soy file " + soyFileSupplier.getPath() + ": " + ioe);
      }
    }
  }


  /**
   * Private helper for {@code parseWithVersions()} to run checking passes.
   */
  private void runCheckingPasses(SoyFileSetNode soyTree) {

    if (doEnforceSyntaxVersionV2) {
      (new AssertSyntaxVersionV2Visitor()).exec(soyTree);
    } else {
      (new RemoveHtmlCommentsVisitor()).exec(soyTree);
    }
    (new VerifyPhnameAttrOnlyOnPlaceholdersVisitor()).exec(soyTree);
    (new CheckSoyDocVisitor(doEnforceSyntaxVersionV2)).exec(soyTree);
    if (doCheckOverrides) {
      (new CheckOverridesVisitor()).exec(soyTree);
    }
    (new CheckDelegatesVisitor()).exec(soyTree);
  }

}
