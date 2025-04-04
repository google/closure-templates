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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expressions;
import com.google.template.soy.jssrc.dsl.FormatOptions;
import com.google.template.soy.jssrc.dsl.SourceMapHelper;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitor.ScopedJsTypeRegistry;
import com.google.template.soy.shared.internal.InternalPlugins;
import com.google.template.soy.shared.internal.NoOpScopedData;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.Metadata;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SharedTestUtils;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link GenCallCodeUtils}. */
@RunWith(JUnit4.class)
public final class GenCallCodeUtilsTest {

  @Test
  public void testGenCallExprForBasicCalls() {
    assertThat(getCallExprTextHelper("{call someFunc data=\"all\" /}"))
        .isEqualTo("ns.someFunc(/** @type {?} */ (opt_data), $ijData);");

    assertThat(getCallExprTextHelper("{@param boo : ?}", "{call someFunc data=\"$boo.foo\" /}"))
        .isEqualTo("ns.someFunc(/** @type {?} */ (boo.foo), $ijData);");

    assertThat(
            getCallExprTextHelper(
                "{@param moo : ?}", "{call someFunc}", "  {param goo: $moo /}", "{/call}"))
        .isEqualTo("ns.someFunc$(soy.$$internalCallMarkerDoNotUse, $ijData, opt_data.moo);");

    assertThat(
            getCallExprTextHelper(
                "{@param boo : ?}",
                "{call someFunc data=\"$boo\"}",
                "  {param goo kind=\"text\"}Blah{/param}",
                "{/call}"))
        .isEqualTo("ns.someFunc(soy.$$assignDefaults({goo: 'Blah'}, boo), $ijData);");

    String callExprText =
        getCallExprTextHelper(
            "{call someFunc}\n"
                + "  {param goo kind=\"text\"}\n"
                + "    {for $i in range(3)}{$i}{/for}\n"
                + "  {/param}\n"
                + "{/call}\n");
    assertThat(callExprText)
        .matches(
            Pattern.quote("ns.someFunc$(soy.$$internalCallMarkerDoNotUse, $ijData, param")
                + "[0-9]+"
                + Pattern.quote(");"));
  }

  @Test
  public void testGenCallExprForBasicCallsWithTypedParamBlocks() {
    assertThat(
            getCallExprTextHelper(
                "{@param boo : ?}",
                "{call someFunc data=\"$boo\"}",
                "  {param goo kind=\"html\"}Blah{/param}",
                "{/call}"))
        .isEqualTo(
            "ns.someFunc(soy.$$assignDefaults("
                + "{goo: soy.VERY_UNSAFE.ordainSanitizedHtml('Blah')}, "
                + "boo), $ijData);");

    String callExprText =
        getCallExprTextHelper(
            "{call someFunc}\n"
                + "  {param goo kind=\"html\"}\n"
                + "    {for $i in range(3)}{$i}{/for}\n"
                + "  {/param}\n"
                + "{/call}\n");
    // NOTE: Soy generates a param### variable to store the output of the for loop.
    assertWithMessage("Actual result: " + callExprText)
        .that(callExprText)
        .matches(
            Pattern.quote("ns.someFunc$(soy.$$internalCallMarkerDoNotUse, $ijData, param")
                + "[0-9]+"
                + Pattern.quote(");"));
  }

  @Test
  public void testGenCallExprForDataAllAndDefaultParameter() {
    assertThat(
            getCallExprTextHelper(
                "{@param boo:= 'default'}",
                "{@param goo:= 12}",
                "{call someFunc data='all'}",
                "  {param goo: 59 /}",
                "{/call}"))
        .isEqualTo("ns.someFunc(soy.$$assignDefaults({boo, goo: 59}, opt_data), $ijData);");
  }

  @Test
  public void testGenCallExprForStrictCall() {
    assertThat(getCallExprTextHelper("{call someFunc /}\n", ImmutableSet.of("|escapeHtml")))
        .isEqualTo("soy.$$escapeHtml(ns.someFunc$(soy.$$internalCallMarkerDoNotUse, $ijData));");
  }

  private static String getCallExprTextHelper(String... callSourceLines) {
    return getCallExprTextHelper(Joiner.on('\n').join(callSourceLines), ImmutableSet.of());
  }

  private static String getCallExprTextHelper(
      String callSource, ImmutableSet<String> escapingDirectives) {

    GenericDescriptor[] desc =
        new GenericDescriptor[0];

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateAndImports(
                SharedTestUtils.buildTestTemplateContent(false, callSource)
                    + "\n{template someFunc}{@param? goo: ?}{/template}",
                desc)
            .errorReporter(ErrorReporter.explodeOnErrorsAndIgnoreDeprecations())
            .parse()
            .fileSet();

    CallNode callNode =
        (CallNode) SoyTreeUtils.getAllNodesOfType(soyTree, TemplateNode.class).get(0).getChild(0);
    // Manually setting the escaping directives.
    callNode.setEscapingDirectives(
        InternalPlugins.internalDirectives(NoOpScopedData.INSTANCE).stream()
            .filter(d -> escapingDirectives.contains(d.getName()))
            .collect(toImmutableList()));

    ErrorReporter errorReporter = ErrorReporter.exploding();
    VisitorsState visitorsState =
        new VisitorsState(
            SoyJsSrcOptions.getDefault(),
            new JavaScriptValueFactoryImpl(BidiGlobalDir.LTR, errorReporter),
            SharedTestUtils.importing(desc));
    visitorsState.enterFileSet(Metadata.EMPTY_FILESET, errorReporter);

    UniqueNameGenerator nameGenerator = JsSrcNameGenerators.forLocalVariables();
    TranslationContext translationContext =
        TranslationContext.of(
            SoyToJsVariableMappings.newEmpty()
                .put("$boo", Expressions.id("boo"))
                .put("$goo", Expressions.id("goo")),
            nameGenerator);
    visitorsState.enterFile(
        translationContext,
        ScopedJsTypeRegistry.PASSTHROUGH,
        AliasUtils.IDENTITY_ALIASES,
        SourceMapHelper.NO_OP);
    CodeChunk call =
        visitorsState.genCallCodeUtils.gen(
            callNode, visitorsState.createTranslateExprNodeVisitor());
    return call.getCode(FormatOptions.JSSRC);
  }
}
