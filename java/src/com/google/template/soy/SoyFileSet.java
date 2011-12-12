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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.InputSupplier;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.util.Providers;
import com.google.template.soy.base.SoyFileSupplier;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.VolatileSoyFileSupplier;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.javasrc.SoyJavaSrcOptions;
import com.google.template.soy.javasrc.SoyTemplateRuntime;
import com.google.template.soy.javasrc.SoyTemplateRuntimes;
import com.google.template.soy.javasrc.dyncompile.SoyToJavaDynamicCompiler;
import com.google.template.soy.javasrc.internal.JavaSrcMain;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.JsSrcMain;
import com.google.template.soy.jssrc.internal.JsSrcUtils;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.msgs.internal.ExtractMsgsVisitor;
import com.google.template.soy.parseinfo.passes.GenerateParseInfoVisitor;
import com.google.template.soy.parsepasses.ChangeCallsToPassAllDataVisitor;
import com.google.template.soy.parsepasses.CheckFunctionCallsVisitor;
import com.google.template.soy.parsepasses.HandleCssCommandVisitor;
import com.google.template.soy.parsepasses.PerformAutoescapeVisitor;
import com.google.template.soy.parsepasses.contextautoesc.ContextualAutoescaper;
import com.google.template.soy.parsepasses.contextautoesc.DerivedTemplateUtils;
import com.google.template.soy.parsepasses.contextautoesc.SoyAutoescapeException;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.SoyGeneralOptions.CssHandlingScheme;
import com.google.template.soy.sharedpasses.AssertNoExternalCallsVisitor;
import com.google.template.soy.sharedpasses.ClearSoyDocStringsVisitor;
import com.google.template.soy.sharedpasses.SubstituteGlobalsVisitor;
import com.google.template.soy.sharedpasses.opti.SimplifyVisitor;
import com.google.template.soy.soyparse.SoyFileSetParser;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.tofu.SoyTofuOptions;
import com.google.template.soy.tofu.internal.BaseTofu.BaseTofuFactory;
import com.google.template.soy.xliffmsgplugin.XliffMsgPlugin;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;


/**
 * Represents a complete set of Soy files for compilation as one bundle. The files may depend on
 * each other but should not have dependencies outside of the set.
 *
 * <p> Note: Soy file (or resource) contents must be encoded in UTF-8.
 *
 * @author Kai Huang
 */
public final class SoyFileSet {


  /**
   * Injectable factory for creating an instance of this class.
   */
  static interface SoyFileSetFactory {

    /**
     * @param soyFileSuppliers The suppliers for the input Soy files.
     * @param options The general compiler options.
     */
    public SoyFileSet create(List<SoyFileSupplier> soyFileSuppliers, SoyGeneralOptions options);
  }


  /**
   * Builder for a {@code SoyFileSet}.
   */
  public static final class Builder {


    /** Factory for creating an instance of SoyFileSet (statically injected). */
    @Inject
    private static SoyFileSetFactory soyFileSetFactory = null;


    /** The list of SoyFileSuppliers collected so far. */
    private final ImmutableList.Builder<SoyFileSupplier> listBuilder;

    /** The general compiler options. */
    private SoyGeneralOptions generalOptions;


    /**
     * Constructs a builder that starts with a default {@link SoyGeneralOptions} object (options
     * can be modified via methods on the builder).
     */
    @Inject
    public Builder() {
      this(new SoyGeneralOptions());
    }


    /**
     * Constructs a builder with a specified {@link SoyGeneralOptions} object.
     * @param generalOptions The {@code SoyGeneralOptions} object to use.
     */
    public Builder(SoyGeneralOptions generalOptions) {

      this.listBuilder = ImmutableList.builder();
      this.generalOptions = generalOptions.clone();

      // Bootstrap Guice for Soy API users that don't use Guice. This is done by statically
      // injecting an instance of SoyFileSetFactory into this Builder class. Everything derived
      // from this SoyFileSetFactory will be Guice-enabled.
      GuiceInitializer.initializeIfNecessary();
    }


    /**
     * Builds the new {@code SoyFileSet}.
     * @return The new {@code SoyFileSet}.
     */
    public SoyFileSet build() {
      return soyFileSetFactory.create(listBuilder.build(), generalOptions);
    }


    /**
     * Adds an input Soy file, given an {@code InputSupplier} for the file content, as well as the
     * desired file path for messages.
     *
     * @param contentSupplier Supplier of a Reader for the Soy file content.
     * @param filePath The path to the Soy file (used for messages only).
     * @return This builder.
     */
    public Builder add(InputSupplier<? extends Reader> contentSupplier, String filePath) {
      listBuilder.add(SoyFileSupplier.Factory.create(contentSupplier, filePath));
      return this;
    }


    /**
     * Adds an input Soy file, given a {@code File}.
     *
     * @param inputFile The Soy file.
     * @return This builder.
     */
    public Builder add(File inputFile) {
      listBuilder.add(SoyFileSupplier.Factory.create(inputFile));
      return this;
    }


    /**
     * Adds an input Soy file that the system will watch for changes, given a {@code File}.
     *
     * @param inputFile The Soy file.
     * @return This builder.
     */
    public Builder addVolatile(File inputFile) {
      listBuilder.add(new VolatileSoyFileSupplier(inputFile));
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
      listBuilder.add(SoyFileSupplier.Factory.create(inputFileUrl, filePath));
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
      listBuilder.add(SoyFileSupplier.Factory.create(inputFileUrl));
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
      listBuilder.add(SoyFileSupplier.Factory.create(content, filePath));
      return this;
    }


    /**
     * Sets whether to allow external calls (calls to undefined templates).
     *
     * @param allowExternalCalls Whether to allow external calls (calls to undefined templates).
     * @return This builder.
     */
    public Builder setAllowExternalCalls(boolean allowExternalCalls) {
      this.generalOptions.setAllowExternalCalls(allowExternalCalls);
      return this;
    }


    /**
     * Sets the scheme for handling {@code css} commands.
     *
     * @param cssHandlingScheme The scheme for handling {@code css} commands.
     * @return This builder.
     */
    public Builder setCssHandlingScheme(CssHandlingScheme cssHandlingScheme) {
      this.generalOptions.setCssHandlingScheme(cssHandlingScheme);
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
      this.generalOptions.setCompileTimeGlobals(compileTimeGlobalsMap);
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
      this.generalOptions.setCompileTimeGlobals(compileTimeGlobalsFile);
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
      this.generalOptions.setCompileTimeGlobals(compileTimeGlobalsResource);
      return this;
    }

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

  /** Provider for getting an instance of JavaSrcMain. */
  private final Provider<JavaSrcMain> javaSrcMainProvider;

  /** The instance of PerformAutoescapeVisitor to use. */
  private final PerformAutoescapeVisitor performAutoescapeVisitor;

  /** The instance of ContextualAutoescaper to use. */
  private final ContextualAutoescaper contextualAutoescaper;

  /** The instance of CheckFunctionCallsVisitor to use. */
  private final CheckFunctionCallsVisitor checkFunctionCallsVisitor;

  /** The instance of SimplifyVisitor to use. */
  private final SimplifyVisitor simplifyVisitor;

  /** The suppliers for the input Soy files. */
  private final List<SoyFileSupplier> soyFileSuppliers;

  /** The general compiler options. */
  private final SoyGeneralOptions generalOptions;


  /**
   * @param baseTofuFactory Factory for creating an instance of BaseTofu.
   * @param jsSrcMainProvider Provider for getting an instance of JsSrcMain.
   * @param javaSrcMainProvider Provider for getting an instance of JavaSrcMain.
   * @param performAutoescapeVisitor The instance of PerformAutoescapeVisitor to use.
   * @param contextualAutoescaper The instance of ContextualAutoescaper to use.
   * @param simplifyVisitor The instance of SimplifyVisitor to use.
   * @param soyFileSuppliers The suppliers for the input Soy files.
   * @param generalOptions The general compiler options.
   */
  @Inject
  SoyFileSet(
      BaseTofuFactory baseTofuFactory, Provider<JsSrcMain> jsSrcMainProvider,
      Provider<JavaSrcMain> javaSrcMainProvider, PerformAutoescapeVisitor performAutoescapeVisitor,
      ContextualAutoescaper contextualAutoescaper, SimplifyVisitor simplifyVisitor,
      CheckFunctionCallsVisitor checkFunctionCallsVisitor,
      @Assisted List<SoyFileSupplier> soyFileSuppliers,
      @Assisted SoyGeneralOptions generalOptions) {

    // Default value is optionally replaced using method injection.
    this.msgBundleHandlerProvider = DEFAULT_SOY_MSG_BUNDLE_HANDLER_PROVIDER;

    this.baseTofuFactory = baseTofuFactory;
    this.jsSrcMainProvider = jsSrcMainProvider;
    this.javaSrcMainProvider = javaSrcMainProvider;
    this.performAutoescapeVisitor = performAutoescapeVisitor;
    this.contextualAutoescaper = contextualAutoescaper;
    this.simplifyVisitor = simplifyVisitor;
    this.checkFunctionCallsVisitor = checkFunctionCallsVisitor;

    Preconditions.checkArgument(
        soyFileSuppliers.size() > 0, "Must have non-zero number of input Soy files.");
    this.soyFileSuppliers = soyFileSuppliers;
    this.generalOptions = generalOptions.clone();
  }


  /** @param msgBundleHandlerProvider Provider for getting an instance of SoyMsgBundleHandler. */
  @Inject(optional = true)
  void setMsgBundleHandlerProvider(Provider<SoyMsgBundleHandler> msgBundleHandlerProvider) {
    this.msgBundleHandlerProvider = msgBundleHandlerProvider;
  }

  /** Returns the list of suppliers for the input Soy files. For testing use only! */
  @VisibleForTesting List<SoyFileSupplier> getSoyFileSuppliersForTesting() {
    return soyFileSuppliers;
  }

  /** Returns the general compiler options. For testing use only! */
  @VisibleForTesting SoyGeneralOptions getOptionsForTesting() {
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
   * @throws SoySyntaxException If a syntax error is found.
   */
  public ImmutableMap<String, String> generateParseInfo(
      String javaPackage, String javaClassNameSource) throws SoySyntaxException {

    SoyFileSetNode soyTree = (new SoyFileSetParser(soyFileSuppliers)).parse();

    return (new GenerateParseInfoVisitor(javaPackage, javaClassNameSource)).exec(soyTree);
  }


  /**
   * Extracts all messages from this Soy file set into a SoyMsgBundle (which can then be turned
   * into an extracted messages file with the help of a SoyMsgBundleHandler).
   *
   * @return A SoyMsgBundle containing all the extracted messages (locale "en").
   * @throws SoySyntaxException If a syntax error is found.
   */
  public SoyMsgBundle extractMsgs() throws SoySyntaxException {

    SoyFileSetNode soyTree =
        (new SoyFileSetParser(soyFileSuppliers))
            .setDoEnforceSyntaxVersionV2(false).setDoCheckOverrides(false).parse();

    return (new ExtractMsgsVisitor()).exec(soyTree);
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

    // TODO: Allow binding a SoyTofu instance to volatile inputs.
    SoyFileSetNode soyTree = (new SoyFileSetParser(soyFileSuppliers)).parse();
    runMiddleendPasses(soyTree, true);

    // If allowExternalCalls is not explicitly set, then disallow by default for Tofu backend.
    if (generalOptions.allowExternalCalls() == null) {
      // TODO: Enable this check when all Google internal projects are compliant.
      //(new AssertNoExternalCallsVisitor()).exec(soyTree);
    }

    // Note: Globals should have been substituted already. The pass below is just a check.
    (new SubstituteGlobalsVisitor(generalOptions.getCompileTimeGlobals(), true)).exec(soyTree);

    // Clear the SoyDoc strings because they use unnecessary memory.
    (new ClearSoyDocStringsVisitor()).exec(soyTree);

    return baseTofuFactory.create(soyTree, tofuOptions.useCaching());
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
   * Compiles this Soy file set into a Java object (type {@code SoyTofu}) capable of rendering the
   * compiled templates.
   *
   * @param useCaching Whether the resulting SoyTofu instance should cache intermediate results
   *     after substitutions from the SoyMsgBundle and the SoyCssRenamingMap. It is recommended to
   *     set this param to true if you're planning to reuse the SoyTofu instance to render multiple
   *     times.
   *
   *     <p> Specifically, if this param is set to true, then
   *     (a) The first time the SoyTofu is used with a new combination of SoyMsgBundle and
   *         SoyCssRenamingMap, the render will be slower. (Note that this first-render slowness can
   *         be eliminated by calling the method {@link SoyTofu#addToCache} to prime the cache.)
   *     (b) The subsequent times the SoyTofu is used with an already-seen combination of
   *         SoyMsgBundle and SoyCssRenamingMap, the render will be faster.
   *
   *     <p> The cache will use memory proportional to the number of distinct combinations of
   *     SoyMsgBundle and SoyCssRenamingMap your app uses (note most apps have at most one
   *     SoyCssRenamingMap). If you find memory usage to be a problem, you can manually control the
   *     contents of the cache. See {@link SoyTofu.Renderer#setDontAddToCache} for details.
   *
   * @see #compileToTofu(com.google.template.soy.tofu.SoyTofuOptions)
   *
   * @return The result of compiling this Soy file set into a Java object.
   * @throws SoySyntaxException If a syntax error is found.
   * @deprecated Use {@link #compileToTofu(com.google.template.soy.tofu.SoyTofuOptions)}.
   */
  @Deprecated public SoyTofu compileToJavaObj(boolean useCaching) throws SoySyntaxException {
    SoyTofuOptions options = new SoyTofuOptions();
    options.setUseCaching(useCaching);
    return compileToTofu(options);
  }


  /**
   * Warning: The Java Src backend is experimental (incomplete, repetitive, untested, undocumented).
   * <p>
   * Returns a bundle of templates compiled using the experimental java compiler that will be
   * automatically recompiled if the underlying Soy sources are modified.
   *
   * @return The compiled result.
   */
  public SoyTemplateRuntimes compileToRuntimes(
      final String bundleName, SoyJavaSrcOptions options, final SoyMsgBundle msgBundle) {

    // Defensively copy options so that changes to them can't affect lazily compiled modules.
    final SoyJavaSrcOptions copyOfOptions = options.clone();
    copyOfOptions.setCodeStyle(SoyJavaSrcOptions.CodeStyle.STRINGBUILDER);

    return new SoyTemplateRuntimes() {

      /** The versions corresponding to compiledTemplates used to produce the compiled form. */
      private List<SoyFileSupplier.Version> compiledVersions = null;
      /** The compiled form or null if not yet compiled. */
      private ImmutableMap<String, SoyTemplateRuntime> compiledTemplates;
      /** True if at least one file supplier might change from the last compiled version. */
      private final boolean isDynamic;

      {
        synchronized (this) {  // Synchronize so isDynamic is in step with compiledVersions.
          compile();

          // If none of the inputs are volatile we never need to check versions in newRenderer().
          boolean isDynamic = false;
          for (SoyFileSupplier.Version version : this.compiledVersions) {
            if (version != SoyFileSupplier.Version.STABLE_VERSION) {
              isDynamic = true;
              break;
            }
          }
          this.isDynamic = isDynamic;
        }
      }

      /**
       * @throws SoySyntaxException If an input is out of date and malformed.
       */
      @Override
      public SoyTemplateRuntime newRenderer(String templateName) throws SoySyntaxException {

        // Recompile if necessary.
        if (isOutOfDate()) {
          compile();
        }

        return compiledTemplates.get(templateName);
      }

      /**
       * Invokes the {@link SoyToJavaDynamicCompiler} to produce an up-to-date version of the
       * template name to SoyRuntime map.
       */
      private void compile() throws SoySyntaxException {

        Pair<SoyFileSetNode, List<SoyFileSupplier.Version>> soyTreeAndVersions =
            (new SoyFileSetParser(soyFileSuppliers)).parseWithVersions();
        SoyFileSetNode soyTree = soyTreeAndVersions.first;
        runMiddleendPasses(soyTree, true);

        ImmutableMap<String, SoyTemplateRuntime> result = SoyToJavaDynamicCompiler.compile(
            bundleName, compileFileSetToJavaSrc(soyTree, copyOfOptions, msgBundle));

        // Atomically updated compiledTemplates, the version, and isDynamic since they are
        // all related.
        synchronized (this) {
          compiledTemplates = result;
          compiledVersions = soyTreeAndVersions.second;
        }
      }

      /**
       * True iff there is a volatile input that is out of date.
       */
      private boolean isOutOfDate() {
        if (isDynamic) {
          // Check if we need to recompile based on changes to volatile inputs.
          int numFiles = soyFileSuppliers.size();
          for (int i = 0; i < numFiles; ++i) {
            if (soyFileSuppliers.get(i).hasChangedSince(compiledVersions.get(i))) {
              return true;
            }
          }
        }
        return false;
      }
    };
  }


  /**
   * Warning: The Java Src backend is experimental (incomplete, repetitive, untested, undocumented).
   *
   * <p> To use Soy from Java, you should call {@link #compileToTofu()} to obtain a
   * {@code SoyTofu} object that will be able to render any public template in this Soy file set.
   *
   * @param javaSrcOptions The compilation options for the Java Src output target.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @return Java source code in one big ugly blob. Can be put inside any class without needing
   *     additional imports because all class names in the generated code are fully qualified.
   */
  public String compileToJavaSrc(SoyJavaSrcOptions javaSrcOptions, SoyMsgBundle msgBundle) {

    SoyFileSetNode soyTree = (new SoyFileSetParser(soyFileSuppliers)).parse();
    runMiddleendPasses(soyTree, true);

    return compileFileSetToJavaSrc(soyTree, javaSrcOptions, msgBundle);
  }


  /**
   * Invokes the Java Src backend after running the prerequisite visitors on the given Soy tree.
   *
   * @param soyTree Modified in place by the prerequisite visitors.
   * @param javaSrcOptions The compilation options for the Java Src output target.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @return Java source code in one big ugly blob. Can be put inside any class without needing
   *     additional imports because all class names in the generated code are fully qualified.
   */
  private String compileFileSetToJavaSrc(
      SoyFileSetNode soyTree, SoyJavaSrcOptions javaSrcOptions, SoyMsgBundle msgBundle) {

    // Note: Globals should have been substituted already. The pass below is just a check.
    (new SubstituteGlobalsVisitor(generalOptions.getCompileTimeGlobals(), true)).exec(soyTree);

    return javaSrcMainProvider.get().genJavaSrc(soyTree, javaSrcOptions, msgBundle);
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
   */
  public List<String> compileToJsSrc(SoyJsSrcOptions jsSrcOptions, @Nullable SoyMsgBundle msgBundle)
      throws SoySyntaxException {

    boolean doEnforceSyntaxVersionV2 = ! jsSrcOptions.shouldAllowDeprecatedSyntax();
    SoyFileSetNode soyTree = (new SoyFileSetParser(soyFileSuppliers))
        .setDoEnforceSyntaxVersionV2(doEnforceSyntaxVersionV2).parse();
    runMiddleendPasses(soyTree, doEnforceSyntaxVersionV2);

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
  void compileToJsSrcFiles(
      String outputPathFormat, String inputFilePathPrefix, SoyJsSrcOptions jsSrcOptions,
      List<String> locales, @Nullable String messageFilePathFormat)
      throws SoySyntaxException, IOException {

    boolean doEnforceSyntaxVersionV2 = ! jsSrcOptions.shouldAllowDeprecatedSyntax();
    SoyFileSetNode soyTree = (new SoyFileSetParser(soyFileSuppliers))
        .setDoEnforceSyntaxVersionV2(doEnforceSyntaxVersionV2).parse();
    runMiddleendPasses(soyTree, doEnforceSyntaxVersionV2);

    if (locales.size() == 0) {
      // Not generating localized JS.
      jsSrcMainProvider.get().genJsFiles(
          soyTree, jsSrcOptions, null, null, outputPathFormat, inputFilePathPrefix);

    } else {
      // Generating localized JS.
      for (String locale : locales) {

        SoyFileSetNode soyTreeClone = soyTree.clone();

        String msgFilePath =
            JsSrcUtils.buildFilePath(messageFilePathFormat, locale, null, inputFilePathPrefix);

        SoyMsgBundle msgBundle =
            msgBundleHandlerProvider.get().createFromFile(new File(msgFilePath));
        if (msgBundle.getLocaleString() == null) {
          // TODO: Remove this check (but make sure no projects depend on this behavior).
          // There was an error reading the message file. We continue processing only if the locale
          // begins with "en", because falling back to the Soy source will proably be fine.
          if (!locale.startsWith("en")) {
            throw new IOException("Error opening or reading message file " + msgFilePath);
          }
        }

        jsSrcMainProvider.get().genJsFiles(
            soyTreeClone, jsSrcOptions, locale, msgBundle, outputPathFormat, inputFilePathPrefix);
      }
    }
  }


  /**
   * Runs middleend passes on the given Soy tree.
   *
   * @param soyTree The Soy tree to run middleend passes on.
   * @param doEnforceSyntaxVersionV2 Whether to enforce SyntaxVersion.V2.
   * @throws SoySyntaxException If a syntax error is found.
   * @throws SoyAutoescapeException If there is a problem determining the context for an
   *     {@code autoescape="contextual"} template or one of its callers.
   */
  private void runMiddleendPasses(SoyFileSetNode soyTree, boolean doEnforceSyntaxVersionV2)
      throws SoySyntaxException {

    // If diallowing external calls, perform the check.
    if (generalOptions.allowExternalCalls() == Boolean.FALSE) {
      (new AssertNoExternalCallsVisitor()).exec(soyTree);
    }

    // Handle CSS commands (if not backend-specific) and substitute compile-time globals.
    (new HandleCssCommandVisitor(generalOptions.getCssHandlingScheme())).exec(soyTree);
    if (generalOptions.getCompileTimeGlobals() != null) {
      (new SubstituteGlobalsVisitor(generalOptions.getCompileTimeGlobals(), false)).exec(soyTree);
    }

    // Soy version 1 allowed calling functions that are not backed by any SoyFunction definition.
    checkFunctionCallsVisitor.setAllowExternallyDefinedFunctions(!doEnforceSyntaxVersionV2);
    checkFunctionCallsVisitor.exec(soyTree);

    // Run contextual escaping after CSS has been done, but before the autoescape visitor adds
    // |escapeHtml directives.  The contextual directive filterHtmlIdent serves the same purpose
    // in some places, but with runtime guarantees.
    doContextualEscaping(soyTree);
    performAutoescapeVisitor.exec(soyTree);

    // Attempt to simplify the tree.
    (new ChangeCallsToPassAllDataVisitor()).exec(soyTree);
    simplifyVisitor.exec(soyTree);
  }


  private void doContextualEscaping(SoyFileSetNode soyTree) throws SoySyntaxException {
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

}
