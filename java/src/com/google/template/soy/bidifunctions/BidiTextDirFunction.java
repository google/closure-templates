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

package com.google.template.soy.bidifunctions;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.internal.i18n.BidiUtils;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Soy function that gets the bidi directionality of a text string (1 for LTR, -1 for RTL, or 0 for
 * none).
 *
 */
@Singleton
final class BidiTextDirFunction
    implements SoyJavaFunction, SoyLibraryAssistedJsSrcFunction, SoyPySrcFunction {

  @Inject
  BidiTextDirFunction() {}

  @Override
  public String getName() {
    return "bidiTextDir";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1, 2);
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue value = args.get(0);
    Dir valueDir = null;
    boolean isHtmlForValueDirEstimation = false;
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      valueDir = sanitizedContent.getContentDirection();
      if (valueDir == null) {
        isHtmlForValueDirEstimation = sanitizedContent.getContentKind() == ContentKind.HTML;
      }
    }
    if (valueDir == null) {
      isHtmlForValueDirEstimation =
          isHtmlForValueDirEstimation || (args.size() == 2 && args.get(1).booleanValue());
      valueDir = BidiUtils.estimateDirection(value.coerceToString(), isHtmlForValueDirEstimation);
    }
    return IntegerData.forValue(valueDir.ord);
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr value = args.get(0);
    JsExpr isHtml = (args.size() == 2) ? args.get(1) : null;

    String callText =
        (isHtml != null)
            ? "soy.$$bidiTextDir(" + value.getText() + ", " + isHtml.getText() + ")"
            : "soy.$$bidiTextDir(" + value.getText() + ")";
    return new JsExpr(callText, Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.<String>of("soy");
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    PyExpr value = args.get(0);
    PyExpr isHtml = (args.size() == 2) ? args.get(1) : null;

    String callText =
        (isHtml != null)
            ? "bidi.text_dir(" + value.getText() + ", " + isHtml.getText() + ")"
            : "bidi.text_dir(" + value.getText() + ")";
    return new PyExpr(callText, Integer.MAX_VALUE);
  }
}
