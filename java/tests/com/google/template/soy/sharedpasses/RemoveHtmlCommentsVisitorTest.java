/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.sharedpasses;

import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;

import junit.framework.TestCase;


/**
 * Unit tests for RemoveHtmlCommentsVisitor.
 *
 * @author Kai Huang
 */
public class RemoveHtmlCommentsVisitorTest extends TestCase {


  public void testRemoveHtmlComments() {

    String testFileContent =
        "/** V1 syntax. */\n" +
        "{template name=\"foo\"}\n" +
        "  <!-- comment 1 -->\n" +
        "  {$boo}\n" +
        "  Blah <!-- comment 2 -->blah.\n" +
        "  {$boo}\n" +
        "  <!-- comment 3 -->\n" +
        "  <!-- comment" +
        "               4 -->\n" +
        "  {$boo}\n" +
        "  <!-- comment 5 -->\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(testFileContent);
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);

    // Before.
    assertEquals(7, template.numChildren());

    (new RemoveHtmlCommentsVisitor()).exec(soyTree);

    // After.
    assertEquals(4, template.numChildren());
    assertEquals("$boo", ((PrintNode) template.getChild(0)).getExprText());
    assertEquals("Blah blah.", ((RawTextNode) template.getChild(1)).getRawText());
    assertEquals("$boo", ((PrintNode) template.getChild(2)).getExprText());
    assertEquals("$boo", ((PrintNode) template.getChild(3)).getExprText());
  }

}
