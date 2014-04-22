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

package com.google.template.soy.coredirectives;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.shared.restricted.EscapingConventions;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPurePrintDirective;

import java.util.List;
import java.util.Set;


/**
 * A directive that HTML-escapes the output.
 *
 */
@Singleton
@SoyPurePrintDirective
public class EscapeHtmlDirective implements SoyJavaPrintDirective, SoyJsSrcPrintDirective {


  public static final String NAME = "|escapeHtml";


  @Inject
  public EscapeHtmlDirective() {}


  @Override public String getName() {
    return NAME;
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }


  @Override public boolean shouldCancelAutoescape() {
    return true;
  }


  @Override public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
    // Pass through known content direction, if any, for use in BidiSpanWrapDirective.
    Dir valueDir = null;
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      if (sanitizedContent.getContentKind() == SanitizedContent.ContentKind.HTML) {
        return value;
      }
      valueDir = sanitizedContent.getContentDirection();
    }
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(
        EscapingConventions.EscapeHtml.INSTANCE.escape(value.coerceToString()),
        ContentKind.HTML, valueDir);
  }


  @Override public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    return new JsExpr("soy.$$escapeHtml(" + value.getText() + ")", Integer.MAX_VALUE);
  }

}
