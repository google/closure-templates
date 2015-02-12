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

package com.google.template.soy.pysrc.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyListExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soyparse.ParseResult;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoytreeUtils;

import junit.framework.TestCase;

import java.util.List;

/**
 * Unit tests for TranslateToPyExprVisitor.
 *
 */
public class TranslateToPyExprVisitorTest extends TestCase {


  public void testNullLiteral() throws Exception {
    assertTranslation("null", new PyExpr("None", Integer.MAX_VALUE));
  }

  public void testBooleanLiteral() throws Exception {
    assertTranslation("true", new PyExpr("True", Integer.MAX_VALUE));
    assertTranslation("false", new PyExpr("False", Integer.MAX_VALUE));
  }

  public void testStringLiteral() throws Exception {
    assertTranslation("'waldo'", new PyExpr("'waldo'", Integer.MAX_VALUE), PyStringExpr.class);
  }

  public void testListLiteral() throws Exception {
    assertTranslation(
        "['blah', 123, $foo]",
        new PyExpr("['blah', 123, opt_data.get('foo')]", Integer.MAX_VALUE), PyListExpr.class);
    assertTranslation("[]", new PyExpr("[]", Integer.MAX_VALUE), PyListExpr.class);
  }

  public void testMapLiteral() throws Exception {
    // Unquoted keys.
    assertTranslation("[:]", new PyExpr("{}", Integer.MAX_VALUE));
    assertTranslation(
        "['aaa': 123, 'bbb': 'blah']",
        new PyExpr("{'aaa': 123, 'bbb': 'blah'}", Integer.MAX_VALUE));
    assertTranslation(
        "['aaa': $foo, 'bbb': 'blah']",
        new PyExpr("{'aaa': opt_data.get('foo'), 'bbb': 'blah'}", Integer.MAX_VALUE));

    // QuotedKeysIfJs should change nothing.
    assertTranslation("quoteKeysIfJs([:])", new PyExpr("{}", Integer.MAX_VALUE));
    assertTranslation(
        "quoteKeysIfJs( ['aaa': $foo, 'bbb': 'blah'] )",
        new PyExpr("{'aaa': opt_data.get('foo'), 'bbb': 'blah'}", Integer.MAX_VALUE));

    // Non-string keys are allowed in Python.
    assertTranslation(
        "[0: 123, 1: 'blah']",
        new PyExpr("{0: 123, 1: 'blah'}", Integer.MAX_VALUE));
  }

  public void testDataRef() throws Exception {
    // TODO(dcphillips): Add local variable tests.
    assertTranslation("$boo", new PyExpr("opt_data.get('boo')", Integer.MAX_VALUE));
    assertTranslation("$boo.goo", new PyExpr("opt_data.get('boo').get('goo')", Integer.MAX_VALUE));
    assertTranslation(
        "$boo.0.1.foo.2",
        new PyExpr("opt_data.get('boo')[0][1].get('foo')[2]", Integer.MAX_VALUE));
    assertTranslation("$boo[0].1", new PyExpr("opt_data.get('boo')[0][1]", Integer.MAX_VALUE));
    assertTranslation(
        "$boo[$foo][$foo+1]",
        new PyExpr("opt_data.get('boo').get(opt_data.get('foo')).get("
            + "runtime.type_safe_add(opt_data.get('foo'), 1))",
            Integer.MAX_VALUE));

    assertTranslation(
        "$boo?.goo",
        new PyExpr(
            "None if opt_data.get('boo') is None else opt_data.get('boo').get('goo')",
            PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
    assertTranslation(
        "$boo?[0]?.1",
        new PyExpr(
            "None if opt_data.get('boo') is None else "
            + "None if opt_data.get('boo')[0] is None else opt_data.get('boo')[0][1]",
            PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  public void testBasicOperators() throws Exception {
    assertTranslation(
        "not $boo or true and $foo",
        new PyExpr("not opt_data.get('boo') or True and opt_data.get('foo')",
            PyExprUtils.pyPrecedenceForOperator(Operator.OR)));
  }

  public void testEqualOperator() throws Exception {
    assertTranslation("'5' == 5", new PyExpr("runtime.type_safe_eq('5', 5)", Integer.MAX_VALUE));
    assertTranslation(
        "'5' == $boo",
        new PyExpr("runtime.type_safe_eq('5', opt_data.get('boo'))", Integer.MAX_VALUE));
  }

  public void testNotEqualOperator() throws Exception {
    assertTranslation(
        "'5' != 5",
        new PyExpr("not runtime.type_safe_eq('5', 5)",
            PyExprUtils.pyPrecedenceForOperator(Operator.NOT)));
  }

  public void testPlusOperator() throws Exception {
    assertTranslation(
        "( (8-4) + (2-1) )",
        new PyExpr("runtime.type_safe_add(8 - 4, 2 - 1)", Integer.MAX_VALUE));
  }

  public void testConditionalOperator() throws Exception {
    assertTranslation(
        "$boo ? 5 : 6",
        new PyExpr(
            "5 if opt_data.get('boo') else 6",
            PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }


  // -----------------------------------------------------------------------------------------------
  // Private helpers.


  /**
   * Checks that the given Soy expression translates to the given PyExpr.
   *
   * @param soyExpr The Soy expression to test.
   * @param expectedPyExpr The expected translated PyExpr.
   */
  private void assertTranslation(String soyExpr, PyExpr expectedPyExpr) throws Exception {
    assertTranslation(soyExpr, expectedPyExpr, null);
  }

  /**
   * Checks that the given Soy expression translates to the given PyExpr.
   *
   * @param soyExpr The Soy expression to test.
   * @param expectedPyExpr The expected translated PyExpr.
   * @param expectedClass An optional expected PyExpr class if a specific subtype if required.
   */
  private void assertTranslation(String soyExpr, PyExpr expectedPyExpr,
      Class<? extends PyExpr> expectedClass) throws Exception {
    ParseResult<SoyFileSetNode> parseResult = SharedTestUtils.parseSoyFiles(
        "{namespace ns autoescape=\"strict\"}\n" +
        "/***/\n" +
        "{template .aaa}\n" +
        "{print \n" + soyExpr + "}\n" +
        "{/template}\n");
    assertTrue(parseResult.isSuccess());
    SoyFileSetNode soyTree = parseResult.getParseTree();
    List<PrintNode> printNodes = SoytreeUtils.getAllNodesOfType(soyTree, PrintNode.class);
    ExprNode exprNode = printNodes.get(0).getExprUnion().getExpr();

    PyExpr actualPyExpr = new TranslateToPyExprVisitor().exec(exprNode);
    assertThat(actualPyExpr.getText()).isEqualTo(expectedPyExpr.getText());
    assertThat(actualPyExpr.getPrecedence()).isEqualTo(expectedPyExpr.getPrecedence());

    if (expectedClass != null) {
      assertThat(actualPyExpr.getClass()).isEqualTo(expectedClass);
    }
  }
}
