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

package com.google.template.soy.parsepasses.contextautoesc;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.coredirectives.EscapeHtmlDirective;
import com.google.template.soy.coredirectives.IdDirective;
import com.google.template.soy.coredirectives.NoAutoescapeDirective;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link PerformDeprecatedNonContextualAutoescapeVisitor}.
 *
 */
@RunWith(JUnit4.class)
public final class PerformDeprecatedNonContextualAutoescapeVisitorTest {

  private static final ImmutableSet<String> AUTOESCAPE_CANCELLING_DIRECTIVE_NAMES =
      ImmutableSet.of(EscapeHtmlDirective.NAME, NoAutoescapeDirective.NAME, IdDirective.NAME);

  private static final ErrorReporter FAIL = ErrorReporter.exploding();

  private static void performAutoescape(SoyFileSetNode soyTree) {
    new PerformDeprecatedNonContextualAutoescapeVisitor(
            AUTOESCAPE_CANCELLING_DIRECTIVE_NAMES, FAIL, soyTree.getNodeIdGenerator())
        .exec(soyTree);
  }

  @Test
  public void testAutoescapeOnSimple() throws Exception {

    String testPrintTags = "{'<br>'}";
    Pair<SoyFileSetNode, List<PrintNode>> helperRetVal = parseTestPrintTagsHelper(testPrintTags);
    SoyFileSetNode soyTree = helperRetVal.first;
    List<PrintNode> printNodes = helperRetVal.second;

    // Before.
    assertThat(printNodes.get(0).getChildren()).isEmpty();

    performAutoescape(soyTree);

    // After.
    assertThat(printNodes.get(0).getChildren()).hasSize(1);
    assertThat(printNodes.get(0).getChild(0).getName()).isEqualTo(EscapeHtmlDirective.NAME);
  }

  @Test
  public void testAutoescapeOnWithOtherDirectives() throws Exception {

    String testPrintTags = "{'<br>' |boo:5}";
    Pair<SoyFileSetNode, List<PrintNode>> helperRetVal = parseTestPrintTagsHelper(testPrintTags);
    SoyFileSetNode soyTree = helperRetVal.first;
    List<PrintNode> printNodes = helperRetVal.second;

    // Before.
    assertThat(printNodes.get(0).getChildren()).hasSize(1);
    assertThat(printNodes.get(0).getChild(0).getName()).isEqualTo("|boo");

    performAutoescape(soyTree);

    // After.
    assertThat(printNodes.get(0).getChildren()).hasSize(2);
    assertThat(printNodes.get(0).getChild(0).getName()).isEqualTo(EscapeHtmlDirective.NAME);
    assertThat(printNodes.get(0).getChild(1).getName()).isEqualTo("|boo");
  }

  @Test
  public void testAutoescapeOnWithNoAutoescape() throws Exception {

    String testPrintTags = "{'<br>' |noAutoescape}{'<br>' |noAutoescape |noAutoescape}";
    Pair<SoyFileSetNode, List<PrintNode>> helperRetVal = parseTestPrintTagsHelper(testPrintTags);
    SoyFileSetNode soyTree = helperRetVal.first;
    List<PrintNode> printNodes = helperRetVal.second;

    // Before.
    assertThat(printNodes.get(0).getChildren()).hasSize(1);
    assertThat(printNodes.get(0).getChild(0).getName()).isEqualTo(NoAutoescapeDirective.NAME);
    assertThat(printNodes.get(1).getChildren()).hasSize(2);
    assertThat(printNodes.get(1).getChild(0).getName()).isEqualTo(NoAutoescapeDirective.NAME);
    assertThat(printNodes.get(1).getChild(1).getName()).isEqualTo(NoAutoescapeDirective.NAME);

    performAutoescape(soyTree);

    // After. Note that noAutoescape remains to filter against ContentKind.TEXT.
    assertThat(printNodes.get(0).getChildren()).hasSize(1);
    assertThat(printNodes.get(0).getChild(0).getName()).isEqualTo(NoAutoescapeDirective.NAME);
    assertThat(printNodes.get(1).getChildren()).hasSize(2);
    assertThat(printNodes.get(1).getChild(0).getName()).isEqualTo(NoAutoescapeDirective.NAME);
    assertThat(printNodes.get(1).getChild(1).getName()).isEqualTo(NoAutoescapeDirective.NAME);
  }

  @Test
  public void testAutoescapeOnWithEscapeHtml() throws Exception {

    String testPrintTags =
        "{'<br>' |escapeHtml}{'<br>' |noAutoescape |escapeHtml}{'<br>' |escapeHtml |noAutoescape}";
    Pair<SoyFileSetNode, List<PrintNode>> helperRetVal = parseTestPrintTagsHelper(testPrintTags);
    SoyFileSetNode soyTree = helperRetVal.first;
    List<PrintNode> printNodes = helperRetVal.second;

    // Before.
    assertThat(printNodes.get(0).getChildren()).hasSize(1);
    assertThat(printNodes.get(0).getChild(0).getName()).isEqualTo(EscapeHtmlDirective.NAME);
    assertThat(printNodes.get(1).getChildren()).hasSize(2);
    assertThat(printNodes.get(1).getChild(0).getName()).isEqualTo(NoAutoescapeDirective.NAME);
    assertThat(printNodes.get(1).getChild(1).getName()).isEqualTo(EscapeHtmlDirective.NAME);
    assertThat(printNodes.get(2).getChildren()).hasSize(2);
    assertThat(printNodes.get(2).getChild(0).getName()).isEqualTo(EscapeHtmlDirective.NAME);
    assertThat(printNodes.get(2).getChild(1).getName()).isEqualTo(NoAutoescapeDirective.NAME);

    performAutoescape(soyTree);

    // After. Note that noAutoescape remains to filter against ContentKind.TEXT.
    assertThat(printNodes.get(0).getChildren()).hasSize(1);
    assertThat(printNodes.get(0).getChild(0).getName()).isEqualTo(EscapeHtmlDirective.NAME);
    assertThat(printNodes.get(1).getChildren()).hasSize(2);
    assertThat(printNodes.get(1).getChild(0).getName()).isEqualTo(NoAutoescapeDirective.NAME);
    assertThat(printNodes.get(1).getChild(1).getName()).isEqualTo(EscapeHtmlDirective.NAME);
    assertThat(printNodes.get(2).getChildren()).hasSize(2);
    assertThat(printNodes.get(2).getChild(0).getName()).isEqualTo(EscapeHtmlDirective.NAME);
    assertThat(printNodes.get(2).getChild(1).getName()).isEqualTo(NoAutoescapeDirective.NAME);
  }

  /**
   * Helper that puts the given test 'print' tags into a test file, parses the test file, and
   * returns both the parse tree and a list of references to the PrintNodes in the parse tree (these
   * PrintNodes correspond to the test 'print' tags).
   *
   * @param testPrintTags The test 'print' tags to be parsed.
   * @return A Pair with the first item being the full parse tree of the generated test file, and
   *     the second item being a list of references to the PrintNodes in the parse tree.
   * @throws SoySyntaxException If a syntax error is found.
   */
  private static Pair<SoyFileSetNode, List<PrintNode>> parseTestPrintTagsHelper(
      String testPrintTags) throws SoySyntaxException {

    String testFileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Foo template. */\n"
            + "{template .foo}\n"
            + testPrintTags
            + "\n"
            + "{/template}\n";

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet();
    SoyFileNode soyFile = soyTree.getChild(0);

    List<PrintNode> printNodes = Lists.newArrayList();
    for (SoyNode child : soyFile.getChild(0).getChildren()) {
      printNodes.add((PrintNode) child);
    }

    return Pair.of(soyTree, printNodes);
  }
}
