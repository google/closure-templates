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

package com.google.template.soy.parsepasses;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.coredirectives.EscapeHtmlDirective;
import com.google.template.soy.coredirectives.IdDirective;
import com.google.template.soy.coredirectives.NoAutoescapeDirective;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.List;
import java.util.Map;


/**
 * Unit tests for PerformAutoescapeVisitor.
 *
 */
public class PerformAutoescapeVisitorTest extends TestCase {


  private static final SoyPrintDirective MOCK_BOO_DIRECTIVE =
      EasyMock.createMock(SoyPrintDirective.class);
  static {
    expect(MOCK_BOO_DIRECTIVE.shouldCancelAutoescape()).andReturn(false).anyTimes();
    replay(MOCK_BOO_DIRECTIVE);
  }

  private static final Map<String, SoyPrintDirective> SOY_DIRECTIVES_MAP =
      ImmutableMap.of(
          EscapeHtmlDirective.NAME, new EscapeHtmlDirective(),
          NoAutoescapeDirective.NAME, new NoAutoescapeDirective(),
          IdDirective.NAME, new IdDirective(),
          "|boo", MOCK_BOO_DIRECTIVE);


  public void testAutoescapeOnSimple() throws Exception {

    String testPrintTags = "{'<br>'}";
    Pair<SoyFileSetNode, List<PrintNode>> helperRetVal =
        parseTestPrintTagsHelper(testPrintTags, true);
    SoyFileSetNode soyTree = helperRetVal.first;
    List<PrintNode> printNodes = helperRetVal.second;

    // Before.
    assertEquals(0, printNodes.get(0).getChildren().size());

    (new PerformAutoescapeVisitor(SOY_DIRECTIVES_MAP)).exec(soyTree);

    // After.
    assertEquals(1, printNodes.get(0).getChildren().size());
    assertEquals(EscapeHtmlDirective.NAME, printNodes.get(0).getChild(0).getName());
  }


  public void testAutoescapeOnWithOtherDirectives() throws Exception {

    String testPrintTags = "{'<br>' |boo:5}";
    Pair<SoyFileSetNode, List<PrintNode>> helperRetVal =
        parseTestPrintTagsHelper(testPrintTags, true);
    SoyFileSetNode soyTree = helperRetVal.first;
    List<PrintNode> printNodes = helperRetVal.second;

    // Before.
    assertEquals(1, printNodes.get(0).getChildren().size());
    assertEquals("|boo", printNodes.get(0).getChild(0).getName());

    (new PerformAutoescapeVisitor(SOY_DIRECTIVES_MAP)).exec(soyTree);

    // After.
    assertEquals(2, printNodes.get(0).getChildren().size());
    assertEquals(EscapeHtmlDirective.NAME, printNodes.get(0).getChild(0).getName());
    assertEquals("|boo", printNodes.get(0).getChild(1).getName());
  }


  public void testAutoescapeOnWithNoAutoescape() throws Exception {

    String testPrintTags = "{'<br>' |noAutoescape}{'<br>' |noAutoescape |noAutoescape}";
    Pair<SoyFileSetNode, List<PrintNode>> helperRetVal =
        parseTestPrintTagsHelper(testPrintTags, true);
    SoyFileSetNode soyTree = helperRetVal.first;
    List<PrintNode> printNodes = helperRetVal.second;

    // Before.
    assertEquals(1, printNodes.get(0).getChildren().size());
    assertEquals(NoAutoescapeDirective.NAME, printNodes.get(0).getChild(0).getName());
    assertEquals(2, printNodes.get(1).getChildren().size());
    assertEquals(NoAutoescapeDirective.NAME, printNodes.get(1).getChild(0).getName());
    assertEquals(NoAutoescapeDirective.NAME, printNodes.get(1).getChild(1).getName());

    (new PerformAutoescapeVisitor(SOY_DIRECTIVES_MAP)).exec(soyTree);

    // After.
    assertEquals(0, printNodes.get(0).getChildren().size());
    assertEquals(0, printNodes.get(1).getChildren().size());
  }


  public void testAutoescapeOnWithEscapeHtml() throws Exception {

    String testPrintTags =
        "{'<br>' |escapeHtml}{'<br>' |noAutoescape |escapeHtml}{'<br>' |escapeHtml |noAutoescape}";
    Pair<SoyFileSetNode, List<PrintNode>> helperRetVal =
        parseTestPrintTagsHelper(testPrintTags, true);
    SoyFileSetNode soyTree = helperRetVal.first;
    List<PrintNode> printNodes = helperRetVal.second;

    // Before.
    assertEquals(1, printNodes.get(0).getChildren().size());
    assertEquals(EscapeHtmlDirective.NAME, printNodes.get(0).getChild(0).getName());
    assertEquals(2, printNodes.get(1).getChildren().size());
    assertEquals(NoAutoescapeDirective.NAME, printNodes.get(1).getChild(0).getName());
    assertEquals(EscapeHtmlDirective.NAME, printNodes.get(1).getChild(1).getName());
    assertEquals(2, printNodes.get(2).getChildren().size());
    assertEquals(EscapeHtmlDirective.NAME, printNodes.get(2).getChild(0).getName());
    assertEquals(NoAutoescapeDirective.NAME, printNodes.get(2).getChild(1).getName());

    (new PerformAutoescapeVisitor(SOY_DIRECTIVES_MAP)).exec(soyTree);

    // After.
    assertEquals(1, printNodes.get(0).getChildren().size());
    assertEquals(EscapeHtmlDirective.NAME, printNodes.get(0).getChild(0).getName());
    assertEquals(1, printNodes.get(1).getChildren().size());
    assertEquals(EscapeHtmlDirective.NAME, printNodes.get(1).getChild(0).getName());
    assertEquals(1, printNodes.get(2).getChildren().size());
    assertEquals(EscapeHtmlDirective.NAME, printNodes.get(2).getChild(0).getName());
  }


  public void testAutoescapeOffSimple() throws Exception {

    String testPrintTags = "{'<br>'}";
    Pair<SoyFileSetNode, List<PrintNode>> helperRetVal =
        parseTestPrintTagsHelper(testPrintTags, false);
    SoyFileSetNode soyTree = helperRetVal.first;
    List<PrintNode> printNodes = helperRetVal.second;

    // Before.
    assertEquals(0, printNodes.get(0).getChildren().size());

    (new PerformAutoescapeVisitor(SOY_DIRECTIVES_MAP)).exec(soyTree);

    // After.
    assertEquals(0, printNodes.get(0).getChildren().size());
  }


  public void testAutoescapeOffWithOtherDirectives() throws Exception {

    String testPrintTags = "{'<br>' |boo:5}";  // V1
    Pair<SoyFileSetNode, List<PrintNode>> helperRetVal =
        parseTestPrintTagsHelper(testPrintTags, false);
    SoyFileSetNode soyTree = helperRetVal.first;
    List<PrintNode> printNodes = helperRetVal.second;

    // Before.
    assertEquals(1, printNodes.get(0).getChildren().size());
    assertEquals("|boo", printNodes.get(0).getChild(0).getName());

    (new PerformAutoescapeVisitor(SOY_DIRECTIVES_MAP)).exec(soyTree);

    // After.
    assertEquals(1, printNodes.get(0).getChildren().size());
    assertEquals("|boo", printNodes.get(0).getChild(0).getName());
  }


  public void testAutoescapeOffWithNoAutoescape() throws Exception {

    String testPrintTags = "{'<br>' |noescape}{'<br>' |noAutoescape |noAutoescape}";  // V1 & V2
    Pair<SoyFileSetNode, List<PrintNode>> helperRetVal =
        parseTestPrintTagsHelper(testPrintTags, false);
    SoyFileSetNode soyTree = helperRetVal.first;
    List<PrintNode> printNodes = helperRetVal.second;

    // Before.
    assertEquals(1, printNodes.get(0).getChildren().size());
    assertEquals(NoAutoescapeDirective.NAME, printNodes.get(0).getChild(0).getName());
    assertEquals(2, printNodes.get(1).getChildren().size());
    assertEquals(NoAutoescapeDirective.NAME, printNodes.get(1).getChild(0).getName());
    assertEquals(NoAutoescapeDirective.NAME, printNodes.get(1).getChild(1).getName());

    (new PerformAutoescapeVisitor(SOY_DIRECTIVES_MAP)).exec(soyTree);

    // After.
    assertEquals(0, printNodes.get(0).getChildren().size());
    assertEquals(0, printNodes.get(1).getChildren().size());
  }


  public void testAutoescapeOffWithEscapeHtml() throws Exception {

    String testPrintTags =
        "{'<br>' |escape}{'<br>' |noAutoescape |escapeHtml}{'<br>' |escape |noescape}";  // V1 & V2
    Pair<SoyFileSetNode, List<PrintNode>> helperRetVal =
        parseTestPrintTagsHelper(testPrintTags, false);
    SoyFileSetNode soyTree = helperRetVal.first;
    List<PrintNode> printNodes = helperRetVal.second;

    // Before.
    assertEquals(1, printNodes.get(0).getChildren().size());
    assertEquals(EscapeHtmlDirective.NAME, printNodes.get(0).getChild(0).getName());
    assertEquals(2, printNodes.get(1).getChildren().size());
    assertEquals(NoAutoescapeDirective.NAME, printNodes.get(1).getChild(0).getName());
    assertEquals(EscapeHtmlDirective.NAME, printNodes.get(1).getChild(1).getName());
    assertEquals(2, printNodes.get(2).getChildren().size());
    assertEquals(EscapeHtmlDirective.NAME, printNodes.get(2).getChild(0).getName());
    assertEquals(NoAutoescapeDirective.NAME, printNodes.get(2).getChild(1).getName());

    (new PerformAutoescapeVisitor(SOY_DIRECTIVES_MAP)).exec(soyTree);

    // After.
    assertEquals(1, printNodes.get(0).getChildren().size());
    assertEquals(EscapeHtmlDirective.NAME, printNodes.get(0).getChild(0).getName());
    assertEquals(1, printNodes.get(1).getChildren().size());
    assertEquals(EscapeHtmlDirective.NAME, printNodes.get(1).getChild(0).getName());
    assertEquals(1, printNodes.get(2).getChildren().size());
    assertEquals(EscapeHtmlDirective.NAME, printNodes.get(2).getChild(0).getName());
  }


  /**
   * Helper that puts the given test 'print' tags into a test file, parses the test file, and
   * returns both the parse tree and a list of references to the PrintNodes in the parse tree
   * (these PrintNodes correspond to the test 'print' tags).
   * @param testPrintTags The test 'print' tags to be parsed.
   * @param shouldAutoescape Whether autoescape should be turned on for the generated test template.
   * @return A Pair with the first item being the full parse tree of the generated test file, and
   *     the second item being a list of references to the PrintNodes in the parse tree.
   * @throws SoySyntaxException If a syntax error is found.
   */
  private static Pair<SoyFileSetNode, List<PrintNode>> parseTestPrintTagsHelper(
      String testPrintTags, boolean shouldAutoescape) throws SoySyntaxException {

    String testFileContent =
        "{namespace boo}\n" +
        "\n" +
        "/** Foo template. */\n" +
        "{template name=\".foo\"" + (shouldAutoescape ? "" : " autoescape=\"false\"") + "}\n" +
        testPrintTags + "\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(testFileContent);
    SoyFileNode soyFile = soyTree.getChild(0);

    List<PrintNode> printNodes = Lists.newArrayList();
    for (SoyNode child : soyFile.getChild(0).getChildren()) {
      printNodes.add((PrintNode) child);
    }

    return Pair.of(soyTree, printNodes);
  }

}
