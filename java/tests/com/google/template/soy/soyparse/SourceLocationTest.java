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

package com.google.template.soy.soyparse;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that the Soy file and template parsers properly embed source locations. */
@RunWith(JUnit4.class)
public final class SourceLocationTest {

  private static final Joiner JOINER = Joiner.on('\n');

  @Test
  public void testLocationsInParsedContent() throws Exception {
    assertSourceLocations(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          @ /example/file.soy:2:1",
            "      RawTextNode              @ /example/file.soy:4:3",
            "      PrintNode                @ /example/file.soy:6:3",
            "      RawTextNode              @ /example/file.soy:7:3",
            "      CallBasicNode            @ /example/file.soy:9:3",
            "    TemplateBasicNode          @ /example/file.soy:11:1",
            "      RawTextNode              @ /example/file.soy:12:3",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .foo autoescape=\"deprecated-noncontextual\"}", // 1
            "{@param world : ?}",
            "  Hello", // 3
            "  {lb}", // 4
            "  {print $world}", // 5
            "  {rb}!", // 6
            "", // 7
            "  {call bar /}", // 8
            "{/template}", // 9
            "{template .bar autoescape=\"deprecated-noncontextual\"}", // 10
            "  Gooodbye", // 11
            "{/template}" // 12
            ));
  }

  @Test
  public void testSwitches() throws Exception {
    assertSourceLocations(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          @ /example/file.soy:2:1",
            "      RawTextNode              @ /example/file.soy:4:3",
            "      SwitchNode               @ /example/file.soy:5:3",
            "        SwitchCaseNode         @ /example/file.soy:6:5",
            "          RawTextNode          @ /example/file.soy:7:7",
            "        SwitchCaseNode         @ /example/file.soy:8:5",
            "          RawTextNode          @ /example/file.soy:9:7",
            "        SwitchCaseNode         @ /example/file.soy:10:5",
            "          RawTextNode          @ /example/file.soy:11:7",
            "        SwitchDefaultNode      @ /example/file.soy:12:5",
            "          RawTextNode          @ /example/file.soy:13:7",
            "      RawTextNode              @ /example/file.soy:15:3",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .foo autoescape=\"deprecated-noncontextual\"}", // 1
            "{@param i : int}", // 2
            "  Hello,", // 3
            "  {switch $i}", // 4
            "    {case 0}", // 5
            "      Mercury", // 6
            "    {case 1}", // 7
            "      Venus", // 8
            "    {case 2}", // 9
            "      Mars", // 10
            "    {default}", // 11
            "      Gassy", // 12
            "  {/switch}", // 13
            "  !", // 14
            "{/template}", // 15
            ""));
  }

  @Test
  public void testForLoop() throws Exception {
    assertSourceLocations(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          @ /example/file.soy:2:1",
            "      RawTextNode              @ /example/file.soy:3:3",
            "      ForNode                  @ /example/file.soy:4:3",
            "        RawTextNode            @ /example/file.soy:5:5",
            "        PrintNode              @ /example/file.soy:6:5",
            "      RawTextNode              @ /example/file.soy:8:3",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .foo autoescape=\"deprecated-noncontextual\"}", // 1
            "  Hello", // 2
            "  {for $i in range(0, 1, 10)}", // 3
            "    ,", // 4
            "    {print $i}", // 5
            "  {/for}", // 6
            "  !", // 7
            "{/template}", // 8
            ""));
  }

  @Test
  public void testForeachLoop() throws Exception {
    assertSourceLocations(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          @ /example/file.soy:2:1",
            "      RawTextNode              @ /example/file.soy:3:3",
            "      ForeachNode              @ /example/file.soy:4:3",
            "        ForeachNonemptyNode    @ /example/file.soy:4:12",
            "          RawTextNode          @ /example/file.soy:5:5",
            "          PrintNode            @ /example/file.soy:6:5",
            "        ForeachIfemptyNode     @ /example/file.soy:7:3",
            "          RawTextNode          @ /example/file.soy:8:5",
            "      RawTextNode              @ /example/file.soy:10:3",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .foo autoescape=\"deprecated-noncontextual\"}", // 1
            "  Hello", // 2
            "  {foreach $planet in ['mercury', 'mars', 'venus']}", // 3
            "    ,", // 4
            "    {print $planet}", // 5
            "  {ifempty}", // 6
            "    lifeless interstellar void", // 7
            "  {/foreach}", // 8
            "  !", // 9
            "{/template}", // 10
            ""));
  }

  @Test
  public void testConditional() throws Exception {
    assertSourceLocations(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          @ /example/file.soy:2:1",
            "      RawTextNode              @ /example/file.soy:5:3",
            "      IfNode                   @ /example/file.soy:6:3",
            "        IfCondNode             @ /example/file.soy:6:3",
            "          RawTextNode          @ /example/file.soy:7:5",
            "        IfCondNode             @ /example/file.soy:8:3",
            "          RawTextNode          @ /example/file.soy:9:5",
            "        IfElseNode             @ /example/file.soy:10:3",
            "          RawTextNode          @ /example/file.soy:11:5",
            "      RawTextNode              @ /example/file.soy:13:3",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .foo autoescape=\"deprecated-noncontextual\"}", // 1
            "{@param skyIsBlue : bool}",
            "{@param isReallyReallyHot : bool}",
            "  Hello,", // 4
            "  {if $skyIsBlue}", // 5
            "    Earth", // 6
            "  {elseif $isReallyReallyHot}", // 7
            "    Venus", // 8
            "  {else}", // 9
            "    Cincinatti", // 10
            "  {/if}", // 11
            "  !", // 12
            "{/template}", // 13
            ""));
  }

  @Test
  public void testDoesntAccessPastEnd() {
    // Make sure that if we have a token stream that ends abruptly, we don't
    // look for a line number and break in a way that suppresses the real error
    // message.
    // JavaCC is pretty good about never using null as a token value.
    ErrorReporter reporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forSuppliers(
            SoyFileSupplier.Factory.create(
                "{template t autoescape=\"deprecated-noncontextual\"}\nHello, World!\n",
                SoyFileKind.SRC,
                "broken.soy"))
        .errorReporter(reporter)
        .parse();
    assertThat(reporter.getErrors()).isNotEmpty();
  }

  @Test
  public void testAdditionalSourceLocationInfo() throws Exception {
    String template =
        JOINER.join("{namespace ns}", "{template .t}", "  hello, world", "{/template}");
    TemplateNode templateNode =
        SoyFileSetParserBuilder.forFileContents(template).parse().fileSet().getChild(0).getChild(0);
    SourceLocation location = templateNode.getSourceLocation();
    // Begin at {template
    assertEquals(2, location.getBeginLine());
    assertEquals(1, location.getBeginColumn());
    // End after .t}
    assertEquals(2, location.getEndLine());
    assertEquals(13, location.getEndColumn());
  }

  @Test
  public void testIsJustBefore() throws Exception {
    String template =
        JOINER.join(
            "{namespace ns}",
            "{template .t}",
            "{@param foo : ?}",
            "{@param bar : ?}",
            "  {$foo}{$bar}", // pair 1
            "  {$foo} {$bar}", // pair 2
            "  {$foo}", // pair 3
            "  {$bar}",
            "{$foo}", // pair 4
            "{$bar}",
            "{/template}");
    TemplateNode templateNode =
        SoyFileSetParserBuilder.forFileContents(template).parse().fileSet().getChild(0).getChild(0);
    List<PrintNode> nodes = SoyTreeUtils.getAllNodesOfType(templateNode, PrintNode.class);
    assertThat(nodes).hasSize(8);

    PrintNode foo1 = nodes.get(0);
    PrintNode bar1 = nodes.get(1);
    assertTrue(foo1.getSourceLocation().isJustBefore(bar1.getSourceLocation()));

    PrintNode foo2 = nodes.get(2);
    PrintNode bar2 = nodes.get(3);
    assertFalse(foo2.getSourceLocation().isJustBefore(bar2.getSourceLocation()));

    PrintNode foo3 = nodes.get(4);
    PrintNode bar3 = nodes.get(5);
    assertFalse(foo3.getSourceLocation().isJustBefore(bar3.getSourceLocation()));

    PrintNode foo4 = nodes.get(6);
    PrintNode bar4 = nodes.get(7);
    assertFalse(foo4.getSourceLocation().isJustBefore(bar4.getSourceLocation()));
  }

  @Test
  public void testRawTextSourceLocations() throws Exception {
    // RawTextNode has some special methods to calculating the source location of characters within
    // the strings, test those
    String template =
        JOINER.join(
            "{namespace ns}",
            "{template .foo}",
            "  Hello,{sp}",
            "  {\\n}{nil}<span>Bob</span>",
            "  // and end of line comment",
            "  !",
            "  What's /*hello comment world*/up?",
            "{/template}",
            "");
    RawTextNode rawText =
        (RawTextNode)
            SoyFileSetParserBuilder.forFileContents(template)
                .parse()
                .fileSet()
                .getChild(0)
                .getChild(0)
                .getChild(0);
    assertThat(rawText.getRawText()).isEqualTo("Hello, \n<span>Bob</span>! What's up?");

    assertThat(rawText.getRawText().substring(0, 5)).isEqualTo("Hello");
    SourceLocation loc = rawText.substringLocation(0, 5);
    assertThat(loc.getBeginLine()).isEqualTo(3);
    assertThat(loc.getBeginColumn()).isEqualTo(3);
    assertThat(loc.getEndLine()).isEqualTo(3);
    assertThat(loc.getEndColumn()).isEqualTo(7);

    assertThat(rawText.getRawText().substring(8, 14)).isEqualTo("<span>");
    loc = rawText.substringLocation(8, 14);
    assertThat(loc.getBeginLine()).isEqualTo(4);
    assertThat(loc.getBeginColumn()).isEqualTo(12);
    assertThat(loc.getEndLine()).isEqualTo(4);
    assertThat(loc.getEndColumn()).isEqualTo(17);

    assertThat(rawText.getRawText().substring(24, 25)).isEqualTo("!");
    loc = rawText.substringLocation(24, 25);
    assertThat(loc.getBeginLine()).isEqualTo(6);
    assertThat(loc.getBeginColumn()).isEqualTo(3);
    assertThat(loc.getEndLine()).isEqualTo(6);
    assertThat(loc.getEndColumn()).isEqualTo(3);

    assertThat(rawText.getRawText().substring(33, 36)).isEqualTo("up?");
    loc = rawText.substringLocation(33, 36);
    assertThat(loc.getBeginLine()).isEqualTo(7);
    assertThat(loc.getBeginColumn()).isEqualTo(33);
    assertThat(loc.getEndLine()).isEqualTo(7);
    assertThat(loc.getEndColumn()).isEqualTo(35);

    final int id = 1337; // doesn't matter
    RawTextNode subStringNode = rawText.substring(id, 0, 5);
    assertThat(subStringNode.getRawText()).isEqualTo("Hello");
    loc = subStringNode.getSourceLocation();
    assertThat(loc.getBeginLine()).isEqualTo(3);
    assertThat(loc.getBeginColumn()).isEqualTo(3);
    assertThat(loc.getEndLine()).isEqualTo(3);
    assertThat(loc.getEndColumn()).isEqualTo(7);

    subStringNode = rawText.substring(id, 24, 25);
    assertThat(subStringNode.getRawText()).isEqualTo("!");
    loc = subStringNode.getSourceLocation();
    assertThat(loc.getBeginLine()).isEqualTo(6);
    assertThat(loc.getBeginColumn()).isEqualTo(3);
    assertThat(loc.getEndLine()).isEqualTo(6);
    assertThat(loc.getEndColumn()).isEqualTo(3);

    // Can't create empty raw text nodes.
    try {
      rawText.substring(id, 24, 24);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      rawText.substring(id, 24, 23);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    try {
      rawText.substring(id, 24, Integer.MAX_VALUE);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  private void assertSourceLocations(String asciiArtExpectedOutput, String soySourceCode) {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(soySourceCode, SoyFileKind.SRC, "/example/file.soy"))
            .parse()
            .fileSet();
    String actual = new AsciiArtVisitor().exec(soyTree);
    assertEquals(
        // Make the message be something copy-pasteable to make it easier to update this test when
        // fixing source locations bugs.
        "REPLACE_WITH:\n\"" + actual.replaceAll("\n", "\",\n\"") + "\"\n\n",
        asciiArtExpectedOutput,
        actual);
  }

  /** Generates a concise readable summary of a soy tree and its source locations. */
  private static class AsciiArtVisitor extends AbstractSoyNodeVisitor<String> {
    final StringBuilder sb = new StringBuilder();
    int depth;

    @Override
    public String exec(SoyNode node) {
      visit(node);
      return sb.toString();
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      // Output a header like:
      //   <indent> <node class>                    @ <location>
      // where indent is 2 spaces per level, and the @ sign is indented to the 31st column.
      for (int indent = depth; --indent >= 0; ) {
        sb.append("  ");
      }
      String typeName = node.getClass().getSimpleName();
      sb.append(typeName);
      // SoyFileSetNode and SoyFileNode don't have source locations.
      if (!(node instanceof SoyFileSetNode) && !(node instanceof SoyFileNode)) {
        int pos = typeName.length() + 2 * depth;
        while (pos < 30) {
          sb.append(' ');
          ++pos;
        }
        sb.append(" @ ").append(node.getSourceLocation());
      }
      sb.append('\n');

      if (node instanceof ParentSoyNode<?>) {
        ++depth;
        visitChildren((ParentSoyNode<?>) node);
        --depth;
      }
    }
  }
}
