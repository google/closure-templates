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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SharedTestUtils;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for ClearSoyDocStringsVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class ClearSoyDocStringsVisitorTest {

  @Test
  public void testClearSoyDocStrings() {

    String testFileContent =
        "{namespace boo}\n"
            + "\n"
            + "/**\n"
            + " * blah blah blah\n"
            + " */\n"
            + "{template .foo}\n"
            + "  /** blah blah */\n"
            + "  {@param goo: ?}\n"
            + "  {$goo}\n"
            + "{/template}\n";

    ErrorReporter boom = ErrorReporter.exploding();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(testFileContent)
            .errorReporter(boom)
            .parse()
            .fileSet();
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);

    assertThat(template.getSoyDoc()).contains("blah");
    assertThat(template.getSoyDocDesc()).contains("blah");
    assertThat(template.getParams().get(0).desc()).contains("blah");

    new ClearSoyDocStringsVisitor().exec(soyTree);

    assertThat(template.getSoyDoc()).isNull();
    assertThat(template.getSoyDocDesc()).isNull();
    assertThat(template.getParams().get(0).desc()).isEqualTo("blah blah");
  }
}
