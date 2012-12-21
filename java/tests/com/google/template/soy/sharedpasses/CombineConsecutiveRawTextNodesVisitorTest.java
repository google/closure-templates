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

package com.google.template.soy.sharedpasses;

import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;

import junit.framework.TestCase;


/**
 * Unit tests for CombineConsecutiveRawTextNodesVisitor.
 *
 * @author Kai Huang
 */
public class CombineConsecutiveRawTextNodesVisitorTest extends TestCase {


  public void testCombineConsecutiveRawTextNodes() {

    String testFileContent =
        "{namespace boo}\n" +
        "\n" +
        "/** @param goo */\n" +
        "{template name=\".foo\"}\n" +
        "  Blah{$goo}blah\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(testFileContent);
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);
    template.addChild(new RawTextNode(0, "bleh"));
    template.addChild(new RawTextNode(0, "bluh"));

    assertEquals(5, template.numChildren());

    (new CombineConsecutiveRawTextNodesVisitor()).exec(soyTree);

    assertEquals(3, template.numChildren());
    assertEquals("Blah", ((RawTextNode) template.getChild(0)).getRawText());
    assertEquals("blahblehbluh", ((RawTextNode) template.getChild(2)).getRawText());
  }

}
