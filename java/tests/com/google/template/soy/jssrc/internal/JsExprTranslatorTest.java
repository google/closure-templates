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

package com.google.template.soy.jssrc.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.basicfunctions.RandomIntFunction;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.internal.TranslateExprNodeVisitor.TranslateExprNodeVisitorFactory;
import com.google.template.soy.shared.restricted.SoyFunction;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for JsExprTranslator.
 *
 */
@RunWith(JUnit4.class)
public final class JsExprTranslatorTest {
  @Test
  public void testTranslateToCodeChunk() {
    JsExprTranslator jsExprTranslator =
        new JsExprTranslator(new TranslateExprNodeVisitorFactory(new SoyJsSrcOptions()));

    TimesOpNode expr = new TimesOpNode(SourceLocation.UNKNOWN);
    expr.addChild(new IntegerNode(3, SourceLocation.UNKNOWN));
    // will be replaced with one of the functions below
    expr.addChild(new NullNode(SourceLocation.UNKNOWN));

    FunctionNode userFnNode =
        new FunctionNode(
            new SoyFunction() {
              @Override
              public String getName() {
                return "userFn";
              }

              @Override
              public Set<Integer> getValidArgsSizes() {
                return ImmutableSet.of(1);
              }
            },
            SourceLocation.UNKNOWN);
    userFnNode.addChild(new IntegerNode(5, SourceLocation.UNKNOWN));

    FunctionNode randomIntFnNode =
        new FunctionNode(new RandomIntFunction(), SourceLocation.UNKNOWN);
    randomIntFnNode.addChild(new IntegerNode(4, SourceLocation.UNKNOWN));

    // Test unsupported function (Soy V1 syntax).
    expr.replaceChild(1, userFnNode);
    UniqueNameGenerator nameGenerator = JsSrcNameGenerators.forLocalVariables();
    assertThat(
            jsExprTranslator
                .translateToCodeChunk(
                    expr,
                    TranslationContext.of(
                        SoyToJsVariableMappings.forNewTemplate(),
                        CodeChunk.Generator.create(nameGenerator),
                        nameGenerator),
                    ErrorReporter.exploding())
                .getCode())
        .isEqualTo("3 * (userFn(5));");

    // Test supported function.
    expr.replaceChild(1, randomIntFnNode);
    assertThat(
            jsExprTranslator
                .translateToCodeChunk(
                    expr,
                    TranslationContext.of(
                        SoyToJsVariableMappings.forNewTemplate(),
                        CodeChunk.Generator.create(nameGenerator),
                        nameGenerator),
                    ErrorReporter.exploding())
                .getCode())
        .isEqualTo("3 * (Math.floor(Math.random() * 4));");
  }
}
