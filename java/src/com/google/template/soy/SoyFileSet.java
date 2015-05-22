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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharSource;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.util.Providers;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.base.internal.VolatileSoyFileSupplier;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.conformance.CheckConformance;
import com.google.template.soy.error.ErrorPrettyPrinter;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.JsSrcMain;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.msgs.internal.ExtractMsgsVisitor;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgBundleImpl;
import com.google.template.soy.parseinfo.passes.GenerateParseInfoVisitor;
import com.google.template.soy.parsepasses.ChangeCallsToPassAllDataVisitor;
import com.google.template.soy.parsepasses.CheckFunctionCallsVisitor.CheckFunctionCallsVisitorFactory;
import com.google.template.soy.parsepasses.HandleCssCommandVisitor;
import com.google.template.soy.parsepasses.PerformAutoescapeVisitor;
import com.google.template.soy.parsepasses.contextautoesc.CheckEscapingSanityVisitor;
import com.google.template.soy.parsepasses.contextautoesc.ContentSecurityPolicyPass;
import com.google.template.soy.parsepasses.contextautoesc.ContextualAutoescaper;
import com.google.template.soy.parsepasses.contextautoesc.DerivedTemplateUtils;
import com.google.template.soy.parsepasses.contextautoesc.SoyAutoescapeException;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.internal.PySrcMain;
import com.google.template.soy.shared.SoyAstCache;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.SoyGeneralOptions.CssHandlingScheme;
import com.google.template.soy.shared.internal.MainEntryPointUtils;
import com.google.template.soy.sharedpasses.AssertNoExternalCallsVisitor;
import com.google.template.soy.sharedpasses.AssertStrictAutoescapingVisitor;
import com.google.template.soy.sharedpasses.ClearSoyDocStringsVisitor;
import com.google.template.soy.sharedpasses.FindTransitiveDepTemplatesVisitor;
import com.google.template.soy.sharedpasses.FindTransitiveDepTemplatesVisitor.TransitiveDepTemplatesInfo;
import com.google.template.soy.sharedpasses.ResolvePackageRelativeCssNamesVisitor;
import com.google.template.soy.sharedpasses.SubstituteGlobalsVisitor;
import com.google.template.soy.sharedpasses.opti.SimplifyVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.tofu.SoyTofuOptions;
import com.google.template.soy.tofu.internal.BaseTofu.BaseTofuFactory;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.primitive.UnknownType;
import com.google.template.soy.xliffmsgplugin.XliffMsgPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a complete set of Soy files for compilation as one bundle. The files may depend on
 * each other but should not have dependencies outside of the set.
 *
 * <p> Note: Soy file (or resource) contents must be encoded in UTF-8.
 *
 */
public final class SoyFileSet {


  /**
   * Creates a builder with the standard set of Soy directives, functions, and types.
   *
   * <p>If you need additional directives, functions, or types, create the Builder instance using
   * Guice.  If your project doesn't otherwise use Guice, you can just use Guice.createInjector
   * with only the modules you need, similar to the implementation of this method.
   */
  @SuppressWarnings("deprecation")
  public static Builder builder() {
    Builder builder = new Builder();
    // We inject based on a plain SoyModule, rather than using GuiceInitializer, to avoid relying
    // on whatever lingering static state is around.
    builder.setFactory(
        Guice.createInjector(new SoyModule()).getInstance(SoyFileSetFactory.class));
    return builder;
  }


  /**
   * Builder for a {@code SoyFileSet}.
   */
  public static final class Builder {

    /**
     * Assisted-injection factory. This is optionally injected since many clients inject
     * SoyFileSet.Builder without installing a SoyModule, in which case we need to fall back to
     * static injection.
     */
    private SoyFileSetFactory factory;

    /**
     * The SoyFileSuppliers collected so far in added order, as a set to prevent dupes.
     */
    private final ImmutableSet.Builder<SoyFileSupplier> setBuilder;

    /** Optional AST cache. */
    private SoyAstCache cache;

    /** The general compiler options. */
    private SoyGeneralOptions lazyGeneralOptions;

    /** Type registry for this fileset only. */
    private SoyTypeRegistry localTypeRegistry;

    /**
     * Constructs a builder using a statically-injected configuration.
     *
     * @deprecated Use the static SoyFileSet.builder() method, or  inject SoyFileSet.Builder
     *     using Guice with SoyModule installed. The behavior of this builder is unpredictable and
     *     will use the Soy configuration from the most recently configured Injector containing a
     *     SoyModule, because it relies on Guice's static injection.
     */
    @Inject
    @Deprecated
    public Builder() {
      this.setBuilder = ImmutableSet.builder();
      this.cache = null;
      this.lazyGeneralOptions = null;
    }

    @Inject(optional = true)
    /** Assigns the factory via Guice. */
    void setFactory(SoyFileSetFactory factory) {
      // Yay, we have Guice, and SoyModule is installed! :-) Inject the factory from the relevant
      // Injector!
      this.factory = factory;
    }

    /**
     * Sets all Soy general options.
     *
     * <p>This must be called before any other setters.
     */
    public void setGeneralOptions(SoyGeneralOptions generalOptions) {
      Preconditions.checkState(lazyGeneralOptions == null,
          "Call SoyFileSet#setGeneralOptions before any other setters.");
      Preconditions.checkNotNull(generalOptions, "Non-null argument expected.");
      lazyGeneralOptions = generalOptions.clone();
    }

    /**
     * Returns and/or lazily-creates the SoyGeneralOptions for this builder.
     *
     * <p>Laziness is an important feature to ensure that setGeneralOptions can fail if options were
     * already set.  Otherwise, it'd be easy to set some options on this builder and overwrite them
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
     * @return The new {@code SoyFileSet}.
     */
    public SoyFileSet build() {
      if (factory == null) {
        factory = GuiceInitializer.getHackySoyFileSetFactory();
      }
      return factory.create(
          setBuilder.build().asList(), cache, getGeneralOptions(), localTypeRegistry);
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
    public Builder addWithKind(
        CharSource contentSource, SoyFileKind soyFileKind, String filePath) {
      setBuilder.add(SoyFileSupplier.Factory.create(contentSource, soyFileKind, filePath));
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
      setBuilder.add(SoyFileSupplier.Factory.create(inputFile, soyFileKind));
      return this;
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
     * actually checks for volatile files. Currently, that feature is
     * {@link #setSoyAstCache(SoyAstCache)}.
     *
     * @param inputFile The Soy file.
     * @param soyFileKind The kind of this input Soy file.
     * @return This builder.
     */
    public Builder addVolatileWithKind(File inputFile, SoyFileKind soyFileKind) {
      setBuilder.add(new VolatileSoyFileSupplier(inputFile, soyFileKind));
      return this;
    }


    /**
     * Adds an input Soy file that supports checking for modifications, given a {@code File}.
     *
     * <p>Note: This does nothing by itself. It should be used in conjunction with a feature that
     * actually checks for volatile files. Currently, that feature is
     * {@link #setSoyAstCache(SoyAstCache)}.
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
      setBuilder.add(SoyFileSupplier.Factory.create(inputFileUrl, soyFileKind, filePath));
      return this;
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
     * <p> Important: This function assumes that the desired file path is returned by
     * {@code inputFileUrl.toString()}. If this is not the case, please use
     * {@link #addWithKind(URL, SoyFileKind, String)} instead.
     *
     * @see #addWithKind(URL, SoyFileKind, String)
     * @param inputFileUrl The Soy file.
     * @param soyFileKind The kind of this input Soy file.
     * @return This builder.
     */
    public Builder addWithKind(URL inputFileUrl, SoyFileKind soyFileKind) {
      setBuilder.add(SoyFileSupplier.Factory.create(inputFileUrl, soyFileKind));
      return this;
    }


    /**
     * Adds an input Soy file, given a resource {@code URL}.
     *
     * <p> Important: This function assumes that the desired file path is returned by
     * {@code inputFileUrl.toString()}. If this is not the case, please use
     * {@link #add(URL, String)} instead.
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
      setBuilder.add(SoyFileSupplier.Factory.create(content, soyFileKind, filePath));
      return this;
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
     * @param cache The cache to use, which can have a lifecycle independent of the SoyFileSet.
     *     Null indicates not to use a cache.
     * @return This builder.
     */
    public Builder setSoyAstCache(SoyAstCache cache) {
      this.cache = cache;
      return this;
    }


    /**
     * Sets the user-declared syntax version name for the Soy file bundle.
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
     * Sets the scheme for handling {@code css} commands.
     *
     * @param cssHandlingScheme The scheme for handling {@code css} commands.
     * @return This builder.
     */
    public Builder setCssHandlingScheme(CssHandlingScheme cssHandlingScheme) {
      getGeneralOptions().setCssHandlingScheme(cssHandlingScheme);
      return this;
    }


    /**
     * Sets the map from compile-time global name to value.
     *
     * <p> The values can be any of the Soy primitive types: null, boolean, integer, float (Java
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
     * <p> Each line of the file should have the format
     * <pre>
     *     &lt;global_name&gt; = &lt;primitive_data&gt;
     * </pre>
     * where primitive_data is a valid Soy expression literal for a primitive type (null, boolean,
     * integer, float, or string). Empty lines and lines beginning with "//" are ignored. The file
     * should be encoded in UTF-8.
     *
     * <p> If you need to generate a file in this format from Java, consider using the utility
     * {@code SoyUtils.generateCompileTimeGlobalsFile()}.
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
     * <p> Each line of the file should have the format
     * <pre>
     *     &lt;global_name&gt; = &lt;primitive_data&gt;
     * </pre>
     * where primitive_data is a valid Soy expression literal for a primitive type (null, boolean,
     * integer, float, or string). Empty lines and lines beginning with "//" are ignored. The file
     * should be encoded in UTF-8.
     *
     * <p> If you need to generate a file in this format from Java, consider using the utility
     * {@code SoyUtils.generateCompileTimeGlobalsFile()}.
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
     * Pass true to enable CSP (Content Security Policy) support which adds an extra pass that marks
     * inline scripts in templates specially so the browser can distinguish scripts written by
     * trusted template authors from scripts injected via XSS.
     * <p>
     * Scripts are marked using a per-page-render secret stored in the injected variable
     * {@code $ij.csp_nonce}.
     * Scripts in non-contextually auto-escaped templates may not be found.
     */
    public Builder setSupportContentSecurityPolicy(boolean supportContentSecurityPolicy) {
      getGeneralOptions().setSupportContentSecurityPolicy(supportContentSecurityPolicy);
      return this;
    }


    /**
     * Override the global type registry with one that is local to this file set.
     */
    public Builder setLocalTypeRegistry(SoyTypeRegistry typeRegistry) {
      localTypeRegistry = typeRegistry;
      return this;
    }
  }


  /**
   * Injectable factory for creating an instance of this class.
   */
  static interface SoyFileSetFactory {

    /**
     * @param soyFileSuppliers The suppliers for the input Soy files.
     * @param cache Optional (nullable) AST cache for faster recompile times.
     * @param options The general compiler options.
     */
    public SoyFileSet create(
        List<SoyFileSupplier> soyFileSuppliers,
        SoyAstCache cache,
        SoyGeneralOptions options,
        @Assisted("localTypeRegistry") SoyTypeRegistry localTypeRegistry);
  }


  /** Default SoyMsgBundleHandler uses the XLIFF message plugin. */
  private static final Provider<SoyMsgBundleHandler> DEFAULT_SOY_MSG_BUNDLE_HANDLER_PROVIDER =
      Providers.of(new SoyMsgBundleHandler(new XliffMsgPlugin()));


  /** Provider for getting an instance of SoyMsgBundleHandler. */
  private Provider<SoyMsgBundleHandler> msgBundleHandlerProvider;

  /** Factory for creating an instance of BaseTofu. */
  private final BaseTofuFactory baseTofuFactory;

  /** Provider for getting an instance of JsSrcMain. */
  private final Provider<JsSrcMain> jsSrcMainProvider;

  /** Provider for getting an instance of PySrcMain. */
  private final Provider<PySrcMain> pySrcMainProvider;

  /** Factory for creating an instance of CheckFunctionCallsVisitor. */
  private final CheckFunctionCallsVisitorFactory checkFunctionCallsVisitorFactory;

  /** The instance of PerformAutoescapeVisitor to use. */
  private final PerformAutoescapeVisitor performAutoescapeVisitor;

  /** The instance of ContextualAutoescaper to use. */
  private final ContextualAutoescaper contextualAutoescaper;

  /** The instance of SimplifyVisitor to use. */
  private final SimplifyVisitor simplifyVisitor;

  /** The type registry for resolving type names. */
  private final SoyTypeRegistry typeRegistry;

  /** The suppliers for the input Soy files. */
  private final List<SoyFileSupplier> soyFileSuppliers;

  /** Optional soy tree cache for faster recompile times. */
  private final SoyAstCache cache;

  /** The general compiler options. */
  private final SoyGeneralOptions generalOptions;

  private CheckConformance checkConformance;

  /** For private use by pruneTranslatedMsgs(). */
  private ImmutableSet<Long> memoizedExtractedMsgIdsForPruning;

  /** For reporting errors during parsing. */
  private final ErrorReporter errorReporter;


  /**
   * @param baseTofuFactory Factory for creating an instance of BaseTofu.
   * @param jsSrcMainProvider Provider for getting an instance of JsSrcMain.
   * @param pySrcMainProvider Provider for getting an instance of PySrcMain.
   * @param checkFunctionCallsVisitorFactory Factory for creating an instance of
   *     CheckFunctionCallsVisitor.
   * @param performAutoescapeVisitor The instance of PerformAutoescapeVisitor to use.
   * @param contextualAutoescaper The instance of ContextualAutoescaper to use.
   * @param simplifyVisitor The instance of SimplifyVisitor to use.
   * @param typeRegistry The type registry to resolve parameter type names.
   * @param soyFileSuppliers The suppliers for the input Soy files.
   * @param generalOptions The general compiler options.
   * @param localTypeRegistry If non-null, use this local type registry instead
   *        of the typeRegistry param which is a global singleton.
   *        (Unfortunately because of the way assisted injection works, we need
   *        the global and local registries to be separate parameters).
   */
  @Inject
  SoyFileSet(
      BaseTofuFactory baseTofuFactory,
      Provider<JsSrcMain> jsSrcMainProvider,
      Provider<PySrcMain> pySrcMainProvider,
      CheckFunctionCallsVisitorFactory checkFunctionCallsVisitorFactory,
      PerformAutoescapeVisitor performAutoescapeVisitor,
      ContextualAutoescaper contextualAutoescaper,
      SimplifyVisitor simplifyVisitor,
      SoyTypeRegistry typeRegistry,
      ErrorReporter errorReporter,
      @Assisted List<SoyFileSupplier> soyFileSuppliers,
      @Assisted SoyGeneralOptions generalOptions,
      @Assisted @Nullable SoyAstCache cache,
      @Assisted("localTypeRegistry") @Nullable SoyTypeRegistry localTypeRegistry) {

    // Default value is optionally replaced using method injection.
    this.msgBundleHandlerProvider = DEFAULT_SOY_MSG_BUNDLE_HANDLER_PROVIDER;

    this.baseTofuFactory = baseTofuFactory;
    this.jsSrcMainProvider = jsSrcMainProvider;
    this.pySrcMainProvider = pySrcMainProvider;
    this.checkFunctionCallsVisitorFactory = checkFunctionCallsVisitorFactory;
    this.performAutoescapeVisitor = performAutoescapeVisitor;
    this.contextualAutoescaper = contextualAutoescaper;
    this.simplifyVisitor = simplifyVisitor;

    Preconditions.checkArgument(
        !soyFileSuppliers.isEmpty(), "Must have non-zero number of input Soy files.");
    this.typeRegistry = localTypeRegistry != null ? localTypeRegistry : typeRegistry;
    this.soyFileSuppliers = soyFileSuppliers;
    this.cache = cache;
    this.generalOptions = generalOptions.clone();
    this.errorReporter = errorReporter;
  }


  /** @param msgBundleHandlerProvider Provider for getting an instance of SoyMsgBundleHandler. */
  @Inject(optional = true)
  void setMsgBundleHandlerProvider(Provider<SoyMsgBundleHandler> msgBundleHandlerProvider) {
    this.msgBundleHandlerProvider = msgBundleHandlerProvider;
  }

  @Inject(optional = true)
  void setCheckConformance(CheckConformance checkConformance) {
    this.checkConformance = checkConformance;
  }

  /** Returns the list of suppliers for the input Soy files. For testing use only! */
  @VisibleForTesting List<SoyFileSupplier> getSoyFileSuppliersForTesting() {
    return soyFileSuppliers;
  }

  /** Returns the general compiler options. For testing use only! */
  @VisibleForTesting SoyGeneralOptions getOptionsForTesting() {
    return generalOptions;
  }

  /** TODO(user): workaround for {@link SoyJsSrcResource}. Remove. */
  ErrorReporter getErrorReporter() {
    return errorReporter;
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
   */
  ParseInfo generateParseInfo(String javaPackage, String javaClassNameSource) {

    SyntaxVersion declaredSyntaxVersion =
        generalOptions.getDeclaredSyntaxVersion(SyntaxVersion.V2_0);
    SoyFileSetNode soyTree = new SoyFileSetParser(
        typeRegistry, cache, declaredSyntaxVersion, soyFileSuppliers, errorReporter)
        .parse();

    // Do renaming of package-relative class names.
    new ResolvePackageRelativeCssNamesVisitor(errorReporter).exec(soyTree);
    ImmutableMap<String, String> parseInfo =
        new GenerateParseInfoVisitor(javaPackage, javaClassNameSource, errorReporter).exec(soyTree);
    return new ParseInfo(result(), parseInfo);
  }

  static final class ParseInfo {
    final CompilationResult result;
    final ImmutableMap<String, String> generatedFiles;

    ParseInfo(CompilationResult result, ImmutableMap<String, String> generatedFiles) {
      this.result = result;
      this.generatedFiles = generatedFiles;
    }
  }

  /**
   * Parses the templates in this file set and generates a template registry. Useful for tools
   * that want to index Soy files without performing further compilation.
   *
   * @throws SoySyntaxException if the templates in this file set cannot be parsed according to
   *     {@link SyntaxVersion#V2_0}.
   */
  public TemplateRegistry generateTemplateRegistry() {
    // If you want your Soy templates to be indexed, you have to use modern syntax.
    SyntaxVersion version = generalOptions.getDeclaredSyntaxVersion(SyntaxVersion.V2_0);
    SoyFileSetNode soyTree = new SoyFileSetParser(
        typeRegistry, cache, version, soyFileSuppliers, errorReporter)
        .parse();
    return new TemplateRegistry(soyTree, errorReporter);
  }

  /**
   * Extracts all messages from this Soy file set into a SoyMsgBundle (which can then be turned
   * into an extracted messages file with the help of a SoyMsgBundleHandler).
   *
   * @return A SoyMsgBundle containing all the extracted messages (locale "en").
   * @throws SoySyntaxException If a syntax error is found.
   */
  public SoyMsgBundle extractMsgs() throws SoySyntaxException {

    SyntaxVersion declaredSyntaxVersion =
        generalOptions.getDeclaredSyntaxVersion(SyntaxVersion.V1_0);
    // Override the type registry with a version that simply returns unknown
    // for any named type.
    SoyTypeRegistry typeRegistry = createDummyTypeRegistry();
    SoyFileSetNode soyTree = new SoyFileSetParser(
        typeRegistry, cache, declaredSyntaxVersion, soyFileSuppliers, errorReporter)
        .parse();
    return new ExtractMsgsVisitor(errorReporter).exec(soyTree);
  }


  /**
   * Prunes messages from a given message bundle, keeping only messages used in this Soy file set.
   *
   * <p> Important: Do not use directly. This is subject to change and your code will break.
   *
   * <p> Note: This method memoizes intermediate results to improve efficiency in the case that it
   * is called multiple times (which is a common case). Thus, this method will not work correctly if
   * the underlying Soy files are modified between calls to this method.
   *
   * @param origTransMsgBundle The message bundle to prune.
   * @return The pruned message bundle.
   * TODO(brndn): Instead of throwing, should return a structure with a list of errors that callers
   * can inspect.
   */
  public SoyMsgBundle pruneTranslatedMsgs(SoyMsgBundle origTransMsgBundle)
      throws SoySyntaxException {

    // ------ Extract msgs from all the templates reachable from public templates. ------
    // Note: In the future, instead of using all public templates as the root set, we can allow the
    // user to provide a root set.

    if (memoizedExtractedMsgIdsForPruning == null) {

      SyntaxVersion declaredSyntaxVersion =
          generalOptions.getDeclaredSyntaxVersion(SyntaxVersion.V1_0);
      SoyTypeRegistry typeRegistry = createDummyTypeRegistry();
      SoyFileSetNode soyTree = new SoyFileSetParser(
          typeRegistry, cache, declaredSyntaxVersion, soyFileSuppliers, errorReporter)
          .parse();

      List<TemplateNode> allPublicTemplates = Lists.newArrayList();
      for (SoyFileNode soyFile : soyTree.getChildren()) {
        for (TemplateNode template : soyFile.getChildren()) {
          if (template.getVisibility() == Visibility.PUBLIC) {
            allPublicTemplates.add(template);
          }
        }
      }

      Map<TemplateNode, TransitiveDepTemplatesInfo> depsInfoMap =
          new FindTransitiveDepTemplatesVisitor(null /* templateRegistry */, errorReporter)
              .execOnMultipleTemplates(allPublicTemplates);
      TransitiveDepTemplatesInfo mergedDepsInfo =
          TransitiveDepTemplatesInfo.merge(depsInfoMap.values());

      SoyMsgBundle extractedMsgBundle = new ExtractMsgsVisitor(errorReporter)
          .execOnMultipleNodes(mergedDepsInfo.depTemplateSet);

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
    return new SoyMsgBundleImpl(
        origTransMsgBundle.getLocaleString(), prunedTransMsgsBuilder.build());
  }


  /**
   * Compiles this Soy file set into a Java object (type {@code SoyTofu}) capable of rendering the
   * compiled templates. The resulting {@code SoyTofu} does not cache intermediate results after
   * substitutions from the SoyMsgBundle and the SoyCssRenamingMap.
   *
   * @see #compileToTofu(com.google.template.soy.tofu.SoyTofuOptions)
   *
   * @return The resulting {@code SoyTofu} object.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public SoyTofu compileToTofu() throws SoySyntaxException {
    return compileToTofu(new SoyTofuOptions());
  }


  /**
   * Compiles this Soy file set into a Java object (type {@code SoyTofu}) capable of rendering the
   * compiled templates.
   *
   * @param tofuOptions The compilation options for the Tofu backend.
   * @return The resulting {@code SoyTofu} object.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public SoyTofu compileToTofu(SoyTofuOptions tofuOptions) throws SoySyntaxException {

    // Defensive copy of options. (Doesn't matter now, but might forget later when it matters.)
    tofuOptions = tofuOptions.clone();

    SyntaxVersion declaredSyntaxVersion =
        generalOptions.getDeclaredSyntaxVersion(SyntaxVersion.V2_0);

    SoyFileSetNode soyTree = new SoyFileSetParser(
        typeRegistry, cache, declaredSyntaxVersion, soyFileSuppliers, errorReporter)
        .parse();
    runMiddleendPasses(soyTree, declaredSyntaxVersion);

    // If allowExternalCalls is not explicitly set, then disallow by default for Tofu backend.
    if (generalOptions.allowExternalCalls() == null) {
      // TODO: Enable this check when all Google internal projects are compliant.
      //(new AssertNoExternalCallsVisitor()).exec(soyTree);
    }

    // Note: Globals should have been substituted already. The pass below is just a check.
    new SubstituteGlobalsVisitor(
        generalOptions.getCompileTimeGlobals(),
        typeRegistry,
        true /* shouldAssertNoUnboundGlobals */,
        errorReporter)
        .exec(soyTree);

    // Clear the SoyDoc strings because they use unnecessary memory.
    new ClearSoyDocStringsVisitor(errorReporter).exec(soyTree);

    ((ErrorReporterImpl) errorReporter).throwIfErrorsPresent();

    return baseTofuFactory.create(soyTree, tofuOptions.useCaching(), errorReporter);
  }


  /**
   * Compiles this Soy file set into a Java object (type {@code SoyTofu}) capable of rendering the
   * compiled templates. The resulting {@code SoyTofu} does not cache intermediate results after
   * substitutions from the SoyMsgBundle and the SoyCssRenamingMap.
   *
   * @see #compileToTofu()
   *
   * @return The result of compiling this Soy file set into a Java object.
   * @throws SoySyntaxException If a syntax error is found.
   * @deprecated Use {@link #compileToTofu()}.
   */
  @Deprecated public SoyTofu compileToJavaObj() throws SoySyntaxException {
    return compileToTofu(new SoyTofuOptions());
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
   * @throws SoySyntaxException If a syntax error is found.
   * TODO(brndn): Instead of throwing, should return a structure with a list of errors that callers
   * can inspect.
   */
  @SuppressWarnings("deprecation")
  public List<String> compileToJsSrc(SoyJsSrcOptions jsSrcOptions, @Nullable SoyMsgBundle msgBundle)
      throws SoySyntaxException {

    // Synchronize old and new ways to declare syntax version V1.
    if (jsSrcOptions.shouldAllowDeprecatedSyntax()) {
      generalOptions.setDeclaredSyntaxVersionName("1.0");
    }
    SyntaxVersion declaredSyntaxVersion =
        generalOptions.getDeclaredSyntaxVersion(SyntaxVersion.V2_0);

    SoyFileSetNode soyTree = new SoyFileSetParser(
        typeRegistry, cache, declaredSyntaxVersion, soyFileSuppliers, errorReporter)
        .parse();
    runMiddleendPasses(soyTree, declaredSyntaxVersion);

    return jsSrcMainProvider.get().genJsSrc(soyTree, jsSrcOptions, msgBundle);
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
   * @throws SoySyntaxException If a syntax error is found.
   * @throws IOException If there is an error in opening/reading a message file or opening/writing
   *     an output JS file.
   */
  @SuppressWarnings("deprecation")
  CompilationResult compileToJsSrcFiles(
      String outputPathFormat, String inputFilePathPrefix, SoyJsSrcOptions jsSrcOptions,
      List<String> locales, @Nullable String messageFilePathFormat)
      throws SoySyntaxException, IOException {

    // Synchronize old and new ways to declare syntax version V1.
    if (jsSrcOptions.shouldAllowDeprecatedSyntax()) {
      generalOptions.setDeclaredSyntaxVersionName("1.0");
    }
    SyntaxVersion declaredSyntaxVersion =
        generalOptions.getDeclaredSyntaxVersion(SyntaxVersion.V2_0);

    Checkpoint checkpoint = errorReporter.checkpoint();
    SoyFileSetNode soyTree = new SoyFileSetParser(
        typeRegistry, cache, declaredSyntaxVersion, soyFileSuppliers, errorReporter)
        .parse();
    if (errorReporter.errorsSince(checkpoint)) {
      return failure();
    }

    checkpoint = errorReporter.checkpoint();
    runMiddleendPasses(soyTree, declaredSyntaxVersion);
    if (errorReporter.errorsSince(checkpoint)) {
      return failure();
    }

    if (locales.isEmpty()) {
      // Not generating localized JS.
      jsSrcMainProvider.get().genJsFiles(
          soyTree, jsSrcOptions, null, null, outputPathFormat, inputFilePathPrefix);

    } else {
      // Generating localized JS.
      for (String locale : locales) {

        SoyFileSetNode soyTreeClone = soyTree.clone();

        String msgFilePath = MainEntryPointUtils.buildFilePath(
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

        jsSrcMainProvider.get().genJsFiles(
            soyTreeClone, jsSrcOptions, locale, msgBundle, outputPathFormat, inputFilePathPrefix);
      }
    }
    return result();
  }

  /**
   * Compiles this Soy file set into Python source code files and writes these Python files to
   * disk.
   *
   * @param outputPathFormat The format string defining how to build the output file path
   *     corresponding to an input file path.
   * @param inputFilePathPrefix The prefix prepended to all input file paths (can be empty string).
   * @param pySrcOptions The compilation options for the Python Src output target.
   * @throws SoySyntaxException If a syntax error is found.
   * @throws IOException If there is an error in opening/reading a message file or opening/writing
   *     an output JS file.
   */
  CompilationResult compileToPySrcFiles(
      String outputPathFormat, String inputFilePathPrefix, SoyPySrcOptions pySrcOptions)
      throws SoySyntaxException, IOException {

    SyntaxVersion declaredSyntaxVersion =
        generalOptions.getDeclaredSyntaxVersion(SyntaxVersion.V2_2);

    Checkpoint checkpoint = errorReporter.checkpoint();
    SoyFileSetNode soyTree = new SoyFileSetParser(
        typeRegistry, cache, declaredSyntaxVersion, soyFileSuppliers, errorReporter)
        .parse();
    if (errorReporter.errorsSince(checkpoint)) {
      return failure();
    }

    checkpoint = errorReporter.checkpoint();
    runMiddleendPasses(soyTree, declaredSyntaxVersion);
    if (errorReporter.errorsSince(checkpoint)) {
      return failure();
    }

    pySrcMainProvider.get().genPyFiles(
        soyTree, pySrcOptions, outputPathFormat, inputFilePathPrefix);

    return result();
  }

  private CompilationResult result() {
    ErrorReporterImpl impl = (ErrorReporterImpl) errorReporter;
    return new CompilationResult(impl.getErrors(), new ErrorPrettyPrinter(soyFileSuppliers));
  }

  private CompilationResult failure() {
    ImmutableCollection<? extends SoySyntaxException> errors
        = ((ErrorReporterImpl) errorReporter).getErrors();
    Preconditions.checkState(!errors.isEmpty());
    return new CompilationResult(errors, new ErrorPrettyPrinter(soyFileSuppliers));
  }

  /**
   * Runs middleend passes on the given Soy tree.
   *
   * @param soyTree The Soy tree to run middleend passes on.
   * @param declaredSyntaxVersion User-declared syntax version.
   * @throws SoySyntaxException If a syntax error is found.
   * @throws SoyAutoescapeException If there is a problem determining the context for an
   *     {@code autoescape="contextual"} template or one of its callers.
   */
  private void runMiddleendPasses(SoyFileSetNode soyTree, SyntaxVersion declaredSyntaxVersion)
      throws SoySyntaxException {

    // Check that all function calls have a SoyFunction definition and have the correct arity.
    // This really belongs in SoyFileSetParser, but moving it there would cause SoyFileSetParser to
    // need to be injected, and that feels like overkill at this time.
    checkFunctionCallsVisitorFactory.create(declaredSyntaxVersion, errorReporter).exec(soyTree);

    // Do renaming of package-relative class names.
    new ResolvePackageRelativeCssNamesVisitor(errorReporter).exec(soyTree);

    // If disallowing external calls, perform the check.
    if (generalOptions.allowExternalCalls() == Boolean.FALSE) {
      (new AssertNoExternalCallsVisitor(errorReporter)).exec(soyTree);
    }

    // If requiring strict autoescaping, check and enforce it.
    if (generalOptions.isStrictAutoescapingRequired()) {
      (new AssertStrictAutoescapingVisitor(errorReporter)).exec(soyTree);
    }

    // Handle CSS commands (if not backend-specific) and substitute compile-time globals.
    new HandleCssCommandVisitor(generalOptions.getCssHandlingScheme(), errorReporter).exec(soyTree);
    if (generalOptions.getCompileTimeGlobals() != null || typeRegistry != null) {
      new SubstituteGlobalsVisitor(
          generalOptions.getCompileTimeGlobals(),
          typeRegistry,
          false /* shouldAssertNoUnboundGlobals */,
          errorReporter)
          .exec(soyTree);
    }

    // Run contextual escaping after CSS has been done, but before the autoescape visitor adds
    // |escapeHtml directives.  The contextual directive filterHtmlIdent serves the same purpose
    // in some places, but with runtime guarantees.
    doContextualEscaping(soyTree);
    performAutoescapeVisitor.exec(soyTree);

    // Run the conformance checks after the CheckEscapingSanityVisitor has run
    // (in doContextualAutoescaping). This is because one particular conformance check,
    // com.google.template.soy.conformance.BanInlineEventHandlers, runs the contextual autoescaper
    // for its side effects (context inference). The contextual autoescaper mutates the parse tree,
    // (specifically, adding |text directives) and the CheckEscapingSanityVisitor, thinking that
    // the template author used them, complains that they are for internal use only.
    // TODO(brndn): consider moving the conformance pass out of the main compilation path.
    // A risk of the current situation is that the conformance pass could actually modify
    // gencode destined for production. On the other hand, moving the conformance pass elsewhere
    // could reduce its value, since it might not see precisely the same AST seen on the main
    // compilation path.
    if (checkConformance != null) {
      checkConformance.exec(soyTree);
    }

    // Add print directives that mark inline-scripts as safe to run.
    if (generalOptions.supportContentSecurityPolicy()) {
      ContentSecurityPolicyPass.blessAuthorSpecifiedScripts(
          contextualAutoescaper.getSlicedRawTextNodes());
    }

    // Attempt to simplify the tree.
    new ChangeCallsToPassAllDataVisitor(errorReporter).exec(soyTree);
    simplifyVisitor.exec(soyTree);
  }


  private void doContextualEscaping(SoyFileSetNode soyTree)
      throws SoySyntaxException {
    new CheckEscapingSanityVisitor(errorReporter).exec(soyTree);
    List<TemplateNode> extraTemplates = contextualAutoescaper.rewrite(soyTree);
    // TODO: Run the redundant template remover here and rename after CL 16642341 is in.
    if (!extraTemplates.isEmpty()) {
      // TODO: pull out somewhere else.  Ideally do the merge as part of the redundant template
      // removal.
      Map<String, SoyFileNode> containingFile = Maps.newHashMap();
      for (SoyFileNode fileNode : soyTree.getChildren()) {
        for (TemplateNode templateNode : fileNode.getChildren()) {
          String name =
              templateNode instanceof TemplateDelegateNode
                  ? ((TemplateDelegateNode) templateNode).getDelTemplateName()
                  : templateNode.getTemplateName();
          containingFile.put(DerivedTemplateUtils.getBaseName(name), fileNode);
        }
      }
      for (TemplateNode extraTemplate : extraTemplates) {
        String name =
            extraTemplate instanceof TemplateDelegateNode
                ? ((TemplateDelegateNode) extraTemplate).getDelTemplateName()
                : extraTemplate.getTemplateName();
        containingFile.get(DerivedTemplateUtils.getBaseName(name)).addChild(extraTemplate);
      }
    }
  }

  private SoyTypeRegistry createDummyTypeRegistry() {
    return new SoyTypeRegistry(ImmutableSet.<SoyTypeProvider>of(
      new SoyTypeProvider() {
        @Override
        public SoyType getType(String typeName, SoyTypeRegistry typeRegistry) {
          return UnknownType.getInstance();
        }
      }));
  }
}
