/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.testing;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.template.soy.SoyFileSetParser;
import com.google.template.soy.SoyFileSetParser.CompilationUnitAndKind;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.conformance.ValidatedConformanceConfig;
import com.google.template.soy.css.CssRegistry;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.passes.CompilerPass;
import com.google.template.soy.passes.PassManager;
import com.google.template.soy.passes.PassManager.PassContinuationRule;
import com.google.template.soy.passes.PluginResolver;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.SoyAstCache;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.internal.InternalPlugins;
import com.google.template.soy.shared.internal.SoyScopedData;
import com.google.template.soy.shared.internal.SoySimpleScope;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyMethodSignature;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypeRegistryBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Fluent builder for configuring {@link com.google.template.soy.SoyFileSetParser}s in tests.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class SoyFileSetParserBuilder {

  private final ImmutableMap<String, SoyFileSupplier> soyFileSuppliers;
  private SoyTypeRegistry typeRegistry = SoyTypeRegistryBuilder.create();
  @Nullable private SoyAstCache astCache = null;
  private ErrorReporter errorReporter = ErrorReporter.exploding(); // See #parse for discussion.
  private boolean allowUnboundGlobals;
  private boolean allowUnknownJsGlobals;
  private boolean allowV1Expression;
  // disable optimization by default
  private boolean runOptimizer = false;
  private final SoyScopedData scopedData;
  private ImmutableList.Builder<SoyFunction> soyFunctions;
  private ImmutableList.Builder<SoyPrintDirective> soyPrintDirectives;
  private ImmutableList.Builder<SoySourceFunction> sourceFunctions;
  private ImmutableList.Builder<SoySourceFunction> soyMethods;
  private ImmutableList<CompilationUnitAndKind> compilationUnits;
  private SoyGeneralOptions options = new SoyGeneralOptions();
  private ValidatedConformanceConfig conformanceConfig = ValidatedConformanceConfig.EMPTY;
  private ValidatedLoggingConfig loggingConfig = ValidatedLoggingConfig.EMPTY;
  private boolean desugarHtmlAndStateNodes = true;
  private Optional<CssRegistry> cssRegistry = Optional.empty();
  // TODO(lukes): disabled for compatibility with unit tests.  Fix tests relying on the
  // escaper not running and enable by default.  This configuration bit only really exists
  // for incrementaldomsrc, not tests.
  private boolean runAutoescaper = false;
  // By default, do not modify the AST to add the HTML comments, since many unit tests depend on
  // the order of the nodes in the AST.
  private boolean addHtmlAttributesForDebugging = false;
  private final PassManager.Builder passManager = new PassManager.Builder();

  /**
   * Returns a builder that gets its Soy inputs from the given strings, treating each string as the
   * full contents of a Soy file.
   */
  public static SoyFileSetParserBuilder forFileContents(String... fileContents) {
    return new SoyFileSetParserBuilder(fileContents);
  }

  /** Returns a builder that gets its Soy inputs from the given {@link SoyFileSupplier}s. */
  public static SoyFileSetParserBuilder forSuppliers(SoyFileSupplier... suppliers) {
    return forSuppliers(Arrays.asList(suppliers));
  }

  public static SoyFileSetParserBuilder forSuppliers(Iterable<SoyFileSupplier> files) {
    return new SoyFileSetParserBuilder(files);
  }

  /**
   * Returns a builder that gets its Soy inputs from the given strings, treating each string as the
   * contents of a Soy template.
   */
  public static SoyFileSetParserBuilder forTemplateContents(String... templateContents) {
    return forTemplateContents(/* strictHtml= */ false, templateContents);
  }

  /**
   * Returns a builder that gets its Soy inputs from the given strings, treating each string as the
   * contents of a Soy template, and using the given strictHtml mode.
   */
  public static SoyFileSetParserBuilder forTemplateContents(
      boolean strictHtml, String... templateContents) {
    String[] fileContents = new String[templateContents.length];
    for (int i = 0; i < fileContents.length; ++i) {
      fileContents[i] = SharedTestUtils.buildTestSoyFileContent(strictHtml, templateContents[i]);
    }
    return new SoyFileSetParserBuilder(fileContents);
  }

  private SoyFileSetParserBuilder(String... soyCode) {
    this(ImmutableList.copyOf(buildTestSoyFileSuppliers(soyCode)));
  }

  private SoyFileSetParserBuilder(Iterable<SoyFileSupplier> suppliers) {
    ImmutableMap.Builder<String, SoyFileSupplier> builder = ImmutableMap.builder();
    for (SoyFileSupplier supplier : suppliers) {
      builder.put(supplier.getFilePath(), supplier);
    }
    this.soyFileSuppliers = builder.build();
    this.scopedData = new SoySimpleScope();
    this.soyFunctions =
        ImmutableList.<SoyFunction>builder().addAll(InternalPlugins.internalLegacyFunctions());
    this.soyPrintDirectives =
        ImmutableList.<SoyPrintDirective>builder()
            .addAll(InternalPlugins.internalDirectives(scopedData));
    this.sourceFunctions =
        ImmutableList.<SoySourceFunction>builder().addAll(InternalPlugins.internalFunctions());
    this.soyMethods =
        ImmutableList.<SoySourceFunction>builder().addAll(InternalPlugins.internalMethods());
    this.compilationUnits = ImmutableList.of();
  }

  /** Enable experiments. Returns this object, for chaining. */
  public SoyFileSetParserBuilder enableExperimentalFeatures(ImmutableList<String> experiments) {
    this.options.setExperimentalFeatures(experiments);
    return this;
  }

  public SoyFileSetParserBuilder errorReporter(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
    return this;
  }

  public SoyFileSetParserBuilder addSoyFunction(SoyFunction function) {
    return addSoyFunctions(ImmutableList.of(function));
  }

  public SoyFileSetParserBuilder addSoyFunctions(Iterable<? extends SoyFunction> newSoyFunctions) {
    this.soyFunctions.addAll(newSoyFunctions);
    return this;
  }

  public SoyFileSetParserBuilder addSoySourceFunction(SoySourceFunction function) {
    boolean method = false;
    if (function.getClass().isAnnotationPresent(SoyMethodSignature.class)) {
      soyMethods.add(function);
      method = true;
    }
    if (!method || function.getClass().isAnnotationPresent(SoyFunctionSignature.class)) {
      sourceFunctions.add(function);
    }
    return this;
  }

  public SoyFileSetParserBuilder addSoySourceFunctions(
      Iterable<? extends SoySourceFunction> newSourceFunctions) {
    for (SoySourceFunction function : newSourceFunctions) {
      addSoySourceFunction(function);
    }
    return this;
  }

  public SoyFileSetParserBuilder addPrintDirectives(
      Iterable<? extends SoyPrintDirective> newSoyPrintDirectives) {
    this.soyPrintDirectives.addAll(newSoyPrintDirectives);
    return this;
  }

  public SoyFileSetParserBuilder addPrintDirective(SoyPrintDirective printDirective) {
    return addPrintDirectives(ImmutableList.of(printDirective));
  }

  public SoyFileSetParserBuilder addMethods(Iterable<? extends SoySourceFunction> newMethods) {
    this.soyMethods.addAll(newMethods);
    return this;
  }

  public SoyFileSetParserBuilder addMethod(SoySourceFunction method) {
    return addMethods(ImmutableList.of(method));
  }

  public SoyFileSetParserBuilder addCompilationUnits(
      Iterable<CompilationUnitAndKind> newCompilationUnits) {
    compilationUnits =
        ImmutableList.<CompilationUnitAndKind>builder()
            .addAll(compilationUnits)
            .addAll(newCompilationUnits)
            .build();
    return this;
  }

  public SoyFileSetParserBuilder addCompilationUnit(CompilationUnitAndKind unit) {
    return addCompilationUnits(ImmutableList.of(unit));
  }

  public SoyFileSetParserBuilder options(SoyGeneralOptions options) {
    this.options = checkNotNull(options);
    return this;
  }

  public SoyFileSetParserBuilder typeRegistry(SoyTypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
    return this;
  }

  public SoyFileSetParserBuilder cssRegistry(CssRegistry cssRegistry) {
    this.cssRegistry = Optional.of(cssRegistry);
    return this;
  }

  public SoyFileSetParserBuilder allowUnboundGlobals(boolean allowUnboundGlobals) {
    this.allowUnboundGlobals = allowUnboundGlobals;
    return this;
  }

  public SoyFileSetParserBuilder allowUnknownJsGlobals(boolean allowUnknownJsGlobals) {
    this.allowUnknownJsGlobals = allowUnknownJsGlobals;
    return this;
  }

  public SoyFileSetParserBuilder allowV1Expression(boolean allowV1Expression) {
    this.allowV1Expression = allowV1Expression;
    return this;
  }

  public SoyFileSetParserBuilder setConformanceConfig(ValidatedConformanceConfig config) {
    this.conformanceConfig = checkNotNull(config);
    return this;
  }

  public SoyFileSetParserBuilder setLoggingConfig(ValidatedLoggingConfig loggingConfig) {
    this.loggingConfig = checkNotNull(loggingConfig);
    return this;
  }

  public SoyFileSetParserBuilder desugarHtmlAndStateNodes(boolean desugarHtmlAndStateNodes) {
    this.desugarHtmlAndStateNodes = desugarHtmlAndStateNodes;
    return this;
  }

  public SoyFileSetParserBuilder runAutoescaper(boolean runAutoescaper) {
    this.runAutoescaper = runAutoescaper;
    return this;
  }

  public SoyFileSetParserBuilder runOptimizer(boolean runOptimizer) {
    this.runOptimizer = runOptimizer;
    return this;
  }

  /**
   * Tests can use this method to force running {@code AddDebugAttributesPass}. By default, this
   * compiler pass is disabled for tests, since it modifies the AST structure and will break a lot
   * of unit tests that rely on particular structure.
   */
  public SoyFileSetParserBuilder addHtmlAttributesForDebugging(
      boolean addHtmlAttributesForDebugging) {
    this.addHtmlAttributesForDebugging = addHtmlAttributesForDebugging;
    return this;
  }

  public static final String FILE_PATH = "no-path";

  private static List<SoyFileSupplier> buildTestSoyFileSuppliers(String... soyFileContents) {

    List<SoyFileSupplier> soyFileSuppliers = Lists.newArrayList();
    for (int i = 0; i < soyFileContents.length; i++) {
      String soyFileContent = soyFileContents[i];
      // Names are now required to be unique in a SoyFileSet. Use one-based indexing.
      String filePath = (i == 0) ? FILE_PATH : (FILE_PATH + "-" + (i + 1));
      soyFileSuppliers.add(SoyFileSupplier.Factory.create(soyFileContent, filePath));
    }
    return soyFileSuppliers;
  }

  /**
   * Tells the compiler to stop either before or after the named pass.
   *
   * <p>Tests can use this to build a parse tree using up to a certain pass. See, for example {@link
   * com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphTest}.
   */
  public SoyFileSetParserBuilder addPassContinuationRule(
      Class<? extends CompilerPass> pass, PassContinuationRule rule) {
    passManager.addPassContinuationRule(pass, rule);
    return this;
  }

  /**
   * Constructs a parse tree from the builder's state, returning the root of the tree.
   *
   * <p>Note: since {@link SoyFileSetParserBuilder} can only be used in tests, this method will
   * throw an {@link AssertionError} if any error is encountered during parsing. For tests that
   * require different behavior (for example, tests that need to inspect the full list of errors
   * encountered during compilation), pass a different {@link ErrorReporter} implementation to
   * {@link #errorReporter}.
   */
  public ParseResult parse() {
    return build().parse();
  }

  public SoyFileSetParser build() {
    // Add the remaining PassManager configuration bits.
    passManager
        .setSoyPrintDirectives(soyPrintDirectives.build())
        .setErrorReporter(errorReporter)
        .setTypeRegistry(typeRegistry)
        .desugarHtmlAndStateNodes(desugarHtmlAndStateNodes)
        .setGeneralOptions(options)
        .setConformanceConfig(conformanceConfig)
        .setCssRegistry(cssRegistry)
        .setPluginResolver(
            new PluginResolver(
                PluginResolver.Mode.REQUIRE_DEFINITIONS,
                soyPrintDirectives.build(),
                soyFunctions.build(),
                sourceFunctions.build(),
                soyMethods.build(),
                errorReporter))
        .insertEscapingDirectives(runAutoescaper)
        .optimize(runOptimizer)
        .addHtmlAttributesForDebugging(addHtmlAttributesForDebugging)
        .setLoggingConfig(loggingConfig);
    if (allowUnboundGlobals) {
      passManager.allowUnknownGlobals();
    }
    if (allowUnknownJsGlobals) {
      passManager.allowUnknownJsGlobals();
    }
    if (allowV1Expression) {
      passManager.allowV1Expression();
    }
    return SoyFileSetParser.newBuilder()
        .setCache(astCache)
        .setSoyFileSuppliers(soyFileSuppliers)
        .setCompilationUnits(compilationUnits)
        .setTypeRegistry(typeRegistry)
        .setPassManager(passManager.build())
        .setErrorReporter(errorReporter)
        .build();
  }
}
