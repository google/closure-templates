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

import com.google.common.collect.Lists;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.template.soy.base.SoyFileSupplier;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.soyparse.SoyFileSetParser;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

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
   * @return The resulting parse tree.
   */
  public static SoyFileSetNode parseSoyCode(String soyCode) {
    return parseSoyCode(true, soyCode);
  }


  /**
   * Parses the given piece of Soy code as the full body of a template.
   *
   * @param soyCode The code to parse as the full body of a template.
   * @return The resulting parse tree.
   */
  public static SoyFileSetNode parseSoyCode(boolean doRunInitialParsingPasses, String soyCode) {

    String testFileContent =
        "{namespace boo}\n" +
        "\n" +
        "/** Test template. */\n" +
        "{template .foo}\n" +
        soyCode + "\n" +
        "{/template}\n";

    return parseSoyFiles(doRunInitialParsingPasses, testFileContent);
  }


  /**
   * Parses the given strings as the contents of Soy files.
   *
   * @param soyFileContents The contents of the Soy files to parse.
   * @return The resulting parse tree.
   */
  public static SoyFileSetNode parseSoyFiles(String... soyFileContents) {
    return parseSoyFiles(true, soyFileContents);
  }


  /**
   * Parses the given strings as the contents of Soy files.
   *
   * @param soyFileContents The contents of the Soy files to parse.
   * @return The resulting parse tree.
   */
  public static SoyFileSetNode parseSoyFiles(
      boolean doRunInitialParsingPasses, String... soyFileContents) {

    List<SoyFileSupplier> soyFileSuppliers = Lists.newArrayList();
    for (String soyFileContent : soyFileContents) {
      soyFileSuppliers.add(SoyFileSupplier.Factory.create(soyFileContent, "no-path"));
    }

    SoyFileSetNode soyTree =
        (new SoyFileSetParser(soyFileSuppliers))
            .setDoRunInitialParsingPasses(doRunInitialParsingPasses)
            .setDoRunCheckingPasses(false)
            .parse();
    return soyTree;
  }


  /**
   * Retrieves the node within the given parse tree indicated by the given indices to reach the
   * desired node.
   *
   * @param soyTree The parse tree.
   * @param indicesToNode The indices to reach the desired node to retrieve. E.g. To retrieve the
   *     first child of the template, simply pass a single 0.
   * @return The desired node in the parse tree.
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
   * within the resulting template parse tree indicated by the given indices to reach the desired
   * node.
   *
   * @param soyCode The code to parse as the full body of a template.
   * @param indicesToNode The indices to reach the desired node to retrieve. E.g. To retrieve the
   *     first child of the template, simply pass a single 0.
   * @return The desired node in the resulting template parse tree.
   */
  public static SoyNode parseSoyCodeAndGetNode(String soyCode, int... indicesToNode) {

    return getNode(parseSoyCode(soyCode), indicesToNode);
  }

}
