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
import com.google.common.io.InputSupplier;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.util.Providers;
import com.google.template.soy.base.SoyFileSupplier;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.javasrc.SoyJavaSrcOptions;
import com.google.template.soy.javasrc.internal.JavaSrcMain;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.JsSrcMain;
import com.google.template.soy.jssrc.internal.JsSrcUtils;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.msgs.internal.ExtractMsgsVisitor;
import com.google.template.soy.parseinfo.passes.GenerateParseInfoVisitor;
import com.google.template.soy.parsepasses.CheckOverridesVisitor;
import com.google.template.soy.parsepasses.HandleCssCommandVisitor;
import com.google.template.soy.parsepasses.PerformAutoescapeVisitor;
import com.google.template.soy.parsepasses.PrependNamespacesVisitor;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.SoyGeneralOptions.CssHandlingScheme;
import com.google.template.soy.sharedpasses.AssertSyntaxVersionV2Visitor;
import com.google.template.soy.sharedpasses.CheckSoyDocVisitor;
import com.google.template.soy.sharedpasses.ClearSoyDocStringsVisitor;
import com.google.template.soy.sharedpasses.ReplaceStringPrintNodesVisitor;
import com.google.template.soy.sharedpasses.SubstituteGlobalsVisitor;
import com.google.template.soy.soyparse.SoyFileSetParser;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoytreeUtils;
import com.google.template.soy.tofu.SoyTofu;
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
    private SoyGeneralOptions options;


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
     * @param options The {@code SoyGeneralOptions} object to use.
     */
    public Builder(SoyGeneralOptions options) {

      this.listBuilder = ImmutableList.builder();
      this.options = options.clone();

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
      return soyFileSetFactory.create(listBuilder.build(), options);
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
      listBuilder.add(new SoyFileSupplier(contentSupplier, filePath));
      return this;
    }


    /**
     * Adds an input Soy file, given a {@code File}.
     *
     * @param inputFile The Soy file.
     * @return This builder.
     */
    public Builder add(File inputFile) {
      listBuilder.add(new SoyFileSupplier(inputFile));
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
      listBuilder.add(new SoyFileSupplier(inputFileUrl, filePath));
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
      listBuilder.add(new SoyFileSupplier(inputFileUrl));
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
      listBuilder.add(new SoyFileSupplier(content, filePath));
      return this;
    }


    /**
     * Sets the scheme for handling {@code css} commands.
     *
     * @param cssHandlingScheme The scheme for handling {@code css} commands.
     * @return This builder.
     */
    public Builder setCssHandlingScheme(CssHandlingScheme cssHandlingScheme) {
      this.options.setCssHandlingScheme(cssHandlingScheme);
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
      this.options.setCompileTimeGlobals(compileTimeGlobalsMap);
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
      this.options.setCompileTimeGlobals(compileTimeGlobalsFile);
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
      this.options.setCompileTimeGlobals(compileTimeGlobalsResource);
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

  /** The suppliers for the input Soy files. */
  private final List<SoyFileSupplier> soyFileSuppliers;

  /** The general compiler options. */
  private final SoyGeneralOptions options;


  /**
   * @param baseTofuFactory Factory for creating an instance of BaseTofu.
   * @param jsSrcMainProvider Provider for getting an instance of JsSrcMain.
   * @param javaSrcMainProvider Provider for getting an instance of JavaSrcMain.
   * @param performAutoescapeVisitor The instance of PerformAutoescapeVisitor to use.
   * @param soyFileSuppliers The suppliers for the input Soy files.
   * @param options The general compiler options.
   */
  @Inject
  SoyFileSet(BaseTofuFactory baseTofuFactory, Provider<JsSrcMain> jsSrcMainProvider,
             Provider<JavaSrcMain> javaSrcMainProvider,
             PerformAutoescapeVisitor performAutoescapeVisitor,
             @Assisted List<SoyFileSupplier> soyFileSuppliers,
             @Assisted SoyGeneralOptions options) {

    // Default value is optionally replaced using method injection.
    this.msgBundleHandlerProvider = DEFAULT_SOY_MSG_BUNDLE_HANDLER_PROVIDER;

    this.baseTofuFactory = baseTofuFactory;
    this.jsSrcMainProvider = jsSrcMainProvider;
    this.javaSrcMainProvider = javaSrcMainProvider;
    this.performAutoescapeVisitor = performAutoescapeVisitor;

    Preconditions.checkArgument(
        soyFileSuppliers.size() > 0, "Must have non-zero number of input Soy files.");
    this.soyFileSuppliers = soyFileSuppliers;
    this.options = options;
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
    return options;
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

    SoyFileSetNode soyTree = parse();

    (new AssertSyntaxVersionV2Visitor()).exec(soyTree);
    (new CheckSoyDocVisitor(true)).exec(soyTree);

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

    SoyFileSetNode soyTree = parse();

    return (new ExtractMsgsVisitor()).exec(soyTree);
  }


  /**
   * Compiles this Soy file set into a Java object (type {@code SoyTofu}) capable of rendering the
   * compiled templates.
   *
   * @return The result of compiling this Soy file set into a Java object.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public SoyTofu compileToJavaObj() throws SoySyntaxException {

    SoyFileSetNode soyTree = parse();

    (new AssertSyntaxVersionV2Visitor()).exec(soyTree);
    (new CheckSoyDocVisitor(true)).exec(soyTree);
    // Note: Globals should have been substituted already. The pass below is just a check.
    SoytreeUtils.visitAllExprs(
        soyTree, new SubstituteGlobalsVisitor(options.getCompileTimeGlobals(), true));

    // Clear the SoyDoc strings because they use unnecessary memory.
    (new ClearSoyDocStringsVisitor()).exec(soyTree);

    return baseTofuFactory.create(soyTree);
  }


  /**
   * <p> Warning: The Java Src backend is experimental (repetitive, untested, undocumented).
   *
   * <p> To use Soy from Java, you should call {@link #compileToJavaObj()} to obtain a
   * {@code SoyTofu} object that will be able to render any public template in this Soy file set.
   *
   * @param javaSrcOptions The compilation options for the Java Src output target.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @return Java source code in one big ugly blob. Can be put inside any class without needing
   *     additional imports because all class names in the generated code are fully qualified.
   */
  public String compileToJavaSrc(SoyJavaSrcOptions javaSrcOptions, SoyMsgBundle msgBundle) {

    SoyFileSetNode soyTree = parse();

    // TODO: Some passes are here, some are in JavaSrcMain... reorganize better.
    (new AssertSyntaxVersionV2Visitor()).exec(soyTree);
    (new CheckSoyDocVisitor(true)).exec(soyTree);
    // Note: Globals should have been substituted already. The pass below is just a check.
    SoytreeUtils.visitAllExprs(
        soyTree, new SubstituteGlobalsVisitor(options.getCompileTimeGlobals(), true));

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

    SoyFileSetNode soyTree = parse();

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

    if (locales.size() == 0) {
      // Not generating localized JS.

      SoyFileSetNode soyTree = parse();

      jsSrcMainProvider.get().genJsFiles(
          soyTree, jsSrcOptions, null, null, outputPathFormat, inputFilePathPrefix);

    } else {
      // Generating localized JS.
      for (String locale : locales) {

        // TODO: Implement clone() for Soy parse tree nodes so we don't have to reparse.
        SoyFileSetNode soyTree = parse();

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
            soyTree, jsSrcOptions, locale, msgBundle, outputPathFormat, inputFilePathPrefix);
      }
    }
  }


  /**
   * Parses the files in this Soy file set and returns the combined parse tree.
   *
   * @return The result of parsing this Soy file set.
   * @throws SoySyntaxException If a syntax error is found.
   */
  private SoyFileSetNode parse() throws SoySyntaxException {

    // Parse the files.
    SoyFileSetNode soyTree = SoyFileSetParser.parseSoyFiles(soyFileSuppliers);

    // Execute passes that fix up the parse tree.
    (new PrependNamespacesVisitor()).exec(soyTree);
    (new CheckOverridesVisitor()).exec(soyTree);
    (new HandleCssCommandVisitor(options.getCssHandlingScheme())).exec(soyTree);
    performAutoescapeVisitor.exec(soyTree);

    if (options.getCompileTimeGlobals() != null) {
      SoytreeUtils.visitAllExprs(
          soyTree, new SubstituteGlobalsVisitor(options.getCompileTimeGlobals(), false));
    }
    (new ReplaceStringPrintNodesVisitor()).exec(soyTree);

    return soyTree;
  }

}
