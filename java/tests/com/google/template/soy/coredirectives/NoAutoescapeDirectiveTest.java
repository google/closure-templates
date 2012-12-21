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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.shared.AbstractSoyPrintDirectiveTestCase;

/**
 * Unit tests for NoAutoescapeDirective.
 *
 * @author Kai Huang
 */
public class NoAutoescapeDirectiveTest extends AbstractSoyPrintDirectiveTestCase {


  public void testApplyForTofu() {

    NoAutoescapeDirective noAutoescapeDirective = new NoAutoescapeDirective();
    assertTofuOutput("", "", noAutoescapeDirective);
    assertTofuOutput("identName", "identName", noAutoescapeDirective);
    assertTofuOutput("<b>rich text</b>", "<b>rich text</b>", noAutoescapeDirective);
    assertTofuOutput(
        "not.html { font-name: \"Arial\" 'Helvetica' }",
        "not.html { font-name: \"Arial\" 'Helvetica' }", noAutoescapeDirective);
    // Explicitly reject "text".
    assertTofuOutput(
        "zSoyz",
        UnsafeSanitizedContentOrdainer.ordainAsSafe("xyz", SanitizedContent.ContentKind.TEXT),
        noAutoescapeDirective);
  }


  public void testApplyForJsSrc() {

    NoAutoescapeDirective noAutoescapeDirective = new NoAutoescapeDirective();
    JsExpr dataRef = new JsExpr("opt_data.myKey", Integer.MAX_VALUE);
    assertEquals(
        "soy.$$filterNoAutoescape(opt_data.myKey)",
        noAutoescapeDirective.applyForJsSrc(dataRef, ImmutableList.<JsExpr>of()).getText());
  }

}
