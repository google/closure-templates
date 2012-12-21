/*
 * Copyright 2012 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.shared.AbstractSoyPrintDirectiveTestCase;

/**
 * Unit tests for TextDirective.
 *
 * @author Garrett Boyer
 */
public class TextDirectiveTest extends AbstractSoyPrintDirectiveTestCase {


  public void testApplyForTofu() {

    TextDirective textDirective = new TextDirective();
    assertTofuOutput("", "", textDirective);
    assertTofuOutput("abcd", "abcd", textDirective);
    assertTofuOutput(
        UnsafeSanitizedContentOrdainer.ordainAsSafe(
            "<div>Test</div>", SanitizedContent.ContentKind.HTML),
        UnsafeSanitizedContentOrdainer.ordainAsSafe(
            "<div>Test</div>", SanitizedContent.ContentKind.HTML),
        textDirective);
    assertTofuOutput(SoyData.createFromExistingData(123), SoyData.createFromExistingData(123),
        textDirective);
  }


  public void testApplyForJsSrc() {

    TextDirective textDirective = new TextDirective();
    JsExpr jsExpr = new JsExpr("whatever", Integer.MAX_VALUE);
    assertEquals(
        "'' + whatever",
        textDirective.applyForJsSrc(jsExpr, ImmutableList.<JsExpr>of()).getText());
  }

}
