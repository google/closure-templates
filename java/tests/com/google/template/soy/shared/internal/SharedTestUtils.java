/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.shared.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.soyparse.SoyFileSetParser;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.types.SoyTypeRegistry;

import java.util.List;

import javax.annotation.Nullable;


/**
 * Shared utilities for unit tests.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class SharedTestUtils {

  private SharedTestUtils() {}


  /**
   * Simulates the start of a new Soy API call by entering/re-entering the ApiCallScope and seeding
   * scoped values common to all backends. Does not seed backend-specific API call parameters.
   *
   * @param injector The Guice injector responsible for injections during the API call.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the
   *     Soy source.
   * @param bidiGlobalDir The bidi global directionality (ltr=1, rtl=-1, or 0 to use a value derived
   *     from the msgBundle locale, if any, otherwise ltr).
   * @return The ApiCallScope object (for use by the caller of this method to seed additional API
   *     call parameters, such as backend-specific parameters).
   */
  public static GuiceSimpleScope simulateNewApiCall(
      Injector injector, @Nullable SoyMsgBundle msgBundle, int bidiGlobalDir) {

    return simulateNewApiCall(
        injector, msgBundle,
        bidiGlobalDir == 0 ? null : BidiGlobalDir.forStaticIsRtl(bidiGlobalDir < 0));
  }


  /**
   * Simulates the start of a new Soy API call by entering/re-entering the ApiCallScope and seeding
   * scoped values common to all backends. Does not seed backend-specific API call parameters.
   *
   * @param injector The Guice injector responsible for injections during the API call.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the
   *     Soy source.
   * @param bidiGlobalDir The bidi global directionality. If null, it is derived from the msgBundle
   *     locale, if any, otherwise ltr.
   * @return The ApiCallScope object (for use by the caller of this method to seed additional API
   *     call parameters, such as backend-specific parameters).
   */
  public static GuiceSimpleScope simulateNewApiCall(
      Injector injector, @Nullable SoyMsgBundle msgBundle, @Nullable BidiGlobalDir bidiGlobalDir) {

    GuiceSimpleScope apiCallScope =
        injector.getInstance(Key.get(GuiceSimpleScope.class, ApiCall.class));

    if (apiCallScope.isActive()) {
      apiCallScope.exit();
    }
    apiCallScope.enter();

    ApiCallScopeUtils.seedSharedParams(apiCallScope, msgBundle, bidiGlobalDir);

    return apiCallScope;
  }


  /**
   * Parses the given piece of Soy code as the full body of a template.
   *
   * @param soyCode The code to parse as the full body of a template.
   * @return The resulting Soy tree.
   */
  public static SoyFileSetNode parseSoyCode(String soyCode) {
    return parseSoyCode(true, soyCode);
  }


  /**
   * Parses the given piece of Soy code as the full body of a template.
   *
   * @param doRunInitialParsingPasses Whether to run initial parsing passes.
   * @param soyCode The code to parse as the full body of a template.
   * @return The resulting Soy tree.
   */
  public static SoyFileSetNode parseSoyCode(boolean doRunInitialParsingPasses, String soyCode) {
    return parseSoyCode(SyntaxVersion.V2_0, doRunInitialParsingPasses, soyCode);
  }


  /**
   * Parses the given piece of Soy code as the full body of a template.
   *
   * @param declaredSyntaxVersion User-declared syntax version.
   * @param doRunInitialParsingPasses Whether to run initial parsing passes.
   * @param soyCode The code to parse as the full body of a template.
   * @return The resulting Soy tree.
   */
  public static SoyFileSetNode parseSoyCode(
      SyntaxVersion declaredSyntaxVersion, boolean doRunInitialParsingPasses, String soyCode) {
    return parseSoyCode(
        new SoyTypeRegistry(), declaredSyntaxVersion, doRunInitialParsingPasses, soyCode);
  }


  /**
   * Parses the given piece of Soy code as the full body of a template.
   *
   * @param typeRegistry The type registry to resolve type names.
   * @param declaredSyntaxVersion User-declared syntax version.
   * @param doRunInitialParsingPasses Whether to run initial parsing passes.
   * @param soyCode The code to parse as the full body of a template.
   * @return The resulting Soy tree.
   */
  public static SoyFileSetNode parseSoyCode(
      SoyTypeRegistry typeRegistry, SyntaxVersion declaredSyntaxVersion,
      boolean doRunInitialParsingPasses, String soyCode) {

    return parseSoyFiles(
        typeRegistry, declaredSyntaxVersion, doRunInitialParsingPasses,
        buildTestSoyFileContent(soyCode));
  }


  /**
   * Builds a test Soy file's content from the given Soy code, which will be the body of the only
   * template in the test Soy file.
   *
   * @param soyCode The code to parse as the full body of a template.
   * @return The test Soy file's content.
   */
  public static String buildTestSoyFileContent(String soyCode) {
    return buildTestSoyFileContent(null, soyCode);
  }


  /**
   * Builds a test Soy file's content from the given Soy code, which will be the body of the only
   * template in the test Soy file.
   *
   * @param soyDocParamNames Param names to declare in SoyDoc of the single template.
   * @param soyCode The code to parse as the full body of a template.
   * @return The test Soy file's content.
   */
  public static String buildTestSoyFileContent(
      @Nullable List<String> soyDocParamNames, String soyCode) {
    return buildTestSoyFileContent(
        "brittle.test.ns", ".brittleTestTemplate", soyDocParamNames, soyCode);
  }


  /**
   * Builds a test Soy file's content from the given Soy code, which will be the body of the only
   * template in the test Soy file.
   *
   * @param namespace The namespace for the test Soy file.
   * @param templateName The template name for the single template.
   * @param soyDocParamNames Param names to declare in SoyDoc of the single template.
   * @param soyCode The code to parse as the full body of a template.
   * @return The test Soy file's content.
   */
  public static String buildTestSoyFileContent(
      String namespace, String templateName, @Nullable List<String> soyDocParamNames,
      String soyCode) {

    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(namespace));
    Preconditions.checkArgument(BaseUtils.isIdentifierWithLeadingDot(templateName));

    StringBuilder soyFileContentBuilder = new StringBuilder();
    soyFileContentBuilder
        .append("{namespace " + namespace + " autoescape=\"deprecated-noncontextual\"}\n")
        .append("\n")
        .append("/** Test template.");
    if (soyDocParamNames != null) {
      for (String paramName : soyDocParamNames) {
        soyFileContentBuilder.append(" @param " + paramName);
      }
    }
    soyFileContentBuilder
        .append(" */\n")
        .append("{template " + templateName + "}\n")
        .append(soyCode + "\n")
        .append("{/template}\n");
    return soyFileContentBuilder.toString();
  }


  /**
   * Parses the given strings as the contents of Soy files.
   *
   * @param soyFileContents The contents of the Soy files to parse.
   * @return The resulting Soy tree.
   */
  public static SoyFileSetNode parseSoyFiles(String... soyFileContents) {
    return parseSoyFiles(true, soyFileContents);
  }


  /**
   * Parses the given strings as the contents of Soy files.
   *
   * @param doRunInitialParsingPasses Whether to run initial parsing passes.
   * @param soyFileContents The contents of the Soy files to parse.
   * @return The resulting Soy tree.
   */
  public static SoyFileSetNode parseSoyFiles(
      boolean doRunInitialParsingPasses, String... soyFileContents) {
    return parseSoyFiles(SyntaxVersion.V2_0, doRunInitialParsingPasses, soyFileContents);
  }

  /**
   * Parses the given strings as the contents of Soy files.
   *
   * @param doRunInitialParsingPasses Whether to run initial parsing passes.
   * @param soyFileSuppliers The contents of the Soy files to parse.
   * @return The resulting Soy tree.
   */
  public static SoyFileSetNode parseSoyFiles(
      boolean doRunInitialParsingPasses, SoyFileSupplier... soyFileSuppliers) {
    return new SoyFileSetParser(
        new SoyTypeRegistry(), null /* astCache */, SyntaxVersion.V2_0, soyFileSuppliers)
        .setDoRunInitialParsingPasses(doRunInitialParsingPasses)
        .setDoRunCheckingPasses(false)
        .parse();
  }

  /**
   * Parses the given strings as the contents of Soy files.
   *
   * @param doRunInitialParsingPasses Whether to run initial parsing passes.
   * @param doRunCheckingPasses Whether to run checking passes.
   * @param soyFileContents The contents of the Soy files to parse.
   * @return The resulting Soy tree.
   */
  public static SoyFileSetNode parseSoyFiles(
      boolean doRunInitialParsingPasses, boolean doRunCheckingPasses, String... soyFileContents) {
    List<SoyFileSupplier> soyFileSuppliers = buildTestSoyFileSuppliers(soyFileContents);
    return new SoyFileSetParser(
        new SoyTypeRegistry(), null /* astCache */, SyntaxVersion.V2_0, soyFileSuppliers)
        .setDoRunInitialParsingPasses(doRunInitialParsingPasses)
        .setDoRunCheckingPasses(doRunCheckingPasses)
        .parse();
  }


  /**
   * Parses the given strings as the contents of Soy files.
   *
   * @param declaredSyntaxVersion User-declared syntax version.
   * @param doRunInitialParsingPasses Whether to run initial parsing passes.
   * @param soyFileContents The contents of the Soy files to parse.
   * @return The resulting Soy tree.
   */
  public static SoyFileSetNode parseSoyFiles(
      SyntaxVersion declaredSyntaxVersion, boolean doRunInitialParsingPasses,
      String... soyFileContents) {
    return parseSoyFiles(
        new SoyTypeRegistry(), declaredSyntaxVersion, doRunInitialParsingPasses, soyFileContents);
  }


  /**
   * Parses the given strings as the contents of Soy files.
   *
   * @param typeRegistry The type registry to resolve type names.
   * @param declaredSyntaxVersion User-declared syntax version.
   * @param doRunInitialParsingPasses Whether to run initial parsing passes.
   * @param soyFileContents The contents of the Soy files to parse.
   * @return The resulting Soy tree.
   */
  public static SoyFileSetNode parseSoyFiles(
      SoyTypeRegistry typeRegistry, SyntaxVersion declaredSyntaxVersion,
      boolean doRunInitialParsingPasses, String... soyFileContents) {

    List<SoyFileSupplier> soyFileSuppliers = buildTestSoyFileSuppliers(soyFileContents);
    return
        (new SoyFileSetParser(typeRegistry, null, declaredSyntaxVersion, soyFileSuppliers))
            .setDoRunInitialParsingPasses(doRunInitialParsingPasses)
            .setDoRunCheckingPasses(false)
            .parse();
  }


  /**
   * Builds a list of SoyFileSuppliers for the given test Soy file contents.
   *
   * @param soyFileContents The contents of the Soy files to parse.
   * @return List of SoyFileSuppliers for the given file contents.
   */
  public static List<SoyFileSupplier> buildTestSoyFileSuppliers(String... soyFileContents) {

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
   * Retrieves the node within the given Soy tree indicated by the given indices to reach the
   * desired node.
   *
   * @param soyTree The Soy tree.
   * @param indicesToNode The indices to reach the desired node to retrieve. E.g. To retrieve the
   *     first child of the template, simply pass a single 0.
   * @return The desired node in the Soy tree.
   */
  public static SoyNode getNode(SoyFileSetNode soyTree, int... indicesToNode) {

    SoyNode node = soyTree.getChild(0).getChild(0);  // initially set to TemplateNode
    for (int index : indicesToNode) {
      node = ((ParentSoyNode<?>) node).getChild(index);
    }
    return node;
  }


  /**
   * Parses the given piece of Soy code as the full body of a template, and then returns the node
   * within the resulting template subtree indicated by the given indices to reach the desired
   * node.
   *
   * @param soyCode The code to parse as the full body of a template.
   * @param indicesToNode The indices to reach the desired node to retrieve. E.g. To retrieve the
   *     first child of the template, simply pass a single 0.
   * @return The desired node in the resulting template subtree.
   */
  public static SoyNode parseSoyCodeAndGetNode(String soyCode, int... indicesToNode) {

    return getNode(parseSoyCode(soyCode), indicesToNode);
  }

}
