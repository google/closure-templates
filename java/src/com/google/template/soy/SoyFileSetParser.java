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
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.passes.PassManager;
import com.google.template.soy.shared.SoyAstCache;
import com.google.template.soy.shared.SoyAstCache.VersionedFile;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.CompilationUnit;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyTypeRegistry;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
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
   * This interface can be implemented by a SoyFileSupplier to provide access to an AST.
   *
   * <p>This is a hack to support our goal of caching AST objects.
   *
   * <p>Currently SoyFileSet is responsible for collecting all compiler configuration and
   * constructing a PassManager. Then it hands the PassManager and all the SoyFileSuppliers to this
   * class to actually parse the source files and invoke the passes.
   *
   * <p>This set up is problematic for the caching strategy implemented by SoyInputCache and used
   * elsewhere. SoyInputCache uses a File object as the cache key, while intuitive this is
   * incompatible with the SoyFileSupplier interface which abstracts the File away. For the other
   * cached objects, they are read/cached prior to being passed to SoyFileSet so this works fine,
   * but for actual Source files, we are very flexible in how they can be configured. So to pass the
   * AST through we use this interface that SoyFileSuppliers can implement to share a cached AST
   * object. So we can 'tunnel' the data through and use a weird downcast to find it later.
   *
   * <p>This tightly couples AbstractSoyCompiler and this class, but I (lukes@) believe such a
   * coupling is reasonable, the thing that makes it awkward is SoyFileSet! Ideally SoyFileSet
   * wouldn't exist and AbstractSoyCompiler would construct SoyFileSetParser directly.
   */
  interface HasAstOrErrors {
    @Nullable
    SoyFileNode getAst(IdGenerator nodeIdGen, ErrorReporter errorReporter);
  }

  /**
   * Simple tuple of un an-evaluatied compilation unit containing information about dependencies.
   */
  @AutoValue
  public abstract static class CompilationUnitAndKind {
    public static CompilationUnitAndKind create(
        SoyFileKind fileKind, String filePath, CompilationUnit compilationUnit) {
      // sanity check
      checkArgument(
          fileKind != SoyFileKind.SRC, "compilation units should only represent dependencies");
      return new AutoValue_SoyFileSetParser_CompilationUnitAndKind(
          fileKind, filePath, compilationUnit);
    }

    abstract SoyFileKind fileKind();

    abstract String filePath();

    abstract CompilationUnit compilationUnit();
  }

  /** A simple tuple for the result of a parse operation. */
  @AutoValue
  public abstract static class ParseResult {
    static ParseResult create(SoyFileSetNode soyTree, TemplateRegistry registry) {
      return new AutoValue_SoyFileSetParser_ParseResult(soyTree, registry);
    }

    public abstract SoyFileSetNode fileSet();

    public abstract TemplateRegistry registry();
  }

  public static Builder newBuilder() {
    return new AutoValue_SoyFileSetParser.Builder();
  }

  /** Optional file cache. */
  @Nullable
  abstract SoyAstCache cache();
  /** Files to parse. Each must have a unique file name. */
  public abstract ImmutableMap<String, SoyFileSupplier> soyFileSuppliers();

  abstract ImmutableList<CompilationUnitAndKind> compilationUnits();

  abstract PassManager passManager();

  abstract ErrorReporter errorReporter();

  public abstract SoyTypeRegistry typeRegistry();

  /** Builder for {@link SoyFileSetParser}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setCache(SoyAstCache cache);

    public abstract Builder setSoyFileSuppliers(
        ImmutableMap<String, SoyFileSupplier> soyFileSuppliers);

    public abstract Builder setCompilationUnits(
        ImmutableList<CompilationUnitAndKind> compilationUnits);

    public abstract Builder setPassManager(PassManager passManager);

    public abstract Builder setErrorReporter(ErrorReporter errorReporter);

    public abstract Builder setTypeRegistry(SoyTypeRegistry typeRegistry);

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
    List<TemplateMetadata> templateMetadatas = new ArrayList<>();
    for (CompilationUnitAndKind unit : compilationUnits()) {
      templateMetadatas.addAll(
          TemplateMetadataSerializer.templatesFromCompilationUnit(
              unit.compilationUnit(),
              unit.fileKind(),
              typeRegistry(),
              unit.filePath(),
              errorReporter()));
    }
    IdGenerator nodeIdGen =
        (cache() != null) ? cache().getNodeIdGenerator() : new IncrementingIdGenerator();
    SoyFileSetNode soyTree = new SoyFileSetNode(nodeIdGen.genId(), nodeIdGen);
    boolean filesWereSkipped = false;
    // TODO(lukes): there are other places in the compiler (autoescaper) which may use the id
    // generator but fail to lock on it.  Eliminate the id system to avoid this whole issue.
    synchronized (nodeIdGen) { // Avoid using the same ID generator in multiple threads.
      for (SoyFileSupplier fileSupplier : soyFileSuppliers().values()) {
        SoyFileSupplier.Version version = fileSupplier.getVersion();
        VersionedFile cachedFile =
            cache() != null ? cache().get(fileSupplier.getFilePath(), version) : null;
        SoyFileNode node;
        if (cachedFile == null) {
          node = parseSoyFileHelper(fileSupplier, nodeIdGen);
          // TODO(b/19269289): implement error recovery and keep on trucking in order to display
          // as many errors as possible. Currently, the later passes just spew NPEs if run on
          // a malformed parse tree.
          if (node == null) {
            filesWereSkipped = true;
            continue;
          }
          // Run passes that are considered part of initial parsing.
          passManager().runSingleFilePasses(node, nodeIdGen);
          // Run passes that check the tree.
          if (cache() != null) {
            cache().put(fileSupplier.getFilePath(), VersionedFile.of(node, version));
          }
        } else {
          node = cachedFile.file();
        }
        soyTree.addChild(node);
      }
      // Only contains information from compilation units, but can be passed into single file
      // passes.
      TemplateRegistry.Builder builder = TemplateRegistry.builder(errorReporter());
      for (TemplateMetadata template : templateMetadatas) {
        builder.addTemplate(template);
      }
      if (!filesWereSkipped) {
        passManager().runTemplateReturnTypeInferencePasses(soyTree, builder.build());
      }
      for (SoyFileNode node : soyTree.getChildren()) {
        for (TemplateNode template : node.getChildren()) {
          builder.addTemplate(TemplateMetadata.fromTemplate(template));
        }
      }
      TemplateRegistry registry = builder.build();
      // Run passes that check the tree iff we successfully parsed every file.
      if (!filesWereSkipped) {
        passManager().runWholeFilesetPasses(soyTree, registry);
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
  private SoyFileNode parseSoyFileHelper(SoyFileSupplier soyFileSupplier, IdGenerator nodeIdGen)
      throws IOException {
    // See copious comments above on this test
    if (soyFileSupplier instanceof HasAstOrErrors) {
      return ((HasAstOrErrors) soyFileSupplier).getAst(nodeIdGen, errorReporter());
    } else {
      try (Reader soyFileReader = soyFileSupplier.open()) {
        String filePath = soyFileSupplier.getFilePath();
        int lastBangIndex = filePath.lastIndexOf('!');
        if (lastBangIndex != -1) {
          // This is a resource in a JAR file. Only keep everything after the bang.
          filePath = filePath.substring(lastBangIndex + 1);
        }
        // Think carefully before adding new parameters to the parser.
        // Currently the only parameters are the id generator, the file, and the errorReporter.
        // This
        // ensures that the file be cached without worrying about other compiler inputs.
        return new SoyFileParser(nodeIdGen, soyFileReader, filePath, errorReporter())
            .parseSoyFile();
      }
    }
  }
}
