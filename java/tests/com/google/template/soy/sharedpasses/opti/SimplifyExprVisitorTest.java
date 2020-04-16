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

package com.google.template.soy.sharedpasses.opti;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.template.soy.exprtree.testing.ExpressionParser;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link SimplifyExprVisitor}.
 *
 */
@RunWith(JUnit4.class)
public final class SimplifyExprVisitorTest {

  @Test
  public void testSimplifyFullySimplifiableExpr() {
    assertThat(new ExpressionParser("-99+-111").parseForParentNode()).simplifiesTo("-210");
    assertThat(new ExpressionParser("-99 + '-111'").parseForParentNode()).simplifiesTo("-99-111");
    assertThat(new ExpressionParser("false or 0 or 0.0 or ''").parseForParentNode())
        .simplifiesTo("");
    assertThat(new ExpressionParser("0 <= 0").parseForParentNode()).simplifiesTo("true");
    assertThat(new ExpressionParser("'22' == 22").parseForParentNode()).simplifiesTo("true");
    assertThat(new ExpressionParser("'22' == '' + 22").parseForParentNode()).simplifiesTo("true");

    // With functions.
    assertThat(new ExpressionParser("max(4, 8)").parseForParentNode()).simplifiesTo("8");
    assertThat(new ExpressionParser("floor(7/2)").parseForParentNode()).simplifiesTo("3");
  }

  @Test
  public void testSimplifyNotSimplifiableExpr() {
    assertThat(new ExpressionParser("$boo").withParam("boo", "string").parseForParentNode())
        .simplifiesTo("$boo");
    assertThat(new ExpressionParser("$boo % 3").withParam("boo", "int").parseForParentNode())
        .simplifiesTo("$boo % 3");
    assertThat(new ExpressionParser("not $boo").withParam("boo", "bool").parseForParentNode())
        .simplifiesTo("not $boo");
    assertThat(new ExpressionParser("$boo + ''").withParam("boo", "string").parseForParentNode())
        .simplifiesTo("$boo + ''");

    // With functions.
    assertThat(new ExpressionParser("max(4, $boo)").withParam("boo", "int").parseForParentNode())
        .simplifiesTo("max(4, $boo)");
    assertThat(new ExpressionParser("floor($boo / 3)").withParam("boo", "int").parseForParentNode())
        .simplifiesTo("floor($boo / 3)");
  }

  @Test
  public void testSimplifyPartiallySimplifiableExpr() {
    assertThat(new ExpressionParser("3 * 5 % $boo").withParam("boo", "int").parseForParentNode())
        .simplifiesTo("15 % $boo");
    assertThat(
            new ExpressionParser("not false and not $boo")
                .withParam("boo", "html")
                .parseForParentNode())
        .simplifiesTo("not $boo");
    assertThat(
            new ExpressionParser("'a' + 'b' + $boo")
                .withParam("boo", "string")
                .parseForParentNode())
        .simplifiesTo("'ab' + $boo");

    // With functions.
    assertThat(
            new ExpressionParser("max(max(4, 8), $boo)")
                .withParam("boo", "int")
                .parseForParentNode())
        .simplifiesTo("max(8, $boo)");
    assertThat(
            new ExpressionParser("floor($boo / (1.0 + 2))")
                .withParam("boo", "float")
                .parseForParentNode())
        .simplifiesTo("floor($boo / 3.0)");
  }

  @Test
  public void testSimplifyListAndMapLiterals() {
    assertThat(new ExpressionParser("['a' + 'b', 1 - 3]").parseForParentNode())
        .simplifiesTo("['ab', -2]");
    assertThat(new ExpressionParser("map('a' + 'b': 1 - 3)").parseForParentNode())
        .simplifiesTo("map('ab': -2)");
    assertThat(new ExpressionParser("[8, ['a' + 'b', 1 - 3]]").parseForParentNode())
        .simplifiesTo("[8, ['ab', -2]]");
    assertThat(new ExpressionParser("map('z': map('a' + 'b': 1 - 3))").parseForParentNode())
        .simplifiesTo("map('z': map('ab': -2))");

    // With functions.
    // Note: Currently, ListLiteralNode and MapLiteralNode are never considered to be constant even
    // though in reality, they can be constant. So in the current implementation, this mapKeys()
    // call cannot be simplified away.
    assertThat(new ExpressionParser("mapKeys(map('a' + 'b': 1 - 3))").parseForParentNode())
        .simplifiesTo("mapKeys(map('ab': -2))");
  }

  @Test
  public void testSimplifyRecordLiterals() {
    assertThat(new ExpressionParser("record(a: 2 + 4)").parseForParentNode())
        .simplifiesTo("record(a: 6)");
    assertThat(new ExpressionParser("record(z: record(a: 1 - 3))").parseForParentNode())
        .simplifiesTo("record(z: record(a: -2))");
    assertThat(new ExpressionParser("record(a: -2).a").parseForParentNode()).simplifiesTo("-2");
  }

  @Test
  public void testDereferenceLiterals() {
    // dereferences of lists and maps can be simplified
    assertThat(new ExpressionParser("[1,2,3]?[0]").parseForParentNode()).simplifiesTo("1");
    assertThat(new ExpressionParser("[1,2,3][1]").parseForParentNode()).simplifiesTo("2");
    assertThat(new ExpressionParser("[1,2,3]?[1]").parseForParentNode()).simplifiesTo("2");

    assertThat(new ExpressionParser("[1,2,3][-1]").parseForParentNode()).simplifiesTo("null");
    assertThat(new ExpressionParser("[1,2,3][3]").parseForParentNode()).simplifiesTo("null");
    assertThat(new ExpressionParser("[1,2,3]?[3]").parseForParentNode()).simplifiesTo("null");

    assertThat(new ExpressionParser("map('a':1, 'b':3)['a']").parseForParentNode())
        .simplifiesTo("1");
    assertThat(new ExpressionParser("map('a':1, 'b':3)?['a']").parseForParentNode())
        .simplifiesTo("1");

    assertThat(new ExpressionParser("map('a':1, 'b':3)['c']").parseForParentNode())
        .simplifiesTo("null");
    assertThat(new ExpressionParser("map('a':1, 'b':3)?['c']").parseForParentNode())
        .simplifiesTo("null");
    // can't simplify unless all keys and indexes are constant
    assertThat(
            new ExpressionParser("map('a': 1, 'b': 3)?[randomInt(10) ? 'a' : 'b']")
                .parseForParentNode())
        .doesntChange();
    // can simplify with dynamic values
    assertThat(
            new ExpressionParser("map('a': randomInt(1), 'b': randomInt(1))?['b']")
                .parseForParentNode())
        .simplifiesTo("randomInt(1)");

    assertThat(new ExpressionParser("record(a:1, b:3).a").parseForParentNode()).simplifiesTo("1");
    assertThat(new ExpressionParser("record(a:1, b:3)?.a").parseForParentNode()).simplifiesTo("1");

    assertThat(
            new ExpressionParser("$null?.a")
                .withVar("null", "true ? null : record(a: 1)")
                .parseForParentNode())
        .simplifiesTo("null");
    assertThat(
            new ExpressionParser("$null?[2]")
                .withVar("null", "true ? null : [1]")
                .parseForParentNode())
        .simplifiesTo("null");
  }

  @Test
  public void testNullSafeAccessChains() {
    // $r?.n?.a when $r.n is null should be null
    assertThat(
            new ExpressionParser("$r?.n?.a")
                .withVar("r", "record(n: true ? null : record(a: 2))")
                .parseForParentNode())
        .simplifiesTo("null");
    // $r.n?.a when $r.n is null should be null
    assertThat(
            new ExpressionParser("$r.n?.a")
                .withVar("r", "record(n: true ? null : record(a: 2))")
                .parseForParentNode())
        .simplifiesTo("null");
    // $r.n?.a.b when $r.n is null should be null
    assertThat(
            new ExpressionParser("$r.n?.a.b")
                .withVar("r", "record(n: true ? null : record(a: record(b: 2)))")
                .parseForParentNode())
        .simplifiesTo("null");
    // $r.n.a?.b when $r.n.a is null should be null
    assertThat(
            new ExpressionParser("$r.n.a?.b")
                .withVar("r", "record(n: record(a: true ? null : record(b: 2)))")
                .parseForParentNode())
        .simplifiesTo("null");

    // $l?[0]?[1] when $l[0] is null should be null
    assertThat(
            new ExpressionParser("$l?[0]?[1]")
                .withVar("l", "[true ? null : ['hey']]")
                .parseForParentNode())
        .simplifiesTo("null");

    // $r?.a.b?.c.d?.e.f when $r is null should be null
    assertThat(
            new ExpressionParser("$r?.a.b?.c.d?.e.f")
                .withVar(
                    "r",
                    "true ? null : record(a: record(b: record(c: record(d: record(e: record(f:"
                        + " 1))))))")
                .parseForParentNode())
        .simplifiesTo("null");
    // $r?.a.b?.c.d?.e.f when r.a.b is null should be null
    assertThat(
            new ExpressionParser("$r?.a.b?.c.d?.e.f")
                .withVar(
                    "r",
                    "record(a: record(b: true ? null : record(c: record(d: record(e: record(f:"
                        + " 1))))))")
                .parseForParentNode())
        .simplifiesTo("null");
    // $r?.a.b?.c.d?.e.f when r.a.b.c.d is null should be null
    assertThat(
            new ExpressionParser("$r?.a.b?.c.d?.e.f")
                .withVar(
                    "r",
                    "record(a: record(b: record(c: record(d: true ? null : record(e: record(f:"
                        + " 1))))))")
                .parseForParentNode())
        .simplifiesTo("null");
    // $r?.a.b?.c.d?.e.f when r.a.b.c.d.e.f is 1 should be 1
    assertThat(
            new ExpressionParser("$r?.a.b?.c.d?.e.f")
                .withVar("r", "record(a: record(b: record(c: record(d: record(e: record(f: 1))))))")
                .parseForParentNode())
        .simplifiesTo("1");
  }

  @Test
  public void testPartiallySimplifiedAccessChains() {
    // Tests that access chains can be partially simplified if some number of the initial data
    // accesses are on a constant.
    assertThat(
            new ExpressionParser("$p?.a.b?.c.d?.e.f")
                .withParam("p", "null|[a: [b: null|[c: [d: [e: null|[f: int]]]]]]")
                .parseForParentNode())
        .simplifiesTo("$p?.a.b?.c.d?.e.f");
    assertThat(
            new ExpressionParser("$r?.a.b?.c.d?.e.f")
                .withParam("p", "[b: null|[c: [d: [e: null|[f: int]]]]]")
                .withVar("r", "true ? record(a: $p) : null")
                .parseForParentNode())
        .simplifiesTo("$p.b?.c.d?.e.f");
    assertThat(
            new ExpressionParser("$r?.a.b?.c.d?.e.f")
                .withParam("p", "null|[c: [d: [e: null|[f: int]]]]")
                .withVar("r", "true ? record(a: record(b: $p)) : null")
                .parseForParentNode())
        .simplifiesTo("$p?.c.d?.e.f");
    assertThat(
            new ExpressionParser("$r?.a.b?.c.d?.e.f")
                .withParam("p", "[d: [e: null|[f: int]]]")
                .withVar("r", "true ? record(a: record(b: true ? record(c: $p) : null)) : null")
                .parseForParentNode())
        .simplifiesTo("$p.d?.e.f");
    assertThat(
            new ExpressionParser("$r?.a.b?.c.d?.e.f")
                .withParam("p", "[e: null|[f: int]]")
                .withVar(
                    "r",
                    "true ? record(a: record(b: true ? record(c: record(d: true ? $p : null)) :"
                        + " null)) : null")
                .parseForParentNode())
        .simplifiesTo("$p?.e.f");
    assertThat(
            new ExpressionParser("$r?.a.b?.c.d?.e.f")
                .withParam("p", "[f: int]")
                .withVar(
                    "r",
                    "true ? record(a: record(b: true ? record(c: record(d: true ? record(e: $p) :"
                        + " null)) : null)) : null")
                .parseForParentNode())
        .simplifiesTo("$p.f");
    assertThat(
            new ExpressionParser("$r?.a.b?.c.d?.e.f")
                .withParam("p", "int")
                .withVar(
                    "r",
                    "true ? record(a: record(b: true ? record(c: record(d: true ? record(e:"
                        + " record(f: $p)) : null)) : null)) : null")
                .parseForParentNode())
        .simplifiesTo("$p");
  }

  @Test
  public void testAccessChainWithNonNullAssertion() {
    assertThat(
            new ExpressionParser("$null?.a!")
                .withVar("null", "true ? null : record(a: 1)")
                .withExperimentalFeatures("enableNonNullAssertionOperator")
                .parseForParentNode())
        .simplifiesTo("null");

    assertThat(
            new ExpressionParser("$r?.a!")
                .withParam("p", "int|null")
                .withVar("r", "true ? record(a: $p) : null")
                .withExperimentalFeatures("enableNonNullAssertionOperator")
                .parseForParentNode())
        .simplifiesTo("record(a: $p)?.a!");

    assertThat(
            new ExpressionParser("$r!.a?.b")
                .withParam("p", "int|null")
                .withVar("r", "true ? record(a: true ? record(b: $p) : null) : null")
                .withExperimentalFeatures("enableNonNullAssertionOperator")
                .parseForParentNode())
        .simplifiesTo("$p");

    assertThat(
            new ExpressionParser("$r?.a?.b!")
                .withParam("p", "int|null")
                .withVar("r", "true ? record(a: true ? record(b: $p) : null) : null")
                .withExperimentalFeatures("enableNonNullAssertionOperator")
                .parseForParentNode())
        .simplifiesTo("record(b: $p)?.b!");
  }

  @Test
  public void testDereferenceLiterals_null() {
    assertThat(new ExpressionParser("(true ? null : record(a:1, b:3))?.a").parseForParentNode())
        .simplifiesTo("null");
    assertThat(new ExpressionParser("(true ? null : map('a':1, 'b':3))?['a']").parseForParentNode())
        .simplifiesTo("null");
    assertThat(new ExpressionParser("(true ? null : [0, 1, 2])?[0]").parseForParentNode())
        .simplifiesTo("null");
  }

  @Test
  public void testSimplifyBinaryLogicalOps() {
    // 'and'
    assertThat(new ExpressionParser("true and true").parseForParentNode()).simplifiesTo("true");
    assertThat(new ExpressionParser("true and false").parseForParentNode()).simplifiesTo("false");
    assertThat(new ExpressionParser("false and true").parseForParentNode()).simplifiesTo("false");
    assertThat(new ExpressionParser("false and false").parseForParentNode()).simplifiesTo("false");
    assertThat(new ExpressionParser("true and $boo").withParam("boo", "bool").parseForParentNode())
        .simplifiesTo("$boo");
    assertThat(
            new ExpressionParser("$boo and true").withParam("boo", "number").parseForParentNode())
        .simplifiesTo("$boo and true"); // Can't simplify
    assertThat(new ExpressionParser("true and 1").parseForParentNode()).simplifiesTo("1");
    assertThat(new ExpressionParser("1 and true").parseForParentNode()).simplifiesTo("true");
    assertThat(new ExpressionParser("false and 1").parseForParentNode()).simplifiesTo("false");
    assertThat(new ExpressionParser("1 and false").parseForParentNode()).simplifiesTo("false");
    assertThat(new ExpressionParser("false and $boo").withParam("boo", "css").parseForParentNode())
        .simplifiesTo("false");
    assertThat(
            new ExpressionParser("$boo and false").withParam("boo", "string").parseForParentNode())
        .simplifiesTo("$boo and false"); // Can't simplify

    // 'or'
    assertThat(new ExpressionParser("true or true").parseForParentNode()).simplifiesTo("true");
    assertThat(new ExpressionParser("true or false").parseForParentNode()).simplifiesTo("true");
    assertThat(new ExpressionParser("false or true").parseForParentNode()).simplifiesTo("true");
    assertThat(new ExpressionParser("false or false").parseForParentNode()).simplifiesTo("false");
    assertThat(new ExpressionParser("true or $boo").withParam("boo", "int").parseForParentNode())
        .simplifiesTo("true");
    assertThat(new ExpressionParser("$boo or true").withParam("boo", "string").parseForParentNode())
        .simplifiesTo("$boo or true"); // Can't simplify
    assertThat(new ExpressionParser("false or $boo").withParam("boo", "bool").parseForParentNode())
        .simplifiesTo("$boo");
    assertThat(
            new ExpressionParser("$boo or false").withParam("boo", "string").parseForParentNode())
        .simplifiesTo("$boo or false");
    assertThat(new ExpressionParser("false or 1").parseForParentNode()).simplifiesTo("1");
    assertThat(new ExpressionParser("1 or false").parseForParentNode()).simplifiesTo("1");
    assertThat(new ExpressionParser("true or 1").parseForParentNode()).simplifiesTo("true");
    assertThat(new ExpressionParser("1 or true").parseForParentNode()).simplifiesTo("1");
  }

  @Test
  public void testSimplifyConditionalOp() {
    assertThat(new ExpressionParser("true ? 111 : 222").parseForParentNode()).simplifiesTo("111");
    assertThat(new ExpressionParser("false ? 111 : 222").parseForParentNode()).simplifiesTo("222");
    assertThat(
            new ExpressionParser("true ? 111 : $boo")
                .withParam("boo", "string")
                .parseForParentNode())
        .simplifiesTo("111");
    assertThat(
            new ExpressionParser("false ? $boo : 222")
                .withParam("boo", "number")
                .parseForParentNode())
        .simplifiesTo("222");
    assertThat(
            new ExpressionParser("$boo or true ? $boo and false : true")
                .withParam("boo", "bool")
                .parseForParentNode())
        .simplifiesTo("$boo or true ? $boo and false : true"); // Can't simplify
  }

  @Test
  public void testSimplifyNullSafeOp() {
    assertThat(new ExpressionParser("null ?: 'aaa'").parseForParentNode()).simplifiesTo("aaa");
    assertThat(new ExpressionParser("2 ?: 'aaa'").parseForParentNode()).simplifiesTo("2");
    assertThat(new ExpressionParser("[null] ?: 'aaa'").parseForParentNode()).simplifiesTo("[null]");
  }

  @Test
  public void testEvaluatePureFunction() {
    assertThat(
            new ExpressionParser("returnsArgument(1)")
                .withFunction(new ReturnsArgumentFunction())
                .parseForParentNode())
        .simplifiesTo("1");
    assertThat(
            new ExpressionParser("returnsList()")
                .withFunction(new ReturnsListFunction())
                .parseForParentNode())
        .simplifiesTo("returnsList()");
    assertThat(
            new ExpressionParser("returnsArgument(returnsList())")
                .withFunction(new ReturnsArgumentFunction())
                .withFunction(new ReturnsListFunction())
                .parseForParentNode())
        .simplifiesTo("returnsArgument(returnsList())");
  }

  @SoyPureFunction
  @SoyFunctionSignature(name = "returnsList", value = @Signature(returnType = "list<string>"))
  public static class ReturnsListFunction implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return factory.listOf(
          Arrays.asList(factory.constant("a"), factory.constant("b"), factory.constant("c")));
    }
  }

  @SoyPureFunction
  @SoyFunctionSignature(
      name = "returnsArgument",
      value = @Signature(parameterTypes = "any", returnType = "any"))
  public static class ReturnsArgumentFunction implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return args.get(0);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  private static final class SimplifySubject extends Subject {
    private final Optional<StandaloneNode> actual;

    private SimplifySubject(FailureMetadata failureMetadata, Optional<StandaloneNode> actual) {
      super(failureMetadata, actual);
      this.actual = actual;
    }

    void doesntChange() {
      simplifiesTo(getSourceString());
    }

    void simplifiesTo(String expected) {
      check("simplifiesTo()").that(getSourceString()).isEqualTo(expected);
    }

    private String getSourceString() {
      if (!actual.isPresent()) {
        return "";
      } else {
        SoyNode result = actual.get();
        if (result.getKind() == Kind.PRINT_NODE) {
          return ((PrintNode) result).getExpr().toSourceString();
        } else if (result.getKind() == Kind.RAW_TEXT_NODE) {
          return result.toSourceString();
        } else {
          throw new AssertionError(result.getKind());
        }
      }
    }
  }

  private static SimplifySubject assertThat(Optional<StandaloneNode> parent) {
    return assertAbout(SimplifySubject::new).that(parent);
  }
}
