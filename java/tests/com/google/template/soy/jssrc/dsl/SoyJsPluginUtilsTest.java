/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.jssrc.dsl;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.jssrc.dsl.Expression.id;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jssrc.internal.JsSrcNameGenerators;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SoyJsPluginUtilsTest {

  private static final Joiner JOINER = Joiner.on('\n');

  private static final SoyJsSrcPrintDirective DIRECTIVE =
      new SoyJsSrcPrintDirective() {
        @Override
        public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
          String text = "new.directive(" + value.getText();
          for (JsExpr arg : args) {
            text += ", " + arg.getText();
          }
          text += ")";
          return new JsExpr(text, Integer.MAX_VALUE);
        }

        @Override
        public Set<Integer> getValidArgsSizes() {
          return ImmutableSet.of();
        }

        @Override
        public String getName() {
          return "|directive";
        }
      };

  @Test
  public void testApplyDirectiveSimpleExprs() {

    Expression expr = id("expr").call();
    Expression arg1 = id("arg1").call();
    Expression arg2 = id("arg2").call();

    Expression result =
        SoyJsPluginUtils.applyDirective(
            expr,
            DIRECTIVE,
            ImmutableList.of(arg1, arg2),
            SourceLocation.UNKNOWN,
            ErrorReporter.exploding());

    assertThat(result.isRepresentableAsSingleExpression()).isTrue();
    assertThat(result.getCode()).isEqualTo("new.directive(expr(), arg1(), arg2());");
  }

  @Test
  public void testApplyDirectiveCompoundExprs() {
    UniqueNameGenerator nameGenerator = JsSrcNameGenerators.forLocalVariables();
    CodeChunk.Generator gen = CodeChunk.Generator.create(nameGenerator);

    Expression expr = gen.declarationBuilder().setRhs(id("expr").call()).build().ref();
    Expression exprWithInit =
        expr.withInitialStatement(expr.dotAccess("initExpr").call().asStatement());

    Expression arg1 = gen.declarationBuilder().setRhs(id("arg1").call()).build().ref();
    Expression arg1WithInit =
        arg1.withInitialStatement(arg1.dotAccess("initArg1").call().asStatement());

    Expression arg2 = gen.declarationBuilder().setRhs(id("arg2").call()).build().ref();

    Expression arg2WithInit =
        arg2.withInitialStatement(arg2.dotAccess("initArg2").call().asStatement());

    Expression result =
        SoyJsPluginUtils.applyDirective(
            exprWithInit,
            DIRECTIVE,
            ImmutableList.of(arg1WithInit, arg2WithInit),
            SourceLocation.UNKNOWN,
            ErrorReporter.exploding());

    assertThat(result.isRepresentableAsSingleExpression()).isFalse();
    assertThat(result.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp = expr();",
                "$tmp.initExpr();",
                "const $tmp$$1 = arg1();",
                "$tmp$$1.initArg1();",
                "const $tmp$$2 = arg2();",
                "$tmp$$2.initArg2();",
                "new.directive($tmp, $tmp$$1, $tmp$$2);"));
  }

  @Test
  public void testApplyMultipleDirectives() {
    UniqueNameGenerator nameGenerator = JsSrcNameGenerators.forLocalVariables();
    CodeChunk.Generator gen = CodeChunk.Generator.create(nameGenerator);
    Expression expr = gen.declarationBuilder().setRhs(id("expr").call()).build().ref();
    Expression exprWithStatement =
        expr.withInitialStatement(expr.dotAccess("initExpr").call().asStatement());
    Expression arg1 = gen.declarationBuilder().setRhs(id("arg1").call()).build().ref();
    Expression arg1WithStatement =
        arg1.withInitialStatement(arg1.dotAccess("initArg1").call().asStatement());

    Expression arg2 = gen.declarationBuilder().setRhs(id("arg2").call()).build().ref();
    Expression arg2WithStatement =
        arg2.withInitialStatement(arg2.dotAccess("initArg2").call().asStatement());

    Expression result =
        SoyJsPluginUtils.applyDirective(
            exprWithStatement,
            DIRECTIVE,
            ImmutableList.of(arg1WithStatement),
            SourceLocation.UNKNOWN,
            ErrorReporter.exploding());
    result =
        SoyJsPluginUtils.applyDirective(
            result,
            DIRECTIVE,
            ImmutableList.of(arg2WithStatement),
            SourceLocation.UNKNOWN,
            ErrorReporter.exploding());

    assertThat(result.isRepresentableAsSingleExpression()).isFalse();
    assertThat(result.getCode())
        .isEqualTo(
            JOINER.join(
                "const $tmp = expr();",
                "$tmp.initExpr();",
                "const $tmp$$1 = arg1();",
                "$tmp$$1.initArg1();",
                "const $tmp$$2 = arg2();",
                "$tmp$$2.initArg2();",
                "new.directive(new.directive($tmp, $tmp$$1), $tmp$$2);"));
  }
}
