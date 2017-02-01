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

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for OptimizeBidiCodeGenVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class OptimizeBidiCodeGenVisitorTest {

  private static final BidiGlobalDir BIDI_GLOBAL_DIR_FOR_ISRTL_CODE_SNIPPET =
      BidiGlobalDir.forIsRtlCodeSnippet("IS_RTL", null, SoyBackendKind.JS_SRC);

  @Test
  public void testOptimizeBidiCodeGenForStaticDir() {

    String soyCode =
        "{@param moo: ?}\n"
            + "{@param goo: ?}\n"
            +
            // These 3 nodes should combine into one RawTextNode.
            "{bidiMark()}{bidiStartEdge() |noAutoescape}{bidiEndEdge() |escapeHtml}\n"
            +
            // These 4 nodes don't get replaced.
            "{$goo}{bidiDirAttr($moo)}{bidiMark() |insertWordBreaks:5}{bidiStartEdge() |escapeUri}";

    SoyFileSetNode soyTree = parse(soyCode);
    OptimizeBidiCodeGenVisitor optimizer = new OptimizeBidiCodeGenVisitor(BidiGlobalDir.LTR);
    optimizer.exec(soyTree);
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);

    assertThat(template.numChildren()).isEqualTo(5);
    assertThat(((RawTextNode) template.getChild(0)).getRawText()).isEqualTo("\\u200Eleftright");
    assertThat(((PrintNode) template.getChild(1)).getExprText()).isEqualTo("$goo");
    assertThat(((PrintNode) template.getChild(2)).getExprText()).isEqualTo("bidiDirAttr($moo)");
    assertThat(((PrintNode) template.getChild(3)).getExprText()).isEqualTo("bidiMark()");
    assertThat(((PrintNode) template.getChild(4)).getExprText()).isEqualTo("bidiStartEdge()");
  }

  @Test
  public void testOptimizeBidiCodeGenForCodeSnippetDir() {

    String soyCode =
        "{@param moo: ?}\n"
            + "{@param goo: ?}\n"
            // None of these nodes should get replaced.
            + "{bidiMark()}{bidiStartEdge() |noAutoescape}{bidiEndEdge() |escapeHtml}\n"
            + "{$goo}{bidiDirAttr($moo)}"
            + "{bidiMark() |insertWordBreaks:5}{bidiStartEdge() |escapeUri}";

    SoyFileSetNode soyTree = parse(soyCode);
    OptimizeBidiCodeGenVisitor optimizer =
        new OptimizeBidiCodeGenVisitor(BIDI_GLOBAL_DIR_FOR_ISRTL_CODE_SNIPPET);
    optimizer.exec(soyTree);
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);

    assertThat(template.numChildren()).isEqualTo(7);
    assertThat(((PrintNode) template.getChild(0)).getExprText()).isEqualTo("bidiMark()");
    assertThat(((PrintNode) template.getChild(1)).getExprText()).isEqualTo("bidiStartEdge()");
    assertThat(((PrintNode) template.getChild(2)).getExprText()).isEqualTo("bidiEndEdge()");
    assertThat(((PrintNode) template.getChild(3)).getExprText()).isEqualTo("$goo");
    assertThat(((PrintNode) template.getChild(4)).getExprText()).isEqualTo("bidiDirAttr($moo)");
    assertThat(((PrintNode) template.getChild(5)).getExprText()).isEqualTo("bidiMark()");
    assertThat(((PrintNode) template.getChild(6)).getExprText()).isEqualTo("bidiStartEdge()");
  }

  private static SoyFileSetNode parse(String soyCode) {
    return SoyFileSetParserBuilder.forTemplateContents(soyCode).parse().fileSet();
  }
}
