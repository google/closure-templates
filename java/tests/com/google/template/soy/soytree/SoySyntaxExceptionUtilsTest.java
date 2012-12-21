/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.shared.internal.SharedTestUtils;

import junit.framework.TestCase;


/**
 * Unit tests for SoySyntaxExceptionUtils.
 *
 * @author Kai Huang
 */
public class SoySyntaxExceptionUtilsTest extends TestCase {


  public void testCreateWithNode() {

    String testFileContent =
        "{namespace boo}\n" +
        "\n" +
        "/** @param goo */\n" +
        "{template name=\".foo\"}\n" +
        "  {$goo}\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(testFileContent);

    String message = "Some error happened.";
    Throwable cause = new Throwable();
    PrintNode pn = (PrintNode) soyTree.getChild(0).getChild(0).getChild(0);
    SoySyntaxException sse = SoySyntaxExceptionUtils.createCausedWithNode(message, cause, pn);
    assertTrue(sse.getMessage().contains(message));
    assertEquals(cause, sse.getCause());
    assertEquals("no-path", sse.getSourceLocation().getFilePath());
    assertEquals("boo.foo", sse.getTemplateName());
  }


  public void testAssociateNode() {

    String message = "Some error happened.";
    Throwable cause = new Throwable();
    SoySyntaxException sse = SoySyntaxException.createCausedWithoutMetaInfo(message, cause);

    String testFileContent =
        "{namespace boo}\n" +
        "\n" +
        "/** @param goo */\n" +
        "{template name=\".foo\"}\n" +
        "  {$goo}\n" +
        "{/template}\n";
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(testFileContent);
    PrintNode pn = (PrintNode) soyTree.getChild(0).getChild(0).getChild(0);

    // Before.
    assertTrue(sse.getMessage().contains(message));
    assertEquals(cause, sse.getCause());
    assertEquals("unknown", sse.getSourceLocation().getFilePath());
    assertEquals(null, sse.getTemplateName());

    SoySyntaxExceptionUtils.associateNode(sse, pn);

    // After.
    assertTrue(sse.getMessage().contains(message));
    assertEquals(cause, sse.getCause());
    assertEquals("no-path", sse.getSourceLocation().getFilePath());
    assertEquals("boo.foo", sse.getTemplateName());
  }

}
