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

package com.google.template.soy.soyparse;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.soyparse.ExpressionSubject.assertThatExpression;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.truth.Correspondence;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
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
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for Soy expression parsing.
 *
 */
@RunWith(JUnit4.class)
public final class ParseExpressionTest {

  @Test
  public void testRecognizeVariable() {
    String[] vars = {"$aaa", "$_", "$a0b1_"};
    for (String var : vars) {
      assertThatExpression(var).isValidVar();
    }

    String[] nonVars = {"$", "$1", "$ aaa", "aaa"};
    for (String nonVar : nonVars) {
      assertThatExpression(nonVar).isNotValidVar();
    }
  }

  @Test
  public void testRecognizeDataReference() {
    String[] dataRefs = {
      "$aaa",
      "$ij.aaa",
      "$a0a0.b1b1",
      "$aaa[0].bbb[12]",
      "$aaa[0].bbb['ccc'][$eee]",
      "$aaa?.bbb",
      "$aaa.bbb?[0]?.ccc?['ddd']",
      "$ij.aaa",
      "$aaa [1] [2] .bbb [ 3 + 4 ]['ccc']. ddd [$eee * $fff]",
      "functionCall($arg).field",
      "function.with.Dots($arg).field",
      "proto().field",
      "pro.to(a: $a).field",
      "record(a : 'b').a",
      "$aaa.method()",
      "$aaa?.method()",
      "$aaa.method(1, 2, 3)",
      "$aaa.method(A.b.c)",
      "$aaa.method().field",
      "$aaa.method()[0]"
    };
    for (String dataRef : dataRefs) {
      assertThatExpression(dataRef).isValidExpression();
    }

    String[] nonDataRefs = {
      "$", "$ aaa", "1aaa", "$1a1a", "$0", "$[12]", "$[$aaa]", "$aaa[]", "$aaa.?bbb"
    };
    for (String nonDataRef : nonDataRefs) {
      assertThatExpression(nonDataRef).isNotValidExpression();
    }
  }

  @Test
  public void testRecognizeGlobal() {
    String[] globals = {"aaa", "aaa.bbb.CCC", "a22 . b88_", "aaa.new", "news"};
    for (String global : globals) {
      assertThatExpression(global).isValidGlobal();
    }

    String[] nonGlobals = {"$aaa", "1a1a", "aaa.1a1a", "22", "aaa[33]", "aaa[bbb]", "aaa['bbb']"};
    for (String nonGlobal : nonGlobals) {
      assertThatExpression(nonGlobal).isNotValidGlobal();
    }
  }

  @Test
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
    assertThatExpression("2147483647").isValidExpression();
    assertThatExpression("-2147483647").isValidExpression();
    assertThatExpression("9007199254740991").isValidExpression();
    assertThatExpression("-9007199254740991").isValidExpression();

    assertThatExpression("0x0G").isNotValidExpression();
    assertThatExpression("9007199254740992").isNotValidExpression(); // JS max safe integer + 1
    assertThatExpression("-9007199254740992").isNotValidExpression();
    assertThatExpression("9223372036854775808").isNotValidExpression(); // Long.MAX_VALUE + 1

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
    assertThatExpression("'\\\\ \\' \\\" \\> \\n \\r \\t \\b \\f  \\xe9 \\u00A9 \\u2468'")
        .isValidExpression();
    assertThatExpression("'\\014 \\77'").isValidExpression();
    assertThatExpression("'\\\\'").isValidExpression();
    assertThatExpression("'\\\\\\\\'").isValidExpression();

    assertThatExpression("'\\xA'").isNotValidExpression();
    assertThatExpression("'\\x0G'").isNotValidExpression();
    assertThatExpression("'\\u123'").isNotValidExpression();
    assertThatExpression("'\\u00AG'").isNotValidExpression();
    assertThatExpression("'\\8'").isNotValidExpression();
    assertThatExpression("'\\a'").isNotValidExpression();
    assertThatExpression("\"\"").isValidExpression();
    assertThatExpression("\"abc\"").isValidExpression();
  }

  @Test
  public void testRecognizeLists() {
    assertThatExpression("[]").isValidExpression();
    assertThatExpression("[55]").isValidExpression();
    assertThatExpression("[55,]").isValidExpression();
    assertThatExpression("['blah', 123, $boo]").isValidExpression();
    assertThatExpression("['blah', 123, $boo,]").isValidExpression();
  }

  @Test
  public void testRecognizeMaps() {
    assertThatExpression("map()").isValidExpression();
    assertThatExpression("map('aa': 55)").isValidExpression();
    assertThatExpression("map('aa': 55,)").isValidExpression();
    assertThatExpression("map('aaa': 'blah', 'bbb': 123, $foo.bar: $boo)").isValidExpression();
    assertThatExpression("map('aaa': 'blah', 'bbb': 123, $foo.bar: $boo,)").isValidExpression();
  }

  @Test
  public void testRecognizeRecordLiterals() {
    assertThatExpression("record()").isNotValidExpression();
    assertThatExpression("record(,)").isNotValidExpression();
    assertThatExpression("record(aa: 55)").isValidExpression();
    assertThatExpression("record(aa: 55,)").isValidExpression();
    assertThatExpression("record(aaa: 'blah', bbb: 123, bar: $boo)").isValidExpression();
    assertThatExpression("record(aaa: 'blah', bbb: 123, bar: $boo,)").isValidExpression();
  }

  @Test
  public void testRecognizeDataRefAsExpression() {
    assertThatExpression("$aaa").isValidExpression();
    assertThatExpression("$a0a0.b1b1").isValidExpression();
    assertThatExpression("$aaa[0].bbb[12]").isValidExpression();
    assertThatExpression("$aaa[0].bbb['ccc'][$eee]").isValidExpression();
    assertThatExpression("$aaa [1] [2] .bbb [ 3 + 4 ]['ccc']. ddd [$eee * $fff]")
        .isValidExpression();

    assertThatExpression("$").isNotValidExpression();
    assertThatExpression("$ aaa").isNotValidExpression();
    assertThatExpression("$1a1a").isNotValidExpression();
    assertThatExpression("$0").isNotValidExpression();
    assertThatExpression("$[12]").isNotValidExpression();
    assertThatExpression("$[$aaa]").isNotValidExpression();
    assertThatExpression("$aaa[]").isNotValidExpression();
  }

  @Test
  public void testRecognizeGlobalAsExpression() {
    assertThatExpression("aaa").isValidExpression();
    assertThatExpression("aaa.bbb.CCC").isValidExpression();
    assertThatExpression("aaa.new").isValidExpression();
    assertThatExpression("a22 . b88_").isValidExpression();
    assertThatExpression("news").isValidExpression();
    assertThatExpression("aaa[33]").isValidExpression(); // the type checker will reject
    assertThatExpression("aaa[bbb]").isValidExpression();

    assertThatExpression("1a1a").isNotValidExpression();
    assertThatExpression("aaa.1a1a").isNotValidExpression();
    assertThatExpression("aaa['bbb']").isValidExpression();
  }

  @Test
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
    assertThatExpression("with(global)").isValidExpression();
    assertThatExpression("with(global.fields)").isValidExpression();
    assertThatExpression("dotted.fn()").isValidExpression();
    assertThatExpression("nested(fn($a))").isValidExpression();
    assertThatExpression("whitespace ($a)").isValidExpression();
    assertThatExpression("white. spaces ($a)").isValidExpression();
    assertThatExpression("proto()").isValidExpression();
    assertThatExpression("pro.to()").isValidExpression();
    assertThatExpression("pro .to ()").isValidExpression();
    assertThatExpression("proto(a: 1, b: $foo, c: proto())").isValidExpression();
    assertThatExpression("pro.to(a: 1, b: $foo, c: proto())").isValidExpression();

    assertThatExpression("$isFirst()").isNotValidExpression();
    assertThatExpression("proto.mixed($a, b: $b)").isNotValidExpression();
    assertThatExpression("proto.mixed(a: $a, $b)").isNotValidExpression();
  }

  @Test
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

  @Test
  public void testRecognizeAdditional() {
    assertThatExpression("1+2*3-4/5<6==7>=-8%9+10").isValidExpression();
    assertThatExpression("((1+2)*((3)-4))/5<6==7>=-8%(9+10)").isValidExpression();
    assertThatExpression("$a and true or $b or $c and false and $d or $e").isValidExpression();
    assertThatExpression("$a and (true or $b) or ($c and false and ($d or $e))")
        .isValidExpression();
    assertThatExpression("$a != 0 ? 33 : $b <= 4 ? 55 : $c ? 77 : $d").isValidExpression();
    assertThatExpression("( ( $a != 0 ? 33 : $b <= 4 ) ? 55 : $c ) ? 77 : $d").isValidExpression();
    assertThatExpression("round(3.14 + length($boo.foo)) != null").isValidExpression();
  }

  @Test
  public void testParseVariable() {
    assertThatExpression("$boo").isValidVarNamed("boo");
  }

  @Test
  public void testParseDataReference() throws Exception {
    SourceLocation loc = SourceLocation.UNKNOWN;

    ExprNode dataRef = assertThatExpression("$boo").isValidExpression();
    assertNodeEquals(new VarRefNode("boo", loc, null), dataRef);

    dataRef = assertThatExpression("$boo.foo").isValidExpression();
    assertNodeEquals(
        new FieldAccessNode(new VarRefNode("boo", loc, null), "foo", loc, false), dataRef);

    dataRef = assertThatExpression("$boo[0][$foo]").isValidExpression();
    assertNodeEquals(
        new ItemAccessNode(
            new ItemAccessNode(
                new VarRefNode("boo", loc, null), new IntegerNode(0, loc), loc, false),
            new VarRefNode("foo", loc, null),
            loc,
            false),
        dataRef);

    dataRef = assertThatExpression("$boo?[0]?[$foo]").isValidExpression();
    assertNodeEquals(
        new ItemAccessNode(
            new ItemAccessNode(
                new VarRefNode("boo", loc, null), new IntegerNode(0, loc), loc, true),
            new VarRefNode("foo", loc, null),
            loc,
            true),
        dataRef);
  }

  @Test
  public void testParseGlobal() {
    assertThatExpression("MOO_2").isValidGlobalNamed("MOO_2");
    assertThatExpression("aaa.BBB").isValidGlobalNamed("aaa.BBB");

    // Aliases are handled later, in RewriteGlobalsPass.
    assertThatExpression("alias.MyEnum.CCC")
        .withAlias("alias", "my.very.long.namespace")
        .isValidGlobalNamed("alias.MyEnum.CCC");
  }

  @Test
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

  @Test
  public void testParseLists() throws Exception {
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
  }

  @Test
  public void testParseRecords() {
    ExprNode expr = assertThatExpression("record(aa: 55)").isValidExpression();
    assertThat(((RecordLiteralNode) expr).numChildren()).isEqualTo(1);
    expr = assertThatExpression("record(aa: 55,)").isValidExpression();
    assertThat(((RecordLiteralNode) expr).numChildren()).isEqualTo(1);
    expr = assertThatExpression("record(aaa: 'blah', bbb: 123, bar: $boo)").isValidExpression();
    assertThat(((RecordLiteralNode) expr).numChildren()).isEqualTo(3);
    expr = assertThatExpression("record(aaa: 'blah', bbb: 123, bar: $boo,)").isValidExpression();
    assertThat(((RecordLiteralNode) expr).numChildren()).isEqualTo(3);
  }

  @Test
  public void testParseDataRefAsExpression() {
    assertThatExpression("$boo.foo").generatesASTWithRootOfType(FieldAccessNode.class);
  }

  @Test
  public void testParseGlobalAsExpression() throws Exception {
    assertThatExpression("aaa.BBB").generatesASTWithRootOfType(GlobalNode.class);
  }

  @Test
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

  @Test
  public void testParseProtoInitCall() throws Exception {
    ExprNode expr =
        assertThatExpression("my.Proto(a: 1, b: glo.bal, c: fn('str'), ext.name: 'str')")
            .isValidExpression();
    ProtoInitNode protoFn = (ProtoInitNode) expr;
    assertThat(protoFn.getProtoName()).isEqualTo("my.Proto");
    assertThat(protoFn.getParamNames())
        .comparingElementsUsing(
            Correspondence.from(
                (Identifier actual, String expected) -> actual.identifier().equals(expected),
                "is equal to"))
        .containsExactly("a", "b", "c", "ext.name")
        .inOrder();
    assertThat(protoFn.numChildren()).isEqualTo(4);
    assertThat(((IntegerNode) protoFn.getChild(0)).getValue()).isEqualTo(1);
    assertThat(((GlobalNode) protoFn.getChild(1)).getName()).isEqualTo("glo.bal");
    assertThat(((StringNode) ((FunctionNode) protoFn.getChild(2)).getChild(0)).getValue())
        .isEqualTo("str");
    assertThat(((StringNode) protoFn.getChild(3)).getValue()).isEqualTo("str");

    assertThatExpression("my.Proto(a: 1, b: glo.bal, c: fn('str'), ext.name: 'str',)")
        .isValidExpression();
  }

  @Test
  public void testParseOperators() throws Exception {
    ExprNode expr = assertThatExpression("-11").isValidExpression();
    IntegerNode negInt = (IntegerNode) expr;
    assertThat(negInt.getValue()).isEqualTo(-11);

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

  @Test
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
        .isEqualTo("((1 + (-2 * 3)) + (4 % 2)) ?: 3");

    assertThat(precedenceString("-$a.b > 0 ? $c.d : $c")).isEqualTo("((- $a.b) > 0) ? $c.d : $c");
  }

  @Test
  public void testNonNullAssertion() {
    ExprNode expr = assertThatExpression("1!").isValidExpression();
    AssertNonNullOpNode nonNullOp = (AssertNonNullOpNode) expr;
    assertThat(nonNullOp.getChild(0)).isInstanceOf(IntegerNode.class);

    expr = assertThatExpression("record(a: 1)!.a").isValidExpression();
    FieldAccessNode fieldAccess = (FieldAccessNode) expr;
    assertThat(fieldAccess.getFieldName()).isEqualTo("a");
    nonNullOp = (AssertNonNullOpNode) fieldAccess.getChild(0);
    assertThat(nonNullOp.getChild(0)).isInstanceOf(RecordLiteralNode.class);

    expr = assertThatExpression("my.Proto(a: 1)?.foo!.bar!.baz!").isValidExpression();
    nonNullOp = (AssertNonNullOpNode) expr;
    fieldAccess = (FieldAccessNode) nonNullOp.getChild(0);
    assertThat(fieldAccess.getFieldName()).isEqualTo("baz");
    assertThat(fieldAccess.isNullSafe()).isFalse();

    nonNullOp = (AssertNonNullOpNode) fieldAccess.getBaseExprChild();
    fieldAccess = (FieldAccessNode) nonNullOp.getChild(0);
    assertThat(fieldAccess.getFieldName()).isEqualTo("bar");
    assertThat(fieldAccess.isNullSafe()).isFalse();

    nonNullOp = (AssertNonNullOpNode) fieldAccess.getBaseExprChild();
    fieldAccess = (FieldAccessNode) nonNullOp.getChild(0);
    assertThat(fieldAccess.getFieldName()).isEqualTo("foo");
    assertThat(fieldAccess.isNullSafe()).isTrue();
    assertThat(fieldAccess.getBaseExprChild()).isInstanceOf(ProtoInitNode.class);

    expr = assertThatExpression("-$a! + $b * $c! < ($d or $e)!").isValidExpression();
    LessThanOpNode lessThan = (LessThanOpNode) expr;

    PlusOpNode plus = (PlusOpNode) lessThan.getChild(0);
    NegativeOpNode negative = (NegativeOpNode) plus.getChild(0);
    assertThat(negative.getChild(0)).isInstanceOf(AssertNonNullOpNode.class);

    TimesOpNode times = (TimesOpNode) plus.getChild(1);
    assertThat(times.getChild(1)).isInstanceOf(AssertNonNullOpNode.class);

    assertThat(lessThan.getChild(1)).isInstanceOf(AssertNonNullOpNode.class);
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
            + " ? "
            + formatNode(opNode.getChild(1), false)
            + " : "
            + formatNode(opNode.getChild(2), false);
      default:
        // everything else is binary
        assertEquals(2, op.getNumOperands());
        return formatNode(opNode.getChild(0), false)
            + " "
            + op.getTokenString()
            + " "
            + formatNode(opNode.getChild(1), false);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  private static void assertNodeEquals(ExprNode expected, ExprNode actual) {
    if (!new ExprEquivalence().equivalent(expected, actual)) {
      fail(
          String.format(
              "Expected <%s> but was: <%s>", expected.toSourceString(), actual.toSourceString()));
    }
  }
}
