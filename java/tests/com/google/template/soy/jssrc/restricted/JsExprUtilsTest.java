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
import static com.google.template.soy.exprtree.Operator.LESS_THAN;
import static com.google.template.soy.exprtree.Operator.MINUS;
import static com.google.template.soy.exprtree.Operator.PLUS;
import static com.google.template.soy.exprtree.Operator.TIMES;
import static com.google.template.soy.jssrc.dsl.Precedence.forSoyOperator;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for JsExprUtils.
 */
@RunWith(JUnit4.class)
public class JsExprUtilsTest {

  @Test
  public void testConcatJsExprs() {

    JsExpr concatResult =
        JsExprUtils.concatJsExprs(
            ImmutableList.of(
                new JsExpr("'blah' + 'blah'", forSoyOperator(MINUS).toInt()),
                new JsExpr("'bleh' + 'bleh'", forSoyOperator(MINUS).toInt()),
                new JsExpr("2 * 8", forSoyOperator(TIMES).toInt())));
    assertThat(concatResult.getText()).isEqualTo("'blah' + 'blah' + ('bleh' + 'bleh') + 2 * 8");
    assertThat(concatResult.getPrecedence()).isEqualTo(forSoyOperator(PLUS).toInt());
  }

  @Test
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
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("'a' + 1", forSoyOperator(MINUS).toInt())))
        .isFalse();
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("1 + 'a'", forSoyOperator(MINUS).toInt())))
        .isFalse();
    assertThat(JsExprUtils.isStringLiteral(new JsExpr("foo('a')", Integer.MAX_VALUE))).isFalse();
  }

  @Test
  public void testWrapWithFunction() {
    JsExpr expr = new JsExpr("'foo' + 'bar'", forSoyOperator(PLUS).toInt());

    assertThat(JsExprUtils.wrapWithFunction("new Frob", expr).getText())
        .isEqualTo("new Frob('foo' + 'bar')");

    assertThat(JsExprUtils.maybeWrapAsSanitizedContent(ContentKind.HTML, expr).getText())
        .isEqualTo("soy.VERY_UNSAFE.ordainSanitizedHtml('foo' + 'bar')");
    assertThat(JsExprUtils.maybeWrapAsSanitizedContent(ContentKind.TEXT, expr).getText())
        .isEqualTo("'foo' + 'bar'");
  }

  /**
   * This test shows the inherent error-prone nature of JsExpr. Nothing checks that the precedence
   * passed to the JsExpr ctor corresponds to the topmost operator in the text passed in to the
   * JsExpr ctor. Passing in the wrong precedence bypasses the parenthesization logic and lead to
   * incorrect gencode. TODO(b/33382980): consolidate JS code generation under CodeChunk and
   * eliminate JsExpr.
   */
  @Test
  public void testJsExprGarbageInGarbageOut() {
    JsExpr lhs = new JsExpr("a", Integer.MAX_VALUE);
    JsExpr wrongPrecedence =
        new JsExpr("b < c", Integer.MAX_VALUE /* should be Operator.LESS_THAN.getPrecedence() */);
    assertThat(JsExprUtils.concatJsExprs(ImmutableList.of(lhs, wrongPrecedence)).getText())
        .isEqualTo("a + b < c"); // wrong! should be a + (b < c)

    JsExpr rightPrecedence = new JsExpr("b < c", forSoyOperator(LESS_THAN).toInt());
    assertThat(JsExprUtils.concatJsExprs(ImmutableList.of(lhs, rightPrecedence)).getText())
        .isEqualTo("a + (b < c)");
  }
}
