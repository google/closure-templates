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
import com.google.template.soy.base.SourceLocation.Point;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for CombineConsecutiveRawTextNodesVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class CombineConsecutiveRawTextNodesVisitorTest {

  @Test
  public void testCombineConsecutiveRawTextNodes() {
    String testFileContent =
        "{namespace boo}\n"
            + "\n"
            + "/** @param goo */\n"
            + "{template .foo}\n"
            + "  Blah{$goo}blah\n"
            + "{/template}\n";

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

    new CombineConsecutiveRawTextNodesVisitor(soyTree.getNodeIdGenerator()).exec(soyTree);

    assertThat(template.numChildren()).isEqualTo(3);
    assertThat(((RawTextNode) template.getChild(0)).getRawText()).isEqualTo("Blah");
    assertThat(((RawTextNode) template.getChild(2)).getRawText()).isEqualTo("blahblehbluh");
  }


  @Test
  public void testCombineConsecutiveRawTextNodes_preserveSourceLocations() {
    String testFileContent = "{namespace boo}{template .foo}\nbl{nil}ah\n{/template}";

    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(testFileContent)
            .errorReporter(boom)
            .parse()
            .fileSet();
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);
    assertThat(template.numChildren()).isEqualTo(1);

    RawTextNode node = (RawTextNode) template.getChild(0);
    assertThat(node.getRawText()).isEqualTo("blah");
    assertThat(node.getSourceLocation().getBeginPoint()).isEqualTo(Point.create(1, 31));
    assertThat(node.getSourceLocation().getEndPoint()).isEqualTo(Point.create(2, 9));

    // we also know the locations of individual characters
    assertThat(node.locationOf(2)).isEqualTo(Point.create(2, 8));

    // split it up into 1 node per character
    int newId = 1; // arbitrary
    RawTextNode c1 = node.substring(newId, 0, 1);
    RawTextNode c2 = node.substring(newId, 1, 2);
    RawTextNode c3 = node.substring(newId, 2, 3);
    RawTextNode c4 = node.substring(newId, 3, 4);
    template.removeChild(node);
    template.addChildren(Arrays.asList(c1, c2, c3, c4));

    assertThat(template.numChildren()).isEqualTo(4);

    new CombineConsecutiveRawTextNodesVisitor(soyTree.getNodeIdGenerator()).exec(soyTree);

    assertThat(template.numChildren()).isEqualTo(1);
    node = (RawTextNode) template.getChild(0);
    // all the data is preserved across the join operation
    assertThat(node.getRawText()).isEqualTo("blah");
    assertThat(node.getSourceLocation().getBeginPoint()).isEqualTo(Point.create(2, 1));
    assertThat(node.getSourceLocation().getEndPoint()).isEqualTo(Point.create(2, 9));
    assertThat(node.locationOf(2)).isEqualTo(Point.create(2, 8));
  }
}
