/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.exprparse;

import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataRefAccessExprNode;
import com.google.template.soy.exprtree.DataRefAccessIndexNode;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarNode;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import java.util.List;


/**
 * Unit tests for the Soy expression parser.
 *
 * @author Kai Huang
 */
public class ExpressionParserTest extends TestCase {


  public void testRecognizeVariable() throws Exception {

    String[] vars = {"$aaa", "$_", "$a0b1_"};
    for (String var : vars) {
      (new ExpressionParser(var)).parseVariable();
    }

    String[] nonVars = {"$", "$1", "$ aaa", "aaa", "$ij", "$ij.aaa"};
    for (String nonVar : nonVars) {
      try {
        (new ExpressionParser(nonVar)).parseVariable();
        fail();
      } catch (TokenMgrError tme) {
        // Test passes.
      } catch (ParseException pe) {
        // Test passes.
      }
    }
  }


  public void testRecognizeDataReference() throws Exception {

    String[] dataRefs =
        {"$aaa", "$ij.aaa", "$a0a0.b1b1", "$aaa.0.bbb.12", "$aaa[0].bbb['ccc'][$eee]",
         "$aaa?.bbb", "$aaa.bbb?[0]?.ccc?['ddd']", "$ij?.aaa",
         "$aaa . 1 [2] .bbb [ 3 + 4 ]['ccc']. ddd [$eee * $fff]"};
    for (String dataRef : dataRefs) {
      (new ExpressionParser(dataRef)).parseDataReference();
    }

    String[] nonDataRefs =
        {"$", "$ aaa", "aaa", "$1a1a", "$0", "$[12]", "$[$aaa]", "$aaa[]", "$ij[4]", "$aaa.?bbb"};
    for (String nonDataRef : nonDataRefs) {
      try {
        (new ExpressionParser(nonDataRef)).parseDataReference();
        fail();
      } catch (TokenMgrError tme) {
        // Test passes.
      } catch (ParseException pe) {
        // Test passes.
      }
    }
  }


  public void testRecognizeGlobal() throws Exception {

    String[] globals = {"aaa", "aaa.bbb.CCC", "a22 . b88_"};
    for (String global : globals) {
      (new ExpressionParser(global)).parseGlobal();
    }

    String[] nonGlobals = {"$aaa", "1a1a", "aaa.1a1a", "22", "aaa[33]", "aaa[bbb]", "aaa['bbb']"};
    for (String nonGlobal : nonGlobals) {
      try {
        (new ExpressionParser(nonGlobal)).parseGlobal();
        fail();
      } catch (TokenMgrError tme) {
        // Test passes.
      } catch (ParseException pe) {
        // Test passes.
      }
    }
  }


  public void testRecognizePrimitives() throws Exception {

    // Null.
    assertIsExpression("null");
    // Boolean.
    assertIsExpression("true", "false");
    // Integer. (Note the negative sign is actually parsed as the unary "-" operator.)
    assertIsExpression("0", "00", "26", "-729", "1234567890", "0x0", "0x1A2B", "-0xCAFE88");
    assertIsNotExpression("0x1a2b", "-0xcafe88");
    // Float. (Note the negative sign is actually parsed as the unary "-" operator.)
    assertIsExpression("0.0", "3.14159", "-20.0", "6.02e23", "3e3", "3e+3", "-3e-3");
    assertIsNotExpression("0.", ".0", "-20.", ".14159", "6.02E23", "-3E-3");
    // String.
    assertIsExpression("''", "'abc'", "'\\\\ \\' \\\" \\n \\r \\t \\b \\f  \\u00A9 \\u2468'");
    assertIsNotExpression("'\\xA9'", "'\\077'", "\"\"", "\"abc\"");
  }


  public void testRecognizeDataRefAsExpression() throws Exception {
    assertIsExpression("$aaa", "$a0a0.b1b1", "$aaa.0.bbb.12", "$aaa[0].bbb['ccc'][$eee]",
                       "$aaa . 1 [2] .bbb [ 3 + 4 ]['ccc']. ddd [$eee * $fff]");
    assertIsNotExpression("$", "$ aaa", "$1a1a", "$0", "$[12]", "$[$aaa]", "$aaa[]");
  }


  public void testRecognizeGlobalAsExpression() throws Exception {
    assertIsExpression("aaa", "aaa.bbb.CCC", "a22 . b88_");
    assertIsNotExpression("1a1a", "aaa.1a1a", "aaa[33]", "aaa[bbb]", "aaa['bbb']");
  }


  public void testRecognizeFunctionCall() throws Exception {
    assertIsExpression("isFirst($x)", "isLast($y)", "index($z)", "hasData()", "length($x.y.z)",
                       "round(3.14159)", "round(3.14159, 2)", "floor(3.14)", "ceiling(-8)");
    assertIsNotExpression("$isFirst()", "$boo.isFirst()");
  }


  public void testRecognizeOperators() throws Exception {

    // Level 8.
    assertIsExpression("- $a", "not true", "not$a");
    assertIsNotExpression("+1", "!$a");
    // Level 7.
    assertIsExpression("$a * $b", "5/3", "5 %$x", "$a * 4 / -7 % 2");
    // Level 6.
    assertIsExpression("$a+$b", "7 - 12", " - 3 + 4 - 5");
    // Level 5.
    assertIsExpression("$a < 0xA00", "$a>$b", "3<=6", "7.5>= $c");
    // Level 4.
    assertIsExpression("$a==0", "-10 != $b");
    // Level 3.
    assertIsExpression("true and $b");
    assertIsNotExpression("true && $b");
    // Level 2.
    assertIsExpression("$a or null");
    assertIsNotExpression("$a || null");
    // Level 1.
    assertIsExpression("$boo?:-1", "$a ?: $b ?: $c");
    assertIsExpression("false?4:-3", "$a ? $b : $c ? $d : $e");
    // Parentheses.
    assertIsExpression("($a)", "( 4- $b *$c )");
  }


  public void testRecognizeAdditional() throws Exception {

    assertIsExpression("1+2*3-4/5<6==7>=-8%9+10");
    assertIsExpression("((1+2)*((3)-4))/5<6==7>=-8%(9+10)");
    assertIsExpression("$a and true or $b or $c and false and $d or $e");
    assertIsExpression("$a and (true or $b) or ($c and false and ($d or $e))");
    assertIsExpression("$a != 0 ? 33 : $b <= 4 ? 55 : $c ? 77 : $d");
    assertIsExpression("( ( $a != 0 ? 33 : $b <= 4 ) ? 55 : $c ) ? 77 : $d");
    assertIsExpression("round(3.14 + length($boo.foo)) != null");
  }


  public void testRecognizeExpressionList() throws Exception {

    String[] exprLists = {"$aaa, $bbb.ccc + 1, index($ddd)"};
    for (String exprList : exprLists) {
      (new ExpressionParser(exprList)).parseExpressionList();
    }

    String[] nonExprLists = {"", "1, , 3"};
    for (String nonExprList : nonExprLists) {
      try {
        (new ExpressionParser(nonExprList)).parseExpressionList();
        fail();
      } catch (TokenMgrError tme) {
        // Test passes.
      } catch (ParseException pe) {
        // Test passes.
      }
    }
  }


  public void testParseVariable() throws Exception {

    VarNode var = (new ExpressionParser("$boo")).parseVariable().getChild(0);
    assertEquals("boo", var.getName());
    assertEquals("$boo", var.toSourceString());
  }


  public void testParseDataReference() throws Exception {

    DataRefNode dataRef = (new ExpressionParser("$boo.0[$foo]")).parseDataReference().getChild(0);
    assertFalse(dataRef.isIjDataRef());
    assertFalse(dataRef.isNullSafeIjDataRef());
    assertEquals("boo", dataRef.getFirstKey());
    assertEquals(2, dataRef.numChildren());
    DataRefAccessIndexNode access0 = (DataRefAccessIndexNode) dataRef.getChild(0);
    assertFalse(access0.isNullSafe());
    assertEquals(0, access0.getIndex());
    DataRefAccessExprNode access1 = (DataRefAccessExprNode) dataRef.getChild(1);
    assertFalse(access1.isNullSafe());
    assertEquals("$foo", access1.getChild(0).toSourceString());

    dataRef = (new ExpressionParser("$boo?.0?[$foo]")).parseDataReference().getChild(0);
    assertFalse(dataRef.isIjDataRef());
    assertFalse(dataRef.isNullSafeIjDataRef());
    assertEquals("boo", dataRef.getFirstKey());
    assertEquals(2, dataRef.numChildren());
    access0 = (DataRefAccessIndexNode) dataRef.getChild(0);
    assertTrue(access0.isNullSafe());
    assertEquals(0, access0.getIndex());
    access1 = (DataRefAccessExprNode) dataRef.getChild(1);
    assertTrue(access1.isNullSafe());
    assertEquals("$foo", access1.getChild(0).toSourceString());

    dataRef = (new ExpressionParser("$ij?.boo?.0[$ij.foo]")).parseDataReference().getChild(0);
    assertTrue(dataRef.isIjDataRef());
    assertTrue(dataRef.isNullSafeIjDataRef());
    assertEquals("boo", dataRef.getFirstKey());
    assertEquals(2, dataRef.numChildren());
    access0 = (DataRefAccessIndexNode) dataRef.getChild(0);
    assertTrue(access0.isNullSafe());
    assertEquals(0, access0.getIndex());
    access1 = (DataRefAccessExprNode) dataRef.getChild(1);
    assertFalse(access1.isNullSafe());
    DataRefNode childDataRef = (DataRefNode) access1.getChild(0);
    assertTrue(childDataRef.isIjDataRef());
    assertFalse(childDataRef.isNullSafeIjDataRef());
    assertEquals("$ij.foo", childDataRef.toSourceString());
  }


  public void testParseGlobal() throws Exception {

    GlobalNode global = (new ExpressionParser("MOO_2")).parseGlobal().getChild(0);
    assertEquals("MOO_2", global.getName());
    assertEquals("MOO_2", global.toSourceString());

    global = (new ExpressionParser("aaa.BBB")).parseGlobal().getChild(0);
    assertEquals("aaa.BBB", global.getName());
    assertEquals("aaa.BBB", global.toSourceString());
  }


  public void testParsePrimitives() throws Exception {

    ExprRootNode<?> expr = (new ExpressionParser("null")).parseExpression();
    assertTrue(expr.getChild(0) instanceof NullNode);

    expr = (new ExpressionParser("true")).parseExpression();
    assertEquals(true, ((BooleanNode) expr.getChild(0)).getValue());

    expr = (new ExpressionParser("false")).parseExpression();
    assertEquals(false, ((BooleanNode) expr.getChild(0)).getValue());

    expr = (new ExpressionParser("26")).parseExpression();
    assertEquals(26, ((IntegerNode) expr.getChild(0)).getValue());

    expr = (new ExpressionParser("0xCAFE")).parseExpression();
    assertEquals(0xCAFE, ((IntegerNode) expr.getChild(0)).getValue());

    expr = (new ExpressionParser("3.14")).parseExpression();
    assertEquals(3.14, ((FloatNode) expr.getChild(0)).getValue());

    expr = (new ExpressionParser("3e-3")).parseExpression();
    assertEquals(3e-3, ((FloatNode) expr.getChild(0)).getValue());

    expr = (new ExpressionParser("'Aa`! \\n \\r \\t \\\\ \\\' \"'")).parseExpression();
    assertEquals("Aa`! \n \r \t \\ \' \"", ((StringNode) expr.getChild(0)).getValue());

    expr = (new ExpressionParser("'\\u2222 \\uEEEE \\u9EC4 \\u607A'")).parseExpression();
    assertEquals("\u2222 \uEEEE \u9EC4 \u607A", ((StringNode) expr.getChild(0)).getValue());

    expr = (new ExpressionParser("'\u2222 \uEEEE \u9EC4 \u607A'")).parseExpression();
    assertEquals("\u2222 \uEEEE \u9EC4 \u607A", ((StringNode) expr.getChild(0)).getValue());
  }


  public void testParseDataRefAsExpression() throws Exception {

    ExprRootNode<?> expr = (new ExpressionParser("$boo.foo")).parseExpression();
    assertTrue(expr.getChild(0) instanceof DataRefNode);
  }


  public void testParseGlobalAsExpression() throws Exception {

    ExprRootNode<?> expr = (new ExpressionParser("aaa.BBB")).parseExpression();
    assertTrue(expr.getChild(0) instanceof GlobalNode);
  }


  public void testParseFunctionCall() throws Exception {

    ExprRootNode<?> expr = (new ExpressionParser("isFirst($x)")).parseExpression();
    FunctionNode isFirstFn = (FunctionNode) expr.getChild(0);
    assertEquals("isFirst", isFirstFn.getFunctionName());
    assertEquals(1, isFirstFn.numChildren());
    assertEquals("$x", ((DataRefNode) isFirstFn.getChild(0)).toSourceString());

    expr = (new ExpressionParser("round(3.14159, 2)")).parseExpression();
    FunctionNode roundFn = (FunctionNode) expr.getChild(0);
    assertEquals("round", roundFn.getFunctionName());
    assertEquals(2, roundFn.numChildren());
    assertEquals(3.14159, ((FloatNode) roundFn.getChild(0)).getValue());
    assertEquals(2, ((IntegerNode) roundFn.getChild(1)).getValue());
  }


  public void testParseOperators() throws Exception {

    ExprRootNode<?> expr = (new ExpressionParser("-11")).parseExpression();
    NegativeOpNode negOp = (NegativeOpNode) expr.getChild(0);
    assertEquals(11, ((IntegerNode) negOp.getChild(0)).getValue());

    expr = (new ExpressionParser("not false")).parseExpression();
    NotOpNode notOp = (NotOpNode) expr.getChild(0);
    assertEquals(false, ((BooleanNode) notOp.getChild(0)).getValue());

    expr = (new ExpressionParser("90 -14.75")).parseExpression();
    MinusOpNode minusOp = (MinusOpNode) expr.getChild(0);
    assertEquals(90, ((IntegerNode) minusOp.getChild(0)).getValue());
    assertEquals(14.75, ((FloatNode) minusOp.getChild(1)).getValue());

    expr = (new ExpressionParser("$a or true")).parseExpression();
    OrOpNode orOp = (OrOpNode) expr.getChild(0);
    assertEquals("$a", orOp.getChild(0).toSourceString());
    assertEquals(true, ((BooleanNode) orOp.getChild(1)).getValue());

    expr = (new ExpressionParser("$a ?: $b ?: $c")).parseExpression();
    NullCoalescingOpNode nullCoalOp0 = (NullCoalescingOpNode) expr.getChild(0);
    assertEquals("$a", nullCoalOp0.getChild(0).toSourceString());
    NullCoalescingOpNode nullCoalOp1 = (NullCoalescingOpNode) nullCoalOp0.getChild(1);
    assertEquals("$b", nullCoalOp1.getChild(0).toSourceString());
    assertEquals("$c", nullCoalOp1.getChild(1).toSourceString());

    expr = (new ExpressionParser("$a?:$b==null?0*1:0x1")).parseExpression();
    NullCoalescingOpNode nullCoalOp = (NullCoalescingOpNode) expr.getChild(0);
    assertEquals("$a", nullCoalOp.getChild(0).toSourceString());
    ConditionalOpNode condOp = (ConditionalOpNode) nullCoalOp.getChild(1);
    assertTrue(condOp.getChild(0) instanceof EqualOpNode);
    assertTrue(condOp.getChild(1) instanceof TimesOpNode);
    assertTrue(condOp.getChild(2) instanceof IntegerNode);
  }


  public void testParseExpressionList() throws Exception {

    List<ExprRootNode<?>> exprList =
        (new ExpressionParser("$aaa, $bbb.ccc + 1, index($ddd)")).parseExpressionList();
    assertTrue(exprList.get(0).getChild(0) instanceof DataRefNode);
    assertTrue(exprList.get(1).getChild(0) instanceof PlusOpNode);
    assertTrue(exprList.get(2).getChild(0) instanceof FunctionNode);
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  /**
   * Asserts that the given inputs are all expressions.
   * @param inputs The input strings to parse.
   * @throws TokenMgrError When one of the given strings has a token error.
   * @throws ParseException When one of the given strings has a parse error.
   */
  private static void assertIsExpression(String... inputs) throws TokenMgrError, ParseException {
    for (String input : inputs) {
      (new ExpressionParser(input)).parseExpression();
    }
  }


  /**
   * Asserts that the given inputs are not expressions.
   * @param inputs The input strings to parse.
   * @throws AssertionFailedError When one of the given strings is actually a valid expression.
   */
  private static void assertIsNotExpression(String... inputs) {

    for (String input : inputs) {
      try {
        (new ExpressionParser(input)).parseExpression();
        fail();
      } catch (TokenMgrError tme) {
        // Test passes.
      } catch (ParseException pe) {
        // Test passes.
      }
    }
  }

}
