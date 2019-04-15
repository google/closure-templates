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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.shared.AbstractSoyPrintDirectiveTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for TextDirective.
 *
 */
@RunWith(JUnit4.class)
public class TextDirectiveTest extends AbstractSoyPrintDirectiveTestCase {
  @Test
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
    assertTofuOutput(IntegerData.forValue(123), IntegerData.forValue(123), textDirective);
  }
  @Test
  public void testApplyForJsSrc() {
    TextDirective textDirective = new TextDirective();
    JsExpr jsExpr = new JsExpr("whatever", Integer.MAX_VALUE);
    assertThat(textDirective.applyForJsSrc(jsExpr, ImmutableList.of()).getText())
        .isEqualTo("'' + whatever");
  }
  @Test
  public void testApplyForPySrc() {
    TextDirective textDirective = new TextDirective();

    PyExpr pyExpr = new PyExpr("whatever", Integer.MAX_VALUE);
    assertThat(textDirective.applyForPySrc(pyExpr, ImmutableList.of()).getText())
        .isEqualTo("str(whatever)");

    PyExpr stringExpr = new PyStringExpr("'string'", Integer.MAX_VALUE);
    assertThat(textDirective.applyForPySrc(stringExpr, ImmutableList.of()).getText())
        .isEqualTo("'string'");
  }
}
