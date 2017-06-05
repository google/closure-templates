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

package com.google.template.soy;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.CharSource;
import com.google.inject.Guice;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.passes.PassManager;
import com.google.template.soy.shared.AutoEscapingType;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.SoyAstCache;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Fluent builder for configuring {@link com.google.template.soy.SoyFileSetParser}s in tests.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class SoyFileSetParserBuilder {

  private final ImmutableMap<String, SoyFileSupplier> soyFileSuppliers;
  private SoyTypeRegistry typeRegistry = new SoyTypeRegistry();
  private SyntaxVersion declaredSyntaxVersion = SyntaxVersion.V2_0;
  @Nullable private SoyAstCache astCache = null;
  private ErrorReporter errorReporter = ExplodingErrorReporter.get(); // See #parse for discussion.
  private boolean allowUnboundGlobals;
  @Inject private ImmutableMap<String, ? extends SoyFunction> soyFunctionMap;
  @Inject private ImmutableMap<String, ? extends SoyPrintDirective> soyPrintDirectiveMap;
  // disable optimization by default
  private SoyGeneralOptions options = new SoyGeneralOptions().disableOptimizer();
  private ImmutableList<CharSource> conformanceConfigs = ImmutableList.of();
  private boolean enableHtmlRewriting;
  private boolean desugarHtmlNodes = true;

  /**
   * Returns a builder that gets its Soy inputs from the given strings, treating each string as the
   * full contents of a Soy file.
   */
  public static SoyFileSetParserBuilder forFileContents(String... fileContents) {
    return new SoyFileSetParserBuilder(fileContents);
  }

  /** Returns a builder that gets its Soy inputs from the given {@link SoyFileSupplier}s. */
  public static SoyFileSetParserBuilder forSuppliers(SoyFileSupplier... suppliers) {
    return new SoyFileSetParserBuilder(ImmutableList.copyOf(Arrays.asList(suppliers)));
  }

  /**
   * Returns a builder that gets its Soy inputs from the given strings, treating each string as the
   * contents of a Soy template.
   */
  public static SoyFileSetParserBuilder forTemplateContents(String... templateContents) {
    return forTemplateContents(AutoEscapingType.DEPRECATED_NONCONTEXTUAL, templateContents);
  }

  /**
   * Returns a builder that gets its Soy inputs from the given strings, treating each string as the
   * contents of a Soy template, and using the given {@link AutoEscapingType}.
   */
  public static SoyFileSetParserBuilder forTemplateContents(
      AutoEscapingType autoEscapingType, String... templateContents) {
    String[] fileContents = new String[templateContents.length];
    for (int i = 0; i < fileContents.length; ++i) {
      fileContents[i] =
          SharedTestUtils.buildTestSoyFileContent(
              autoEscapingType, null /* soyDocParamNames */, templateContents[i]);
    }
    return new SoyFileSetParserBuilder(fileContents);
  }

  private SoyFileSetParserBuilder(String... soyCode) {
    this(ImmutableList.copyOf(buildTestSoyFileSuppliers(soyCode)));
  }

  private SoyFileSetParserBuilder(ImmutableList<SoyFileSupplier> suppliers) {
    ImmutableMap.Builder<String, SoyFileSupplier> builder = ImmutableMap.builder();
    for (SoyFileSupplier supplier : suppliers) {
      builder.put(supplier.getFilePath(), supplier);
    }
    this.soyFileSuppliers = builder.build();
    // inject our @Inject fields to get the default set of functions and print directives
    Guice.createInjector(new SharedModule()).injectMembers(this);
  }

  /** Sets the parser's declared syntax version. Returns this object, for chaining. */
  public SoyFileSetParserBuilder declaredSyntaxVersion(SyntaxVersion version) {
    this.declaredSyntaxVersion = version;
    return this;
  }

  public SoyFileSetParserBuilder errorReporter(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
    return this;
  }

  public SoyFileSetParserBuilder addSoyFunction(SoyFunction function) {
    this.soyFunctionMap =
        ImmutableMap.<String, SoyFunction>builder()
            .putAll(soyFunctionMap)
            .put(function.getName(), function)
            .build();
    return this;
  }

  public SoyFileSetParserBuilder options(SoyGeneralOptions options) {
    this.options = checkNotNull(options);
    // allow the version in the options to override the declared default, if there is one.
    this.declaredSyntaxVersion = options.getDeclaredSyntaxVersion(declaredSyntaxVersion);
    return this;
  }

  public SoyFileSetParserBuilder typeRegistry(SoyTypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
    return this;
  }

  public SoyFileSetParserBuilder allowUnboundGlobals(boolean allowUnboundGlobals) {
    this.allowUnboundGlobals = allowUnboundGlobals;
    return this;
  }

  public SoyFileSetParserBuilder setConformanceConfigs(Iterable<CharSource> configs) {
    this.conformanceConfigs = ImmutableList.copyOf(configs);
    return this;
  }

  public SoyFileSetParserBuilder enableHtmlRewriting() {
    this.enableHtmlRewriting = true;
    return this;
  }

  public SoyFileSetParserBuilder desugarHtmlNodes(boolean desugarHtmlNodes) {
    this.desugarHtmlNodes = desugarHtmlNodes;
    return this;
  }

  private static List<SoyFileSupplier> buildTestSoyFileSuppliers(String... soyFileContents) {

    List<SoyFileSupplier> soyFileSuppliers = Lists.newArrayList();
    for (int i = 0; i < soyFileContents.length; i++) {
      String soyFileContent = soyFileContents[i];
      // Names are now required to be unique in a SoyFileSet. Use one-based indexing.
      String filePath = (i == 0) ? "no-path" : ("no-path-" + (i + 1));
      soyFileSuppliers.add(
          SoyFileSupplier.Factory.create(soyFileContent, SoyFileKind.SRC, filePath));
    }
    return soyFileSuppliers;
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
    PassManager.Builder passManager =
        new PassManager.Builder()
            .setDeclaredSyntaxVersion(declaredSyntaxVersion)
            .setSoyFunctionMap(soyFunctionMap)
            .setSoyPrintDirectiveMap(soyPrintDirectiveMap)
            .setErrorReporter(errorReporter)
            .setTypeRegistry(typeRegistry)
            .desugarHtmlNodes(desugarHtmlNodes)
            .setGeneralOptions(options)
            .setConformanceConfigs(conformanceConfigs)
            // TODO(lukes): disabled for compatibility with unit tests.  fix tests relying on the
            // escaper not running and enable by default.  This configuration bit only really exists
            // for incrementaldom not tests
            .setAutoescaperEnabled(false);
    if (allowUnboundGlobals) {
      passManager.allowUnknownGlobals();
    }
    if (enableHtmlRewriting) {
      passManager.enableHtmlRewriting();
    }
    return new SoyFileSetParser(astCache, soyFileSuppliers, passManager.build(), errorReporter)
        .parse();
  }

}
