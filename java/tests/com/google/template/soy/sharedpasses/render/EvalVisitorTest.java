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

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.testing.SharedTestUtils.untypedTemplateBodyForExpression;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.plugin.java.PluginInstances;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for EvalVisitor. */
@RunWith(JUnit4.class)
public class EvalVisitorTest {

  private static final ImmutableMap<String, SoyValueProvider> LOCALS =
      ImmutableMap.of(
          "zoo", StringData.forValue("loo"),
          "woo", FloatData.forValue(-1.618));

  private SoyRecord testData;
  private SoyIdRenamingMap xidRenamingMap = null;
  private SoyCssRenamingMap cssRenamingMap = null;

  @Before
  public void setUp() {
    testData = createTestData();
  }

  protected SoyRecord createTestData() {
    SoyList tri = SoyValueConverterUtility.newList(1, 3, 6, 10, 15, 21);
    return SoyValueConverterUtility.newDict(
        "boo",
        8,
        "foo.bar",
        "baz",
        "foo.goo2",
        tri,
        "goo",
        tri,
        "moo",
        3.14,
        "t",
        true,
        "f",
        false,
        "n",
        null,
        "map0",
        SoyValueConverterUtility.newDict(),
        "list0",
        SoyValueConverterUtility.newList(),
        "longNumber",
        1000000000000000001L,
        "floatNumber",
        1.5);
  }

  /**
   * Evaluates the given expression and returns the result.
   *
   * @param expression The expression to evaluate.
   * @return The expression result.
   * @throws Exception If there's an error.
   */
  private SoyValue eval(String expression) throws Exception {
    PrintNode code =
        (PrintNode)
            ((TemplateNode)
                    SoyFileSetParserBuilder.forTemplateContents(
                            // wrap in a function so we don't run into the 'can't print bools' error
                            // message
                            untypedTemplateBodyForExpression("fakeFunction(" + expression + ")"))
                        .addSoyFunction(
                            new SoyFunction() {
                              @Override
                              public String getName() {
                                return "fakeFunction";
                              }

                              @Override
                              public Set<Integer> getValidArgsSizes() {
                                return ImmutableSet.of(1);
                              }
                            })
                        .parse()
                        .fileSet()
                        .getChild(0)
                        .getChild(0))
                .getChild(0);
    ExprNode expr = ((FunctionNode) code.getExpr().getChild(0)).getChild(0);

    EvalVisitor evalVisitor =
        new EvalVisitorFactoryImpl()
            .create(
                TestingEnvironment.createForTest(testData, LOCALS),
                cssRenamingMap,
                xidRenamingMap,
                null,
                /* debugSoyTemplateInfo= */ false,
                PluginInstances.empty(),
                ImmutableTable.of(),
                null,
                null);
    return evalVisitor.exec(expr);
  }

  /**
   * Asserts that the given expression evaluates to the given result.
   *
   * @param expression The expression to evaluate.
   * @param result The expected expression result.
   * @throws Exception If the assertion is not true or if there's an error.
   */
  private void assertEval(String expression, boolean result) throws Exception {
    assertThat(eval(expression).booleanValue()).isEqualTo(result);
  }

  /**
   * Asserts that the given expression evaluates to the given result.
   *
   * @param expression The expression to evaluate.
   * @param result The expected result.
   * @throws Exception If the assertion is not true or if there's an error.
   */
  private void assertEval(String expression, long result) throws Exception {
    assertThat(eval(expression).longValue()).isEqualTo(result);
  }

  /**
   * Asserts that the given expression evaluates to the given result.
   *
   * @param expression The expression to evaluate.
   * @param result The expected result.
   * @throws Exception If the assertion is not true or if there's an error.
   */
  private void assertEval(String expression, double result) throws Exception {
    assertThat(eval(expression).floatValue()).isEqualTo(result);
  }

  /**
   * Asserts that the given expression evaluates to the given result.
   *
   * @param expression The expression to evaluate.
   * @param result The expected result.
   * @throws Exception If the assertion is not true or if there's an error.
   */
  private void assertEval(String expression, String result) throws Exception {
    assertThat(eval(expression).stringValue()).isEqualTo(result);
  }

  /**
   * Asserts that evaluating the given expression causes a RenderException.
   *
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
        assertThat(re).hasMessageThat().contains(errorMsgSubstring);
      }
      // Test passes.
    }
  }

  /**
   * Asserts that evaluating the given expression causes a SoyDataException.
   *
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
        assertThat(e).hasMessageThat().contains(errorMsgSubstring);
      }
      // Test passes.
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Tests begin here.

  @Test
  public void testEvalPrimitives() throws Exception {
    assertThat(eval("null")).isInstanceOf(NullData.class);
    assertEval("true", true);
    assertEval("false", false);
    assertEval("26", 26);
    assertEval("8.27", 8.27);
    assertEval("'boo'", "boo");
  }

  @Test
  public void testEvalListLiteral() throws Exception {

    SoyList result = (SoyList) eval("['blah', 123, $boo]");
    assertThat(result.length()).isEqualTo(3);
    assertThat(result.get(0).stringValue()).isEqualTo("blah");
    assertThat(result.get(1).integerValue()).isEqualTo(123);
    assertThat(result.get(2).integerValue()).isEqualTo(8);

    result = (SoyList) eval("['blah', 123, $boo,]"); // trailing comma
    assertThat(result.length()).isEqualTo(3);
    assertThat(result.get(0).stringValue()).isEqualTo("blah");
    assertThat(result.get(1).integerValue()).isEqualTo(123);
    assertThat(result.get(2).integerValue()).isEqualTo(8);

    result = (SoyList) eval("[]");
    assertThat(result.length()).isEqualTo(0);
  }

  @Test
  public void testEvalRecordLiteral() throws Exception {
    SoyDict result = (SoyDict) eval("record(aaa: 'blah', bbb: 123, ccc: $boo)");
    assertThat(result.getItemKeys()).hasSize(3);
    assertThat(result.getField("aaa").stringValue()).isEqualTo("blah");
    assertThat(result.getField("bbb").integerValue()).isEqualTo(123);
    assertThat(result.getField("ccc").integerValue()).isEqualTo(8);
  }

  @Test
  public void testEvalDataRefBasic() throws Exception {

    assertEval("$zoo", "loo");
    assertEval("$woo", -1.618);

    assertEval("$boo", 8);
    assertEval("$foo.bar", "baz");
    assertEval("$goo[2]", 6);

    assertThat(eval("$too")).isInstanceOf(UndefinedData.class);
    assertThat(eval("$foo.too")).isInstanceOf(UndefinedData.class);
    assertThat(eval("$foo.goo2[22]")).isInstanceOf(UndefinedData.class);

    // TODO: If enabling exception for undefined LHS (see EvalVisitor), uncomment tests below.
    // assertRenderException(
    //    "$foo.bar.moo.tar", "encountered undefined LHS just before accessing \".tar\"");
    assertRenderException(
        "$foo.bar.moo.tar", "Attempted to access field \"moo\" of non-record type");
    // assertRenderException(
    //    "$foo.baz.moo.tar", "encountered undefined LHS just before accessing \".moo\"");
    assertThat(eval("$foo.baz.moo.tar")).isInstanceOf(UndefinedData.class);
    assertRenderException("$boo?[2]", "encountered non-map/list just before accessing \"[2]\"");
    assertRenderException(
        "$boo?['xyz']", "encountered non-map/list just before accessing \"['xyz']\"");
    assertDataException(
        "$foo[2]",
        "SoyDict accessed with non-string key (got key type"
            + " com.google.template.soy.data.restricted.IntegerData).");
    assertRenderException("$moo.too", "Attempted to access field \"too\" of non-record type");
    // assertRenderException(
    //    "$roo.too", "encountered undefined LHS just before accessing \".too\"");
    assertThat(eval("$roo.too")).isInstanceOf(UndefinedData.class);
    // assertRenderException("$roo[2]", "encountered undefined LHS just before accessing \"[2]\"");
    assertThat(eval("$roo[2]")).isInstanceOf(UndefinedData.class);
  }

  @Test
  public void testEvalDataRefWithNullSafeAccess() throws Exception {

    // Note: Null-safe access only helps when left side is undefined or null, not when it's the
    // wrong type.
    assertRenderException(
        "$foo?.bar?.moo.tar", "encountered non-record just before accessing \".moo\"");
    assertThat(eval("$foo?.baz?.moo.tar")).isInstanceOf(NullData.class);
    assertDataException(
        "$foo[2]",
        "SoyDict accessed with non-string key (got key type"
            + " com.google.template.soy.data.restricted.IntegerData).");
    assertRenderException("$moo?.too", "encountered non-record just before accessing \".too\"");
    assertThat(eval("$roo?.too")).isInstanceOf(NullData.class);
    assertThat(eval("$roo?[2]")).isInstanceOf(NullData.class);
  }

  @Test
  public void testEvalNumericalOperators() throws Exception {

    assertEval("-$boo", -8);

    assertEval("$goo[3]*3", 30);
    assertEval("2 * $moo", 6.28);

    assertEval("$goo[0] / 4", 0.25);
    assertEval("$woo/-0.8090", 2.0);

    assertEval("$boo % 3", 2);

    assertEval("-99+-111", -210);
    assertEval("$moo + $goo[5]", 24.14);
    assertEval("'boo'+'hoo'", "boohoo"); // string concatenation
    assertEval("8 + $zoo + 8.0", "8loo8"); // coercion to string type

    assertEval("$goo[4] - $boo", 7);
    assertEval("1.002- $woo", 2.62);

    // Ensure longs work.
    assertEval("$longNumber + $longNumber", 2000000000000000002L);
    assertEval("$longNumber * 4 - $longNumber", 3000000000000000003L);
    assertEval("$longNumber / $longNumber", 1.0); // NOTE: Division is on floats.
    assertEval("$longNumber < ($longNumber + 1)", true);
    assertEval("$longNumber < ($longNumber - 1)", false);
  }

  @Test
  public void testEvalDataRefWithExpressions() throws Exception {

    assertEval("$foo['bar']", "baz");
    assertEval("$goo[2]", 6);
    assertEval("$foo['goo' + 2][2+2]", 15);
    assertEval("$foo['goo'+2][4]", 15);
    assertEval("$foo.goo2[2 + 2]", 15);
  }

  @Test
  public void testEvalBooleanOperators() throws Exception {

    assertEval("not $t", false);
    assertEval("not null", true);
    assertEval("not $boo", false);
    assertEval("not 0.0", true);
    assertEval("not $foo.bar", false);
    assertEval("not ''", true);
    assertEval("not $foo", false);
    assertEval("not $map0", false);
    assertEval("not $goo", false);
    assertEval("not $list0", false);

    assertEval("false and $undefinedName", false); // short-circuit evaluation
    assertEval("$t and -1 and $goo and $foo.bar", true);
  }

  @Test
  public void testEvalNullCoalescingOperator() throws Exception {
    assertEval("true ?: $undefinedName", true); // short-circuit evaluation
    assertEval("$f ?: 0.0 ?: ''", false);
  }

  @Test
  public void testEvalComparisonOperators() throws Exception {

    assertEval("1<1", false);
    assertEval("$woo < 0", true);

    assertEval("$goo[0]>0", true);
    assertEval("$moo> 11.1111", false);

    assertEval("0 <= 0", true);
    assertEval("$moo <= -$woo", false);

    assertEval("2 >= $goo[2]", false);
    assertEval("4 >=$moo", true);

    assertEval("15==$goo[4]", true);
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

    assertEval("$goo[4]!=15", false);
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

    assertEval("$longNumber < $longNumber", false);
    assertEval("$longNumber < ($longNumber - 1)", false);
    assertEval("($longNumber - 1) < $longNumber", true);

    assertEval("$longNumber <= $longNumber", true);
    assertEval("$longNumber <= ($longNumber - 1)", false);
    assertEval("($longNumber - 1) <= $longNumber", true);

    assertEval("$longNumber > $longNumber", false);
    assertEval("$longNumber > ($longNumber - 1)", true);
    assertEval("($longNumber - 1) > $longNumber", false);

    assertEval("$longNumber >= $longNumber", true);
    assertEval("$longNumber >= ($longNumber - 1)", true);
    assertEval("($longNumber - 1) >= $longNumber", false);

    assertEval("$floatNumber < $floatNumber", false);
    assertEval("$floatNumber < ($floatNumber - 1)", false);
    assertEval("($floatNumber - 1) < $floatNumber", true);

    assertEval("$floatNumber <= $floatNumber", true);
    assertEval("$floatNumber <= ($floatNumber - 1)", false);
    assertEval("($floatNumber - 1) <= $floatNumber", true);

    assertEval("$floatNumber > $floatNumber", false);
    assertEval("$floatNumber > ($floatNumber - 1)", true);
    assertEval("($floatNumber - 1) > $floatNumber", false);

    assertEval("$floatNumber >= $floatNumber", true);
    assertEval("$floatNumber >= ($floatNumber - 1)", true);
    assertEval("($floatNumber - 1) >= $floatNumber", false);
  }

  @Test
  public void testEvalConditionalOperator() throws Exception {

    assertEval("($f and 0)?4 : '4'", "4");
    assertEval("$goo ? $goo[1]:1", 3);
  }

  @Test
  public void testEvalFunctions() throws Exception {
    assertEval("null != null", false);
    assertEval("0 != null", true);
    assertEval("1 != null", true);
    assertEval("false != null", true);
    assertEval("true != null", true);
    assertEval("'' != null", true);
    assertEval("$undefined != null", false);
    assertEval("$n != null", false);
    assertEval("$boo != null", true);
    assertEval("$foo.goo2 != null", true);
    assertEval("$map0 != null", true);
  }

  @Test
  public void testCss() throws Exception {
    cssRenamingMap = key -> key + "_renamed_css";
    assertEval("css('class')", "class_renamed_css");
    assertEval("css($zoo, 'class')", "loo-class_renamed_css");

    cssRenamingMap = null;
    assertEval("css('class')", "class");
    assertEval("css($zoo, 'class')", "loo-class");
  }

  @Test
  public void testXid() throws Exception {
    xidRenamingMap = key -> key + "_renamed_xid";
    assertEval("xid('id')", "id_renamed_xid");

    xidRenamingMap = null;
    assertEval("xid('id')", "id_");
  }
}
