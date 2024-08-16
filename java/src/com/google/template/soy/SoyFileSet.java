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
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSink;
import com.google.common.io.CharSource;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.InlineMe;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.base.internal.KytheMode;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.conformance.ValidatedConformanceConfig;
import com.google.template.soy.css.CssRegistry;
import com.google.template.soy.error.ErrorFormatter;
import com.google.template.soy.error.ErrorFormatterImpl;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyCompilationException;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.error.SoyInternalCompilerException;
import com.google.template.soy.incrementaldomsrc.IncrementalDomSrcMain;
import com.google.template.soy.incrementaldomsrc.SoyIncrementalDomSrcOptions;
import com.google.template.soy.javagencode.GenerateBuildersVisitor;
import com.google.template.soy.javagencode.GenerateParseInfoVisitor;
import com.google.template.soy.jbcsrc.BytecodeCompiler;
import com.google.template.soy.jbcsrc.api.SoySauce;
import com.google.template.soy.jbcsrc.api.SoySauceImpl;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.JsSrcMain;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.msgs.SoyMsgBundleHandler.OutputFileOptions;
import com.google.template.soy.msgs.internal.ExtractMsgsVisitor;
import com.google.template.soy.passes.CheckTemplateHeaderVarsPass;
import com.google.template.soy.passes.ClearSoyDocStringsVisitor;
import com.google.template.soy.passes.PassManager;
import com.google.template.soy.passes.PassManager.AstRewrites;
import com.google.template.soy.passes.PassManager.PassContinuationRule;
import com.google.template.soy.passes.PluginResolver;
import com.google.template.soy.passes.SoyConformancePass;
import com.google.template.soy.plugin.internal.PluginValidator;
import com.google.template.soy.plugin.internal.SoySourceFunctionDescriptor;
import com.google.template.soy.plugin.java.MethodChecker;
import com.google.template.soy.plugin.java.MethodChecker.Code;
import com.google.template.soy.plugin.java.MethodChecker.Response;
import com.google.template.soy.plugin.java.PluginInstances;
import com.google.template.soy.plugin.java.ReflectiveMethodChecker;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.internal.PySrcMain;
import com.google.template.soy.shared.SoyAstCache;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.ToggleRegistry;
import com.google.template.soy.shared.internal.InternalPlugins;
import com.google.template.soy.shared.internal.SoyScopedData;
import com.google.template.soy.shared.internal.SoySimpleScope;
import com.google.template.soy.shared.internal.gencode.GeneratedFile;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyMethodSignature;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CompilationUnit;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.Metadata;
import com.google.template.soy.soytree.Metadata.CompilationUnitAndKind;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.tofu.internal.BaseTofu;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypeRegistryBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Represents a complete set of Soy files for compilation as one bundle. The files may depend on
 * each other but should not have dependencies outside of the set.
 *
 * <p><strong>Note that this implementation is not synchronized.</strong> If multiple threads use a
 * SoyFileSet concurrently, it <i>must</i> be synchronized externally.
 *
 * <p>Note: Soy file (or resource) contents must be encoded in UTF-8.
 */
public final class SoyFileSet {
  private static final Logger logger = Logger.getLogger(SoyFileSet.class.getName());

  /**
   * Creates a builder with the standard set of Soy directives, functions, and types.
   *
   * <p>If you need additional directives, functions, or types, create the Builder instance and then
   * call {@link Builder#addSourceFunction(SoySourceFunction)}.
   *
   * @deprecated Use the command line compilers to generate code instead of this interface. SoySauce
   *     users can get an SoySauce instance via SoySauceBuilder.
   */
  @Deprecated
  public static Builder builder() {
    return new Builder(/* ignored= */ true);
  }

  /**
   * Builder for a {@code SoyFileSet}.
   *
   * <p>Instances of this can be obtained by calling {@link #builder()} or by installing {@link
   * SoyModule} and injecting it.
   */
  public static final class Builder {
    /** The SoyFileSuppliers collected so far in added order, as a set to prevent dupes. */
    private final Map<SourceLogicalPath, SoyFileSupplier> filesBuilder = new LinkedHashMap<>();

    private final ImmutableList.Builder<CompilationUnitAndKind> compilationUnitsBuilder =
        ImmutableList.builder();

    /** Optional AST cache. */
    private SoyAstCache cache = null;

    /** The general compiler options. */
    private SoyGeneralOptions lazyGeneralOptions = null;

    /** The SoyProtoTypeProvider builder that will be built for local type registry. */
    private final SoyTypeRegistryBuilder typeRegistryBuilder = new SoyTypeRegistryBuilder();

    @Nullable private Appendable warningSink;

    private ValidatedConformanceConfig conformanceConfig = ValidatedConformanceConfig.EMPTY;

    private ImmutableList<File> pluginRuntimeJars = ImmutableList.of();

    private CssRegistry cssRegistry = CssRegistry.EMPTY;

    private ToggleRegistry toggleRegistry = ToggleRegistry.EMPTY;

    private boolean skipPluginValidation = false;

    private boolean optimize = true;

    private MethodChecker javaPluginValidator =
        (className, methodName, returnType, arguments) -> Response.error(Code.NO_SUCH_CLASS);

    private Set<SourceLogicalPath> generatedPathsToCheck = ImmutableSet.of();

    private final ImmutableSet.Builder<SoyFunction> soyFunctions = ImmutableSet.builder();
    private final ImmutableSet.Builder<SoyPrintDirective> soyPrintDirectives =
        ImmutableSet.builder();
    private final ImmutableSet.Builder<SoySourceFunctionDescriptor> sourceFunctions =
        ImmutableSet.builder();
    private final ImmutableSet.Builder<SoySourceFunction> sourceMethods = ImmutableSet.builder();

    Builder(boolean ignored) {
      // we use an ignored parameter to prevent guice from creating implicit bindings for this
      // object.
    }

    /**
     * Sets all Soy general options.
     *
     * <p>This must be called before any other setters.
     */
    @CanIgnoreReturnValue
    public Builder setGeneralOptions(SoyGeneralOptions generalOptions) {
      Preconditions.checkState(
          lazyGeneralOptions == null,
          "Call SoyFileSet#setGeneralOptions before any other setters.");
      Preconditions.checkNotNull(generalOptions, "Non-null argument expected.");
      lazyGeneralOptions = generalOptions.clone();
      return this;
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
      SoyScopedData data = new SoySimpleScope();
      return new SoyFileSet(
          data,
          typeRegistryBuilder.build(),
          ImmutableList.<SoyFunction>builder()
              .addAll(InternalPlugins.internalLegacyFunctions())
              .addAll(soyFunctions.build())
              .build(),
          ImmutableList.<SoyPrintDirective>builder()
              .addAll(InternalPlugins.internalDirectives(data))
              .addAll(soyPrintDirectives.build())
              .build(),
          ImmutableList.<SoySourceFunctionDescriptor>builder()
              .addAll(InternalPlugins.internalFunctionDescriptors())
              .addAll(sourceFunctions.build())
              .build(),
          ImmutableList.<SoySourceFunction>builder()
              .addAll(InternalPlugins.internalMethods())
              .addAll(sourceMethods.build())
              .build(),
          ImmutableMap.copyOf(filesBuilder),
          compilationUnitsBuilder.build(),
          getGeneralOptions(),
          cache,
          conformanceConfig,
          warningSink,
          pluginRuntimeJars,
          skipPluginValidation,
          optimize,
          generatedPathsToCheck,
          cssRegistry,
          toggleRegistry,
          javaPluginValidator);
    }

    @CanIgnoreReturnValue
    public Builder setJavaPluginValidator(MethodChecker javaPluginValidator) {
      this.javaPluginValidator = javaPluginValidator;
      return this;
    }

    /**
     * This is useful to get externs working in projects that use in-process Soy compilation via
     * SoyFileSet. Instead of configuring complex plugin validators and the locations of the plugin
     * runtime jars, this just uses reflection. It's less performant but much simpler to setup.
     */
    @CanIgnoreReturnValue
    public Builder setReflectivePluginValidator() {
      this.javaPluginValidator = new ReflectiveMethodChecker();
      return this;
    }

    // Deprecate this?
    @CanIgnoreReturnValue
    public Builder addSourceFunction(SoySourceFunction function) {
      return addSourceFunctionInternal(null, function);
    }

    @CanIgnoreReturnValue
    public Builder addSourceFunction(String pluginTarget, SoySourceFunction function) {
      return addSourceFunctionInternal(Preconditions.checkNotNull(pluginTarget), function);
    }

    /** Adds one {@link SoySourceFunction} to the functions used by this SoyFileSet. */
    @CanIgnoreReturnValue
    private Builder addSourceFunctionInternal(
        @Nullable String pluginTarget, SoySourceFunction function) {
      boolean method = false;
      if (SoyMethodSignature.IS_SOY_METHOD.test(function)) {
        sourceMethods.add(function);
        method = true;
      }
      if (!method || function.getClass().isAnnotationPresent(SoyFunctionSignature.class)) {
        sourceFunctions.add(
            pluginTarget != null
                ? SoySourceFunctionDescriptor.create(pluginTarget, function)
                : SoySourceFunctionDescriptor.createUnknownPlugin(function));
      }
      return this;
    }

    /** Adds many {@link SoySourceFunction}s to the functions used by this SoyFileSet. */
    @CanIgnoreReturnValue
    public Builder addSourceFunctions(Iterable<? extends SoySourceFunction> function) {
      for (SoySourceFunction f : function) {
        addSourceFunctionInternal(null, f);
      }
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addSourceMethod(SoySourceFunction function) {
      Preconditions.checkArgument(SoyMethodSignature.IS_SOY_METHOD.test(function));
      sourceMethods.add(function);
      return this;
    }

    /** Adds one {@link SoyFunction} to the functions used by this SoyFileSet. */
    @CanIgnoreReturnValue
    public Builder addSoyFunction(SoyFunction function) {
      soyFunctions.add(function);
      return this;
    }

    /** Adds many {@link SoyFunction}s to the functions used by this SoyFileSet. */
    @CanIgnoreReturnValue
    public Builder addSoyFunctions(Iterable<? extends SoyFunction> function) {
      soyFunctions.addAll(function);
      return this;
    }

    /** Adds one {@link SoyPrintDirective} to the print directives used by this SoyFileSet. */
    @CanIgnoreReturnValue
    public Builder addSoyPrintDirective(SoyPrintDirective function) {
      soyPrintDirectives.add(function);
      return this;
    }

    /** Adds many {@link SoyPrintDirective}s to the print directives used by this SoyFileSet. */
    @CanIgnoreReturnValue
    public Builder addSoyPrintDirectives(Iterable<? extends SoyPrintDirective> function) {
      soyPrintDirectives.addAll(function);
      return this;
    }

    /**
     * Adds an input Soy file, given a {@code CharSource} for the file content, as well as the
     * desired file path for messages.
     */
    @CanIgnoreReturnValue
    public Builder add(SoyFileSupplier soyFileSupplier) {
      return addFile(soyFileSupplier);
    }

    /**
     * Adds an input Soy file, given a {@code CharSource} for the file content, as well as the
     * desired file path for messages.
     *
     * @param contentSource Source for the Soy file content.
     * @param filePath The path to the Soy file (used for messages only).
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder add(CharSource contentSource, String filePath) {
      return addFile(
          SoyFileSupplier.Factory.create(contentSource, SourceFilePath.create(filePath, filePath)));
    }

    /**
     * Adds an input Soy file, given a resource {@code URL}, as well as the desired file path for
     * messages.
     *
     * @param inputFileUrl The Soy file.
     * @param filePath The path to the Soy file (used for messages only).
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder add(URL inputFileUrl, String filePath) {
      return addFile(
          SoyFileSupplier.Factory.create(
              inputFileUrl, SourceFilePath.create(filePath, inputFileUrl.toString())));
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
     * @deprecated This method is incompatible with imports since the filename is unlikely to be
     *     correct. Please call {@link #add(URL, String)} instead, or better yet, migrate off of
     *     SoyFileSet.
     */
    @CanIgnoreReturnValue
    @Deprecated
    public Builder add(URL inputFileUrl) {
      return add(inputFileUrl, inputFileUrl.toString());
    }

    /**
     * Adds an input Soy file, given the file content provided as a string, as well as the desired
     * file path for messages.
     *
     * @param content The Soy file content.
     * @param filePath The path to the Soy file (used for messages only).
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder add(CharSequence content, String filePath) {
      return addFile(
          SoyFileSupplier.Factory.create(content, SourceFilePath.create(filePath, filePath)));
    }

    /**
     * Adds an input Soy file, given a {@code File}.
     *
     * @param inputFile The Soy file.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder add(File inputFile) {
      return addFile(SoyFileSupplier.Factory.create(inputFile));
    }

    /**
     * Replaces a Soy file already in this builder with another file, returning the original file.
     */
    @CanIgnoreReturnValue
    public SoyFileSupplier clobberFile(SoyFileSupplier contents) {
      SoyFileSupplier previous = filesBuilder.put(contents.getFilePath().asLogicalPath(), contents);
      return Preconditions.checkNotNull(previous);
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
    @CanIgnoreReturnValue
    public Builder setSoyAstCache(SoyAstCache cache) {
      this.cache = cache;
      return this;
    }

    /**
     * Sets experimental features. These features are unreleased and are not generally available.
     *
     * @param experimentalFeatures
     * @return This builder.
     */
    @CanIgnoreReturnValue
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
    @CanIgnoreReturnValue
    public Builder disableOptimizer() {
      optimize = false;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setGeneratedPathsToCheck(Set<SourceLogicalPath> generatedPaths) {
      this.generatedPathsToCheck = generatedPaths;
      return this;
    }

    /**
     * Registers a collection of protocol buffer descriptors. This makes all the types defined in
     * the provided descriptors available to use in soy.
     */
    @CanIgnoreReturnValue
    public Builder addProtoDescriptors(SoyFileKind depKind, GenericDescriptor... descriptors) {
      return addProtoDescriptors(depKind, Arrays.asList(descriptors));
    }

    /**
     * Registers a collection of protocol buffer descriptors. This makes all the types defined in
     * the provided descriptors available to use in soy.
     */
    @CanIgnoreReturnValue
    public Builder addProtoDescriptors(
        SoyFileKind depKind, Iterable<? extends GenericDescriptor> descriptors) {
      typeRegistryBuilder.addDescriptors(depKind, descriptors);
      return this;
    }

    /**
     * @deprecated Use {@link #addProtoDescriptors(SoyFileKind, GenericDescriptor...)} instead.
     */
    @InlineMe(
        replacement = "this.addProtoDescriptors(SoyFileKind.DEP, descriptors)",
        imports = "com.google.template.soy.base.internal.SoyFileKind")
    @CanIgnoreReturnValue
    @Deprecated
    public Builder addProtoDescriptors(GenericDescriptor... descriptors) {
      return addProtoDescriptors(SoyFileKind.DEP, descriptors);
    }

    /**
     * @deprecated Use {@link #addProtoDescriptors(SoyFileKind, Iterable)} instead.
     */
    @InlineMe(
        replacement = "this.addProtoDescriptors(SoyFileKind.DEP, descriptors)",
        imports = "com.google.template.soy.base.internal.SoyFileKind")
    @CanIgnoreReturnValue
    @Deprecated
    public Builder addProtoDescriptors(Iterable<? extends GenericDescriptor> descriptors) {
      return addProtoDescriptors(SoyFileKind.DEP, descriptors);
    }

    /** Registers a conformance config proto. */
    @CanIgnoreReturnValue
    Builder setConformanceConfig(ValidatedConformanceConfig config) {
      checkNotNull(config);
      this.conformanceConfig = config;
      return this;
    }

    @CanIgnoreReturnValue
    Builder addCompilationUnit(SoyFileKind fileKind, CompilationUnit compilationUnit) {
      compilationUnitsBuilder.add(
          Metadata.CompilationUnitAndKind.create(fileKind, compilationUnit));
      return this;
    }

    @CanIgnoreReturnValue
    private Builder addFile(SoyFileSupplier supplier) {
      SoyFileSupplier previous = filesBuilder.put(supplier.getFilePath().asLogicalPath(), supplier);
      if (previous != null) {
        throw new IllegalArgumentException("Duplicate path " + supplier.getFilePath());
      }
      return this;
    }

    /**
     * Configures a place to write warnings for successful compilations.
     *
     * <p>For compilation failures warnings are reported along with the errors, by throwing an
     * exception. The default is to report warnings to the logger for SoyFileSet.
     */
    @CanIgnoreReturnValue
    public Builder setWarningSink(Appendable warningSink) {
      this.warningSink = checkNotNull(warningSink);
      return this;
    }

    /**
     * Sets the location of the jars containing plugin runtime code, for use validating plugin
     * MethodRefs.
     */
    @CanIgnoreReturnValue
    Builder setPluginRuntimeJars(List<File> pluginRuntimeJars) {
      this.pluginRuntimeJars = ImmutableList.copyOf(pluginRuntimeJars);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setCssRegistry(CssRegistry cssRegistry) {
      this.cssRegistry = checkNotNull(cssRegistry);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setToggleRegistry(ToggleRegistry toggleRegistry) {
      this.toggleRegistry = checkNotNull(toggleRegistry);
      return this;
    }

    /**
     * Sets whether or not to skip plugin validation. Defaults to false. This should usually not be
     * set unless you're doing something real funky.
     */
    @CanIgnoreReturnValue
    public Builder setSkipPluginValidation(boolean skipPluginValidation) {
      this.skipPluginValidation = skipPluginValidation;
      return this;
    }
  }

  private final SoyScopedData scopedData;

  private final SoyTypeRegistry typeRegistry;
  private final ImmutableMap<SourceLogicalPath, SoyFileSupplier> soyFileSuppliers;
  private final ImmutableList<CompilationUnitAndKind> compilationUnits;

  /** Optional soy tree cache for faster recompile times. */
  @Nullable private final SoyAstCache cache;

  private final SoyGeneralOptions generalOptions;

  private final ValidatedConformanceConfig conformanceConfig;
  private final ImmutableList<File> pluginRuntimeJars;
  private final CssRegistry cssRegistry;
  private final ToggleRegistry toggleRegistry;

  private final ImmutableList<SoyFunction> soyFunctions;
  private final ImmutableList<SoyPrintDirective> printDirectives;
  private final ImmutableList<SoySourceFunctionDescriptor> soySourceFunctions;
  private final ImmutableList<SoySourceFunction> soyMethods;

  private final boolean skipPluginValidation;

  private final boolean optimize;
  private final ImmutableSet<SourceLogicalPath> generatedPathsToCheck;
  private final MethodChecker javaMethodChecker;

  /** For reporting errors during parsing. */
  private ErrorReporter errorReporter;

  @Nullable private final Appendable warningSink;

  SoyFileSet(
      SoyScopedData apiCallScopeProvider,
      SoyTypeRegistry typeRegistry,
      ImmutableList<SoyFunction> soyFunctions,
      ImmutableList<SoyPrintDirective> printDirectives,
      ImmutableList<SoySourceFunctionDescriptor> soySourceFunctions,
      ImmutableList<SoySourceFunction> soyMethods,
      ImmutableMap<SourceLogicalPath, SoyFileSupplier> soyFileSuppliers,
      ImmutableList<CompilationUnitAndKind> compilationUnits,
      SoyGeneralOptions generalOptions,
      @Nullable SoyAstCache cache,
      ValidatedConformanceConfig conformanceConfig,
      @Nullable Appendable warningSink,
      ImmutableList<File> pluginRuntimeJars,
      boolean skipPluginValidation,
      boolean optimize,
      Set<SourceLogicalPath> generatedPathsToCheck,
      CssRegistry cssRegistry,
      ToggleRegistry toggleRegistry,
      MethodChecker javaMethodChecker) {
    this.scopedData = apiCallScopeProvider;
    this.typeRegistry = typeRegistry;
    this.soyFileSuppliers = soyFileSuppliers;
    this.compilationUnits = compilationUnits;
    this.cache = cache;
    this.generalOptions = generalOptions.clone();
    this.soyFunctions = InternalPlugins.filterDuplicateFunctions(soyFunctions);
    this.printDirectives = InternalPlugins.filterDuplicateDirectives(printDirectives);
    this.soySourceFunctions = soySourceFunctions;
    this.soyMethods = soyMethods;
    this.conformanceConfig = checkNotNull(conformanceConfig);
    this.warningSink = warningSink;
    this.pluginRuntimeJars = pluginRuntimeJars;
    this.skipPluginValidation = skipPluginValidation;
    this.optimize = optimize;
    this.generatedPathsToCheck = ImmutableSet.copyOf(generatedPathsToCheck);
    this.cssRegistry = cssRegistry;
    this.toggleRegistry = toggleRegistry;
    this.javaMethodChecker = javaMethodChecker;
  }

  /** Returns the list of suppliers for the input Soy files. For testing use only! */
  @VisibleForTesting
  ImmutableMap<SourceLogicalPath, SoyFileSupplier> getSoyFileSuppliersForTesting() {
    return soyFileSuppliers;
  }

  public ErrorFormatter getErrorFormatterWithSnippets() {
    return ErrorFormatterImpl.create().withSources(soyFileSuppliers);
  }

  ImmutableList<SourceLogicalPath> getSourceFilePaths() {
    return soyFileSuppliers.keySet().asList();
  }

  @VisibleForTesting
  SoyTypeRegistry getTypeRegistryForTesting() {
    return typeRegistry;
  }

  private boolean isCompiling;

  /** Template pattern for any public or package visible entry point method that returns a value. */
  private <T> T entryPoint(Supplier<T> variant) {
    synchronized (this) {
      if (isCompiling) {
        // TODO(lukes): upgrade to an exception
        logger.log(
            Level.SEVERE,
            "concurrent use of a SoyFileSet object is undefined behavior.",
            new Exception());
      }
      isCompiling = true;
    }
    resetErrorReporter();
    try {
      T rv = variant.get();
      throwIfErrorsPresent();
      reportWarnings();
      return rv;
    } catch (SoyCompilationException | SoyInternalCompilerException e) {
      throw e;
    } catch (RuntimeException e) {
      if (errorReporter != null && errorReporter.hasErrorsOrWarnings()) {
        throw new SoyInternalCompilerException(
            Iterables.concat(errorReporter.getErrors(), errorReporter.getWarnings()),
            getErrorFormatterWithSnippets(),
            e);
      } else {
        throw e;
      }
    } finally {
      synchronized (this) {
        isCompiling = false;
      }
    }
  }

  /** Template pattern for any public or package visible entry point method that is void. */
  private void entryPointVoid(Runnable variant) {
    Object unused =
        entryPoint(
            () -> {
              variant.run();
              return null;
            });
  }

  /**
   * Generates Java classes containing template invocation builders for setting param values. There
   * will be one Java file per Soy file.
   *
   * @param javaPackage The Java package for the generated classes.
   * @return A list of generated files to write (of the form "<*>FooSoyTemplates.java").
   * @throws SoyCompilationException If compilation fails.
   */
  ImmutableList<GeneratedFile> generateBuilders(String javaPackage, KytheMode kytheMode) {
    return entryPoint(
        () -> {
          ParseResult result = parseWithoutOptimizingOrDesugaringHtml();
          throwIfErrorsPresent();
          SoyFileSetNode soyTree = result.fileSet();

          // Generate template invocation builders for the soy tree.
          return new GenerateBuildersVisitor(
                  errorReporter, javaPackage, kytheMode, result.registry())
              .exec(soyTree);
        });
  }

  /**
   * Generates Java classes containing parse info (param names, template names, meta info). There
   * will be one Java class per Soy file.
   *
   * @param javaPackage The Java package for the generated classes.
   * @param kytheMode The Kythe output mode.
   * @param javaClassNameSource Source of the generated class names. Must be one of "filename",
   *     "namespace", or "generic".
   * @return A list of generated files to write (of the form "<*>SoyInfo.java").
   * @throws SoyCompilationException If compilation fails.
   */
  ImmutableList<GeneratedFile> generateParseInfo(
      String javaPackage, KytheMode kytheMode, String javaClassNameSource) {
    return entryPoint(
        () -> {
          ParseResult result = parseWithoutOptimizingOrDesugaringHtml();
          throwIfErrorsPresent();

          SoyFileSetNode soyTree = result.fileSet();
          FileSetMetadata registry = result.registry();

          // Do renaming of package-relative class names.
          return new GenerateParseInfoVisitor(javaPackage, kytheMode, javaClassNameSource, registry)
              .exec(soyTree);
        });
  }

  /**
   * Validates any user SoySourceFunction plugins.
   *
   * @return The source code of the TSX translation.
   */
  String validateUserPlugins(boolean validateJavaImpls, boolean translateToTsx) {
    Preconditions.checkArgument(!translateToTsx || validateJavaImpls);
    return entryPoint(
        () -> {
          // First resolve all the plugins to ensure they're well-formed (no bad names, etc.).
          new PluginResolver(
              PluginResolver.Mode.REQUIRE_DEFINITIONS,
              printDirectives,
              soyFunctions,
              soySourceFunctions,
              soyMethods,
              errorReporter);
          // if constructing the resolver found an error, bail out now.
          throwIfErrorsPresent();

          if (validateJavaImpls) {
            ImmutableList<SoySourceFunction> userSuppliedFunctions =
                soySourceFunctions.stream()
                    .filter(desc -> !desc.isInternal())
                    .map(SoySourceFunctionDescriptor::soySourceFunction)
                    .collect(toImmutableList());
            new PluginValidator(errorReporter, typeRegistry, pluginRuntimeJars)
                .validate(userSuppliedFunctions);
            throwIfErrorsPresent();
          }
          return "";
        });
  }

  /** A simple tool to enforce conformance and only conformance. */
  void checkConformance() {
    entryPointVoid(
        () ->
            // to check conformance we only need to run as much as it takes to execute the
            // SoyConformance pass.
            parse(
                passManagerBuilder()
                    .allowUnknownJsGlobals()
                    .astRewrites(AstRewrites.KYTHE)
                    .desugarHtmlNodes(false)
                    .optimize(false)
                    .addHtmlAttributesForDebugging(false)
                    // TODO(lukes): kill the pass continuation mechanism
                    .addPassContinuationRule(
                        SoyConformancePass.class, PassContinuationRule.STOP_AFTER_PASS)
                    .validateJavaMethods(false)));
  }

  /**
   * Extracts all messages from this Soy file set into a SoyMsgBundle (which can then be turned into
   * an extracted messages file with the help of a SoyMsgBundleHandler).
   *
   * @return A SoyMsgBundle containing all the extracted messages (locale "en").
   * @throws SoyCompilationException If compilation fails.
   * @deprecated Use the command line APIs to extract messages instead
   */
  @Deprecated
  public SoyMsgBundle extractMsgs() {
    return entryPoint(this::doExtractMsgs);
  }

  /**
   * Extracts all messages from this Soy file set and writes the messages to an output sink.
   *
   * @param msgBundleHandler Handler to write the messages.
   * @param options Options to configure how to write the extracted messages.
   * @param output Where to write the extracted messages.
   */
  void extractAndWriteMsgs(
      SoyMsgBundleHandler msgBundleHandler, OutputFileOptions options, ByteSink output) {
    entryPointVoid(
        () -> {
          SoyMsgBundle bundle = doExtractMsgs();
          try {
            msgBundleHandler.writeExtractedMsgs(bundle, options, output, errorReporter);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  /** Performs the parsing and extraction logic. */
  private SoyMsgBundle doExtractMsgs() {
    SoyFileSetNode soyTree =
        parse(
                passManagerBuilder()
                    .allowUnknownGlobals()
                    .allowUnknownJsGlobals()
                    // Skip optimization, we could run it but it seems to be a waste of time
                    .optimize(false)
                    .desugarHtmlNodes(false)
                    .validateJavaMethods(false))
            .fileSet();
    throwIfErrorsPresent();
    SoyMsgBundle bundle = new ExtractMsgsVisitor(errorReporter).exec(soyTree);
    throwIfErrorsPresent();
    return bundle;
  }

  /**
   * Compiles this Soy file set into a Java object (type {@code SoyTofu}) capable of rendering the
   * compiled templates.
   *
   * @return The resulting {@code SoyTofu} object.
   * @throws SoyCompilationException If compilation fails.
   * @deprecated Use SoySauce instead. All users should be able to switch from
   *     SoyFileSet.compileToTofu() to SoyFileSet.compileTemplates(). To use the support for
   *     precompilation see SoySauceBuilder.
   */
  @Deprecated
  public SoyTofu compileToTofu() {
    return compileToTofu(ImmutableMap.of());
  }

  /**
   * Compiles this Soy file set into a Java object (type {@code SoyTofu}) capable of rendering the
   * compiled templates.
   *
   * @return The resulting {@code SoyTofu} object.
   * @throws SoyCompilationException If compilation fails.
   * @deprecated Use SoySauce instead. All users should be able to switch from
   *     SoyFileSet.compileToTofu() to SoyFileSet.compileTemplates(). To use the support for
   *     precompilation see SoySauceBuilder.
   */
  @Deprecated
  public SoyTofu compileToTofu(Map<String, ? extends Supplier<Object>> pluginInstances) {
    return entryPoint(
        () -> {
          ServerCompilationPrimitives primitives = compileForServerRendering();
          throwIfErrorsPresent();
          return doCompileToTofu(primitives, pluginInstances);
        });
  }

  /** Helper method to compile SoyTofu from {@link ServerCompilationPrimitives} */
  private SoyTofu doCompileToTofu(
      ServerCompilationPrimitives primitives,
      Map<String, ? extends Supplier<Object>> pluginInstances) {
    return new BaseTofu(
        scopedData.enterable(), primitives.soyTree, PluginInstances.of(pluginInstances));
  }

  /**
   * Compiles this Soy file set into a set of java classes implementing the {@link SoySauce}
   * interface.
   *
   * <p>This is useful for implementing 'edit refresh' workflows. Most production usecases should
   * use the command line interface to 'ahead of time' compile templates to jar files and then use
   * {@code SoySauceBuilder} to get access to a {@link SoySauce} object without invoking the
   * compiler. This will allow applications to avoid invoking the soy compiler at runtime which can
   * be relatively slow.
   *
   * @return A set of compiled templates
   * @throws SoyCompilationException If compilation fails.
   */
  public SoySauce compileTemplates() {
    return compileTemplates(ImmutableMap.of());
  }

  /**
   * Compiles this Soy file set into a set of java classes implementing the {@link SoySauce}
   * interface.
   *
   * <p>This is useful for implementing 'edit refresh' workflows. Most production usecases should
   * use the command line interface to 'ahead of time' compile templates to jar files and then use
   * {@code SoySauceBuilder} to get access to a {@link SoySauce} object without invoking the
   * compiler. This will allow applications to avoid invoking the soy compiler at runtime which can
   * be relatively slow.
   *
   * @return A set of compiled templates
   * @throws SoyCompilationException If compilation fails.
   */
  public SoySauce compileTemplates(Map<String, ? extends Supplier<Object>> pluginInstances) {
    return entryPoint(
        () -> {
          ServerCompilationPrimitives primitives = compileForServerRendering();
          throwIfErrorsPresent();
          return doCompileSoySauce(primitives, PluginInstances.of(pluginInstances));
        });
  }

  public CssRegistry getCssRegistry() {
    return cssRegistry;
  }

  /**
   * Compiles this Soy file set into a set of java classes implementing the {@link
   * com.google.template.soy.jbcsrc.shared.CompiledTemplate} interface and writes them out to the
   * given ByteSink as a JAR file.
   *
   * @throws SoyCompilationException If compilation fails.
   */
  void compileToJar(ByteSink jarTarget, Optional<ByteSink> srcJarTarget) {
    entryPointVoid(
        () -> {
          ServerCompilationPrimitives primitives = compileForServerRendering();
          try {
            BytecodeCompiler.compileToJar(
                primitives.soyTree, errorReporter, typeRegistry, jarTarget, primitives.registry);
            if (srcJarTarget.isPresent()) {
              BytecodeCompiler.writeSrcJar(
                  primitives.soyTree, soyFileSuppliers, srcJarTarget.get());
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  /** Helper method to compile SoySauce from {@link ServerCompilationPrimitives} */
  private SoySauce doCompileSoySauce(
      ServerCompilationPrimitives primitives, PluginInstances pluginInstances) {
    Optional<CompiledTemplates> templates =
        BytecodeCompiler.compile(
            primitives.registry, primitives.soyTree, errorReporter, soyFileSuppliers, typeRegistry);

    throwIfErrorsPresent();

    return new SoySauceImpl(templates.get(), soyFunctions, printDirectives, pluginInstances);
  }

  /**
   * A tuple of the outputs of shared compiler passes that are needed to produce SoyTofu or
   * SoySauce.
   */
  private static final class ServerCompilationPrimitives {
    final SoyFileSetNode soyTree;
    final FileSetMetadata registry;

    ServerCompilationPrimitives(FileSetMetadata registry, SoyFileSetNode soyTree) {
      this.registry = registry;
      this.soyTree = soyTree;
    }
  }

  /** Runs common compiler logic shared by tofu and jbcsrc backends. */
  private ServerCompilationPrimitives compileForServerRendering() {
    ParseResult result = parse();
    throwIfErrorsPresent();

    SoyFileSetNode soyTree = result.fileSet();
    FileSetMetadata registry = result.registry();
    // Clear the SoyDoc strings because they use unnecessary memory, unless we have a cache, in
    // which case it is pointless.
    if (cache == null) {
      new ClearSoyDocStringsVisitor().exec(soyTree);
    }

    throwIfErrorsPresent();
    return new ServerCompilationPrimitives(registry, soyTree);
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
   * @deprecated Do not call. Use the command line API.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  public List<String> compileToJsSrc(
      SoyJsSrcOptions jsSrcOptions, @Nullable SoyMsgBundle msgBundle) {
    return compileToJsSrcInternal(jsSrcOptions, msgBundle);
  }

  List<String> compileToJsSrcInternal(
      SoyJsSrcOptions jsSrcOptions, @Nullable SoyMsgBundle msgBundle) {
    return entryPoint(
        () -> {
          PassManager.Builder builder =
              passManagerBuilder()
                  .allowUnknownJsGlobals()
                  .desugarHtmlNodes(false)
                  .validateJavaMethods(false);
          ParseResult result = parse(builder);
          throwIfErrorsPresent();
          FileSetMetadata registry = result.registry();
          SoyFileSetNode fileSet = result.fileSet();
          return new JsSrcMain(scopedData.enterable(), typeRegistry)
              .genJsSrc(fileSet, registry, jsSrcOptions, msgBundle, errorReporter);
        });
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
  List<String> compileToIncrementalDomSrcInternal(SoyIncrementalDomSrcOptions jsSrcOptions) {
    return entryPoint(
        () -> {
          // For incremental dom backend, we don't desugar HTML nodes since it requires HTML
          // context.
          ParseResult result =
              parse(
                  passManagerBuilder()
                      .desugarHtmlNodes(false)
                      .allowUnknownJsGlobals()
                      .desugarIdomFeatures(false)
                      .validateJavaMethods(false));
          throwIfErrorsPresent();
          return new IncrementalDomSrcMain(scopedData.enterable(), typeRegistry)
              .genJsSrc(result.fileSet(), result.registry(), jsSrcOptions, errorReporter);
        });
  }

  /**
   * Compiles this Soy file set into Python source code files and writes these Python files to disk.
   *
   * @param pySrcOptions The compilation options for the Python Src output target.
   * @throws SoyCompilationException If compilation fails.
   * @throws RuntimeException If there is an error in opening/reading a message file or
   *     opening/writing an output JS file.
   */
  List<String> compileToPySrcFiles(SoyPySrcOptions pySrcOptions) {
    return entryPoint(
        () -> {
          try {
            ParseResult result = parse(passManagerBuilder().validateJavaMethods(false));
            throwIfErrorsPresent();
            return new PySrcMain(scopedData.enterable())
                .genPyFiles(result.fileSet(), result.registry(), pySrcOptions, errorReporter);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @AutoValue
  abstract static class HeaderResult {
    abstract SoyFileSetNode fileSet();

    abstract FileSetMetadata templateRegistry();

    abstract CssRegistry cssRegistry();
  }

  /**
   * Performs the minimal amount of work needed to calculate TemplateMetadata objects for header
   * compilation.
   */
  HeaderResult compileMinimallyForHeaders() {
    return entryPoint(
        () -> {
          ParseResult parseResult =
              parse(
                  passManagerBuilder()
                      // Because we allow this for JS generated templates, we allow this for
                      // headers.
                      .allowUnknownJsGlobals()
                      // Only run passes that not cross template checking.
                      .addPassContinuationRule(
                          CheckTemplateHeaderVarsPass.class, PassContinuationRule.STOP_BEFORE_PASS)
                      .validateJavaMethods(false),
                  typeRegistry);
          // throw before accessing registry() to make sure it is definitely available.
          throwIfErrorsPresent();
          return new AutoValue_SoyFileSet_HeaderResult(
              parseResult.fileSet(), parseResult.registry(), cssRegistry);
        });
  }

  /** Returns the result of {@link #compileForAnalysis}. */
  @AutoValue
  public abstract static class AnalysisResult {
    AnalysisResult() {}

    /**
     * The template registry, will be empty if errors occurred early and it couldn't be constructed.
     */
    public abstract Optional<FileSetMetadata> registry();

    /** The full parsed AST. */
    public abstract SoyFileSetNode fileSet();

    public abstract CssRegistry cssRegistry();

    public abstract ToggleRegistry toggleRegistry();

    /** Compiler warnings. This will include errors if {@code treatErrorsAsWarnings} was set. */
    public abstract ImmutableList<SoyError> warnings();
  }

  /** Performs enough work to retrieve all possible warnings in a compile. */
  public AnalysisResult compileForAnalysis(boolean treatErrorsAsWarnings, AstRewrites astRewrites) {
    return entryPoint(
        () -> {
          ParseResult result = parse(passManagerBuilderForAnalysis(astRewrites));
          ImmutableList<SoyError> warnings;
          if (treatErrorsAsWarnings) {
            // we are essentially ignoring errors
            resetErrorReporter();
            warnings =
                ImmutableList.<SoyError>builder()
                    .addAll(errorReporter.getErrors())
                    .addAll(errorReporter.getWarnings())
                    .build();
          } else {
            warnings = errorReporter.getWarnings();
            throwIfErrorsPresent();
          }
          return new AutoValue_SoyFileSet_AnalysisResult(
              result.hasRegistry() ? Optional.of(result.registry()) : Optional.empty(),
              result.fileSet(),
              result.cssRegistry(),
              toggleRegistry,
              warnings);
        });
  }

  /** Parses the file set with the options we need for analysis. */
  private PassManager.Builder passManagerBuilderForAnalysis(AstRewrites astRewrites) {
    return passManagerBuilder()
        // the optimizer mutates the AST heavily which inhibits certain source analysis rules.
        .optimize(false)
        .astRewrites(astRewrites)
        // skip adding extra attributes
        .addHtmlAttributesForDebugging(false)
        // skip the autoescaper
        .insertEscapingDirectives(false)
        .desugarHtmlNodes(false)
        // TODO(lukes): This is needed for kythe apparently
        .allowUnknownGlobals()
        .allowUnknownJsGlobals()
        .validateJavaMethods(false);
  }

  /**
   * Parses the file set with the options we need for writing generated java *SoyInfo and invocation
   * builders.
   */
  private ParseResult parseWithoutOptimizingOrDesugaringHtml() {
    return parse(passManagerBuilderWithoutOptimizingOrDesugaringHtml());
  }

  // Parse the current file set.
  @VisibleForTesting
  public ParseResult parse() {
    return parse(passManagerBuilder());
  }

  private ParseResult parse(PassManager.Builder builder) {
    return parse(builder, typeRegistry);
  }

  private ParseResult parse(PassManager.Builder builder, SoyTypeRegistry typeRegistry) {
    return SoyFileSetParser.newBuilder()
        .setCache(cache)
        .setSoyFileSuppliers(soyFileSuppliers)
        .setCompilationUnits(compilationUnits)
        .setCssRegistry(cssRegistry)
        .setToggleRegistry(toggleRegistry)
        .setTypeRegistry(typeRegistry)
        .setPassManager(builder.setTypeRegistry(typeRegistry).build())
        .setErrorReporter(errorReporter)
        .build()
        .parse();
  }

  ParseResult parseInEntryPoint() {
    return entryPoint(this::parse);
  }

  private PassManager.Builder passManagerBuilder() {
    return new PassManager.Builder()
        .setGeneralOptions(generalOptions)
        .optimize(optimize)
        .setGeneratedPathsToCheck(generatedPathsToCheck)
        .setSoyPrintDirectives(printDirectives)
        .setCssRegistry(cssRegistry)
        .setToggleRegistry(toggleRegistry)
        .setErrorReporter(errorReporter)
        .setJavaPluginValidator(javaMethodChecker)
        .setConformanceConfig(conformanceConfig)
        .setPluginResolver(buildPluginResolver());
  }

  private PluginResolver buildPluginResolver() {
    return new PluginResolver(
        skipPluginValidation
            ? PluginResolver.Mode.ALLOW_UNDEFINED
            : PluginResolver.Mode.REQUIRE_DEFINITIONS,
        printDirectives,
        soyFunctions,
        soySourceFunctions,
        soyMethods,
        errorReporter);
  }

  private PassManager.Builder passManagerBuilderWithoutOptimizingOrDesugaringHtml() {
    // N.B. we do not run the optimizer here for 2 reasons:
    // 1. it would just waste time, since we are not running code generation the optimization
    //    work doesn't help anything
    // 2. it potentially removes metadata from the tree by precalculating expressions. For
    //     example, trivial print nodes are evaluated, which can remove globals from the tree,
    //     but the gencode needs to find these so that their proto types can be used to bootstrap
    //     development mode compilation.
    return passManagerBuilder()
        .optimize(false)
        // Don't desugar, this is a bit of a waste of time and it destroys type
        // information about @state parameters
        .desugarHtmlNodes(false);
  }

  /**
   * This method resets the error reporter field in preparation to a new compiler invocation.
   *
   * <p>This method should be called at the beginning of every entry point into SoyFileSet.
   */
  @VisibleForTesting
  public synchronized void resetErrorReporter() {
    errorReporter = ErrorReporter.create();
  }

  private void throwIfErrorsPresent() {
    if (errorReporter.hasErrors()) {
      // if we are reporting errors we should report warnings at the same time.
      Iterable<SoyError> errors =
          Iterables.concat(errorReporter.getErrors(), errorReporter.getWarnings());
      // clear the field to ensure that error reporters can't leak between compilations
      errorReporter = null;
      throw new SoyCompilationException(errors, getErrorFormatterWithSnippets());
    }
  }

  /**
   * Reports warnings ot the user configured warning sink. Should be called at the end of successful
   * compiles.
   */
  private void reportWarnings() {
    ImmutableList<SoyError> warnings = errorReporter.getWarnings();
    if (warnings.isEmpty()) {
      return;
    }
    // this is a custom feature used by the integration test suite.
    if (generalOptions.getExperimentalFeatures().contains("testonly_throw_on_warnings")) {
      errorReporter = null;
      throw new SoyCompilationException(warnings, getErrorFormatterWithSnippets());
    }
    String formatted = SoyErrors.formatErrors(warnings, getErrorFormatterWithSnippets());
    if (warningSink != null) {
      try {
        warningSink.append(formatted);
      } catch (IOException ioe) {
        System.err.println("error while printing warnings");
        ioe.printStackTrace();
      }
    } else {
      logger.warning(formatted);
    }
  }
}
