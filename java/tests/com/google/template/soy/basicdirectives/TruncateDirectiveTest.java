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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.testing.AbstractSoyPrintDirectiveTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for TruncateDirective.
 *
 */
@RunWith(JUnit4.class)
public class TruncateDirectiveTest extends AbstractSoyPrintDirectiveTestCase {
  @Test
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
  @Test
  public void testApplyForJsSrc() {
    TruncateDirective truncateDirective = new TruncateDirective();

    JsExpr dataRefJsExpr = new JsExpr("opt_data.myKey", Integer.MAX_VALUE);
    JsExpr maxLenJsExpr = new JsExpr("8", Integer.MAX_VALUE);
    JsExpr trueJsExpr = new JsExpr("true", Integer.MAX_VALUE);
    JsExpr falseJsExpr = new JsExpr("false", Integer.MAX_VALUE);
    assertThat(
            truncateDirective
                .applyForJsSrc(dataRefJsExpr, ImmutableList.of(maxLenJsExpr))
                .getText())
        .isEqualTo("soy.$$truncate(opt_data.myKey, 8, true)");
    assertThat(
            truncateDirective
                .applyForJsSrc(dataRefJsExpr, ImmutableList.of(maxLenJsExpr, trueJsExpr))
                .getText())
        .isEqualTo("soy.$$truncate(opt_data.myKey, 8, true)");
    assertThat(
            truncateDirective
                .applyForJsSrc(dataRefJsExpr, ImmutableList.of(maxLenJsExpr, falseJsExpr))
                .getText())
        .isEqualTo("soy.$$truncate(opt_data.myKey, 8, false)");
  }
  @Test
  public void testApplyForPySrc() {
    TruncateDirective truncateDirective = new TruncateDirective();

    PyExpr data = new PyStringExpr("'data'", Integer.MAX_VALUE);
    PyExpr dataRef = new PyExpr("opt_data[myKey]", Integer.MAX_VALUE);
    PyExpr maxLenExpr = new PyExpr("8", Integer.MAX_VALUE);
    PyExpr trueExpr = new PyExpr("True", Integer.MAX_VALUE);
    PyExpr falseExpr = new PyExpr("False", Integer.MAX_VALUE);
    assertThat(truncateDirective.applyForPySrc(data, ImmutableList.of(maxLenExpr)).getText())
        .isEqualTo("directives.truncate('data', 8, True)");
    assertThat(
            truncateDirective.applyForPySrc(data, ImmutableList.of(maxLenExpr, trueExpr)).getText())
        .isEqualTo("directives.truncate('data', 8, True)");
    assertThat(
            truncateDirective
                .applyForPySrc(data, ImmutableList.of(maxLenExpr, falseExpr))
                .getText())
        .isEqualTo("directives.truncate('data', 8, False)");
    assertThat(truncateDirective.applyForPySrc(dataRef, ImmutableList.of(maxLenExpr)).getText())
        .isEqualTo("directives.truncate(str(opt_data[myKey]), 8, True)");
  }
}
