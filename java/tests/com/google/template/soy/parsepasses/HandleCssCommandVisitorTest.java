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

import com.google.template.soy.shared.SoyGeneralOptions.CssHandlingScheme;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;

import junit.framework.TestCase;


/**
 * Unit tests for HandleCssCommandVisitor.
 *
 */
public class HandleCssCommandVisitorTest extends TestCase {


  public void testHandleLiteral() {

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyCode("{css selected-option}");
    (new HandleCssCommandVisitor(CssHandlingScheme.LITERAL)).exec(soyTree);
    SoyNode soyNode = SharedTestUtils.getNode(soyTree, 0);
    assertEquals("selected-option", ((RawTextNode) soyNode).getRawText());
  }


  public void testHandleReference() {

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyCode("{css $cssSelectedOption}");
    (new HandleCssCommandVisitor(CssHandlingScheme.REFERENCE)).exec(soyTree);
    SoyNode soyNode = SharedTestUtils.getNode(soyTree, 0);
    assertEquals("$cssSelectedOption", ((PrintNode) soyNode).getExprText());

    soyTree = SharedTestUtils.parseSoyCode("{css CSS_SELECTED_OPTION}");
    (new HandleCssCommandVisitor(CssHandlingScheme.REFERENCE)).exec(soyTree);
    soyNode = SharedTestUtils.getNode(soyTree, 0);
    assertEquals("CSS_SELECTED_OPTION", ((PrintNode) soyNode).getExprText());
  }

}
