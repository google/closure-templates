/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.basicfunctions;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import java.util.List;
import java.util.Set;

/**
 * Soy special function for internal usages.
 *
 * <p>This function is explicitly not registered with {@link BasicFunctionsModule}. It exists for
 * inspecting the Soy template information from the rendered page, and should not be used in any
 * templates.
 */
public final class DebugSoyTemplateInfoFunction
    implements SoyJavaFunction, SoyLibraryAssistedJsSrcFunction, SoyPySrcFunction {

  // $$ prefix ensures that the function cannot be used directly
  public static final String NAME = "$$debugSoyTemplateInfo";

  public static final DebugSoyTemplateInfoFunction INSTANCE = new DebugSoyTemplateInfoFunction();

  // Do not @Inject; should not be used outside of {@link AddHtmlCommentsForDebugPass}.
  private DebugSoyTemplateInfoFunction() {}

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    // Throw an exception here since this method should never be directly invoked.
    // The value of this plugin function will be updated by the results of runtime libraries.
    throw new UnsupportedOperationException();
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    /**
     * soy.$$debugSoyTemplateInfo is a variable defined in soyutils_usegoog.js. Client-side library
     * can explicitly call a method to configure this and control whether the compiler should
     * generate additional HTML comments. We also guard this condition by goog.DEBUG so that it will
     * be stripped in optimized mode.
     */
    return new JsExpr("(goog.DEBUG && soy.$$debugSoyTemplateInfo)", Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    // 'debugSoyTemplateInfo' is used for inpsecting soy template info from rendered pages.
    // Always resolve to false since there is no plan to support this feature in PySrc.
    return new PyExpr("False", Integer.MAX_VALUE);
  }
}
