/*
 * Copyright 2010 Google Inc.
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
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;

import junit.framework.TestCase;


/**
 * Unit tests for ClearSoyDocStringsVisitor.
 *
 * @author Kai Huang
 */
public class ClearSoyDocStringsVisitorTest extends TestCase {


  public void testClearSoyDocStrings() {

    String testFileContent =
        "{namespace boo}\n" +
        "\n" +
        "/**\n" +
        " * blah blah blah\n" +
        " *\n" +
        " * @param goo blah blah\n" +
        " */\n" +
        "{template name=\".foo\"}\n" +
        "  {$goo}\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(testFileContent);
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);

    assertTrue(template.getSoyDoc().contains("blah"));
    assertTrue(template.getSoyDocDesc().contains("blah"));
    assertTrue(template.getSoyDocParams().get(0).desc.contains("blah"));

    (new ClearSoyDocStringsVisitor()).exec(soyTree);

    assertNull(template.getSoyDoc());
    assertNull(template.getSoyDocDesc());
    assertNull(template.getSoyDocParams().get(0).desc);
  }

}
