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

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.exprparse.ExpressionSubject.assertThatExpression;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.Operator;
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
import com.google.template.soy.exprtree.VarRefNode;

import junit.framework.TestCase;

import java.util.List;

/**
 * Unit tests for the Soy expression parser.
 *
 */
public class ExpressionParserTest extends TestCase {

  public void testRecognizeVariable() {
    String[] vars = {"$aaa", "$_", "$a0b1_"};
    for (String var : vars) {
      assertThatExpression(var).isValidVar();
    }

    String[] nonVars = {"$", "$1", "$ aaa", "aaa", "$ij", "$ij.aaa"};
    for (String nonVar : nonVars) {
      assertThatExpression(nonVar).isNotValidVar();
    }
  }

  public void testRecognizeDataReference() {
    String[] dataRefs =
        {"$aaa", "$ij.aaa", "$a0a0.b1b1", "$aaa[0].bbb[12]", "$aaa[0].bbb['ccc'][$eee]",
         "$aaa?.bbb", "$aaa.bbb?[0]?.ccc?['ddd']", "$ij.aaa",
         "$aaa [1] [2] .bbb [ 3 + 4 ]['ccc']. ddd [$eee * $fff]",
         "functionCall($arg).field",
         "['a' : 'b'].a"};
    for (String dataRef : dataRefs) {
      assertThatExpression(dataRef).isValidExpression();
    }

    String[] nonDataRefs =
        {"$", "$ aaa", "1aaa", "$1a1a", "$0", "$[12]", "$[$aaa]", "$aaa[]", "$ij[4]", "$aaa.?bbb"};
    for (String nonDataRef : nonDataRefs) {
      assertThatExpression(nonDataRef).isNotValidExpression();
    }
  }


  public void testRecognizeGlobal() {
    String[] globals = {"aaa", "aaa.bbb.CCC", "a22 . b88_"};
    for (String global : globals) {
      assertThatExpression(global).isValidGlobal();
    }

    String[] nonGlobals = {"$aaa", "1a1a", "aaa.1a1a", "22", "aaa[33]", "aaa[bbb]", "aaa['bbb']"};
    for (String nonGlobal : nonGlobals) {
      assertThatExpression(nonGlobal).isNotValidGlobal();
    }
  }


  public void testRecognizePrimitives() {
    // Null
    assertThatExpression("null").isValidExpression();
    // Boolean.
    assertThatExpression("true").isValidExpression();
    assertThatExpression("false").isValidExpression();
    // Integer. (Note the negative sign is actually parsed as the unary "-" operator.)
    assertThatExpression("0").isValidExpression();
    assertThatExpression("00").isValidExpression();
    assertThatExpression("26").isValidExpression();
    assertThatExpression("-729").isValidExpression();
    assertThatExpression("1234567890").isValidExpression();
    assertThatExpression("0x0").isValidExpression();
    assertThatExpression("0x1A2B").isValidExpression();
    assertThatExpression("-0xCAFE88").isValidExpression();
    assertThatExpression("0x1A2b").isValidExpression();
    assertThatExpression("-0xcafe88").isValidExpression();

    assertThatExpression("0x0G").isNotValidExpression();

    // Float. (Note the negative sign is actually parsed as the unary "-" operator.)
    assertThatExpression("0.0").isValidExpression();
    assertThatExpression("3.14159").isValidExpression();
    assertThatExpression("-20.0").isValidExpression();
    assertThatExpression("6.02e23").isValidExpression();
    assertThatExpression("3e3").isValidExpression();
    assertThatExpression("3e+3").isValidExpression();
    assertThatExpression("-3e-3").isValidExpression();

    assertThatExpression("0.").isNotValidExpression();
    assertThatExpression(".0").isNotValidExpression();
    assertThatExpression("-20.").isNotValidExpression();
    assertThatExpression(".14159").isNotValidExpression();
    assertThatExpression("6.02E23").isNotValidExpression();
    assertThatExpression("-3E-3").isNotValidExpression();

    // String.
    assertThatExpression("''").isValidExpression();
    assertThatExpression("'{}'").isValidExpression();
    assertThatExpression("'abc'").isValidExpression();
    assertThatExpression("'\\\\ \\' \\\" \\n \\r \\t \\b \\f  \\u00A9 \\u2468'").isValidExpression();

    assertThatExpression("'\\xA9'").isNotValidExpression();
    assertThatExpression("'\\077'").isNotValidExpression();
    assertThatExpression("\"\"").isNotValidExpression();
    assertThatExpression("\"abc\"").isNotValidExpression();
  }


  public void testRecognizeListsAndMaps() {
    assertThatExpression("[]").isValidExpression();
    assertThatExpression("[55]").isValidExpression();
    assertThatExpression("[55,]").isValidExpression();
    assertThatExpression("['blah', 123, $boo]").isValidExpression();
    assertThatExpression("['blah', 123, $boo,]").isValidExpression();

    assertThatExpression("[:]").isValidExpression();
    assertThatExpression("['aa': 55]").isValidExpression();
    assertThatExpression("['aa': 55,]").isValidExpression();
    assertThatExpression("['aaa': 'blah', 'bbb': 123, $foo.bar: $boo]").isValidExpression();
    assertThatExpression("['aaa': 'blah', 'bbb': 123, $foo.bar: $boo,]").isValidExpression();
  }


  public void testRecognizeDataRefAsExpression() {
    assertThatExpression("$aaa").isValidExpression();
    assertThatExpression("$a0a0.b1b1").isValidExpression();
    assertThatExpression("$aaa[0].bbb[12]").isValidExpression();
    assertThatExpression("$aaa[0].bbb['ccc'][$eee]").isValidExpression();
    assertThatExpression("$aaa [1] [2] .bbb [ 3 + 4 ]['ccc']. ddd [$eee * $fff]").isValidExpression();

    assertThatExpression("$").isNotValidExpression();
    assertThatExpression("$ aaa").isNotValidExpression();
    assertThatExpression("$1a1a").isNotValidExpression();
    assertThatExpression("$0").isNotValidExpression();
    assertThatExpression("$[12]").isNotValidExpression();
    assertThatExpression("$[$aaa]").isNotValidExpression();
    assertThatExpression("$aaa[]").isNotValidExpression();
  }


  public void testRecognizeGlobalAsExpression() {
    assertThatExpression("aaa").isValidExpression();
    assertThatExpression("aaa.bbb.CCC").isValidExpression();
    assertThatExpression("a22 . b88_").isValidExpression();

    assertThatExpression("1a1a").isNotValidExpression();
    assertThatExpression("aaa.1a1a").isNotValidExpression();
    // These used to be rejected by the parser, now they will be rejected by the type checker.
    assertThatExpression("aaa[33]").isValidExpression();
    assertThatExpression("aaa[bbb]").isValidExpression();
    assertThatExpression("aaa['bbb']").isValidExpression();
  }


  public void testRecognizeFunctionCall() {
    assertThatExpression("isFirst($x)").isValidExpression();
    assertThatExpression("isLast($y)").isValidExpression();
    assertThatExpression("index($z)").isValidExpression();
    assertThatExpression("randomInt()").isValidExpression();
    assertThatExpression("length($x.y.z)").isValidExpression();
    assertThatExpression("round(3.14159)").isValidExpression();
    assertThatExpression("round(3.14159, 2)").isValidExpression();
    assertThatExpression("floor(3.14)").isValidExpression();
    assertThatExpression("ceiling(-8)").isValidExpression();

    assertThatExpression("$isFirst()").isNotValidExpression();
    assertThatExpression("$boo.isFirst()").isNotValidExpression();
  }


  public void testRecognizeOperators() {

    // Level 8.
    assertThatExpression("- $a").isValidExpression();
    assertThatExpression("not true").isValidExpression();
    assertThatExpression("not$a").isValidExpression();
    assertThatExpression("+1").isNotValidExpression();
    assertThatExpression("!$a").isNotValidExpression();
    // Level 7.
    assertThatExpression("$a * $b").isValidExpression();
    assertThatExpression("5/3").isValidExpression();
    assertThatExpression("5 %$x").isValidExpression();
    assertThatExpression("$a * 4 / -7 % 2").isValidExpression();
    // Level 6.
    assertThatExpression("$a+$b").isValidExpression();
    assertThatExpression("7 - 12").isValidExpression();
    assertThatExpression(" - 3 + 4 - 5").isValidExpression();
    // Level 5.
    assertThatExpression("$a < 0xA00").isValidExpression();
    assertThatExpression("$a>$b").isValidExpression();
    assertThatExpression("3<=6").isValidExpression();
    assertThatExpression("7.5>= $c").isValidExpression();
    // Level 4.
    assertThatExpression("$a==0").isValidExpression();
    assertThatExpression("-10 != $b").isValidExpression();
    // Level 3.
    assertThatExpression("true and $b").isValidExpression();
    assertThatExpression("true && $b").isNotValidExpression();
    // Level 2.
    assertThatExpression("$a or null").isValidExpression();
    assertThatExpression("$a || null").isNotValidExpression();
    // Level 1.
    assertThatExpression("$boo?:-1").isValidExpression();
    assertThatExpression("$a ?: $b ?: $c").isValidExpression();
    assertThatExpression("false?4:-3").isValidExpression();
    assertThatExpression("$a ? $b : $c ? $d : $e").isValidExpression();
    // Parentheses.
    assertThatExpression("($a)").isValidExpression();
    assertThatExpression("( 4- $b *$c )").isValidExpression();
  }


  public void testRecognizeAdditional() {
    assertThatExpression("1+2*3-4/5<6==7>=-8%9+10").isValidExpression();
    assertThatExpression("((1+2)*((3)-4))/5<6==7>=-8%(9+10)").isValidExpression();
    assertThatExpression("$a and true or $b or $c and false and $d or $e").isValidExpression();
    assertThatExpression("$a and (true or $b) or ($c and false and ($d or $e))").isValidExpression();
    assertThatExpression("$a != 0 ? 33 : $b <= 4 ? 55 : $c ? 77 : $d").isValidExpression();
    assertThatExpression("( ( $a != 0 ? 33 : $b <= 4 ) ? 55 : $c ) ? 77 : $d").isValidExpression();
    assertThatExpression("round(3.14 + length($boo.foo)) != null").isValidExpression();
  }


  public void testRecognizeExpressionList() {
    assertThatExpression("$aaa, $bbb.ccc + 1, index($ddd)").isValidExpressionList();
    assertThatExpression("").isNotValidExpressionList();
    assertThatExpression("1, , 3").isNotValidExpressionList();
  }


  public void testParseVariable() {
    assertThatExpression("$boo").isValidVarNamed("boo");
  }


  public void testParseDataReference() throws Exception {

    SourceLocation loc = SourceLocation.UNKNOWN;

    ExprNode dataRef = assertThatExpression("$boo").isValidExpression();
    assertNodeEquals(
        new VarRefNode("boo", loc, false, null),
        dataRef);

    dataRef = assertThatExpression("$boo.foo").isValidExpression();
    assertNodeEquals(
        new FieldAccessNode(
            new VarRefNode("boo", loc, false, null),
            "foo",
            false),
        dataRef);

    dataRef = assertThatExpression("$boo[0][$foo]").isValidExpression();
    assertNodeEquals(
        new ItemAccessNode(
            new ItemAccessNode(
                new VarRefNode("boo", loc, false, null),
                new IntegerNode(0, loc),
                false),
            new VarRefNode("foo", loc, false, null),
            false),
        dataRef);

    dataRef = assertThatExpression("$boo?[0]?[$foo]").isValidExpression();
    assertNodeEquals(
        new ItemAccessNode(
            new ItemAccessNode(
                new VarRefNode("boo", loc, false, null),
                new IntegerNode(0, loc),
                true),
            new VarRefNode("foo", loc, false, null),
            true),
        dataRef);

    dataRef = assertThatExpression("$ij.boo?[0][$ij.foo]").isValidExpression();
    assertNodeEquals(
        new ItemAccessNode(
            new ItemAccessNode(
                new VarRefNode("boo", loc, true, null),
                new IntegerNode(0, loc),
                true),
            new VarRefNode("foo", loc, true, null),
            false),
        dataRef);
  }


  public void testParseGlobal() {
    assertThatExpression("MOO_2").isValidGlobalNamed("MOO_2");
    assertThatExpression("aaa.BBB").isValidGlobalNamed("aaa.BBB");
    assertThatExpression("alias.MyEnum.CCC")
        .withAlias("alias", "my.very.long.namespace")
        .isValidGlobalNamed("my.very.long.namespace.MyEnum.CCC");
  }


  public void testParsePrimitives() throws Exception {

    ExprNode expr = assertThatExpression("null").isValidExpression();
    assertThat(expr).isInstanceOf(NullNode.class);

    expr = assertThatExpression("true").isValidExpression();
    assertThat(((BooleanNode) expr).getValue()).isTrue();

    expr = assertThatExpression("false").isValidExpression();
    assertThat(((BooleanNode) expr).getValue()).isFalse();

    expr = assertThatExpression("26").isValidExpression();
    assertThat(((IntegerNode) expr).getValue()).isEqualTo(26);

    expr = assertThatExpression("0xCAFE").isValidExpression();
    assertThat(((IntegerNode) expr).getValue()).isEqualTo(0xCAFE);

    expr = assertThatExpression("3.14").isValidExpression();
    assertThat(((FloatNode) expr).getValue()).isEqualTo(3.14);

    expr = assertThatExpression("3e-3").isValidExpression();
    assertThat(((FloatNode) expr).getValue()).isEqualTo(3e-3);

    expr = assertThatExpression("'Aa`! \\n \\r \\t \\\\ \\\' \"'").isValidExpression();
    assertThat(((StringNode) expr).getValue()).isEqualTo("Aa`! \n \r \t \\ \' \"");

    expr = assertThatExpression("'\\u2222 \\uEEEE \\u9EC4 \\u607A'").isValidExpression();
    assertThat(((StringNode) expr).getValue()).isEqualTo("\u2222 \uEEEE \u9EC4 \u607A");

    expr = assertThatExpression("'\u2222 \uEEEE \u9EC4 \u607A'").isValidExpression();
    assertThat(((StringNode) expr).getValue()).isEqualTo("\u2222 \uEEEE \u9EC4 \u607A");
  }


  public void testParseListsAndMaps() throws Exception {

    ExprNode expr = assertThatExpression("[]").isValidExpression();
    assertThat(((ListLiteralNode) expr).numChildren()).isEqualTo(0);
    expr = assertThatExpression("[55]").isValidExpression();
    assertThat(((ListLiteralNode) expr).numChildren()).isEqualTo(1);
    expr = assertThatExpression("[55,]").isValidExpression();
    assertThat(((ListLiteralNode) expr).numChildren()).isEqualTo(1);
    expr = assertThatExpression("['blah', 123, $boo]").isValidExpression();
    assertThat(((ListLiteralNode) expr).numChildren()).isEqualTo(3);
    expr = assertThatExpression("['blah', 123, $boo,]").isValidExpression();
    assertThat(((ListLiteralNode) expr).numChildren()).isEqualTo(3);

    expr = assertThatExpression("[:]").isValidExpression();
    assertThat(((MapLiteralNode) expr).numChildren()).isEqualTo(0);
    expr = assertThatExpression("['aa': 55]").isValidExpression();
    assertThat(((MapLiteralNode) expr).numChildren()).isEqualTo(2);
    expr = assertThatExpression("['aa': 55,]").isValidExpression();
    assertThat(((MapLiteralNode) expr).numChildren()).isEqualTo(2);
    expr = assertThatExpression("['aaa': 'blah', 'bbb': 123, $foo.bar: $boo]").isValidExpression();
    assertThat(((MapLiteralNode) expr).numChildren()).isEqualTo(6);
    expr = assertThatExpression("['aaa': 'blah', 'bbb': 123, $foo.bar: $boo,]").isValidExpression();
    assertThat(((MapLiteralNode) expr).numChildren()).isEqualTo(6);
  }


  public void testParseDataRefAsExpression() {
    assertThatExpression("$boo.foo").generatesASTWithRootOfType(FieldAccessNode.class);
  }


  public void testParseGlobalAsExpression() throws Exception {
    assertThatExpression("aaa.BBB").generatesASTWithRootOfType(GlobalNode.class);
  }


  public void testParseFunctionCall() throws Exception {

    ExprNode expr = assertThatExpression("isFirst($x)").isValidExpression();
    FunctionNode isFirstFn = (FunctionNode) expr;
    assertThat(isFirstFn.getFunctionName()).isEqualTo("isFirst");
    assertThat(isFirstFn.numChildren()).isEqualTo(1);
    assertThat(isFirstFn.getChild(0).toSourceString()).isEqualTo("$x");

    expr = assertThatExpression("round(3.14159, 2)").isValidExpression();
    FunctionNode roundFn = (FunctionNode) expr;
    assertThat(roundFn.getFunctionName()).isEqualTo("round");
    assertThat(roundFn.numChildren()).isEqualTo(2);
    assertThat(((FloatNode) roundFn.getChild(0)).getValue()).isEqualTo(3.14159);
    assertThat(((IntegerNode) roundFn.getChild(1)).getValue()).isEqualTo(2);
  }


  public void testParseOperators() throws Exception {
    ExprNode expr = assertThatExpression("-11").isValidExpression();
    NegativeOpNode negOp = (NegativeOpNode) expr;
    assertThat(((IntegerNode) negOp.getChild(0)).getValue()).isEqualTo(11);

    expr = assertThatExpression("not false").isValidExpression();
    NotOpNode notOp = (NotOpNode) expr;
    assertThat(((BooleanNode) notOp.getChild(0)).getValue()).isFalse();

    expr = assertThatExpression("90 -14.75").isValidExpression();
    MinusOpNode minusOp = (MinusOpNode) expr;
    assertThat(((IntegerNode) minusOp.getChild(0)).getValue()).isEqualTo(90);
    assertThat(((FloatNode) minusOp.getChild(1)).getValue()).isEqualTo(14.75);

    expr = assertThatExpression("$a or true").isValidExpression();
    OrOpNode orOp = (OrOpNode) expr;
    assertThat(orOp.getChild(0).toSourceString()).isEqualTo("$a");
    assertThat(((BooleanNode) orOp.getChild(1)).getValue()).isTrue();

    expr = assertThatExpression("$a ?: $b ?: $c").isValidExpression();
    NullCoalescingOpNode nullCoalOp0 = (NullCoalescingOpNode) expr;
    assertThat(nullCoalOp0.getChild(0).toSourceString()).isEqualTo("$a");
    NullCoalescingOpNode nullCoalOp1 = (NullCoalescingOpNode) nullCoalOp0.getChild(1);
    assertThat(nullCoalOp1.getChild(0).toSourceString()).isEqualTo("$b");
    assertThat(nullCoalOp1.getChild(1).toSourceString()).isEqualTo("$c");

    expr = assertThatExpression("$a?:$b==null?0*1:0x1").isValidExpression();
    NullCoalescingOpNode nullCoalOp = (NullCoalescingOpNode) expr;
    assertThat(nullCoalOp.getChild(0).toSourceString()).isEqualTo("$a");
    ConditionalOpNode condOp = (ConditionalOpNode) nullCoalOp.getChild(1);
    assertThat(condOp.getChild(0)).isInstanceOf(EqualOpNode.class);
    assertThat(condOp.getChild(1)).isInstanceOf(TimesOpNode.class);
    assertThat(condOp.getChild(2)).isInstanceOf(IntegerNode.class);
  }


  public void testParseExpressionList() throws Exception {
    List<ExprNode> exprList
        = assertThatExpression("$aaa, $bbb.ccc + 1, index($ddd)").isValidExpressionList();
    assertThat(exprList.get(0)).isInstanceOf(VarRefNode.class);
    assertThat(exprList.get(1)).isInstanceOf(PlusOpNode.class);
    assertThat(exprList.get(2)).isInstanceOf(FunctionNode.class);
  }

  public void testOperatorPrecedence() throws Exception {
    // + is left associative
    assertThat(precedenceString("1 + 2")).isEqualTo("1 + 2");
    assertThat(precedenceString("1 + 2 + 3")).isEqualTo("(1 + 2) + 3");
    assertThat(precedenceString("1 + 2 + 3 + 4 + 5 + 6"))
        .isEqualTo("((((1 + 2) + 3) + 4) + 5) + 6");

    // ?: is right associative
    assertThat(precedenceString("$a ?: $b ?: $c")).isEqualTo("$a ?: ($b ?: $c)");

    // ternary is right associative (though still confusing)
    assertThat(precedenceString("$a ? $b ? $c : $d : $e ? $f : $g"))
        .isEqualTo("$a ? ($b ? $c : $d) : ($e ? $f : $g)");

    // unary negation ?: is right associative
    assertThat(precedenceString("- - $a")).isEqualTo("- (- $a)");

    // all together now!
    assertThat(precedenceString("1 + - 2 * 3 + 4 % 2 ?: 3"))
        .isEqualTo("((1 + ((- 2) * 3)) + (4 % 2)) ?: 3");

    assertThat(precedenceString("-$a.b > 0 ? $c.d : $c"))
        .isEqualTo("((- $a.b) > 0) ? $c.d : $c");
  }

  // Parses the soy expression and then prints it with copious parens to indicate the associativity
  private String precedenceString(String soyExpr) {
    ExprNode node = assertThatExpression(soyExpr).isValidExpression();
    return formatNode(node, true);
  }

  private String formatNode(ExprNode node, boolean outermost) {
    if (node instanceof OperatorNode) {
      OperatorNode opNode = (OperatorNode) node;
      String formatted = formatOperator(opNode);
      if (!outermost) {
        return "(" + formatted + ")";
      }
      return formatted;
    } else {
      return node.toSourceString();
    }
  }

  private String formatOperator(OperatorNode opNode) {
    Operator op = opNode.getOperator();
    switch (op) {
      case NEGATIVE:
      case NOT:
        // unary
        return op.getTokenString() + " " + formatNode(opNode.getChild(0), false);
      case CONDITIONAL:
        return formatNode(opNode.getChild(0), false)
            + " ? " + formatNode(opNode.getChild(1), false)
            + " : " + formatNode(opNode.getChild(2), false);
      default:
        // everything else is binary
        assertEquals(2, op.getNumOperands());
        return formatNode(opNode.getChild(0), false) + " " + op.getTokenString() + " "
            + formatNode(opNode.getChild(1), false);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.


  private void assertNodeEquals(ExprNode expected, ExprNode actual) {
    if (!ExprEquivalence.get().equivalent(expected, actual)) {
      fail(String.format(
          "Expected <%s> but was: <%s>", expected.toSourceString(), actual.toSourceString()));
    }
  }

}
