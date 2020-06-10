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

import com.google.common.base.Strings;
import com.google.template.soy.base.SourceLocation.Point;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SharedTestUtils;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for CombineConsecutiveRawTextNodesPass.
 *
 */
@RunWith(JUnit4.class)
public final class CombineConsecutiveRawTextNodesPassTest {

  @Test
  public void testCombineConsecutiveRawTextNodes() {
    String testFileContent =
        "{namespace boo}\n"
            + "\n"
            + "{template .foo}\n"
            + "  {@param goo: ?}\n"
            + "  Blah{$goo}blah{sp}blooh\n"
            + "{/template}\n";

    ErrorReporter boom = ErrorReporter.exploding();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(testFileContent)
            .errorReporter(boom)
            .parse()
            .fileSet();
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);
    template.addChild(new RawTextNode(0, "bleh", template.getSourceLocation()));
    template.addChild(new RawTextNode(0, "bluh", template.getSourceLocation()));

    assertThat(template.numChildren()).isEqualTo(5);

    new CombineConsecutiveRawTextNodesPass().run(soyTree);

    assertThat(template.numChildren()).isEqualTo(3);
    assertThat(((RawTextNode) template.getChild(0)).getRawText()).isEqualTo("Blah");
    assertThat(((RawTextNode) template.getChild(2)).getRawText()).isEqualTo("blah bloohblehbluh");
  }

  @Test
  public void testCombineConsecutiveRawTextNodes_preserveSourceLocations() {
    String testFileContent = "{namespace boo}{template .foo}\nbl\n{nil}ah\n{/template}";

    ErrorReporter boom = ErrorReporter.exploding();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(testFileContent)
            .errorReporter(boom)
            .parse()
            .fileSet();
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);
    assertThat(template.numChildren()).isEqualTo(1);

    RawTextNode node = (RawTextNode) template.getChild(0);
    assertThat(node.getRawText()).isEqualTo("blah");
    assertThat(node.getSourceLocation().getBeginPoint()).isEqualTo(Point.create(2, 1));
    assertThat(node.getSourceLocation().getEndPoint()).isEqualTo(Point.create(3, 7));

    // we also know the locations of individual characters
    assertThat(node.locationOf(2)).isEqualTo(Point.create(3, 6)); // letter "a".

    // split it up into 1 node per character
    int newId = 1; // arbitrary
    RawTextNode c1 = node.substring(newId, 0, 1);
    RawTextNode c2 = node.substring(newId, 1, 2);
    RawTextNode c3 = node.substring(newId, 2, 3);
    RawTextNode c4 = node.substring(newId, 3, 4);
    template.removeChild(node);
    template.addChildren(Arrays.asList(c1, c2, c3, c4));

    assertThat(template.numChildren()).isEqualTo(4);

    new CombineConsecutiveRawTextNodesPass().run(soyTree);

    assertThat(template.numChildren()).isEqualTo(1);
    node = (RawTextNode) template.getChild(0);
    // all the data is preserved across the join operation
    assertThat(node.getRawText()).isEqualTo("blah");
    assertThat(node.getSourceLocation().getBeginPoint()).isEqualTo(Point.create(2, 1));
    assertThat(node.getSourceLocation().getEndPoint()).isEqualTo(Point.create(3, 7));
    assertThat(node.locationOf(2)).isEqualTo(Point.create(3, 6));
  }

  // There used to be a pathological performance issue when merging many raw text nodes, this stress
  // test would have timed out under the old implementation but now succeeds quickly.
  // Before the fix this test took > 2 minutes
  // After the fix it was down to about 1.5s
  @Test
  public void testPathologicalPerformance() {
    String testFileContent = "{namespace boo}{template .foo}{/template}\n";

    ErrorReporter boom = ErrorReporter.exploding();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(testFileContent)
            .errorReporter(boom)
            .parse()
            .fileSet();
    TemplateNode template = (TemplateNode) soyTree.getChild(0).getChild(0);
    // Things like this like this could happen in templates with a large number of html tags (e.g.
    // in a literal block). since this is how they would be desugared.
    final int numCopies = 100_000;
    for (int i = 0; i < numCopies; i++) {
      template.addChild(new RawTextNode(0, "<", template.getSourceLocation()));
      template.addChild(new RawTextNode(0, "div", template.getSourceLocation()));
      template.addChild(new RawTextNode(0, " ", template.getSourceLocation()));
      template.addChild(new RawTextNode(0, "class", template.getSourceLocation()));
      template.addChild(new RawTextNode(0, "=", template.getSourceLocation()));
      template.addChild(new RawTextNode(0, "'", template.getSourceLocation()));
      template.addChild(new RawTextNode(0, "foo", template.getSourceLocation()));
      template.addChild(new RawTextNode(0, "'", template.getSourceLocation()));
      template.addChild(new RawTextNode(0, ">", template.getSourceLocation()));
    }
    new CombineConsecutiveRawTextNodesPass().run(soyTree);
    assertThat(template.numChildren()).isEqualTo(1);
    assertThat(((RawTextNode) template.getChild(0)).getRawText())
        .isEqualTo(Strings.repeat("<div class='foo'>", numCopies));
  }

  @Test
  public void testForConcurrentModificationBug() {
    String testFileContent =
        "{namespace boo}\n"
            + "{template .finishedInWrongBlock}\n"
            + "{@param p : ?}\n"
            + "// the whitespace finishes the attribute value, but it was started in another"
            + " block\n"
            + "<div x=x{if $p}a {/if}>\n"
            + "<div x=\"x{if $p}a\"{/if}>  // ditto but for quoted values\n"
            + "\n"
            + "// special case for finishing in the wrong block\n"
            + "{/template}";

    ErrorReporter boomForTest = ErrorReporter.createForTest();
    SoyFileSetNode soyFileSetNode =
        SoyFileSetParserBuilder.forFileContents(testFileContent)
            .runAutoescaper(true)
            .errorReporter(boomForTest)
            .parse() // NOTE(b/145693330): this would fail.
            .fileSet();
    TemplateNode template = (TemplateNode) soyFileSetNode.getChild(0).getChild(0);
    for (int i = 0; i < template.numChildren(); i++) {
      if (template.getChild(i) instanceof RawTextNode) {
        RawTextNode rtn = (RawTextNode) template.getChild(i);
        assertThat(rtn.getRawText()).isNotEmpty();
        if (i > 0) {
          assertThat(template.getChild(i - 1).getKind()).isNotEqualTo(Kind.RAW_TEXT_NODE);
        }
        if (i < template.numChildren() - 1) {
          assertThat(template.getChild(i + 1).getKind()).isNotEqualTo(Kind.RAW_TEXT_NODE);
        }
      }
    }
  }
}
