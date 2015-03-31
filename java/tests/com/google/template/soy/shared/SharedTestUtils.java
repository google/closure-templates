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

package com.google.template.soy.shared;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
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
public final class SharedTestUtils {


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
        AutoEscapingType.DEPRECATED_NONCONTEXTUAL, soyDocParamNames, soyCode);
  }


  /**
   * Builds a test Soy file's content from the given Soy code, which will be the body of the only
   * template in the test Soy file.
   *
   * @param autoEscaping The form of autescaping to use for this namespace.
   * @param soyDocParamNames Param names to declare in SoyDoc of the single template.
   * @param soyCode The code to parse as the full body of a template.
   * @return The test Soy file's content.
   */
  public static String buildTestSoyFileContent(
      AutoEscapingType autoEscaping, @Nullable List<String> soyDocParamNames, String soyCode) {

    String namespace = "brittle.test.ns";
    String templateName = ".brittleTestTemplate";

    StringBuilder soyFileContentBuilder = new StringBuilder();
    soyFileContentBuilder
        .append("{namespace " + namespace)
        .append(" autoescape=\"" + autoEscaping.getKey() + "\"}\n")
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
}
