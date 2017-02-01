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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.internal.LegacyInternalSyntaxException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SoySyntaxExceptionUtils.
 *
 */
@RunWith(JUnit4.class)
public final class SoySyntaxExceptionUtilsTest {

  @Test
  public void testCreateWithNode() {

    String testFileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** @param goo */\n"
            + "{template .foo}\n"
            + "  {$goo}\n"
            + "{/template}\n";

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet();

    String message = "Some error happened.";
    PrintNode pn = (PrintNode) soyTree.getChild(0).getChild(0).getChild(0);
    LegacyInternalSyntaxException sse =
        LegacyInternalSyntaxException.createWithMetaInfo(message, pn.getSourceLocation());
    assertTrue(sse.getMessage().contains(message));
    assertEquals("no-path", sse.getSourceLocation().getFilePath());
  }

  @Test
  public void testAssociateNode() {

    String message = "Some error happened.";
    LegacyInternalSyntaxException sse =
        LegacyInternalSyntaxException.createWithoutMetaInfo(message);

    String testFileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** @param goo */\n"
            + "{template .foo}\n"
            + "  {$goo}\n"
            + "{/template}\n";
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet();
    PrintNode pn = (PrintNode) soyTree.getChild(0).getChild(0).getChild(0);

    // Before.
    assertTrue(sse.getMessage().contains(message));
    assertEquals("unknown", sse.getSourceLocation().getFilePath());

    sse.associateMetaInfo(pn.getSourceLocation(), null, null);

    // After.
    assertTrue(sse.getMessage().contains(message));
    assertEquals("no-path", sse.getSourceLocation().getFilePath());
  }
}
