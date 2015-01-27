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

import static org.easymock.EasyMock.createMock;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;

import junit.framework.TestCase;

import java.util.Map;

/**
 * Unit tests for OptimizeBidiCodeGenVisitor.
 *
 */
public class OptimizeBidiCodeGenVisitorTest extends TestCase {


  private static final SoyJsSrcFunction MOCK_FUNCTION = createMock(SoyJsSrcFunction.class);

  private static final Map<String, SoyJsSrcFunction> SOY_JS_SRC_FUNCTIONS_MAP =
      ImmutableMap.<String, SoyJsSrcFunction>builder()
          .put("bidiDirAttr", MOCK_FUNCTION)
          .put("bidiMark", MOCK_FUNCTION)
          .put("bidiStartEdge", MOCK_FUNCTION)
          .put("bidiEndEdge", MOCK_FUNCTION)
          .build();

  private static final BidiGlobalDir BIDI_GLOBAL_DIR_FOR_STATIC_LTR =
      BidiGlobalDir.forStaticIsRtl(false);

  private static final BidiGlobalDir BIDI_GLOBAL_DIR_FOR_ISRTL_CODE_SNIPPET =
      BidiGlobalDir.forIsRtlCodeSnippet("IS_RTL", SoyBackendKind.JS_SRC);


  public void testOptimizeBidiCodeGenForStaticDir() {

    String soyCode =
        // These 3 nodes should combine into one RawTextNode.
        "{bidiMark()}{bidiStartEdge() |noAutoescape}{bidiEndEdge() |escapeHtml}\n" +
        // These 4 nodes don't get replaced.
        "{$goo}{bidiDirAttr($moo)}{bidiMark() |insertWordBreaks:5}{bidiStartEdge() |escapeUri}\n";

    SoyFileSetNode soyTree = JsSrcTestUtils.parseSoyCode(soyCode);
    OptimizeBidiCodeGenVisitor optimizer = new OptimizeBidiCodeGenVisitor(
        SOY_JS_SRC_FUNCTIONS_MAP, BIDI_GLOBAL_DIR_FOR_STATIC_LTR);
    optimizer.exec(soyTree);
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);

    assertEquals(5, template.numChildren());
    assertEquals("\\u200Eleftright", ((RawTextNode) template.getChild(0)).getRawText());
    assertEquals("$goo", ((PrintNode) template.getChild(1)).getExprText());
    assertEquals("bidiDirAttr($moo)", ((PrintNode) template.getChild(2)).getExprText());
    assertEquals("bidiMark()", ((PrintNode) template.getChild(3)).getExprText());
    assertEquals("bidiStartEdge()", ((PrintNode) template.getChild(4)).getExprText());
  }


  public void testOptimizeBidiCodeGenForCodeSnippetDir() {

    String soyCode =
        // None of these nodes should get replaced.
        "{bidiMark()}{bidiStartEdge() |noAutoescape}{bidiEndEdge() |escapeHtml}\n" +
        "{$goo}{bidiDirAttr($moo)}{bidiMark() |insertWordBreaks:5}{bidiStartEdge() |escapeUri}\n";

    SoyFileSetNode soyTree = JsSrcTestUtils.parseSoyCode(soyCode);
    OptimizeBidiCodeGenVisitor optimizer = new OptimizeBidiCodeGenVisitor(
        SOY_JS_SRC_FUNCTIONS_MAP, BIDI_GLOBAL_DIR_FOR_ISRTL_CODE_SNIPPET);
    optimizer.exec(soyTree);
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);

    assertEquals(7, template.numChildren());
    assertEquals("bidiMark()", ((PrintNode) template.getChild(0)).getExprText());
    assertEquals("bidiStartEdge()", ((PrintNode) template.getChild(1)).getExprText());
    assertEquals("bidiEndEdge()", ((PrintNode) template.getChild(2)).getExprText());
    assertEquals("$goo", ((PrintNode) template.getChild(3)).getExprText());
    assertEquals("bidiDirAttr($moo)", ((PrintNode) template.getChild(4)).getExprText());
    assertEquals("bidiMark()", ((PrintNode) template.getChild(5)).getExprText());
    assertEquals("bidiStartEdge()", ((PrintNode) template.getChild(6)).getExprText());
  }

}
