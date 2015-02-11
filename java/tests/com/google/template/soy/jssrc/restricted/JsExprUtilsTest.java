/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jssrc.restricted;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Lists;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.exprtree.Operator;

import junit.framework.TestCase;

/**
 * Unit tests for JsExprUtils.
 *
 */
public class JsExprUtilsTest extends TestCase {


  public void testConcatJsExprs() {

    JsExpr concatResult = JsExprUtils.concatJsExprs(Lists.newArrayList(
        new JsExpr("'blah' + 'blah'", Operator.MINUS.getPrecedence()),
        new JsExpr("'bleh' + 'bleh'", Operator.MINUS.getPrecedence()),
        new JsExpr("2 * 8", Operator.TIMES.getPrecedence())));
    assertThat(concatResult.getText()).isEqualTo("'blah' + 'blah' + ('bleh' + 'bleh') + 2 * 8");
    assertThat(concatResult.getPrecedence()).isEqualTo(Operator.PLUS.getPrecedence());
  }

  public void testConcatJsExprsForceString() {

    JsExpr concatResult = JsExprUtils.concatJsExprsForceString(Lists.newArrayList(
        new JsExpr("2", Integer.MAX_VALUE),
        new JsExpr("2", Integer.MAX_VALUE)));
    assertThat(concatResult.getText()).isEqualTo("'' + 2 + 2");
    assertThat(concatResult.getPrecedence()).isEqualTo(Operator.PLUS.getPrecedence());
  }

  public void testIsStringLiteral() {
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("''", Integer.MAX_VALUE))).isTrue();
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("'a'", Integer.MAX_VALUE))).isTrue();
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("'abc'", Integer.MAX_VALUE))).isTrue();
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("'ab\\nc'", Integer.MAX_VALUE))).isTrue();
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("'ab\\'c'", Integer.MAX_VALUE))).isTrue();
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("'\\'abc\\''", Integer.MAX_VALUE))).isTrue();
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("'\\u1234'", Integer.MAX_VALUE))).isTrue();

    assertThat(JsExprUtils.isStringLiteral(new JsExpr("abc", Integer.MAX_VALUE))).isFalse();
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("123", Integer.MAX_VALUE))).isFalse();
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("foo()", Integer.MAX_VALUE))).isFalse();
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("'a' + 1", Operator.MINUS.getPrecedence())))
        .isFalse();
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("1 + 'a'", Operator.MINUS.getPrecedence())))
        .isFalse();
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("foo('a')", Integer.MAX_VALUE))).isFalse();
  }

  public void testWrapWithFunction() {
    JsExpr expr = new JsExpr("'foo' + 'bar'", Operator.PLUS.getPrecedence());

    assertThat(JsExprUtils.wrapWithFunction("new Frob", expr).getText())
        .isEqualTo("new Frob('foo' + 'bar')");

    assertThat(JsExprUtils.maybeWrapAsSanitizedContent(ContentKind.HTML, expr).getText())
        .isEqualTo("soydata.VERY_UNSAFE.ordainSanitizedHtml('foo' + 'bar')");
    assertThat(JsExprUtils.maybeWrapAsSanitizedContent(null, expr).getText())
        .isEqualTo("'foo' + 'bar'");
  }

}
