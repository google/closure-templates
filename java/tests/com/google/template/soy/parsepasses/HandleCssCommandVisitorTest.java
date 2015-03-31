/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.parsepasses;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.SoyGeneralOptions.CssHandlingScheme;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;

import junit.framework.TestCase;

/**
 * Unit tests for HandleCssCommandVisitor.
 *
 */
public final class HandleCssCommandVisitorTest extends TestCase {

  public void testHandleLiteral() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forTemplateContents("{css selected-option}")
        .parse()
        .getParseTree();
    (new HandleCssCommandVisitor(CssHandlingScheme.LITERAL)).exec(soyTree);
    SoyNode soyNode = SharedTestUtils.getNode(soyTree, 0);
    assertThat(((RawTextNode) soyNode).getRawText()).isEqualTo("selected-option");
  }

  public void testHandleReference() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forTemplateContents("{css $cssSelectedOption}")
        .parse()
        .getParseTree();
    (new HandleCssCommandVisitor(CssHandlingScheme.REFERENCE)).exec(soyTree);
    SoyNode soyNode = SharedTestUtils.getNode(soyTree, 0);
    assertThat(((PrintNode) soyNode).getExprText()).isEqualTo("$cssSelectedOption");

    soyTree = SoyFileSetParserBuilder.forTemplateContents("{css CSS_SELECTED_OPTION}")
        .parse()
        .getParseTree();
    (new HandleCssCommandVisitor(CssHandlingScheme.REFERENCE)).exec(soyTree);
    soyNode = SharedTestUtils.getNode(soyTree, 0);
    assertThat(((PrintNode) soyNode).getExprText()).isEqualTo("CSS_SELECTED_OPTION");
  }

}
