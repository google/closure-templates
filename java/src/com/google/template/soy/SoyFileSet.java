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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.io.ByteSink;
import com.google.common.io.CharSource;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.base.internal.TriState;
import com.google.template.soy.base.internal.VolatileSoyFileSupplier;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.error.ErrorReporterImpl;
import com.google.template.soy.error.PrettyErrorFactory;
import com.google.template.soy.error.SnippetFormatter;
import com.google.template.soy.error.SoyCompilationException;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.incrementaldomsrc.IncrementalDomSrcMain;
import com.google.template.soy.incrementaldomsrc.SoyIncrementalDomSrcOptions;
import com.google.template.soy.jbcsrc.BytecodeCompiler;
import com.google.template.soy.jbcsrc.api.SoySauce;
import com.google.template.soy.jbcsrc.api.SoySauceImpl;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.JsSrcMain;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.msgs.internal.ExtractMsgsVisitor;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgBundleImpl;
import com.google.template.soy.parseinfo.passes.GenerateParseInfoVisitor;
import com.google.template.soy.passes.ClearSoyDocStringsVisitor;
import com.google.template.soy.passes.FindIjParamsVisitor;
import com.google.template.soy.passes.FindIjParamsVisitor.IjParamsInfo;
import com.google.template.soy.passes.FindTransitiveDepTemplatesVisitor;
import com.google.template.soy.passes.FindTransitiveDepTemplatesVisitor.TransitiveDepTemplatesInfo;
import com.google.template.soy.passes.PassManager;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.internal.PySrcMain;
import com.google.template.soy.shared.SoyAstCache;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.internal.MainEntryPointUtils;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.tofu.internal.BaseTofu.BaseTofuFactory;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.proto.SoyProtoTypeProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a complete set of Soy files for compilation as one bundle. The files may depend on
 * each other but should not have dependencies outside of the set.
 *
 * <p>Note: Soy file (or resource) contents must be encoded in UTF-8.
 *
 */
public final class SoyFileSet {

  /**
   * Creates a builder with the standard set of Soy directives, functions, and types.
   *
   * <p>If you need additional directives, functions, or types, create the Builder instance using
   * Guice. If your project doesn't otherwise use Guice, you can just use Guice.createInjector with
   * only the modules you need, similar to the implementation of this method.
   */
  public static Builder builder() {
    return Guice.createInjector(new SoyModule()).getInstance(Builder.class);
  }

  // Implementation detail of SoyFileSet.Builder.
  // having it as its own 'parameter' class removes a small amount of boilerplate.
  static final class CoreDependencies {
    private final BaseTofuFactory baseTofuFactory;
    private final SoySauceImpl.Factory soyTemplatesFactory;
    private final Provider<JsSrcMain> jsSrcMainProvider;
    private final Provider<IncrementalDomSrcMain> incrementalDomSrcMainProvider;
    private final Provider<PySrcMain> pySrcMainProvider;
    private final SoyValueConverter valueConverter;
    private final SoyTypeRegistry typeRegistry;
    private final ImmutableMap<String, ? extends SoyFunction> soyFunctionMap;
    private final ImmutableMap<String, ? extends SoyPrintDirective> printDirectives;
    private final Provider<SoyMsgBundleHandler> msgBundleHandlerProvider;

    @Inject
    CoreDependencies(
        BaseTofuFactory baseTofuFactory,
        SoySauceImpl.Factory soyTemplatesFactory,
        Provider<JsSrcMain> jsSrcMainProvider,
        Provider<IncrementalDomSrcMain> incrementalDomSrcMainProvider,
        Provider<PySrcMain> pySrcMainProvider,
        SoyValueConverter valueConverter,
        SoyTypeRegistry typeRegistry,
        ImmutableMap<String, ? extends SoyFunction> soyFunctionMap,
        ImmutableMap<String, ? extends SoyPrintDirective> printDirectives,
        Provider<SoyMsgBundleHandler> msgBundleHandlerProvider) {
      this.baseTofuFactory = baseTofuFactory;
      this.soyTemplatesFactory = soyTemplatesFactory;
      this.jsSrcMainProvider = jsSrcMainProvider;
      this.incrementalDomSrcMainProvider = incrementalDomSrcMainProvider;
      this.pySrcMainProvider = pySrcMainProvider;
      this.valueConverter = valueConverter;
      this.typeRegistry = typeRegistry;
      this.soyFunctionMap = soyFunctionMap;
      this.printDirectives = printDirectives;
      this.msgBundleHandlerProvider = msgBundleHandlerProvider;
    }
  }

  /**
   * Builder for a {@code SoyFileSet}.
   *
   * <p>Instances of this can be obtained by calling {@link #builder()} or by installing {@link
   * SoyModule} and injecting it.
   */
  public static final class Builder {
    /** The SoyFileSuppliers collected so far in added order, as a set to prevent dupes. */
    private final ImmutableMap.Builder<String, SoyFileSupplier> filesBuilder;

    /** Optional AST cache. */
    private SoyAstCache cache;

    /** The general compiler options. */
    private SoyGeneralOptions lazyGeneralOptions;
    /** Type registry for this fileset only. */
    private SoyTypeRegistry localTypeRegistry;

    private final CoreDependencies coreDependencies;

    /** The SoyProtoTypeProvider builder that will be built for local type registry. */
    private final SoyProtoTypeProvider.Builder protoTypeProviderBuilder;

    private ImmutableList<CharSource> conformanceConfigs = ImmutableList.of();

    Builder(CoreDependencies coreDependencies) {
      this.coreDependencies = coreDependencies;
      this.filesBuilder = ImmutableMap.builder();
      this.protoTypeProviderBuilder = new SoyProtoTypeProvider.Builder();
      this.cache = null;
      this.lazyGeneralOptions = null;
    }

    /**
     * Sets all Soy general options.
     *
     * <p>This must be called before any other setters.
     */
    public void setGeneralOptions(SoyGeneralOptions generalOptions) {
      Preconditions.checkState(
          lazyGeneralOptions == null,
          "Call SoyFileSet#setGeneralOptions before any other setters.");
      Preconditions.checkNotNull(generalOptions, "Non-null argument expected.");
      lazyGeneralOptions = generalOptions.clone();
    }

    /**
     * Returns and/or lazily-creates the SoyGeneralOptions for this builder.
     *
     * <p>Laziness is an important feature to ensure that setGeneralOptions can fail if options were
     * already set. Otherwise, it'd be easy to set some options on this builder and overwrite them
     * by calling setGeneralOptions.
     */
    private SoyGeneralOptions getGeneralOptions() {
      if (lazyGeneralOptions == null) {
        lazyGeneralOptions = new SoyGeneralOptions();
      }
      return lazyGeneralOptions;
    }

    /**
     * Builds the new {@code SoyFileSet}.
     *
     * @return The new {@code SoyFileSet}.
     */
    public SoyFileSet build() {
      try {
        if (!protoTypeProviderBuilder.isEmpty()) {
          Set<SoyTypeProvider> typeProviders =
              ImmutableSet.<SoyTypeProvider>of(protoTypeProviderBuilder.build());
          localTypeRegistry = new SoyTypeRegistry(typeProviders);
        }
      } catch (DescriptorValidationException | IOException ex) {
        throw new RuntimeException("Malformed descriptor set", ex);
      }
      return new SoyFileSet(
          coreDependencies.baseTofuFactory,
          coreDependencies.soyTemplatesFactory,
          coreDependencies.jsSrcMainProvider,
          coreDependencies.incrementalDomSrcMainProvider,
          coreDependencies.pySrcMainProvider,
          coreDependencies.valueConverter,
          localTypeRegistry == null ? coreDependencies.typeRegistry : localTypeRegistry,
          coreDependencies.soyFunctionMap,
          coreDependencies.printDirectives,
          filesBuilder.build(),
          getGeneralOptions(),
          cache,
          coreDependencies.msgBundleHandlerProvider,
          conformanceConfigs);
    }

    /**
     * Adds an input Soy file, given a {@code CharSource} for the file content, as well as the
     * desired file path for messages.
     *
     * @param contentSource Source for the Soy file content.
     * @param soyFileKind The kind of this input Soy file.
     * @param filePath The path to the Soy file (used for messages only).
     * @return This builder.
     */
    public Builder addWithKind(CharSource contentSource, SoyFileKind soyFileKind, String filePath) {
      return addFile(SoyFileSupplier.Factory.create(contentSource, soyFileKind, filePath));
    }

    /**
     * Adds an input Soy file, given a {@code CharSource} for the file content, as well as the
     * desired file path for messages.
     *
     * @param contentSource Source for the Soy file content.
     * @param filePath The path to the Soy file (used for messages only).
     * @return This builder.
     */
    public Builder add(CharSource contentSource, String filePath) {
      return addWithKind(contentSource, SoyFileKind.SRC, filePath);
    }

    /**
     * Adds an input Soy file, given a {@code File}.
     *
     * @param inputFile The Soy file.
     * @param soyFileKind The kind of this input Soy file.
     * @return This builder.
     */
    public Builder addWithKind(File inputFile, SoyFileKind soyFileKind) {
      return addFile(SoyFileSupplier.Factory.create(inputFile, soyFileKind));
    }

    /**
     * Adds an input Soy file, given a {@code File}.
     *
     * @param inputFile The Soy file.
     * @return This builder.
     */
    public Builder add(File inputFile) {
      return addWithKind(inputFile, SoyFileKind.SRC);
    }

    /**
     * Adds an input Soy file that supports checking for modifications, given a {@code File}.
     *
     * <p>Note: This does nothing by itself. It should be used in conjunction with a feature that
     * actually checks for volatile files. Currently, that feature is {@link
     * #setSoyAstCache(SoyAstCache)}.
     *
     * @param inputFile The Soy file.
     * @param soyFileKind The kind of this input Soy file.
     * @return This builder.
     */
    public Builder addVolatileWithKind(File inputFile, SoyFileKind soyFileKind) {
      return addFile(new VolatileSoyFileSupplier(inputFile, soyFileKind));
    }

    /**
     * Adds an input Soy file that supports checking for modifications, given a {@code File}.
     *
     * <p>Note: This does nothing by itself. It should be used in conjunction with a feature that
     * actually checks for volatile files. Currently, that feature is {@link
     * #setSoyAstCache(SoyAstCache)}.
     *
     * @param inputFile The Soy file.
     * @return This builder.
     */
    public Builder addVolatile(File inputFile) {
      return addVolatileWithKind(inputFile, SoyFileKind.SRC);
    }

    /**
     * Adds an input Soy file, given a resource {@code URL}, as well as the desired file path for
     * messages.
     *
     * @param inputFileUrl The Soy file.
     * @param soyFileKind The kind of this input Soy file.
     * @param filePath The path to the Soy file (used for messages only).
     * @return This builder.
     */
    public Builder addWithKind(URL inputFileUrl, SoyFileKind soyFileKind, String filePath) {
      return addFile(SoyFileSupplier.Factory.create(inputFileUrl, soyFileKind, filePath));
    }

    /**
     * Adds an input Soy file, given a resource {@code URL}, as well as the desired file path for
     * messages.
     *
     * @param inputFileUrl The Soy file.
     * @param filePath The path to the Soy file (used for messages only).
     * @return This builder.
     */
    public Builder add(URL inputFileUrl, String filePath) {
      return addWithKind(inputFileUrl, SoyFileKind.SRC, filePath);
    }

    /**
     * Adds an input Soy file, given a resource {@code URL}.
     *
     * <p>Important: This function assumes that the desired file path is returned by {@code
     * inputFileUrl.toString()}. If this is not the case, please use {@link #addWithKind(URL,
     * SoyFileKind, String)} instead.
     *
     * @see #addWithKind(URL, SoyFileKind, String)
     * @param inputFileUrl The Soy file.
     * @param soyFileKind The kind of this input Soy file.
     * @return This builder.
     */
    public Builder addWithKind(URL inputFileUrl, SoyFileKind soyFileKind) {
      return addFile(SoyFileSupplier.Factory.create(inputFileUrl, soyFileKind));
    }

    /**
     * Adds an input Soy file, given a resource {@code URL}.
     *
     * <p>Important: This function assumes that the desired file path is returned by {@code
     * inputFileUrl.toString()}. If this is not the case, please use {@link #add(URL, String)}
     * instead.
     *
     * @see #add(URL, String)
     * @param inputFileUrl The Soy file.
     * @return This builder.
     */
    public Builder add(URL inputFileUrl) {
      return addWithKind(inputFileUrl, SoyFileKind.SRC);
    }

    /**
     * Adds an input Soy file, given the file content provided as a string, as well as the desired
     * file path for messages.
     *
     * @param content The Soy file content.
     * @param soyFileKind The kind of this input Soy file.
     * @param filePath The path to the Soy file (used for messages only).
     * @return This builder.
     */
    public Builder addWithKind(CharSequence content, SoyFileKind soyFileKind, String filePath) {
      return addFile(SoyFileSupplier.Factory.create(content, soyFileKind, filePath));
    }

    /**
     * Adds an input Soy file, given the file content provided as a string, as well as the desired
     * file path for messages.
     *
     * @param content The Soy file content.
     * @param filePath The path to the Soy file (used for messages only).
     * @return This builder.
     */
    public Builder add(CharSequence content, String filePath) {
      return addWithKind(content, SoyFileKind.SRC, filePath);
    }

    /**
     * Configures to use an AST cache to speed up development time.
     *
     * <p>This is undesirable in production mode since it uses strictly more memory, and this only
     * helps if the same templates are going to be recompiled frequently.
     *
     * @param cache The cache to use, which can have a lifecycle independent of the SoyFileSet. Null
     *     indicates not to use a cache.
     * @return This builder.
     */
    public Builder setSoyAstCache(SoyAstCache cache) {
      this.cache = cache;
      return this;
    }

    /**
     * Sets the user-declared syntax version name for the Soy file bundle.
     *
     * @param versionName The syntax version name, e.g. "1.0", "2.0", "2.3".
     */
    public Builder setDeclaredSyntaxVersionName(@Nonnull String versionName) {
      getGeneralOptions().setDeclaredSyntaxVersionName(versionName);
      return this;
    }

    /**
     * Sets whether to allow external calls (calls to undefined templates).
     *
     * @param allowExternalCalls Whether to allow external calls (calls to undefined templates).
     * @return This builder.
     */
    public Builder setAllowExternalCalls(boolean allowExternalCalls) {
      getGeneralOptions().setAllowExternalCalls(allowExternalCalls);
      return this;
    }

    /**
     * Sets experimental features. These features are unreleased and are not generally available.
     *
     * @param experimentalFeatures
     * @return This builder.
     */
    public Builder setExperimentalFeatures(List<String> experimentalFeatures) {
      getGeneralOptions().setExperimentalFeatures(experimentalFeatures);
      return this;
    }

    /**
     * Disables optimizer. The optimizer tries to simplify the Soy AST by evaluating constant
     * expressions. It generally improves performance and should only be disabled in integration
     * tests.
     *
     * <p>This is public only because we need to set it in {@code SoyFileSetHelper}, that are
     * necessary for integration tests. Normal users should not use this.
     *
     * @return This builder.
     */
    public Builder disableOptimizer() {
      getGeneralOptions().disableOptimizer();
      return this;
    }

    /**
     * Sets whether to force strict autoescaping. Enabling will cause compile time exceptions if
     * non-strict autoescaping is used in namespaces or templates.
     *
     * @param strictAutoescapingRequired Whether strict autoescaping is required.
     * @return This builder.
     */
    public Builder setStrictAutoescapingRequired(boolean strictAutoescapingRequired) {
      getGeneralOptions().setStrictAutoescapingRequired(strictAutoescapingRequired);
      return this;
    }

    /**
     * Sets the map from compile-time global name to value.
     *
     * <p>The values can be any of the Soy primitive types: null, boolean, integer, float (Java
     * double), or string.
     *
     * @param compileTimeGlobalsMap Map from compile-time global name to value. The values can be
     *     any of the Soy primitive types: null, boolean, integer, float (Java double), or string.
     * @return This builder.
     * @throws SoySyntaxException If one of the values is not a valid Soy primitive type.
     */
    public Builder setCompileTimeGlobals(Map<String, ?> compileTimeGlobalsMap) {
      getGeneralOptions().setCompileTimeGlobals(compileTimeGlobalsMap);
      return this;
    }

    /**
     * Sets the file containing compile-time globals.
     *
     * <p>Each line of the file should have the format
     *
     * <pre>
     *     &lt;global_name&gt; = &lt;primitive_data&gt;
     * </pre>
     *
     * where primitive_data is a valid Soy expression literal for a primitive type (null, boolean,
     * integer, float, or string). Empty lines and lines beginning with "//" are ignored. The file
     * should be encoded in UTF-8.
     *
     * <p>If you need to generate a file in this format from Java, consider using the utility {@code
     * SoyUtils.generateCompileTimeGlobalsFile()}.
     *
     * @param compileTimeGlobalsFile The file containing compile-time globals.
     * @return This builder.
     * @throws IOException If there is an error reading the compile-time globals file.
     */
    public Builder setCompileTimeGlobals(File compileTimeGlobalsFile) throws IOException {
      getGeneralOptions().setCompileTimeGlobals(compileTimeGlobalsFile);
      return this;
    }

    /**
     * Sets the resource file containing compile-time globals.
     *
     * <p>Each line of the file should have the format
     *
     * <pre>
     *     &lt;global_name&gt; = &lt;primitive_data&gt;
     * </pre>
     *
     * where primitive_data is a valid Soy expression literal for a primitive type (null, boolean,
     * integer, float, or string). Empty lines and lines beginning with "//" are ignored. The file
     * should be encoded in UTF-8.
     *
     * <p>If you need to generate a file in this format from Java, consider using the utility {@code
     * SoyUtils.generateCompileTimeGlobalsFile()}.
     *
     * @param compileTimeGlobalsResource The resource containing compile-time globals.
     * @return This builder.
     * @throws IOException If there is an error reading the compile-time globals file.
     */
    public Builder setCompileTimeGlobals(URL compileTimeGlobalsResource) throws IOException {
      getGeneralOptions().setCompileTimeGlobals(compileTimeGlobalsResource);
      return this;
    }

    /** Add all proto descriptors found in the file to the type registry. */
    public Builder addProtoDescriptorsFromFile(File descriptorFile) {
      protoTypeProviderBuilder.addFileDescriptorSetFromFile(descriptorFile);
      return this;
    }

    /** Add all proto descriptors found in all files to the type registry. */
    public Builder addProtoDescriptorsFromFiles(Iterable<File> descriptorFiles) {
      for (File descriptorFile : descriptorFiles) {
        protoTypeProviderBuilder.addFileDescriptorSetFromFile(descriptorFile);
      }
      return this;
    }

    /**
     * Registers a collection of protocol buffer descriptors. This makes all the types defined in
     * the provided descriptors available to use in soy.
     */
    public Builder addProtoDescriptors(GenericDescriptor... descriptors) {
      return addProtoDescriptors(Arrays.asList(descriptors));
    }

    /**
     * Registers a collection of protocol buffer descriptors. This makes all the types defined in
     * the provided descriptors available to use in soy.
     */
    public Builder addProtoDescriptors(Iterable<? extends GenericDescriptor> descriptors) {
      protoTypeProviderBuilder.addDescriptors(descriptors);
      return this;
    }

    /** Override the global type registry with one that is local to this file set. */
    public Builder setLocalTypeRegistry(SoyTypeRegistry typeRegistry) {
      this.localTypeRegistry = typeRegistry;
      return this;
    }

    private Builder addFile(SoyFileSupplier supplier) {
      filesBuilder.put(supplier.getFilePath(), supplier);
      return this;
    }
  }

  /** Provider for getting an instance of SoyMsgBundleHandler. */
  private final Provider<SoyMsgBundleHandler> msgBundleHandlerProvider;

  private final BaseTofuFactory baseTofuFactory;
  private final SoySauceImpl.Factory soyTemplatesFactory;
  private final Provider<JsSrcMain> jsSrcMainProvider;
  private final Provider<IncrementalDomSrcMain> incrementalDomSrcMainProvider;
  private final Provider<PySrcMain> pySrcMainProvider;

  private final SoyValueConverter valueConverter;

  private final SoyTypeRegistry typeRegistry;
  private final ImmutableMap<String, SoyFileSupplier> soyFileSuppliers;

  /** Optional soy tree cache for faster recompile times. */
  @Nullable private final SoyAstCache cache;

  private final SoyGeneralOptions generalOptions;

  private final ImmutableList<CharSource> conformanceConfigs;

  /** For private use by pruneTranslatedMsgs(). */
  private ImmutableSet<Long> memoizedExtractedMsgIdsForPruning;

  private final ImmutableMap<String, ? extends SoyFunction> soyFunctionMap;
  private final ImmutableMap<String, ? extends SoyPrintDirective> printDirectives;

  /** For reporting errors during parsing. */
  private ErrorReporterImpl errorReporter;

  /**
   * @param baseTofuFactory Factory for creating an instance of BaseTofu.
   * @param jsSrcMainProvider Provider for getting an instance of JsSrcMain.
   * @param incrementalDomSrcMainProvider Provider for getting an instance of IncrementalDomSrcMain.
   * @param pySrcMainProvider Provider for getting an instance of PySrcMain.
   * @param typeRegistry The type registry to resolve parameter type names.
   * @param soyFileSuppliers The suppliers for the input Soy files.
   * @param generalOptions The general compiler options.
   */
  SoyFileSet(
      BaseTofuFactory baseTofuFactory,
      SoySauceImpl.Factory soyTemplatesFactory,
      Provider<JsSrcMain> jsSrcMainProvider,
      Provider<IncrementalDomSrcMain> incrementalDomSrcMainProvider,
      Provider<PySrcMain> pySrcMainProvider,
      SoyValueConverter valueConverter,
      SoyTypeRegistry typeRegistry,
      ImmutableMap<String, ? extends SoyFunction> soyFunctionMap,
      ImmutableMap<String, ? extends SoyPrintDirective> printDirectives,
      ImmutableMap<String, SoyFileSupplier> soyFileSuppliers,
      SoyGeneralOptions generalOptions,
      @Nullable SoyAstCache cache,
      Provider<SoyMsgBundleHandler> msgBundleHandlerProvider,
      ImmutableList<CharSource> conformanceConfigs) {
    // Default value is optionally replaced using method injection.
    this.soyTemplatesFactory = soyTemplatesFactory;
    this.baseTofuFactory = baseTofuFactory;
    this.jsSrcMainProvider = jsSrcMainProvider;
    this.incrementalDomSrcMainProvider = incrementalDomSrcMainProvider;
    this.pySrcMainProvider = pySrcMainProvider;
    this.valueConverter = valueConverter;

    Preconditions.checkArgument(
        !soyFileSuppliers.isEmpty(), "Must have non-zero number of input Soy files.");
    this.typeRegistry = typeRegistry;
    this.soyFileSuppliers = soyFileSuppliers;
    this.cache = cache;
    this.generalOptions = generalOptions.clone();
    this.soyFunctionMap = soyFunctionMap;
    this.printDirectives = printDirectives;
    this.msgBundleHandlerProvider = msgBundleHandlerProvider;
    this.conformanceConfigs = conformanceConfigs;
  }

  /** Returns the list of suppliers for the input Soy files. For testing use only! */
  @VisibleForTesting
  ImmutableMap<String, SoyFileSupplier> getSoyFileSuppliersForTesting() {
    return soyFileSuppliers;
  }

  /** Returns the general compiler options. For testing use only! */
  @VisibleForTesting
  SoyGeneralOptions getOptionsForTesting() {
    return generalOptions;
  }

  /**
   * Generates Java classes containing parse info (param names, template names, meta info). There
   * will be one Java class per Soy file.
   *
   * @param javaPackage The Java package for the generated classes.
   * @param javaClassNameSource Source of the generated class names. Must be one of "filename",
   *     "namespace", or "generic".
   * @return A map from generated file name (of the form "<*>SoyInfo.java") to generated file
   *     content.
   * @throws SoyCompilationException If compilation fails.
   */
  public ImmutableMap<String, String> generateParseInfo(
      String javaPackage, String javaClassNameSource) {
    resetErrorReporter();
    // TODO(lukes): see if we can enforce that globals are provided at compile time here. given that
    // types have to be, this should be possible.  Currently it is disabled for backwards
    // compatibility
    // N.B. we do not run the optimizer here for 2 reasons:
    // 1. it would just waste time, since we are not running code generation the optimization work
    //    doesn't help anything
    // 2. it potentially removes metadata from the tree by precalculating expressions. For example,
    //    trivial print nodes are evaluated, which can remove globals from the tree, but the
    //    generator requires data about globals to generate accurate proto descriptors.
    generalOptions.disableOptimizer();
    ParseResult result =
        parse(passManagerBuilder(SyntaxVersion.V2_0).allowUnknownGlobals().allowUnknownFunctions());
    throwIfErrorsPresent();

    SoyFileSetNode soyTree = result.fileSet();
    TemplateRegistry registry = result.registry();

    // Do renaming of package-relative class names.
    ImmutableMap<String, String> parseInfo =
        new GenerateParseInfoVisitor(javaPackage, javaClassNameSource, registry).exec(soyTree);
    throwIfErrorsPresent();
    return parseInfo;
  }

  /**
   * Extracts all messages from this Soy file set into a SoyMsgBundle (which can then be turned into
   * an extracted messages file with the help of a SoyMsgBundleHandler).
   *
   * @return A SoyMsgBundle containing all the extracted messages (locale "en").
   * @throws SoyCompilationException If compilation fails.
   */
  public SoyMsgBundle extractMsgs() {
    resetErrorReporter();
    // extractMsgs disables a bunch of passes since it is typically not configured with things
    // like global definitions, type definitions, etc.
    SoyFileSetNode soyTree =
        parse(
                passManagerBuilder(SyntaxVersion.V1_0)
                    .allowUnknownGlobals()
                    .allowUnknownFunctions()
                    // override the type registry so that the parser doesn't report errors when it
                    // can't resolve strict types
                    .setTypeRegistry(SoyTypeRegistry.DEFAULT_UNKNOWN)
                    .disableAllTypeChecking())
            .fileSet();
    throwIfErrorsPresent();
    SoyMsgBundle bundle = new ExtractMsgsVisitor().exec(soyTree);
    throwIfErrorsPresent();
    return bundle;
  }

  /**
   * Prunes messages from a given message bundle, keeping only messages used in this Soy file set.
   *
   * <p>Important: Do not use directly. This is subject to change and your code will break.
   *
   * <p>Note: This method memoizes intermediate results to improve efficiency in the case that it is
   * called multiple times (which is a common case). Thus, this method will not work correctly if
   * the underlying Soy files are modified between calls to this method.
   *
   * @param origTransMsgBundle The message bundle to prune.
   * @return The pruned message bundle.
   * @throws SoyCompilationException If compilation fails.
   */
  public SoyMsgBundle pruneTranslatedMsgs(SoyMsgBundle origTransMsgBundle) {
    resetErrorReporter();
    // ------ Extract msgs from all the templates reachable from public templates. ------
    // Note: In the future, instead of using all public templates as the root set, we can allow the
    // user to provide a root set.

    if (memoizedExtractedMsgIdsForPruning == null) {
      ParseResult result =
          parse(
              passManagerBuilder(SyntaxVersion.V1_0)
                  // override the type registry so that the parser doesn't report errors when it
                  // can't resolve strict types
                  .setTypeRegistry(SoyTypeRegistry.DEFAULT_UNKNOWN)
                  .allowUnknownGlobals()
                  .allowUnknownFunctions()
                  .disableAllTypeChecking());

      SoyFileSetNode soyTree = result.fileSet();
      TemplateRegistry registry = result.registry();

      List<TemplateNode> allPublicTemplates = Lists.newArrayList();
      for (SoyFileNode soyFile : soyTree.getChildren()) {
        for (TemplateNode template : soyFile.getChildren()) {
          if (template.getVisibility() == Visibility.PUBLIC) {
            allPublicTemplates.add(template);
          }
        }
      }
      Map<TemplateNode, TransitiveDepTemplatesInfo> depsInfoMap =
          new FindTransitiveDepTemplatesVisitor(registry)
              .execOnMultipleTemplates(allPublicTemplates);
      TransitiveDepTemplatesInfo mergedDepsInfo =
          TransitiveDepTemplatesInfo.merge(depsInfoMap.values());

      SoyMsgBundle extractedMsgBundle =
          new ExtractMsgsVisitor().execOnMultipleNodes(mergedDepsInfo.depTemplateSet);

      ImmutableSet.Builder<Long> extractedMsgIdsBuilder = ImmutableSet.builder();
      for (SoyMsg extractedMsg : extractedMsgBundle) {
        extractedMsgIdsBuilder.add(extractedMsg.getId());
      }
      memoizedExtractedMsgIdsForPruning = extractedMsgIdsBuilder.build();
    }

    // ------ Prune. ------

    ImmutableList.Builder<SoyMsg> prunedTransMsgsBuilder = ImmutableList.builder();
    for (SoyMsg transMsg : origTransMsgBundle) {
      if (memoizedExtractedMsgIdsForPruning.contains(transMsg.getId())) {
        prunedTransMsgsBuilder.add(transMsg);
      }
    }
    // TODO(lukes): this should call throwIfErrorsPresent(), but can't because it will break
    // build rules.
    return new SoyMsgBundleImpl(
        origTransMsgBundle.getLocaleString(), prunedTransMsgsBuilder.build());
  }

  /**
   * Compiles this Soy file set into a Java object (type {@code SoyTofu}) capable of rendering the
   * compiled templates.
   *
   * @return The resulting {@code SoyTofu} object.
   * @throws SoyCompilationException If compilation fails.
   */
  public SoyTofu compileToTofu() {
    resetErrorReporter();
    ServerCompilationPrimitives primitives = compileForServerRendering();
    throwIfErrorsPresent();
    return doCompileToTofu(primitives);
  }

  /** Helper method to compile SoyTofu from {@link ServerCompilationPrimitives} */
  private SoyTofu doCompileToTofu(ServerCompilationPrimitives primitives) {
    return baseTofuFactory.create(
        primitives.registry, getTransitiveIjs(primitives.soyTree, primitives.registry));
  }

  /**
   * This is an <em>extremely experimental API</em> and subject to change. Not all features of soy
   * are implemented in this new backend and the features that are implemented are not necessarily
   * correct!
   *
   * <p>See com/google/template/soy/jbcsrc/README.md for background on this new backend.
   *
   * <p>Compiles this Soy file set into a set of java classes implementing the {@link
   * CompiledTemplate} interface.
   *
   * @return A set of compiled templates
   * @throws SoyCompilationException If compilation fails.
   */
  public SoySauce compileTemplates() {
    resetErrorReporter();
    disallowExternalCalls();
    ServerCompilationPrimitives primitives = compileForServerRendering();
    throwIfErrorsPresent();
    return doCompileSoySauce(primitives);
  }

  /**
   * Compiles this Soy file set into a set of java classes implementing the {@link CompiledTemplate}
   * interface and writes them out to the given ByteSink as a JAR file.
   *
   * @throws SoyCompilationException If compilation fails.
   */
  void compileToJar(ByteSink jarTarget, Optional<ByteSink> srcJarTarget) throws IOException {
    resetErrorReporter();
    disallowExternalCalls();
    ServerCompilationPrimitives primitives = compileForServerRendering();
    BytecodeCompiler.compileToJar(primitives.registry, errorReporter, jarTarget);
    if (srcJarTarget.isPresent()) {
      BytecodeCompiler.writeSrcJar(primitives.registry, soyFileSuppliers, srcJarTarget.get());
    }
    throwIfErrorsPresent();
  }

  /** Helper method to compile SoySauce from {@link ServerCompilationPrimitives} */
  private SoySauce doCompileSoySauce(ServerCompilationPrimitives primitives) {
    Optional<CompiledTemplates> templates =
        BytecodeCompiler.compile(
            primitives.registry,
            // if there is an AST cache, assume we are in 'dev mode' and trigger lazy compilation.
            cache != null,
            errorReporter);

    throwIfErrorsPresent();

    return soyTemplatesFactory.create(templates.get(), soyFunctionMap, printDirectives);
  }

  /**
   * A tuple of the outputs of shared compiler passes that are needed to produce SoyTofu or
   * SoySauce.
   */
  private static final class ServerCompilationPrimitives {
    final SoyFileSetNode soyTree;
    final TemplateRegistry registry;

    ServerCompilationPrimitives(TemplateRegistry registry, SoyFileSetNode soyTree) {
      this.registry = registry;
      this.soyTree = soyTree;
    }
  }

  /** Runs common compiler logic shared by tofu and jbcsrc backends. */
  private ServerCompilationPrimitives compileForServerRendering() {
    ParseResult result = parse(SyntaxVersion.V2_0);
    throwIfErrorsPresent();

    SoyFileSetNode soyTree = result.fileSet();
    TemplateRegistry registry = result.registry();
    // Clear the SoyDoc strings because they use unnecessary memory, unless we have a cache, in
    // which case it is pointless.
    if (cache == null) {
      new ClearSoyDocStringsVisitor().exec(soyTree);
    }

    throwIfErrorsPresent();
    return new ServerCompilationPrimitives(registry, soyTree);
  }

  private ImmutableMap<String, ImmutableSortedSet<String>> getTransitiveIjs(
      SoyFileSetNode soyTree, TemplateRegistry registry) {
    ImmutableMap<TemplateNode, IjParamsInfo> templateToIjParamsInfoMap =
        new FindIjParamsVisitor(registry).execOnAllTemplates(soyTree);
    ImmutableMap.Builder<String, ImmutableSortedSet<String>> templateToTransitiveIjParams =
        ImmutableMap.builder();
    for (Map.Entry<TemplateNode, IjParamsInfo> entry : templateToIjParamsInfoMap.entrySet()) {
      templateToTransitiveIjParams.put(
          entry.getKey().getTemplateName(), entry.getValue().ijParamSet);
    }
    return templateToTransitiveIjParams.build();
  }

  private void disallowExternalCalls() {
    TriState allowExternalCalls = generalOptions.allowExternalCalls();
    if (allowExternalCalls == TriState.UNSET) {
      generalOptions.setAllowExternalCalls(false);
    } else if (allowExternalCalls == TriState.ENABLED) {
      throw new IllegalStateException(
          "SoyGeneralOptions.setAllowExternalCalls(true) is not supported with this method");
    }
    // otherwise, it was already explicitly set to false which is what we want.
  }

  private void requireStrictAutoescaping() {
    TriState strictAutoescapingRequired = generalOptions.isStrictAutoescapingRequired();
    if (strictAutoescapingRequired == TriState.UNSET) {
      generalOptions.setStrictAutoescapingRequired(true);
    } else if (strictAutoescapingRequired == TriState.DISABLED) {
      throw new IllegalStateException(
          "SoyGeneralOptions.isStrictAutoescapingRequired(false) is not supported with this"
              + " method");
    }
    // otherwise, it was already explicitly set to true which is what we want.
  }

  /**
   * Compiles this Soy file set into JS source code files and returns these JS files as a list of
   * strings, one per file.
   *
   * @param jsSrcOptions The compilation options for the JS Src output target.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @return A list of strings where each string represents the JS source code that belongs in one
   *     JS file. The generated JS files correspond one-to-one to the original Soy source files.
   * @throws SoyCompilationException If compilation fails.
   */
  @SuppressWarnings("deprecation")
  public List<String> compileToJsSrc(
      SoyJsSrcOptions jsSrcOptions, @Nullable SoyMsgBundle msgBundle) {
    resetErrorReporter();

    // Synchronize old and new ways to declare syntax version V1.
    if (jsSrcOptions.shouldAllowDeprecatedSyntax()) {
      generalOptions.setDeclaredSyntaxVersionName("1.0");
    }
    // JS has traditionally allowed unknown globals, as a way for soy to reference normal js enums
    // and constants.  For consistency/reusability of templates it would be nice to not allow that
    // but the cat is out of the bag.
    PassManager.Builder builder = passManagerBuilder(SyntaxVersion.V2_0).allowUnknownGlobals();
    ParseResult parseResult = parse(builder);
    throwIfErrorsPresent();
    TemplateRegistry registry = parseResult.registry();
    SoyFileSetNode fileSet = parseResult.fileSet();
    List<String> generatedSrcs =
        jsSrcMainProvider
            .get()
            .genJsSrc(
                fileSet,
                registry,
                jsSrcOptions,
                msgBundle,
                errorReporter);
    throwIfErrorsPresent();
    return generatedSrcs;
  }

  /**
   * Compiles this Soy file set into JS source code files and writes these JS files to disk.
   *
   * @param outputPathFormat The format string defining how to build the output file path
   *     corresponding to an input file path.
   * @param inputFilePathPrefix The prefix prepended to all input file paths (can be empty string).
   * @param jsSrcOptions The compilation options for the JS Src output target.
   * @param locales The list of locales. Can be an empty list if not applicable.
   * @param messageFilePathFormat The message file path format, or null if not applicable.
   * @throws SoyCompilationException If compilation fails.
   * @throws IOException If there is an error in opening/reading a message file or opening/writing
   *     an output JS file.
   */
  @SuppressWarnings("deprecation")
  void compileToJsSrcFiles(
      String outputPathFormat,
      String inputFilePathPrefix,
      SoyJsSrcOptions jsSrcOptions,
      List<String> locales,
      @Nullable String messageFilePathFormat)
      throws IOException {
    resetErrorReporter();

    // Synchronize old and new ways to declare syntax version V1.
    if (jsSrcOptions.shouldAllowDeprecatedSyntax()) {
      generalOptions.setDeclaredSyntaxVersionName("1.0");
    }
    // Allow unknown globals for backwards compatibility
    ParseResult result = parse(passManagerBuilder(SyntaxVersion.V2_0).allowUnknownGlobals());
    throwIfErrorsPresent();

    SoyFileSetNode soyTree = result.fileSet();
    TemplateRegistry registry = result.registry();
    if (locales.isEmpty()) {
      // Not generating localized JS.
      jsSrcMainProvider
          .get()
          .genJsFiles(
              soyTree,
              registry,
              jsSrcOptions,
              null,
              null,
              outputPathFormat,
              inputFilePathPrefix,
              errorReporter);

    } else {
      // Generating localized JS.
      for (String locale : locales) {

        SoyFileSetNode soyTreeClone = SoyTreeUtils.cloneNode(soyTree);

        String msgFilePath =
            MainEntryPointUtils.buildFilePath(
                messageFilePathFormat, locale, null, inputFilePathPrefix);

        SoyMsgBundle msgBundle =
            msgBundleHandlerProvider.get().createFromFile(new File(msgFilePath));
        if (msgBundle.getLocaleString() == null) {
          // TODO: Remove this check (but make sure no projects depend on this behavior).
          // There was an error reading the message file. We continue processing only if the locale
          // begins with "en", because falling back to the Soy source will probably be fine.
          if (!locale.startsWith("en")) {
            throw new IOException("Error opening or reading message file " + msgFilePath);
          }
        }

        jsSrcMainProvider
            .get()
            .genJsFiles(
                soyTreeClone,
                registry,
                jsSrcOptions,
                locale,
                msgBundle,
                outputPathFormat,
                inputFilePathPrefix,
                errorReporter);
      }
    }
    throwIfErrorsPresent();
  }

  /**
   * Compiles this Soy file set into iDOM source code files and returns these JS files as a list of
   * strings, one per file.
   *
   * @param jsSrcOptions The compilation options for the JS Src output target.
   * @return A list of strings where each string represents the JS source code that belongs in one
   *     JS file. The generated JS files correspond one-to-one to the original Soy source files.
   * @throws SoyCompilationException If compilation fails.
   */
  public List<String> compileToIncrementalDomSrc(SoyIncrementalDomSrcOptions jsSrcOptions) {
    resetErrorReporter();
    ParseResult result = preprocessIncrementalDOMResults();
    List<String> generatedSrcs =
        incrementalDomSrcMainProvider
            .get()
            .genJsSrc(
                result.fileSet(),
                result.registry(),
                jsSrcOptions,
                errorReporter);
    throwIfErrorsPresent();
    return generatedSrcs;
  }

  /**
   * Compiles this Soy file set into JS source code files and writes these JS files to disk.
   *
   * @param outputPathFormat The format string defining how to build the output file path
   *     corresponding to an input file path.
   * @param jsSrcOptions The compilation options for the JS Src output target.
   * @throws SoyCompilationException If compilation fails.
   * @throws IOException If there is an error in opening/reading a message file or opening/writing
   *     an output JS file.
   */
  void compileToIncrementalDomSrcFiles(
      String outputPathFormat, SoyIncrementalDomSrcOptions jsSrcOptions) throws IOException {
    resetErrorReporter();
    disallowExternalCalls();
    ParseResult result = preprocessIncrementalDOMResults();

    incrementalDomSrcMainProvider
        .get()
        .genJsFiles(
            result.fileSet(),
            result.registry(),
            jsSrcOptions,
            outputPathFormat,
            errorReporter);

    throwIfErrorsPresent();
  }

  /** Prepares the parsed result for use in generating Incremental DOM source code. */
  @SuppressWarnings("deprecation")
  private ParseResult preprocessIncrementalDOMResults() {
    SyntaxVersion declaredSyntaxVersion =
        generalOptions.getDeclaredSyntaxVersion(SyntaxVersion.V2_0);

    Preconditions.checkState(
        declaredSyntaxVersion.num >= SyntaxVersion.V2_0.num,
        "Incremental DOM code generation only supports syntax version of V2 or higher.");
    requireStrictAutoescaping();
    // incremental dom requires the html rewriting pass.  It is currently incompatible with the
    // other backends though because of issues with the autoescaper.  When that is fixed this will
    // be come the default behavior.
    ParseResult result =
        parse(
            passManagerBuilder(SyntaxVersion.V2_0)
                .enableHtmlRewriting()
                .desugarHtmlNodes(false)
                .setAutoescaperEnabled(false));
    throwIfErrorsPresent();
    return result;
  }

  /**
   * Compiles this Soy file set into Python source code files and writes these Python files to disk.
   *
   * @param outputPathFormat The format string defining how to build the output file path
   *     corresponding to an input file path.
   * @param inputFilePathPrefix The prefix prepended to all input file paths (can be empty string).
   * @param pySrcOptions The compilation options for the Python Src output target.
   * @throws SoyCompilationException If compilation fails.
   * @throws IOException If there is an error in opening/reading a message file or opening/writing
   *     an output JS file.
   */
  public void compileToPySrcFiles(
      String outputPathFormat, String inputFilePathPrefix, SoyPySrcOptions pySrcOptions)
      throws IOException {
    resetErrorReporter();

    ParseResult result = parse(SyntaxVersion.V2_0);
    throwIfErrorsPresent();
    SoyFileSetNode soyTree = result.fileSet();
    TemplateRegistry registry = result.registry();

    pySrcMainProvider
        .get()
        .genPyFiles(
            soyTree,
            registry,
            pySrcOptions,
            outputPathFormat,
            inputFilePathPrefix,
            errorReporter);

    throwIfErrorsPresent();
  }

  // Parse the current file set with the given default syntax version.
  private ParseResult parse(SyntaxVersion defaultVersion) {
    return parse(passManagerBuilder(defaultVersion));
  }

  private PassManager.Builder passManagerBuilder(SyntaxVersion defaultVersion) {
    PassManager.Builder builder =
        new PassManager.Builder()
            .setTypeRegistry(typeRegistry)
            .setGeneralOptions(generalOptions)
            .setDeclaredSyntaxVersion(generalOptions.getDeclaredSyntaxVersion(defaultVersion))
            .setSoyFunctionMap(soyFunctionMap)
            .setSoyPrintDirectiveMap(printDirectives)
            .setValueConverter(valueConverter)
            .setErrorReporter(errorReporter)
            .setConformanceConfigs(conformanceConfigs);
    return builder;
  }

  private ParseResult parse(PassManager.Builder builder) {
    return new SoyFileSetParser(cache, soyFileSuppliers, builder.build(), errorReporter).parse();
  }

  /**
   * This method resets the error reporter field in preparation to a new compiler invocation.
   *
   * <p>This method should be called at the beginning of every entry point into SoyFileSet.
   *
   * <p>TODO(lukes): instead of resetting, consider making it an error to call a compiler method on
   * the same SoyFileSet object more than once.
   */
  private void resetErrorReporter() {
    // TODO(lukes): consider moving ErrorReporterImpl to the error package and making this a static
    // factory method there somewhere
    errorReporter =
        new ErrorReporterImpl(new PrettyErrorFactory(new SnippetFormatter(soyFileSuppliers)));
  }

  private void throwIfErrorsPresent() {
    if (errorReporter.hasErrors()) {
      Iterable<SoyError> errors = errorReporter.getErrors();
      // clear the field to ensure that error reporters can't leak between compilations
      errorReporter = null;
      throw new SoyCompilationException(errors);
    }
  }
}
