/*
 * Copyright 2011 Google Inc.
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
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.shared.AbstractSoyPrintDirectiveTestCase;


/**
 * Unit tests for TruncateDirective.
 *
 */
public class TruncateDirectiveTest extends AbstractSoyPrintDirectiveTestCase {


  public void testApplyForTofu() {

    TruncateDirective truncateDirective = new TruncateDirective();
    assertTofuOutput("", "", truncateDirective, 8);
    assertTofuOutput("", "", truncateDirective, 8, true);
    assertTofuOutput("", "", truncateDirective, 8, false);
    assertTofuOutput("blahblah", "blahblah", truncateDirective, 8);
    assertTofuOutput("blahblah", "blahblah", truncateDirective, 8, true);
    assertTofuOutput("blahblah", "blahblah", truncateDirective, 8, false);
    assertTofuOutput("blahb...", "blahblahblah", truncateDirective, 8);
    assertTofuOutput("blahb...", "blahblahblah", truncateDirective, 8, true);
    assertTofuOutput("blahblah", "blahblahblah", truncateDirective, 8, false);
    // Test maxLen <= 3.
    assertTofuOutput("bla", "blahblah", truncateDirective, 3);
    assertTofuOutput("bla", "blahblah", truncateDirective, 3, true);
    assertTofuOutput("bla", "blahblah", truncateDirective, 3, false);
    // Test unicode surrogate pair at cut point.
    assertTofuOutput("1234...", "1234\uD800\uDC00blah", truncateDirective, 8);
    assertTofuOutput("1234...", "1234\uD800\uDC00blah", truncateDirective, 8, true);
    assertTofuOutput("1234567", "1234567\uD800\uDC00", truncateDirective, 8, false);
  }


  public void testApplyForJsSrc() {

    TruncateDirective truncateDirective = new TruncateDirective();
    JsExpr dataRefJsExpr = new JsExpr("opt_data.myKey", Integer.MAX_VALUE);
    JsExpr maxLenJsExpr = new JsExpr("8", Integer.MAX_VALUE);
    JsExpr trueJsExpr = new JsExpr("true", Integer.MAX_VALUE);
    JsExpr falseJsExpr = new JsExpr("false", Integer.MAX_VALUE);
    assertEquals(
        "soy.$$truncate(opt_data.myKey, 8, true)",
        truncateDirective.applyForJsSrc(dataRefJsExpr, ImmutableList.of(maxLenJsExpr)).getText());
    assertEquals(
        "soy.$$truncate(opt_data.myKey, 8, true)",
        truncateDirective.applyForJsSrc(dataRefJsExpr, ImmutableList.of(maxLenJsExpr, trueJsExpr))
            .getText());
    assertEquals(
        "soy.$$truncate(opt_data.myKey, 8, false)",
        truncateDirective.applyForJsSrc(dataRefJsExpr, ImmutableList.of(maxLenJsExpr, falseJsExpr))
            .getText());
  }

}
