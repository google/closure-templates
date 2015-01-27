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


package com.google.template.soy.sharedpasses.render;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.basicdirectives.BasicDirectivesModule;
import com.google.template.soy.basicfunctions.BasicFunctionsModule;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.ParseException;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.sharedpasses.SharedPassesModule;
import com.google.template.soy.sharedpasses.render.EvalVisitor.EvalVisitorFactory;
import com.google.template.soy.soytree.PrintNode;

import junit.framework.TestCase;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * Unit tests for EvalVisitor.
 *
 */
public class EvalVisitorTest extends TestCase {

  private static final Injector INJECTOR =
      Guice.createInjector(new SharedModule(), new SharedPassesModule(),
          new BasicDirectivesModule(), new BasicFunctionsModule());

  protected static final SoyValueHelper VALUE_HELPER = INJECTOR.getInstance(SoyValueHelper.class);

  private SoyRecord testData;
  private static final SoyRecord TEST_IJ_DATA =
      VALUE_HELPER.newEasyDict("ijBool", true, "ijInt", 26, "ijStr", "injected");

  private final Map<String, SoyValueProvider> locals = Maps.newHashMap(
      ImmutableMap.<String, SoyValueProvider>of(
          "zoo", StringData.forValue("loo"),
          "woo", FloatData.forValue(-1.618)));


  @Override protected void setUp() {
    testData = createTestData();
    SharedTestUtils.simulateNewApiCall(INJECTOR, null, 0);
  }

  protected SoyRecord createTestData() {
    SoyList tri = VALUE_HELPER.newEasyList(1, 3, 6, 10, 15, 21);
    return VALUE_HELPER.newEasyDict(
        "boo", 8, "foo.bar", "baz", "foo.goo2", tri, "goo", tri,
        "moo", 3.14, "t", true, "f", false, "n", null,
        "map0", VALUE_HELPER.newEasyDict(), "list0", VALUE_HELPER.newEasyList(),
        "longNumber", 1000000000000000001L);
  }


  /**
   * Evaluates the given expression and returns the result.
   * @param expression The expression to evaluate.
   * @return The expression result.
   * @throws Exception If there's an error.
   */
  private SoyValue eval(String expression) throws Exception {
    PrintNode code =
        (PrintNode) SharedTestUtils.parseSoyCode("{" + expression + "}").getChild(0).getChild(0)
        .getChild(0);
    ExprRootNode<?> expr = code.getExprUnion().getExpr();

    EvalVisitor evalVisitor =
        INJECTOR.getInstance(EvalVisitorFactory.class)
            .create(TEST_IJ_DATA, TestingEnvironment.createForTest(testData, locals));
    return evalVisitor.exec(expr);
  }


  /**
   * Asserts that the given expression evaluates to the given result.
   * @param expression The expression to evaluate.
   * @param result The expected expression result.
   * @throws Exception If the assertion is not true or if there's an error.
   */
  private void assertEval(String expression, boolean result) throws Exception {
    assertEquals(result, eval(expression).booleanValue());
  }


  /**
   * Asserts that the given expression evaluates to the given result.
   * @param expression The expression to evaluate.
   * @param result The expected result.
   * @throws Exception If the assertion is not true or if there's an error.
   */
  private void assertEval(String expression, long result) throws Exception {
    assertEquals(result, eval(expression).longValue());
  }


  /**
   * Asserts that the given expression evaluates to the given result.
   * @param expression The expression to evaluate.
   * @param result The expected result.
   * @throws Exception If the assertion is not true or if there's an error.
   */
  private void assertEval(String expression, double result) throws Exception {
    assertEquals(result, eval(expression).floatValue());
  }


  /**
   * Asserts that the given expression evaluates to the given result.
   * @param expression The expression to evaluate.
   * @param result The expected result.
   * @throws Exception If the assertion is not true or if there's an error.
   */
  private void assertEval(String expression, String result) throws Exception {
    assertEquals(result, eval(expression).stringValue());
  }


  /**
   * Asserts that evaluating the given expression causes a ParseException.
   * @param expression The expression to evaluate.
   * @throws Exception If the assertion is not true or if there's an error.
   */
  private void assertParseException(String expression, @Nullable String errorMsgSubstring)
      throws Exception {

    try {
      new ExpressionParser(expression).parseExpression();
      fail();

    } catch (ParseException pe) {
      if (errorMsgSubstring != null) {
        assertTrue(pe.getMessage().contains(errorMsgSubstring));
      }
      // Test passes.
    }
  }


  /**
   * Asserts that evaluating the given expression causes a RenderException.
   * @param expression The expression to evaluate.
   * @throws Exception If the assertion is not true or if there's an error.
   */
  private void assertRenderException(String expression, @Nullable String errorMsgSubstring)
      throws Exception {

    try {
      eval(expression);
      fail();

    } catch (RenderException re) {
      if (errorMsgSubstring != null) {
        assertTrue(re.getMessage().contains(errorMsgSubstring));
      }
      // Test passes.
    }
  }


  /**
   * Asserts that evaluating the given expression causes a SoyDataException.
   * @param expression The expression to evaluate.
   * @throws Exception If the assertion is not true or if there's an error.
   */
  private void assertDataException(String expression, @Nullable String errorMsgSubstring)
      throws Exception {

    try {
      eval(expression);
      fail();

    } catch (SoyDataException e) {
      if (errorMsgSubstring != null) {
        assertTrue(e.getMessage().contains(errorMsgSubstring));
      }
      // Test passes.
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Tests begin here.


  public void testEvalPrimitives() throws Exception {

    assertTrue(eval("null") instanceof NullData);
    assertEval("true", true);
    assertEval("false", false);
    assertEval("26", 26);
    assertEval("8.27", 8.27);
    assertEval("'boo'", "boo");
  }


  public void testEvalListLiteral() throws Exception {

    SoyList result = (SoyList) eval("['blah', 123, $boo]");
    assertEquals(3, result.length());
    assertEquals("blah", result.get(0).stringValue());
    assertEquals(123, result.get(1).integerValue());
    assertEquals(8, result.get(2).integerValue());

    result = (SoyList) eval("['blah', 123, $boo,]");  // trailing comma
    assertEquals(3, result.length());
    assertEquals("blah", result.get(0).stringValue());
    assertEquals(123, result.get(1).integerValue());
    assertEquals(8, result.get(2).integerValue());

    result = (SoyList) eval("[]");
    assertEquals(0, result.length());

    assertParseException("[,]", null);
  }


  public void testEvalMapLiteral() throws Exception {

    SoyDict result = (SoyDict) eval("[:]");
    assertEquals(0, Iterables.size(result.getItemKeys()));

    result = (SoyDict) eval("['aaa': 'blah', 'bbb': 123, $foo.bar: $boo]");
    assertEquals(3, Iterables.size(result.getItemKeys()));
    assertEquals("blah", result.getField("aaa").stringValue());
    assertEquals(123, result.getField("bbb").integerValue());
    assertEquals(8, result.getField("baz").integerValue());

    result = (SoyDict) eval("['aaa': 'blah', 'bbb': 123, $foo.bar: $boo,]");  // trailing comma
    assertEquals(3, Iterables.size(result.getItemKeys()));
    assertEquals("blah", result.getField("aaa").stringValue());
    assertEquals(123, result.getField("bbb").integerValue());
    assertEquals(8, result.getField("baz").integerValue());

    result = (SoyDict) eval("quoteKeysIfJs([:])");
    assertEquals(0, Iterables.size(result.getItemKeys()));

    result = (SoyDict) eval("quoteKeysIfJs( ['aaa': 'blah', 'bbb': 123, $foo.bar: $boo] )");
    assertEquals(3, Iterables.size(result.getItemKeys()));
    assertEquals("blah", result.getField("aaa").stringValue());
    assertEquals(123, result.getField("bbb").integerValue());
    assertEquals(8, result.getField("baz").integerValue());

    assertParseException("[:,]", null);
    assertParseException("[,:]", null);

    // Test error on single-identifier key.
    assertParseException(
        "[aaa: 'blah',]",
        "Disallowed single-identifier key \"aaa\" in map literal");
    assertParseException(
        "['aaa': 'blah', bbb: 123]",
        "Disallowed single-identifier key \"bbb\" in map literal");

    // Test last value overwrites earlier value for the same key.
    result = (SoyDict) eval("['baz': 'blah', $foo.bar: 'bluh']");
    assertEquals("bluh", result.getField("baz").stringValue());
  }


  public void testEvalDataRefBasic() throws Exception {

    assertEval("$zoo", "loo");
    assertEval("$woo", -1.618);

    assertEval("$boo", 8);
    assertEval("$foo.bar", "baz");
    assertEval("$goo.2", 6);

    assertEval("$ij.ijBool", true);
    assertEval("$ij.ijInt", 26);
    assertEval("$ij.ijStr", "injected");

    assertTrue(eval("$too") instanceof UndefinedData);
    assertTrue(eval("$foo.too") instanceof UndefinedData);
    assertTrue(eval("$foo.goo2.22") instanceof UndefinedData);
    assertTrue(eval("$ij.boo") instanceof UndefinedData);

    // TODO: If enabling exception for undefined LHS (see EvalVisitor), uncomment tests below.
    //assertRenderException(
    //    "$foo.bar.moo.tar", "encountered undefined LHS just before accessing \".tar\"");
    assertTrue(eval("$foo.bar.moo.tar") instanceof UndefinedData);
    //assertRenderException(
    //    "$foo.baz.moo.tar", "encountered undefined LHS just before accessing \".moo\"");
    assertTrue(eval("$foo.baz.moo.tar") instanceof UndefinedData);
    assertRenderException("$boo?.2", "encountered non-map/list just before accessing \"?.2\"");
    assertRenderException(
        "$boo?['xyz']", "encountered non-map/list just before accessing \"?['xyz']\"");
    assertDataException(
        "$foo.2",
        "SoyDict accessed with non-string key (got key type" +
            " com.google.template.soy.data.restricted.IntegerData).");
    assertDataException(
        "$foo[2]",
        "SoyDict accessed with non-string key (got key type" +
            " com.google.template.soy.data.restricted.IntegerData).");
    assertTrue(eval("$moo.too") instanceof UndefinedData);
    //assertRenderException(
    //    "$roo.too", "encountered undefined LHS just before accessing \".too\"");
    assertTrue(eval("$roo.too") instanceof UndefinedData);
    //assertRenderException("$roo[2]", "encountered undefined LHS just before accessing \"[2]\"");
    assertTrue(eval("$roo[2]") instanceof UndefinedData);
    assertTrue(eval("$ij.ijInt.boo") instanceof UndefinedData);
    //assertRenderException(
    //    "$ij.ijZoo.boo", "encountered undefined LHS just before accessing \".boo\"");
    assertTrue(eval("$ij.ijZoo.boo") instanceof UndefinedData);
  }


  public void testEvalDataRefWithNullSafeAccess() throws Exception {

    // Note: Null-safe access only helps when left side is undefined or null, not when it's the
    // wrong type.
    assertRenderException(
        "$foo?.bar?.moo.tar", "encountered non-record just before accessing \"?.moo\"");
    assertTrue(eval("$foo?.baz?.moo.tar") instanceof NullData);
    assertDataException(
        "$foo.2",
        "SoyDict accessed with non-string key (got key type" +
            " com.google.template.soy.data.restricted.IntegerData).");
    assertDataException(
        "$foo[2]",
        "SoyDict accessed with non-string key (got key type" +
            " com.google.template.soy.data.restricted.IntegerData).");
    assertRenderException(
        "$moo?.too", "encountered non-record just before accessing \"?.too\"");
    assertTrue(eval("$roo?.too") instanceof NullData);
    assertTrue(eval("$roo?[2]") instanceof NullData);
    assertRenderException(
        "$ij.ijInt?.boo", "encountered non-record just before accessing \"?.boo\"");
    assertTrue(eval("$ij.ijZoo?.boo") instanceof NullData);
  }


  public void testEvalNumericalOperators() throws Exception {

    assertEval("-$boo", -8);

    assertEval("$goo.3*3", 30);
    assertEval("2 * $moo", 6.28);

    assertEval("$goo.0 / 4", 0.25);
    assertEval("$woo/-0.8090", 2.0);

    assertEval("$boo % 3", 2);

    assertEval("-99+-111", -210);
    assertEval("$moo + $goo.5", 24.14);
    assertEval("$ij.ijInt + $boo", 34);
    assertEval("'boo'+'hoo'", "boohoo");  // string concatenation
    assertEval("$foo.bar + $ij.ijStr", "bazinjected");  // string concatenation
    assertEval("8 + $zoo + 8.0", "8loo8");  // coercion to string type

    assertEval("$goo.4 - $boo", 7);
    assertEval("1.002- $woo", 2.62);

    // Ensure longs work.
    assertEval("$longNumber + $longNumber", 2000000000000000002L);
    assertEval("$longNumber * 4 - $longNumber", 3000000000000000003L);
    assertEval("$longNumber / $longNumber", 1.0);  // NOTE: Division is on floats.
    assertEval("$longNumber < ($longNumber + 1)", true);
    assertEval("$longNumber < ($longNumber - 1)", false);
  }


  public void testEvalDataRefWithExpressions() throws Exception {

    assertEval("$foo['bar']", "baz");
    assertEval("$goo[2]", 6);
    assertEval("$foo['goo' + 2][2+2]", 15);
    assertEval("$foo['goo'+2].4", 15);
    assertEval("$foo.goo2[2 + 2]", 15);
  }


  public void testEvalBooleanOperators() throws Exception {

    assertEval("not $t", false);
    assertEval("not null", true);
    assertEval("not $boo", false);
    assertEval("not $ij.ijBool", false);
    assertEval("not 0.0", true);
    assertEval("not $foo.bar", false);
    assertEval("not ''", true);
    assertEval("not $foo", false);
    assertEval("not $map0", false);
    assertEval("not $goo", false);
    assertEval("not $list0", false);

    assertEval("false and $undefinedName", false);  // short-circuit evaluation
    assertEval("$t and -1 and $goo and $foo.bar", true);

    assertEval("true or $undefinedName", true);  // short-circuit evaluation
    assertEval("$f or 0.0 or ''", false);
  }


  public void testEvalComparisonOperators() throws Exception {

    assertEval("1<1", false);
    assertEval("$woo < 0", true);

    assertEval("$goo.0>0", true);
    assertEval("$moo> 11.1111", false);

    assertEval("0 <= 0", true);
    assertEval("$moo <= -$woo", false);

    assertEval("2 >= $goo.2", false);
    assertEval("4 >=$moo", true);

    assertEval("15==$goo.4", true);
    assertEval("$woo == 1.61", false);
    assertEval("4.0 ==4", true);
    assertEval("$f == true", false);
    assertEval("null== null", true);
    assertEval("'$foo.bar' == $foo.bar", false);
    assertEval("$foo.bar == 'b' + 'a'+'z'", true);
    assertEval("$foo == $map0", false);
    assertEval("$foo.goo2 == $goo", true);
    assertEval("'22' == 22", true);
    assertEval("'22' == '' + 22", true);

    assertEval("$goo.4!=15", false);
    assertEval("1.61 != $woo", true);
    assertEval("4 !=4.0", false);
    assertEval("true != $f", true);
    assertEval("null!= null", false);
    assertEval("$foo.bar != '$foo.bar'", true);
    assertEval("'b' + 'a'+'z' != $foo.bar", false);
    assertEval("$map0 != $foo", true);
    assertEval("$goo != $foo.goo2", false);
    assertEval("22 != '22'", false);
    assertEval("'' + 22 != '22'", false);
  }


  public void testEvalConditionalOperator() throws Exception {

    assertEval("($f and 0)?4 : '4'", "4");
    assertEval("$goo ? $goo.1:1", 3);
  }


  public void testEvalFunctions() throws Exception {

    assertEval("isNonnull(null)", false);
    assertEval("isNonnull(0)", true);
    assertEval("isNonnull(1)", true);
    assertEval("isNonnull(false)", true);
    assertEval("isNonnull(true)", true);
    assertEval("isNonnull('')", true);
    assertEval("isNonnull($undefined)", false);
    assertEval("isNonnull($n)", false);
    assertEval("isNonnull($boo)", true);
    assertEval("isNonnull($foo.goo2)", true);
    assertEval("isNonnull($map0)", true);
  }

}
