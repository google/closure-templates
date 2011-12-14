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

import com.google.common.base.Joiner;
import com.google.template.soy.base.SoyFileSupplier;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

import junit.framework.TestCase;

/**
 * Tests that the Soy file and template parsers properly embed source locations.
 */
public class SourceLocationTest extends TestCase {
  public final void testLocationsInParsedContent() throws Exception {
    assertSourceLocations(
        Joiner.on('\n').join(
            "SoyFileSetNode                 @ unknown",
            "  SoyFileNode                  @ /example/file.soy",
            "    TemplateBasicNode          @ /example/file.soy:1",
            "      RawTextNode              @ /example/file.soy:2",
            "      PrintNode                @ /example/file.soy:4",
            "      RawTextNode              @ /example/file.soy:5",
            "      CallBasicNode            @ /example/file.soy:7",
            "    TemplateBasicNode          @ /example/file.soy:9",
            "      RawTextNode              @ /example/file.soy:10",
            ""),
        "/example/file.soy",
        Joiner.on('\n').join(
            "{template foo}",     // 1
            "  Hello",            // 2
            "  {lb}",             // 3
            "  {print $world}",   // 4
            "  {rb}!",            // 5
            "",                   // 6
            "  {call bar /}",     // 7
            "{/template}",        // 8
            "{template bar}",     // 9
            "  Gooodbye",         // 10
            "{/template}"         // 11
            )
        );
  }

  public final void testSwitches() throws Exception {
    assertSourceLocations(
        Joiner.on('\n').join(
            "SoyFileSetNode                 @ unknown",
            "  SoyFileNode                  @ /example/file.soy",
            "    TemplateBasicNode          @ /example/file.soy:1",
            "      RawTextNode              @ /example/file.soy:2",
            "      SwitchNode               @ /example/file.soy:3",
            "        SwitchCaseNode         @ /example/file.soy:4",
            "          RawTextNode          @ /example/file.soy:5",
            "        SwitchCaseNode         @ /example/file.soy:6",
            "          RawTextNode          @ /example/file.soy:7",
            "        SwitchCaseNode         @ /example/file.soy:8",
            "          RawTextNode          @ /example/file.soy:9",
            "        SwitchDefaultNode      @ /example/file.soy:10",
            "          RawTextNode          @ /example/file.soy:11",
            "      RawTextNode              @ /example/file.soy:13",
            ""),
        "/example/file.soy",
        Joiner.on('\n').join(
            "{template foo}",  // 1
            "  Hello,",        // 2
            "  {switch $i}",   // 3
            "    {case 0}",    // 4
            "      Mercury",   // 5
            "    {case 1}",    // 6
            "      Venus",     // 7
            "    {case 2}",    // 8
            "      Mars",      // 9
            "    {default}",   // 10
            "      Gassy",     // 11
            "  {/switch}",     // 12
            "  !",             // 13
            "{/template}",     // 14
            "")
        );
  }

  public final void testForLoop() throws Exception {
    assertSourceLocations(
        Joiner.on('\n').join(
            "SoyFileSetNode                 @ unknown",
            "  SoyFileNode                  @ /example/file.soy",
            "    TemplateBasicNode          @ /example/file.soy:1",
            "      RawTextNode              @ /example/file.soy:2",
            "      ForNode                  @ /example/file.soy:3",
            "        RawTextNode            @ /example/file.soy:4",
            "        PrintNode              @ /example/file.soy:5",
            "      RawTextNode              @ /example/file.soy:7",
            ""),
        "/example/file.soy",
        Joiner.on('\n').join(
            "{template foo}",                   // 1
            "  Hello",                          // 2
            "  {for $i in range($s, $e, $t)}",  // 3
            "    ,",                            // 4
            "    {print $planet[$i]}",          // 5
            "  {/for}",                         // 6
            "  !",                              // 7
            "{/template}",                      // 8
            "")
        );
  }

  public final void testForeachLoop() throws Exception {
    assertSourceLocations(
        Joiner.on('\n').join(
            "SoyFileSetNode                 @ unknown",
            "  SoyFileNode                  @ /example/file.soy",
            "    TemplateBasicNode          @ /example/file.soy:1",
            "      RawTextNode              @ /example/file.soy:2",
            "      ForeachNode              @ /example/file.soy:3",
            "        ForeachNonemptyNode    @ /example/file.soy:3",
            "          RawTextNode          @ /example/file.soy:4",
            "          PrintNode            @ /example/file.soy:5",
            "        ForeachIfemptyNode     @ /example/file.soy:6",
            "          RawTextNode          @ /example/file.soy:7",
            "      RawTextNode              @ /example/file.soy:9",
            ""),
        "/example/file.soy",
        Joiner.on('\n').join(
            "{template foo}",                   // 1
            "  Hello",                          // 2
            "  {foreach $planet in $planets}",  // 3
            "    ,",                            // 4
            "    {print $planet[$i]}",          // 5
            "  {ifempty}",                      // 6
            "    lifeless interstellar void",   // 7
            "  {/foreach}",                     // 8
            "  !",                              // 9
            "{/template}",                      // 10
            "")
        );
  }

  public final void testConditional() throws Exception {
    assertSourceLocations(
        Joiner.on('\n').join(
            "SoyFileSetNode                 @ unknown",
            "  SoyFileNode                  @ /example/file.soy",
            "    TemplateBasicNode          @ /example/file.soy:1",
            "      RawTextNode              @ /example/file.soy:2",
            "      IfNode                   @ /example/file.soy:3",
            "        IfCondNode             @ /example/file.soy:3",
            "          RawTextNode          @ /example/file.soy:4",
            "        IfCondNode             @ /example/file.soy:5",
            "          RawTextNode          @ /example/file.soy:6",
            "        IfElseNode             @ /example/file.soy:7",
            "          RawTextNode          @ /example/file.soy:8",
            "      RawTextNode              @ /example/file.soy:10",
            ""),
        "/example/file.soy",
        Joiner.on('\n').join(
            "{template foo}",                 // 1
            "  Hello,",                       // 2
            "  {if $skyIsBlue}",              // 3
            "    Earth",                      // 4
            "  {elseif $isReallyReallyHot}",  // 5
            "    Venus",                      // 6
            "  {else}",                       // 7
            "    Cincinatti",                 // 8
            "  {/if}",                        // 9
            "  !",                            // 10
            "{/template}",                    // 11
            "")
        );
  }

  public final void testDoesntAccessPastEnd() {
    // Make sure that if we have a token stream that ends abruptly, we don't
    // look for a line number and break in a way that suppresses the real error
    // message.
    // JavaCC is pretty good about never using null as a token value.
    try {
      (new SoyFileSetParser(
          SoyFileSupplier.Factory.create("{template t}\nHello, World!\n", "borken.soy")))
          .setDoRunInitialParsingPasses(false)
          .parse();
    } catch (SoySyntaxException ex) {
      // OK
      return;
    }
  }


  void assertSourceLocations(
      String asciiArtExpectedOutput, String soySourcePath, String soySourceCode)
      throws Exception {

    SoyFileSetNode soyTree =
        (new SoyFileSetParser(SoyFileSupplier.Factory.create(soySourceCode, soySourcePath)))
            .setDoRunInitialParsingPasses(false)
            .parse();

    String actual = new AsciiArtVisitor().exec(soyTree);
    assertEquals(asciiArtExpectedOutput, actual);
  }


  /**
   * Generates a concise readable summary of a soy tree and its source locations.
   */
  static class AsciiArtVisitor extends AbstractSoyNodeVisitor<String> {
    final StringBuilder sb = new StringBuilder();
    int depth;

    @Override public String exec(SoyNode node) {
      visit(node);
      return sb.toString();
    }

    @Override protected void visitSoyNode(SoyNode node) {
      // Output a header like:
      //   <indent> <node class>                    @ <location>
      // where indent is 2 spaces per level, and the @ sign is indented to the 31st column.
      for (int indent = depth; --indent >= 0;) {
        sb.append("  ");
      }
      String typeName = node.getClass().getSimpleName();
      sb.append(typeName);
      int pos = typeName.length() + 2 * depth;
      while (pos < 30) {
        sb.append(' ');
        ++pos;
      }
      sb.append(" @ ").append(node.getLocation()).append('\n');

      if (node instanceof ParentSoyNode<?>) {
        ++depth;
        visitChildren((ParentSoyNode<?>) node);
        --depth;
      }
    }
  }
}
