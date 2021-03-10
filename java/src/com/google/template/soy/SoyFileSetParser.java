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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.FixedIdGenerator;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.css.CssRegistry;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.passes.PassManager;
import com.google.template.soy.shared.SoyAstCache;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.CompilationUnit;
import com.google.template.soy.soytree.FileSetTemplateRegistry;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileP;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyTypeRegistry;
import java.io.IOException;
import java.io.Reader;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Static functions for parsing a set of Soy files into a {@link SoyFileSetNode}.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@AutoValue
public abstract class SoyFileSetParser {

  /**
   * Simple tuple of un an-evaluatied compilation unit containing information about dependencies.
   */
  @AutoValue
  public abstract static class CompilationUnitAndKind {
    public static CompilationUnitAndKind create(
        SoyFileKind fileKind, SourceFilePath filePath, CompilationUnit compilationUnit) {
      // sanity check
      checkArgument(
          fileKind != SoyFileKind.SRC, "compilation units should only represent dependencies");
      return new AutoValue_SoyFileSetParser_CompilationUnitAndKind(
          fileKind, filePath, compilationUnit);
    }

    abstract SoyFileKind fileKind();

    abstract SourceFilePath filePath();

    abstract CompilationUnit compilationUnit();
  }

  /** A simple tuple for the result of a parse operation. */
  public static class ParseResult {
    private final SoyFileSetNode soyTree;

    /** The TemplateRegistry, which is guaranteed to be present if the error reporter is empty. */
    private final Optional<TemplateRegistry> registry;

    private final Optional<CssRegistry> cssRegistry;

    static ParseResult create(
        SoyFileSetNode soyTree,
        Optional<TemplateRegistry> registry,
        Optional<CssRegistry> cssRegistry) {
      return new ParseResult(soyTree, registry, cssRegistry);
    }

    ParseResult(
        SoyFileSetNode soyTree,
        Optional<TemplateRegistry> registry,
        Optional<CssRegistry> cssRegistry) {
      this.soyTree = soyTree;
      this.registry = registry;
      this.cssRegistry = cssRegistry;
    }

    public SoyFileSetNode fileSet() {
      return soyTree;
    }

    /**
     * Gets the TemplateRegistry, which is guaranteed to be present if the error reporter is empty.
     */
    public final TemplateRegistry registry() {
      return registry.orElseThrow(
          () ->
              new IllegalStateException(
                  "No template registry, did you forget to check the error reporter?"));
    }

    public final CssRegistry cssRegistry() {
      return cssRegistry.orElseThrow(
          () ->
              new IllegalStateException(
                  "No template registry, did you forget to check the error reporter?"));
    }

    public final boolean hasRegistry() {
      return registry.isPresent();
    }
  }

  public static Builder newBuilder() {
    return new AutoValue_SoyFileSetParser.Builder();
  }

  /** Optional file cache. */
  @Nullable
  abstract SoyAstCache cache();
  /** Files to parse. Each must have a unique file name. */
  public abstract ImmutableMap<SourceFilePath, SoyFileSupplier> soyFileSuppliers();

  abstract ImmutableList<CompilationUnitAndKind> compilationUnits();

  abstract PassManager passManager();

  abstract ErrorReporter errorReporter();

  public abstract SoyTypeRegistry typeRegistry();

  public abstract Optional<CssRegistry> cssRegistry();

  /** Builder for {@link SoyFileSetParser}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setCache(SoyAstCache cache);

    public abstract Builder setSoyFileSuppliers(
        ImmutableMap<SourceFilePath, SoyFileSupplier> soyFileSuppliers);

    public abstract Builder setCompilationUnits(
        ImmutableList<CompilationUnitAndKind> compilationUnits);

    public abstract Builder setPassManager(PassManager passManager);

    public abstract Builder setErrorReporter(ErrorReporter errorReporter);

    public abstract Builder setTypeRegistry(SoyTypeRegistry typeRegistry);

    public abstract Builder setCssRegistry(Optional<CssRegistry> cssRegistry);

    public abstract SoyFileSetParser build();
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
    SoyFileSetNode soyTree = new SoyFileSetNode(new IncrementingIdGenerator());
    boolean filesWereSkipped = false;
    // Use a fixed id generator for parsing.  This ensures that the ids assigned to nodes are not
    // dependent on whether or not there was a cache hit.  So we parse with fixed ids and then
    // assign ids later.
    // TODO(lukes): this is a good argument for eliminating the id system.  They are only used to
    // help with assigning unique names in the js and python backends.  We should just move this
    // into those backends
    FixedIdGenerator fixedIdGenerator = new FixedIdGenerator(-1);
    for (SoyFileSupplier fileSupplier : soyFileSuppliers().values()) {
      SoyFileSupplier.Version version = fileSupplier.getVersion();
      SoyFileNode node = cache() != null ? cache().get(fileSupplier.getFilePath(), version) : null;
      if (node == null) {
        node = parseSoyFileHelper(fileSupplier, fixedIdGenerator);
        // TODO(b/19269289): implement error recovery and keep on trucking in order to display
        // as many errors as possible. Currently, the later passes just spew NPEs if run on
        // a malformed parse tree.
        if (node == null) {
          filesWereSkipped = true;
          continue;
        }
        // Run passes that are considered part of initial parsing.
        passManager().runParsePasses(node, fixedIdGenerator);
        // Run passes that check the tree.
        if (cache() != null) {
          cache().put(fileSupplier.getFilePath(), version, node);
        }
      }
      // Make a copy here and assign ids.
      // We need to make a copy because we may have stored a version in the cache or taken a version
      // out of the cache, the cache does not make defensive copies, so we need to.
      // Also, we need to assign ids because we performed all parsing with the fixed id generator.
      // In theory we could optimize the no cache case and avoid this copy, but that is an
      // increasingly uncommon configuration.
      node = SoyTreeUtils.cloneWithNewIds(node, soyTree.getNodeIdGenerator());
      soyTree.addChild(node);
    }

    // If we couldn't parse all the files, we can't run the fileset passes or build the template
    // registry.
    if (filesWereSkipped) {
      return ParseResult.create(soyTree, Optional.empty(), Optional.empty());
    }

    // Build the template registry for the file set & its dependencies.
    FileSetTemplateRegistry.Builder builder = FileSetTemplateRegistry.builder(errorReporter());

    // Register templates for each file in the dependencies.
    for (CompilationUnitAndKind unit : compilationUnits()) {
      for (SoyFileP file : unit.compilationUnit().getFileList()) {
        builder.addTemplates(
            TemplateMetadataSerializer.templatesFromSoyFileP(
                file, unit.fileKind(), typeRegistry(), unit.filePath(), errorReporter()));
      }
    }

    // Run the passes that we need to finish building the template registry.
    FileSetTemplateRegistry partialRegistryForDeps = builder.build();
    passManager().runPasses(soyTree, partialRegistryForDeps);

    TemplateRegistry finalTemplateRegistry = passManager().getFinalTemplateRegistry();
    return ParseResult.create(soyTree, Optional.ofNullable(finalTemplateRegistry), cssRegistry());
  }

  /**
   * Private helper for {@code parseWithVersions()} to parse one Soy file.
   *
   * @param soyFileSupplier Supplier of the Soy file content and path.
   * @param nodeIdGen The generator of node ids.
   * @return The resulting parse tree for one Soy file and the version from which it was parsed.
   */
  private SoyFileNode parseSoyFileHelper(SoyFileSupplier soyFileSupplier, IdGenerator nodeIdGen)
      throws IOException {
    try (Reader soyFileReader = soyFileSupplier.open()) {
      String filePath = soyFileSupplier.getFilePath().path();
      // TODO(lukes): this logic should move into relevant SoyFileSupplier implementations
      int lastBangIndex = filePath.lastIndexOf('!');
      if (lastBangIndex != -1) {
        // This is a resource in a JAR file. Only keep everything after the bang.
        filePath = filePath.substring(lastBangIndex + 1);
      }
      // Think carefully before adding new parameters to the parser.
      // Currently the only parameters are the id generator, the file, and the errorReporter.
      // This ensures that the file be cached without worrying about other compiler inputs.
      return new SoyFileParser(
              nodeIdGen, soyFileReader, SourceFilePath.create(filePath), errorReporter())
          .parseSoyFile();
    }
  }
}
