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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.passes.CombineConsecutiveRawTextNodesVisitor;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;

import junit.framework.TestCase;

/**
 * Unit tests for CombineConsecutiveRawTextNodesVisitor.
 *
 */
public final class CombineConsecutiveRawTextNodesVisitorTest extends TestCase {

  public void testCombineConsecutiveRawTextNodes() {
    String testFileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n" +
        "\n" +
        "/** @param goo */\n" +
        "{template .foo}\n" +
        "  Blah{$goo}blah\n" +
        "{/template}\n";

    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(testFileContent)
            .errorReporter(boom)
            .parse()
            .fileSet();
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);
    template.addChild(new RawTextNode(0, "bleh", template.getSourceLocation()));
    template.addChild(new RawTextNode(0, "bluh", template.getSourceLocation()));

    assertThat(template.numChildren()).isEqualTo(5);

    new CombineConsecutiveRawTextNodesVisitor().exec(soyTree);

    assertThat(template.numChildren()).isEqualTo(3);
    assertThat(((RawTextNode) template.getChild(0)).getRawText()).isEqualTo("Blah");
    assertThat(((RawTextNode) template.getChild(2)).getRawText()).isEqualTo("blahblehbluh");
  }

}
