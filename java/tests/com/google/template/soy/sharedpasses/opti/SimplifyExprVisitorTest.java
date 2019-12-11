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

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.passes.PluginResolver;
import com.google.template.soy.passes.PluginResolver.Mode;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.internal.InternalPlugins;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.util.Arrays;
import java.util.List;
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
    assertThat("-99+-111").simplifiesTo("-210");
    assertThat("-99+-111").simplifiesTo("-210");
    assertThat("-99 + '-111'").simplifiesTo("'-99-111'");
    assertThat("false or 0 or 0.0 or ''").simplifiesTo("''");
    assertThat("0 <= 0").simplifiesTo("true");
    assertThat("'22' == 22").simplifiesTo("true");
    assertThat("'22' == '' + 22").simplifiesTo("true");

    // With functions.
    assertThat("max(4, 8)").simplifiesTo("8");
    assertThat("floor(7/2)").simplifiesTo("3");
  }

  @Test
  public void testSimplifyNotSimplifiableExpr() {
    assertThat("$boo").simplifiesTo("$boo");
    assertThat("$boo % 3").simplifiesTo("$boo % 3");
    assertThat("not $boo").simplifiesTo("not $boo");
    assertThat("$boo + ''").simplifiesTo("$boo + ''");

    // With functions.
    assertThat("max(4, $boo)").simplifiesTo("max(4, $boo)");
    assertThat("floor($boo / 3)").simplifiesTo("floor($boo / 3)");
  }

  @Test
  public void testSimplifyPartiallySimplifiableExpr() {
    assertThat("3 * 5 % $boo").simplifiesTo("15 % $boo");
    assertThat("not false and not $boo").simplifiesTo("not $boo");
    assertThat("'a' + 'b' + $boo").simplifiesTo("'ab' + $boo");

    // With functions.
    assertThat("max(max(4, 8), $boo)").simplifiesTo("max(8, $boo)");
    assertThat("floor($boo / (1.0 + 2))").simplifiesTo("floor($boo / 3.0)");
  }

  @Test
  public void testSimplifyListAndMapLiterals() {
    assertThat("['a' + 'b', 1 - 3]").simplifiesTo("['ab', -2]");
    assertThat("map('a' + 'b': 1 - 3)").simplifiesTo("map('ab': -2)");
    assertThat("[8, ['a' + 'b', 1 - 3]]").simplifiesTo("[8, ['ab', -2]]");
    assertThat("map('z': map('a' + 'b': 1 - 3))").simplifiesTo("map('z': map('ab': -2))");

    // With functions.
    // Note: Currently, ListLiteralNode and MapLiteralNode are never considered to be constant, even
    // though in reality, they can be constant. So in the current implementation, this mapKeys()
    // call cannot be simplified away.
    assertThat("mapKeys(map('a' + 'b': 1 - 3))").simplifiesTo("mapKeys(map('ab': -2))");
  }

  @Test
  public void testSimplifyRecordLiterals() {
    assertThat("record(a: 2 + 4)").simplifiesTo("record(a: 6)");
    assertThat("record(z: record(a: 1 - 3))").simplifiesTo("record(z: record(a: -2))");
    assertThat("record(a: -2).a").simplifiesTo("-2");
  }

  @Test
  public void testDereferenceLiterals() {
    // dereferences of lists and maps can be simplified
    assertThat("[1,2,3][1]").simplifiesTo("2");
    assertThat("[1,2,3]?[1]").simplifiesTo("2");

    assertThat("[1,2,3][3]").simplifiesTo("null");
    assertThat("[1,2,3]?[3]").simplifiesTo("null");

    assertThat("map('a':1, 'b':3)['a']").simplifiesTo("1");
    assertThat("map('a':1, 'b':3)?['a']").simplifiesTo("1");

    assertThat("record(a:1, b:3).a").simplifiesTo("1");
    assertThat("record(a:1, b:3)?.a").simplifiesTo("1");
  }

  @Test
  public void testSimplifyBinaryLogicalOps() {
    // 'and'
    assertThat("true and true").simplifiesTo("true");
    assertThat("true and false").simplifiesTo("false");
    assertThat("false and true").simplifiesTo("false");
    assertThat("false and false").simplifiesTo("false");
    assertThat("true and $boo").simplifiesTo("$boo");
    assertThat("$boo and true").simplifiesTo("$boo and true"); // Can't simplify
    assertThat("true and 1").simplifiesTo("1");
    assertThat("1 and true").simplifiesTo("true");
    assertThat("false and 1").simplifiesTo("false");
    assertThat("1 and false").simplifiesTo("false");
    assertThat("false and $boo").simplifiesTo("false");
    assertThat("$boo and false").simplifiesTo("$boo and false"); // Can't simplify

    // 'or'
    assertThat("true or true").simplifiesTo("true");
    assertThat("true or false").simplifiesTo("true");
    assertThat("false or true").simplifiesTo("true");
    assertThat("false or false").simplifiesTo("false");
    assertThat("true or $boo").simplifiesTo("true");
    assertThat("$boo or true").simplifiesTo("$boo or true"); // Can't simplify
    assertThat("false or $boo").simplifiesTo("$boo");
    assertThat("$boo or false").simplifiesTo("$boo or false");
    assertThat("false or 1").simplifiesTo("1");
    assertThat("1 or false").simplifiesTo("1");
    assertThat("true or 1").simplifiesTo("true");
    assertThat("1 or true").simplifiesTo("1");
  }

  @Test
  public void testSimplifyConditionalOp() {
    assertThat("true ? 111 : 222").simplifiesTo("111");
    assertThat("false ? 111 : 222").simplifiesTo("222");
    assertThat("true ? 111 : $boo").simplifiesTo("111");
    assertThat("false ? $boo : 222").simplifiesTo("222");
    assertThat("$boo or true ? $boo and false : true")
        .simplifiesTo("$boo or true ? $boo and false : true"); // Can't simplify
  }

  @Test
  public void testSimplifyNullSafeOp() {
    assertThat("null ?: 'aaa'").simplifiesTo("'aaa'");
    assertThat("2 ?: 'aaa'").simplifiesTo("2");
    assertThat("[null] ?: 'aaa'").simplifiesTo("[null]");
  }

  @Test
  public void testEvaluatePureFunction() {
    assertThat("returnsArgument(1)").simplifiesTo("1");
    assertThat("returnsList()").simplifiesTo("returnsList()");
    assertThat("returnsArgument(returnsList())").simplifiesTo("returnsArgument(returnsList())");
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
    private final String actual;

    private SimplifySubject(FailureMetadata failureMetadata, String s) {
      super(failureMetadata, s);
      this.actual = s;
    }

    private void simplifiesTo(String expected) {
      ExprRootNode exprRoot =
          new ExprRootNode(SoyFileParser.parseExpression(actual, ErrorReporter.exploding()));

      PluginResolver resolver =
          new PluginResolver(
              Mode.REQUIRE_DEFINITIONS,
              /** directives= */
              ImmutableMap.of(),
              InternalPlugins.internalLegacyFunctionMap(),
              ImmutableMap.<String, SoySourceFunction>builder()
                  .putAll(InternalPlugins.internalFunctionMap())
                  .put("returnsList", new ReturnsListFunction())
                  .put("returnsArgument", new ReturnsArgumentFunction())
                  .build(),
              ErrorReporter.exploding());
      for (FunctionNode function : SoyTreeUtils.getAllNodesOfType(exprRoot, FunctionNode.class)) {
        function.setSoyFunction(
            resolver.lookupSoyFunction(
                function.getFunctionName(), function.numChildren(), function.getSourceLocation()));
      }
      new SimplifyExprVisitor().exec(exprRoot);
      Truth.assertThat(exprRoot.toSourceString()).isEqualTo(expected);
    }
  }

  private static SimplifySubject assertThat(String input) {
    return Truth.assertAbout(SimplifySubject::new).that(input);
  }
}
