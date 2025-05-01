/*
 * Copyright 2025 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jssrc.dsl.FormatOptions;
import com.google.template.soy.jssrc.dsl.Id;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SharedTestUtils;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class OutputVarHandlerTest {

  private static TemplateNode buildSoy(String soyCode) {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(SharedTestUtils.NS + "\n" + soyCode)
            .errorReporter(ErrorReporter.exploding())
            .parse()
            .fileSet();
    return (TemplateNode) SharedTestUtils.getNode(soyTree);
  }

  private static final String HTML_TEMPLATE = "{template foo}hello{/template}";

  private static final String HTML_WITH_HTML_PRINT =
      "{template foo}{@param foo: html}{$foo}{/template}";

  private static final String HTML_WITH_STRING_PRINT =
      "{template foo}{@param foo: string}{$foo}{/template}";

  private static final String HTML_WITH_HTML_CALL =
      "{template foo}{call t /}{/template}{template t}{/template}";

  private static final String HTML_WITH_TEXT_CALL =
      "{template foo}{call t /}{/template}{template t kind='text'}{/template}";

  @Test
  public void testOutputStyleForBlock() {
    OutputVarHandler handler = new OutputVarHandler(true);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_TEMPLATE)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_HTML_PRINT)))
        .isEqualTo(OutputVarHandler.Style.LAZY);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_STRING_PRINT)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_HTML_CALL)))
        .isEqualTo(OutputVarHandler.Style.LAZY);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_TEXT_CALL)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);

    handler.enterBranch(OutputVarHandler.StyleBranchState.ALLOW);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_TEMPLATE)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_HTML_PRINT)))
        .isEqualTo(OutputVarHandler.Style.LAZY);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_STRING_PRINT)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_HTML_CALL)))
        .isEqualTo(OutputVarHandler.Style.LAZY);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_TEXT_CALL)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);

    handler.exitBranch();
    handler.enterBranch(OutputVarHandler.StyleBranchState.DISALLOW);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_TEMPLATE)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_HTML_PRINT)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_STRING_PRINT)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_HTML_CALL)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_TEXT_CALL)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);
  }

  @Test
  public void testOutputStyleForBlockFlagGuarded() {
    OutputVarHandler handler = new OutputVarHandler(false);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_TEMPLATE)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_HTML_PRINT)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_STRING_PRINT)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_HTML_CALL)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);
    assertThat(handler.outputStyleForBlock(buildSoy(HTML_WITH_TEXT_CALL)))
        .isEqualTo(OutputVarHandler.Style.APPENDING);
  }

  @Test
  public void testAddPartsAppending() {
    OutputVarHandler handler = new OutputVarHandler(true);
    handler.pushOutputVar("$output", OutputVarHandler.Style.APPENDING);
    assertThat(handler.currentOutputVarStyle()).isEqualTo(OutputVarHandler.Style.APPENDING);
    assertThat(
            handler
                .addPartsToOutputVar(
                    ImmutableList.of(
                        OutputVarHandler.createStringPart(
                            ImmutableList.of(Id.create("foo"), Id.create("bar"))),
                        OutputVarHandler.createDynamicPart(Id.create("html"))))
                .getCode(FormatOptions.JSSRC))
        .isEqualTo("let $output = '' + foo + bar + html;");
    assertThat(
            handler
                .addPartsToOutputVar(
                    ImmutableList.of(OutputVarHandler.createDynamicPart(Id.create("html2"))))
                .getCode(FormatOptions.JSSRC))
        .isEqualTo("$output += html2;");
  }

  @Test
  public void testDeclaredAppending() {
    OutputVarHandler handler = new OutputVarHandler(true);
    handler.pushOutputVar("$output", OutputVarHandler.Style.APPENDING);
    handler.setOutputVarDeclared();
    assertThat(
            handler
                .addPartsToOutputVar(
                    ImmutableList.of(
                        OutputVarHandler.createStringPart(
                            ImmutableList.of(Id.create("foo"), Id.create("bar"))),
                        OutputVarHandler.createDynamicPart(Id.create("html"))))
                .getCode(FormatOptions.JSSRC))
        .isEqualTo("$output = '' + foo + bar + html;");
  }

  @Test
  public void testAddPartsLazy() {
    OutputVarHandler handler = new OutputVarHandler(true);
    handler.pushOutputVar("$output", OutputVarHandler.Style.LAZY);
    assertThat(handler.currentOutputVarStyle()).isEqualTo(OutputVarHandler.Style.LAZY);
    assertThat(
            handler
                .addPartsToOutputVar(
                    ImmutableList.of(
                        OutputVarHandler.createStringPart(
                            ImmutableList.of(Id.create("foo"), Id.create("bar"))),
                        OutputVarHandler.createDynamicPart(Id.create("html"))))
                .getCode(FormatOptions.JSSRC))
        .isEqualTo(
            "const $output = soy.$$createHtmlOutputBuffer().addString(foo +"
                + " bar).addDynamic(html);");
    assertThat(
            handler
                .addPartsToOutputVar(
                    ImmutableList.of(OutputVarHandler.createDynamicPart(Id.create("html2"))))
                .getCode(FormatOptions.JSSRC))
        .isEqualTo("$output.addDynamic(html2);");
  }

  @Test
  public void testDeclaredLazy() {
    OutputVarHandler handler = new OutputVarHandler(true);
    handler.pushOutputVar("$output", OutputVarHandler.Style.LAZY);
    handler.setOutputVarDeclared();
    assertThat(
            handler
                .addPartsToOutputVar(
                    ImmutableList.of(
                        OutputVarHandler.createStringPart(
                            ImmutableList.of(Id.create("foo"), Id.create("bar"))),
                        OutputVarHandler.createDynamicPart(Id.create("html"))))
                .getCode(FormatOptions.JSSRC))
        .isEqualTo(
            "$output = soy.$$createHtmlOutputBuffer().addString(foo +" + " bar).addDynamic(html);");
  }

  @Test
  public void testCreateHtmlArrayBufferExpr() {
    assertThat(
            OutputVarHandler.createHtmlArrayBufferExpr(
                    ImmutableList.of(
                        OutputVarHandler.createStringPart(
                            ImmutableList.of(Id.create("foo"), Id.create("bar"))),
                        OutputVarHandler.createDynamicPart(Id.create("html"))))
                .getCode(FormatOptions.JSSRC))
        .isEqualTo("soy.$$createHtmlOutputBuffer().addString(foo + bar).addDynamic(html);");
  }
}
