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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSink;
import com.google.common.io.CharSource;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.SoyFileSetParser.CompilationUnitAndKind;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.base.internal.TriState;
import com.google.template.soy.base.internal.VolatileSoyFileSupplier;
import com.google.template.soy.conformance.ValidatedConformanceConfig;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyCompilationException;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.jbcsrc.BytecodeCompiler;
import com.google.template.soy.jbcsrc.api.SoySauce;
import com.google.template.soy.jbcsrc.api.SoySauceImpl;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.JsSrcMain;
import com.google.template.soy.logging.LoggingConfig;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.msgs.SoyMsgBundleHandler.OutputFileOptions;
import com.google.template.soy.msgs.internal.ExtractMsgsVisitor;
import com.google.template.soy.parseinfo.passes.GenerateParseInfoVisitor;
import com.google.template.soy.passes.ClearSoyDocStringsVisitor;
import com.google.template.soy.passes.PassManager;
import com.google.template.soy.passes.PassManager.PassContinuationRule;
import com.google.template.soy.passes.PluginResolver;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.internal.PySrcMain;
import com.google.template.soy.shared.SoyAstCache;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.internal.InternalPlugins;
import com.google.template.soy.shared.internal.SoyScopedData;
import com.google.template.soy.shared.internal.SoySimpleScope;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CompilationUnit;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.tofu.internal.BaseTofu;
import com.google.template.soy.types.SoyTypeRegistry;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Represents a complete set of Soy files for compilation as one bundle. The files may depend on
 * each other but should not have dependencies outside of the set.
 *
 * <p>Note: Soy file (or resource) contents must be encoded in UTF-8.
 *
 */
public final class SoyFileSet {
  private static final Logger logger = Logger.getLogger(SoyFileSet.class.getName());

  /**
   * Creates a builder with the standard set of Soy directives, functions, and types.
   *
   * <p>If you need additional directives, functions, or types, create the Builder instance and then
   * call {@link Builder#addSourceFunction(SoySourceFunction)}.
   */
  public static Builder builder() {
    return new Builder(
        new CoreDependencies(new SoySimpleScope(), ImmutableMap.of(), ImmutableMap.of()));
  }

  // Implementation detail of SoyFileSet.Builder.
  // having it as its own 'parameter' class removes a small amount of boilerplate.
  static final class CoreDependencies {
    private final SoyScopedData scopedData;
    private final ImmutableMap<String, SoyFunction> pluginFunctions;
    private final ImmutableMap<String, SoyPrintDirective> pluginDirectives;

    @Inject
    CoreDependencies(
        SoyScopedData scopedData,
        ImmutableMap<String, ? extends SoyFunction> pluginFunctions,
        ImmutableMap<String, ? extends SoyPrintDirective> pluginDirectives) {
      this.scopedData = scopedData;
      this.pluginFunctions = ImmutableMap.copyOf(pluginFunctions);
      this.pluginDirectives = ImmutableMap.copyOf(pluginDirectives);
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
    private final ImmutableMap.Builder<String, SoyFileSupplier> filesBuilder =
        ImmutableMap.builder();

    private final ImmutableList.Builder<CompilationUnitAndKind> compilationUnitsBuilder =
        ImmutableList.builder();

    /** Optional AST cache. */
    private SoyAstCache cache;

    /** The general compiler options. */
    private SoyGeneralOptions lazyGeneralOptions;

    private final CoreDependencies coreDependencies;

    /** The SoyProtoTypeProvider builder that will be built for local type registry. */
    private final SoyTypeRegistry.Builder typeRegistryBuilder = new SoyTypeRegistry.Builder();

    @Nullable private Appendable warningSink;

    private ValidatedConformanceConfig conformanceConfig = ValidatedConformanceConfig.EMPTY;

    private ValidatedLoggingConfig loggingConfig = ValidatedLoggingConfig.EMPTY;

    private final ImmutableSet.Builder<SoyFunction> extraSoyFunctions = ImmutableSet.builder();
    private final ImmutableSet.Builder<SoyPrintDirective> extraSoyPrintDirectives =
        ImmutableSet.builder();
    private final ImmutableSet.Builder<SoySourceFunction> extraSourceFunctions =
        ImmutableSet.builder();

    Builder(CoreDependencies coreDependencies) {
      this.coreDependencies = coreDependencies;
      this.cache = null;
      this.lazyGeneralOptions = null;
    }

    /**
     * Sets all Soy general options.
     *
     * <p>This must be called before any other setters.
     */
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
      return new SoyFileSet(
          coreDependencies.scopedData,
          typeRegistryBuilder.build(),
          ImmutableMap.<String, SoyFunction>builder()
              .putAll(InternalPlugins.internalLegacyFunctionMap())
              .putAll(coreDependencies.pluginFunctions)
              .putAll(InternalPlugins.fromLegacyFunctions(extraSoyFunctions.build()))
              .build(),
          ImmutableMap.<String, SoyPrintDirective>builder()
              .putAll(InternalPlugins.internalDirectiveMap(coreDependencies.scopedData))
              .putAll(coreDependencies.pluginDirectives)
              .putAll(InternalPlugins.fromDirectives(extraSoyPrintDirectives.build()))
              .build(),
          ImmutableMap.<String, SoySourceFunction>builder()
              .putAll(InternalPlugins.internalFunctionMap())
              .putAll(InternalPlugins.fromFunctions(extraSourceFunctions.build()))
              .build(),
          filesBuilder.build(),
          compilationUnitsBuilder.build(),
          getGeneralOptions(),
          cache,
          conformanceConfig,
          loggingConfig,
          warningSink);
    }

    /** Adds one {@link SoySourceFunction} to the functions used by this SoyFileSet. */
    public Builder addSourceFunction(SoySourceFunction function) {
      extraSourceFunctions.add(function);
      return this;
    }

    /** Adds many {@link SoySourceFunction}s to the functions used by this SoyFileSet. */
    public Builder addSourceFunctions(Iterable<? extends SoySourceFunction> function) {
      extraSourceFunctions.addAll(function);
      return this;
    }

    /** Adds one {@link SoyFunction} to the functions used by this SoyFileSet. */
    public Builder addSoyFunction(SoyFunction function) {
      extraSoyFunctions.add(function);
      return this;
    }

    /** Adds many {@link SoyFunction}s to the functions used by this SoyFileSet. */
    public Builder addSoyFunctions(Iterable<? extends SoyFunction> function) {
      extraSoyFunctions.addAll(function);
      return this;
    }

    /** Adds one {@link SoyPrintDirective} to the print directives used by this SoyFileSet. */
    public Builder addSoyPrintDirective(SoyPrintDirective function) {
      extraSoyPrintDirectives.add(function);
      return this;
    }

    /** Adds many {@link SoyPrintDirective}s to the print directives used by this SoyFileSet. */
    public Builder addSoyPrintDirectives(Iterable<? extends SoyPrintDirective> function) {
      extraSoyPrintDirectives.addAll(function);
      return this;
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
      return addFile(SoyFileSupplier.Factory.create(contentSource, filePath));
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
      return addFile(SoyFileSupplier.Factory.create(inputFileUrl, filePath));
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
      return addFile(SoyFileSupplier.Factory.create(inputFileUrl));
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
      return addFile(SoyFileSupplier.Factory.create(content, filePath));
    }

    /**
     * Adds an input Soy file, given a {@code File}.
     *
     * @param inputFile The Soy file.
     * @return This builder.
     */
    public Builder add(File inputFile) {
      return addFile(SoyFileSupplier.Factory.create(inputFile));
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
      return addFile(new VolatileSoyFileSupplier(inputFile));
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
     * @throws IllegalArgumentException If one of the values is not a valid Soy primitive type.
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

    /**
     * Add all proto descriptors found in the file to the type registry.
     *
     * @param descriptorFile A file containing FileDescriptorSet binary protos. These typically end
     *     in {@code .proto.bin}. Note that this isn't the same as a {@code .proto} source file.
     * @deprecated Call {@link #addProtoDescriptors} instead
     */
    @Deprecated
    public Builder addProtoDescriptorsFromFile(File descriptorFile) throws IOException {
      typeRegistryBuilder.addFileDescriptorSetFromFile(descriptorFile);
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
      typeRegistryBuilder.addDescriptors(descriptors);
      return this;
    }

    /** Registers a conformance config proto. */
    Builder setConformanceConfig(ValidatedConformanceConfig config) {
      checkNotNull(config);
      this.conformanceConfig = config;
      return this;
    }

    Builder addCompilationUnit(
        SoyFileKind fileKind, String filePath, CompilationUnit compilationUnit) {
      compilationUnitsBuilder.add(
          CompilationUnitAndKind.create(fileKind, filePath, compilationUnit));
      return this;
    }

    Builder addFile(SoyFileSupplier supplier) {
      filesBuilder.put(supplier.getFilePath(), supplier);
      return this;
    }

    /**
     * Configures a place to write warnings for successful compilations.
     *
     * <p>For compilation failures warnings are reported along with the errors, by throwing an
     * exception. The default is to report warnings to the logger for SoyFileSet.
     */
    Builder setWarningSink(Appendable warningSink) {
      this.warningSink = checkNotNull(warningSink);
      return this;
    }

    /**
     * Sets the logging config to use.
     *
     * @throws IllegalArgumentException if the config proto is invalid. For example, if there are
     *     multiple elements with the same {@code name} or {@code id}, or if the name not a valid
     *     identifier.
     */
    public Builder setLoggingConfig(LoggingConfig config) {
      return setValidatedLoggingConfig(ValidatedLoggingConfig.create(config));
    }

    /** Sets the validated logging config to use. */
    Builder setValidatedLoggingConfig(ValidatedLoggingConfig parseLoggingConfigs) {
      this.loggingConfig = checkNotNull(parseLoggingConfigs);
      return this;
    }
  }

  private final SoyScopedData scopedData;

  private final SoyTypeRegistry typeRegistry;
  private final ImmutableMap<String, SoyFileSupplier> soyFileSuppliers;
  private final ImmutableList<CompilationUnitAndKind> compilationUnits;

  /** Optional soy tree cache for faster recompile times. */
  @Nullable private final SoyAstCache cache;

  private final SoyGeneralOptions generalOptions;

  private final ValidatedConformanceConfig conformanceConfig;
  private final ValidatedLoggingConfig loggingConfig;

  private final ImmutableMap<String, SoyFunction> soyFunctionMap;
  private final ImmutableMap<String, SoyPrintDirective> printDirectives;
  private final ImmutableMap<String, SoySourceFunction> soySourceFunctionMap;

  /** For reporting errors during parsing. */
  private ErrorReporter errorReporter;

  @Nullable private final Appendable warningSink;

  SoyFileSet(
      SoyScopedData apiCallScopeProvider,
      SoyTypeRegistry typeRegistry,
      ImmutableMap<String, SoyFunction> soyFunctionMap,
      ImmutableMap<String, SoyPrintDirective> printDirectives,
      ImmutableMap<String, SoySourceFunction> soySourceFunctionMap,
      ImmutableMap<String, SoyFileSupplier> soyFileSuppliers,
      ImmutableList<CompilationUnitAndKind> compilationUnits,
      SoyGeneralOptions generalOptions,
      @Nullable SoyAstCache cache,
      ValidatedConformanceConfig conformanceConfig,
      ValidatedLoggingConfig loggingConfig,
      @Nullable Appendable warningSink) {
    this.scopedData = apiCallScopeProvider;

    Preconditions.checkArgument(
        !soyFileSuppliers.isEmpty(), "Must have non-zero number of input Soy files.");
    this.typeRegistry = typeRegistry;
    this.soyFileSuppliers = soyFileSuppliers;
    this.compilationUnits = compilationUnits;
    this.cache = cache;
    this.generalOptions = generalOptions.clone();
    this.soyFunctionMap = soyFunctionMap;
    this.printDirectives = printDirectives;
    this.soySourceFunctionMap = soySourceFunctionMap;
    this.conformanceConfig = checkNotNull(conformanceConfig);
    this.loggingConfig = checkNotNull(loggingConfig);
    this.warningSink = warningSink;
  }

  /** Returns the list of suppliers for the input Soy files. For testing use only! */
  @VisibleForTesting
  ImmutableMap<String, SoyFileSupplier> getSoyFileSuppliersForTesting() {
    return soyFileSuppliers;
  }

  @VisibleForTesting
  SoyTypeRegistry getTypeRegistryForTesting() {
    return typeRegistry;
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
  ImmutableMap<String, String> generateParseInfo(String javaPackage, String javaClassNameSource) {
    resetErrorReporter();
    // TODO(lukes): see if we can enforce that globals are provided at compile time here. given that
    // types have to be, this should be possible.  Currently it is disabled for backwards
    // compatibility
    // N.B. we do not run the optimizer here for 2 reasons:
    // 1. it would just waste time, since we are not running code generation the optimization work
    //    doesn't help anything
    // 2. it potentially removes metadata from the tree by precalculating expressions. For example,
    //    trivial print nodes are evaluated, which can remove globals from the tree, but the
    ParseResult result = parse(passManagerBuilder().allowUnknownGlobals().optimize(false));
    throwIfErrorsPresent();

    SoyFileSetNode soyTree = result.fileSet();
    TemplateRegistry registry = result.registry();

    // Do renaming of package-relative class names.
    ImmutableMap<String, String> parseInfo =
        new GenerateParseInfoVisitor(javaPackage, javaClassNameSource, registry, typeRegistry)
            .exec(soyTree);
    throwIfErrorsPresent();
    reportWarnings();
    return parseInfo;
  }

  /** A simple tool to enforce conformance and only conformance. */
  void checkConformance() {
    resetErrorReporter();
    // to check conformance we only need to run as much as it takes to execute the SoyConformance
    // pass.
    parse(
        passManagerBuilder()
            .addPassContinuationRule("SoyConformance", PassContinuationRule.STOP_AFTER_PASS));
    throwIfErrorsPresent();
    reportWarnings();
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
    SoyMsgBundle bundle = doExtractMsgs();
    reportWarnings();
    return bundle;
  }

  /**
   * Extracts all messages from this Soy file set and writes the messages to an output sink.
   *
   * @param msgBundleHandler Handler to write the messages.
   * @param options Options to configure how to write the extracted messages.
   * @param output Where to write the extracted messages.
   * @throws IOException If there are errors writing to the output.
   */
  public void extractAndWriteMsgs(
      SoyMsgBundleHandler msgBundleHandler, OutputFileOptions options, ByteSink output)
      throws IOException {
    resetErrorReporter();
    SoyMsgBundle bundle = doExtractMsgs();
    msgBundleHandler.writeExtractedMsgs(bundle, options, output, errorReporter);
    throwIfErrorsPresent();
    reportWarnings();
  }

  /** Performs the parsing and extraction logic. */
  private SoyMsgBundle doExtractMsgs() {
    // extractMsgs disables a bunch of passes since it is typically not configured with things
    // like global definitions, type definitions, plugins, etc.
    SoyFileSetNode soyTree =
        parse(
                passManagerBuilder()
                    .allowUnknownGlobals()
                    .allowV1Expression()
                    .setTypeRegistry(SoyTypeRegistry.DEFAULT_UNKNOWN)
                    // TODO(lukes): consider changing this to pass a null resolver instead of the
                    // ALLOW_UNDEFINED mode
                    .setPluginResolver(
                        new PluginResolver(
                            PluginResolver.Mode.ALLOW_UNDEFINED,
                            printDirectives,
                            soyFunctionMap,
                            soySourceFunctionMap,
                            errorReporter))
                    .disableAllTypeChecking(),
                // override the type registry so that the parser doesn't report errors when it
                // can't resolve strict types
                SoyTypeRegistry.DEFAULT_UNKNOWN)
            .fileSet();
    throwIfErrorsPresent();
    SoyMsgBundle bundle = new ExtractMsgsVisitor().exec(soyTree);
    throwIfErrorsPresent();
    return bundle;
  }

  /**
   * Compiles this Soy file set into a Java object (type {@code SoyTofu}) capable of rendering the
   * compiled templates.
   *
   * @return The resulting {@code SoyTofu} object.
   * @throws SoyCompilationException If compilation fails.
   */
  public SoyTofu compileToTofu() {
    return compileToTofu(ImmutableMap.of());
  }
  /**
   * Compiles this Soy file set into a Java object (type {@code SoyTofu}) capable of rendering the
   * compiled templates.
   *
   * @return The resulting {@code SoyTofu} object.
   * @throws SoyCompilationException If compilation fails.
   */
  public SoyTofu compileToTofu(Map<String, Supplier<Object>> pluginInstances) {
    resetErrorReporter();
    ServerCompilationPrimitives primitives = compileForServerRendering();
    throwIfErrorsPresent();
    SoyTofu tofu = doCompileToTofu(primitives, pluginInstances);

    reportWarnings();
    return tofu;
  }

  /** Helper method to compile SoyTofu from {@link ServerCompilationPrimitives} */
  private SoyTofu doCompileToTofu(
      ServerCompilationPrimitives primitives, Map<String, Supplier<Object>> pluginInstances) {
    return new BaseTofu(
        scopedData.enterable(),
        primitives.soyTree,
        pluginInstances);
  }

  /**
   * Compiles this Soy file set into a set of java classes implementing the {@link SoySauce}
   * interface.
   *
   * <p>This is useful for implementing 'edit refresh' workflows. Most production usecases should
   * use the command line interface to 'ahead of time' compile templates to jar files and then use
   * {@code PrecompiledSoyModule} or {@code SoySauceBuilder} to get access to a {@link SoySauce}
   * object without invoking the compiler. This will allow applications to avoid invoking the soy
   * compiler at runtime which can be relatively slow.
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
   * {@code PrecompiledSoyModule} or {@code SoySauceBuilder} to get access to a {@link SoySauce}
   * object without invoking the compiler. This will allow applications to avoid invoking the soy
   * compiler at runtime which can be relatively slow.
   *
   * @return A set of compiled templates
   * @throws SoyCompilationException If compilation fails.
   */
  public SoySauce compileTemplates(Map<String, Supplier<Object>> pluginInstances) {
    resetErrorReporter();
    disallowExternalCalls();
    ServerCompilationPrimitives primitives = compileForServerRendering();
    throwIfErrorsPresent();
    SoySauce sauce = doCompileSoySauce(primitives, pluginInstances);

    reportWarnings();
    return sauce;
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
    BytecodeCompiler.compileToJar(
        primitives.registry, primitives.soyTree, errorReporter, typeRegistry, jarTarget);
    if (srcJarTarget.isPresent()) {
      BytecodeCompiler.writeSrcJar(primitives.soyTree, soyFileSuppliers, srcJarTarget.get());
    }
    throwIfErrorsPresent();
    reportWarnings();
  }

  /** Helper method to compile SoySauce from {@link ServerCompilationPrimitives} */
  private SoySauce doCompileSoySauce(
      ServerCompilationPrimitives primitives, Map<String, Supplier<Object>> pluginInstances) {
    Optional<CompiledTemplates> templates =
        BytecodeCompiler.compile(
            primitives.registry,
            primitives.soyTree,
            // if there is an AST cache, assume we are in 'dev mode' and trigger lazy compilation.
            cache != null,
            errorReporter,
            soyFileSuppliers,
            typeRegistry);

    throwIfErrorsPresent();

    return new SoySauceImpl(
        templates.get(),
        scopedData.enterable(),
        soyFunctionMap,
        printDirectives,
        ImmutableMap.copyOf(pluginInstances));
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
    ParseResult result = parse();
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
   * <p>TODO(lukes): deprecate and delete localized builds
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
    // JS has traditionally allowed unknown globals, as a way for soy to reference normal js enums
    // and constants. For consistency/reusability of templates it would be nice to not allow that
    // but the cat is out of the bag.
    PassManager.Builder builder =
        passManagerBuilder().allowUnknownGlobals().allowV1Expression().desugarHtmlNodes(false);
    ParseResult result = parse(builder);
    throwIfErrorsPresent();
    TemplateRegistry registry = result.registry();
    SoyFileSetNode fileSet = result.fileSet();
    List<String> generatedSrcs =
        new JsSrcMain(scopedData.enterable(), typeRegistry)
            .genJsSrc(fileSet, registry, jsSrcOptions, msgBundle, errorReporter);
    throwIfErrorsPresent();
    reportWarnings();
    return generatedSrcs;
  }

  /**
   * Compiles this Soy file set into Python source code files and writes these Python files to disk.
   *
   * @param outputPathFormat The format string defining how to build the output file path
   *     corresponding to an input file path.
   * @param pySrcOptions The compilation options for the Python Src output target.
   * @throws SoyCompilationException If compilation fails.
   * @throws IOException If there is an error in opening/reading a message file or opening/writing
   *     an output JS file.
   */
  void compileToPySrcFiles(String outputPathFormat, SoyPySrcOptions pySrcOptions)
      throws IOException {
    resetErrorReporter();
    requireStrictAutoescaping();
    ParseResult result = parse();
    throwIfErrorsPresent();
    new PySrcMain(scopedData.enterable())
        .genPyFiles(result.fileSet(), pySrcOptions, outputPathFormat, errorReporter);

    throwIfErrorsPresent();
    reportWarnings();
  }

  /**
   * Performs the minimal amount of work needed to calculate TemplateMetadata objects for header
   * compilation.
   */
  ParseResult compileMinimallyForHeaders() {
    resetErrorReporter();
    ParseResult result =
        parse(
            passManagerBuilder()
                // ResolveHeaderParamTypesPass resolve types which is necessary for template
                // metadatas
                .addPassContinuationRule(
                    "ResolveHeaderParamTypes", PassContinuationRule.STOP_AFTER_PASS),
            typeRegistry);

    throwIfErrorsPresent();
    reportWarnings();
    return result;
  }

  ImmutableMap<String, SoyFunction> getSoyFunctions() {
    return soyFunctionMap;
  }

  ImmutableMap<String, SoyPrintDirective> getSoyPrintDirectives() {
    return printDirectives;
  }

  ImmutableMap<String, SoySourceFunction> getSoySourceFunctions() {
    return soySourceFunctionMap;
  }

  // Parse the current file set.
  @VisibleForTesting
  ParseResult parse() {
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
        .setTypeRegistry(typeRegistry)
        .setPassManager(builder.setTypeRegistry(typeRegistry).build())
        .setErrorReporter(errorReporter)
        .build()
        .parse();
  }

  private PassManager.Builder passManagerBuilder() {
    return new PassManager.Builder()
        .setGeneralOptions(generalOptions)
        .setSoyPrintDirectiveMap(printDirectives)
        .setErrorReporter(errorReporter)
        .setConformanceConfig(conformanceConfig)
        .setLoggingConfig(loggingConfig)
        .setPluginResolver(
            new PluginResolver(
                PluginResolver.Mode.REQUIRE_DEFINITIONS,
                printDirectives,
                soyFunctionMap,
                soySourceFunctionMap,
                errorReporter));
  }

  /**
   * This method resets the error reporter field in preparation to a new compiler invocation.
   *
   * <p>This method should be called at the beginning of every entry point into SoyFileSet.
   */
  @VisibleForTesting
  void resetErrorReporter() {
    errorReporter = ErrorReporter.create(soyFileSuppliers);
  }

  private void throwIfErrorsPresent() {
    if (errorReporter.hasErrors()) {
      // if we are reporting errors we should report warnings at the same time.
      Iterable<SoyError> errors =
          Iterables.concat(errorReporter.getErrors(), errorReporter.getWarnings());
      // clear the field to ensure that error reporters can't leak between compilations
      errorReporter = null;
      throw new SoyCompilationException(errors);
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
      throw new SoyCompilationException(warnings);
    }
    String formatted = SoyErrors.formatErrors(warnings);
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
