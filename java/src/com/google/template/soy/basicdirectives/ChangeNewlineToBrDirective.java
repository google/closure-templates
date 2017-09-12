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

package com.google.template.soy.basicdirectives;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPurePrintDirective;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A directive that replaces newlines (\n, \r, or \r\n) with HTML line breaks (&lt;br&gt;).
 *
 */
@Singleton
@SoyPurePrintDirective
final class ChangeNewlineToBrDirective
    implements SanitizedContentOperator,
        SoyJavaPrintDirective,
        SoyLibraryAssistedJsSrcPrintDirective,
        SoyPySrcPrintDirective {

  private static final Pattern NEWLINE_PATTERN = Pattern.compile("\\r\\n|\\r|\\n");

  @Inject
  public ChangeNewlineToBrDirective() {}

  @Override
  public String getName() {
    return "|changeNewlineToBr";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }

  @Override
  public boolean shouldCancelAutoescape() {
    return false;
  }

  @Override
  @Nonnull
  public SanitizedContent.ContentKind getContentKind() {
    // This directive expects HTML as input and produces HTML as output.
    return SanitizedContent.ContentKind.HTML;
  }

  @Override
  public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
    String result = NEWLINE_PATTERN.matcher(value.coerceToString()).replaceAll("<br>");

    // Make sure to transmit the known direction, if any, to any downstream directive that may need
    // it, e.g. BidiSpanWrapDirective. Since a known direction is carried only by SanitizedContent,
    // and the transformation we make is only valid in HTML, we only transmit the direction when we
    // get HTML SanitizedContent.
    // TODO(user): Consider always returning HTML SanitizedContent.
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      if (sanitizedContent.getContentKind() == ContentKind.HTML) {
        return UnsafeSanitizedContentOrdainer.ordainAsSafe(
            result, ContentKind.HTML, sanitizedContent.getContentDirection());
      }
    }

    return StringData.forValue(result);
  }

  @Override
  public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    return new JsExpr("soy.$$changeNewlineToBr(" + value.getText() + ")", Integer.MAX_VALUE);
  }
  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public PyExpr applyForPySrc(PyExpr value, List<PyExpr> args) {
    return new PyExpr("sanitize.change_newline_to_br(" + value.getText() + ")", Integer.MAX_VALUE);
  }
}
