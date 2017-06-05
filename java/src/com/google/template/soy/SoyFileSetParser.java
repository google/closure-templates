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
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.passes.PassManager;
import com.google.template.soy.shared.SoyAstCache;
import com.google.template.soy.shared.SoyAstCache.VersionedFile;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyTypeRegistry;
import java.io.IOException;
import java.io.Reader;
import javax.annotation.Nullable;

/**
 * Static functions for parsing a set of Soy files into a {@link SoyFileSetNode}.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
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

  /** Optional file cache. */
  @Nullable private final SoyAstCache cache;

  /** Files to parse. */
  private final ImmutableMap<String, ? extends SoyFileSupplier> soyFileSuppliers;

  private final PassManager passManager;

  private final ErrorReporter errorReporter;

  /**
   * @param astCache The AST cache to use, if any.
   * @param soyFileSuppliers The suppliers for the Soy files. Each must have a unique file name.
   */
  public SoyFileSetParser(
      @Nullable SoyAstCache astCache,
      ImmutableMap<String, ? extends SoyFileSupplier> soyFileSuppliers,
      PassManager passManager,
      ErrorReporter errorReporter) {
    this.cache = astCache;
    this.soyFileSuppliers = checkNotNull(soyFileSuppliers);
    this.errorReporter = checkNotNull(errorReporter);
    this.passManager = checkNotNull(passManager);
  }

  /** Parses a set of Soy files, returning a structure containing the parse tree and any errors. */
  public ParseResult parse() {
    try {
      return parseWithVersions();
    } catch (IOException e) {
      // parse has 9 callers in SoyFileSet, and those are public API methods,
      // whose signatures it is infeasible to change.
      throw new RuntimeException(e);
    }
  }

  /**
   * Parses a set of Soy files, returning a structure containing the parse tree and template
   * registry.
   */
  private ParseResult parseWithVersions() throws IOException {
    IdGenerator nodeIdGen =
        (cache != null) ? cache.getNodeIdGenerator() : new IncrementingIdGenerator();
    SoyFileSetNode soyTree = new SoyFileSetNode(nodeIdGen.genId(), nodeIdGen);
    boolean filesWereSkipped = false;
    // TODO(lukes): there are other places in the compiler (autoescaper) which may use the id
    // generator but fail to lock on it.  Eliminate the id system to avoid this whole issue.
    synchronized (nodeIdGen) { // Avoid using the same ID generator in multiple threads.
      for (SoyFileSupplier fileSupplier : soyFileSuppliers.values()) {
        SoyFileSupplier.Version version = fileSupplier.getVersion();
        VersionedFile cachedFile =
            cache != null ? cache.get(fileSupplier.getFilePath(), version) : null;
        SoyFileNode node;
        if (cachedFile == null) {
          node = parseSoyFileHelper(fileSupplier, nodeIdGen, passManager.getTypeRegistry());
          // TODO(user): implement error recovery and keep on trucking in order to display
          // as many errors as possible. Currently, the later passes just spew NPEs if run on
          // a malformed parse tree.
          if (node == null) {
            filesWereSkipped = true;
            continue;
          }
          // Run passes that are considered part of initial parsing.
          passManager.runSingleFilePasses(node, nodeIdGen);
          // Run passes that check the tree.
          if (cache != null) {
            cache.put(fileSupplier.getFilePath(), VersionedFile.of(node, version));
          }
        } else {
          node = cachedFile.file();
        }
        soyTree.addChild(node);
      }

      TemplateRegistry registry;
      // Run passes that check the tree iff we successfully parsed every file.
      if (!filesWereSkipped) {
        registry = passManager.runWholeFilesetPasses(soyTree);
      } else {
        registry = new TemplateRegistry(soyTree, errorReporter);
      }
      return ParseResult.create(soyTree, registry);
    }
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
}
