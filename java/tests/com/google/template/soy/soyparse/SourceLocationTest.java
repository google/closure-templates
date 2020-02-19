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
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.template.soy.base.SourceLocation;
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
import com.google.template.soy.testing.SoyFileSetParserBuilder;
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
    assertSourceRanges(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          {template .foo}{@param wo[...]{call .bar /}{/template}",
            "      RawTextNode              Hello{lb}",
            "      PrintNode                {print $world}",
            "      RawTextNode              {rb}!",
            "      CallBasicNode            {call .bar /}",
            "    TemplateBasicNode          {template .bar}Gooodbye{/template}",
            "      RawTextNode              Gooodbye",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .foo}",
            "{@param world : ?}",
            "  Hello",
            "  {lb}",
            "  {print $world}",
            "  {rb}!",
            "",
            "  {call .bar /}",
            "{/template}",
            "{template .bar}",
            "  Gooodbye",
            "{/template}",
            ""));
  }

  @Test
  public void testTemplateCall() throws Exception {
    assertSourceRanges(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          {template .foo}{call .pla[...]am}{/delcall}{/template}",
            "      CallBasicNode            {call .planet}{param inde[...]'}Jupiter{/param}{/call}",
            "        CallParamValueNode     {param index: 5 /}",
            "        CallParamContentNode   {param name kind='text'}Jupiter{/param}",
            "          RawTextNode          Jupiter",
            "      CallDelegateNode         {delcall ns.maybePlanet}{[...]}Pluto{/param}{/delcall}",
            "        CallParamValueNode     {param index: 9 /}",
            "        CallParamContentNode   {param name kind='text'}Pluto{/param}",
            "          RawTextNode          Pluto",
            "    TemplateBasicNode          {template .planet}{@param[...]ex}: {$name}.{/template}",
            "      RawTextNode              Planet #",
            "      PrintNode                {$index}",
            "      RawTextNode              :",
            "      PrintNode                {$name}",
            "      RawTextNode              .",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .foo}",
            "  {call .planet}",
            "    {param index: 5 /}",
            "    {param name kind='text'}",
            "      Jupiter",
            "    {/param}",
            "  {/call}",
            "  {delcall ns.maybePlanet}",
            "    {param index: 9 /}",
            "    {param name kind='text'}",
            "      Pluto",
            "    {/param}",
            "  {/delcall}",
            "{/template}",
            "{template .planet}",
            "  {@param index: number}",
            "  {@param name: string}",
            "  Planet #{$index}: {$name}.",
            "{/template}",
            ""));
  }

  @Test
  public void testSwitches() throws Exception {
    assertSourceRanges(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          {template .foo}{@param i [...]ssy{/switch}!{/template}",
            "      RawTextNode              Hello,",
            "      SwitchNode               {switch $i}{case 0}Mercur[...]s{default}Gassy{/switch}",
            "        SwitchCaseNode         {case 0}Mercury",
            "          RawTextNode          Mercury",
            "        SwitchCaseNode         {case 1}Venus",
            "          RawTextNode          Venus",
            "        SwitchCaseNode         {case 2}Mars",
            "          RawTextNode          Mars",
            "        SwitchDefaultNode      {default}Gassy",
            "          RawTextNode          Gassy",
            "      RawTextNode              !",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .foo}",
            "{@param i : int}",
            "  Hello,",
            "  {switch $i}",
            "    {case 0}",
            "      Mercury",
            "    {case 1}",
            "      Venus",
            "    {case 2}",
            "      Mars",
            "    {default}",
            "      Gassy",
            "  {/switch}",
            "  !",
            "{/template}",
            ""));
  }

  @Test
  public void testVeid() throws Exception {
    assertSourceRanges(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          {template .foo}{@param ve[...]</h1>{/velog}{/template}",
            "      VeLogNode                {velog $veData}<h1>Hello</h1>{/velog}",
            "        RawTextNode            <h1>Hello</h1>",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .foo}",
            "  {@param veData: ve_data}",
            "  {velog $veData}",
            "    <h1>Hello</h1>",
            "  {/velog}",
            "{/template}",
            ""));
  }

  @Test
  public void testForLoop() throws Exception {
    assertSourceRanges(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          {template .foo}Hello{for [...]r void{/for}!{/template}",
            "      RawTextNode              Hello",
            "      ForNode                  {for $planet in ['mercury[...] interstellar void{/for}",
            "        ForNonemptyNode        {for $planet in ['mercury[...]venus']},{print $planet}",
            "          RawTextNode          ,",
            "          PrintNode            {print $planet}",
            "        ForIfemptyNode         {ifempty}lifeless interstellar void",
            "          RawTextNode          lifeless interstellar void",
            "      RawTextNode              !",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .foo}",
            "  Hello",
            "  {for $planet in ['mercury', 'mars', 'venus']}",
            "    ,",
            "    {print $planet}",
            "  {ifempty}",
            "    lifeless interstellar void",
            "  {/for}",
            "  !",
            "{/template}",
            ""));
  }

  @Test
  public void testConditional() throws Exception {
    assertSourceRanges(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          {template .foo}{@param sk[...]cinatti{/if}!{/template}",
            "      RawTextNode              Hello,",
            "      IfNode                   {if $skyIsBlue}Earth{else[...]nus{else}Cincinatti{/if}",
            "        IfCondNode             {if $skyIsBlue}Earth",
            "          RawTextNode          Earth",
            "        IfCondNode             {elseif $isReallyReallyHot}Venus",
            "          RawTextNode          Venus",
            "        IfElseNode             {else}Cincinatti",
            "          RawTextNode          Cincinatti",
            "      RawTextNode              !",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .foo}",
            "{@param skyIsBlue : bool}",
            "{@param isReallyReallyHot : bool}",
            "  Hello,",
            "  {if $skyIsBlue}",
            "    Earth",
            "  {elseif $isReallyReallyHot}",
            "    Venus",
            "  {else}",
            "    Cincinatti",
            "  {/if}",
            "  !",
            "{/template}",
            ""));
  }

  @Test
  public void testLet() throws Exception {
    assertSourceRanges(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          {template .approximateDis[...] {$formatted}{/template}",
            "      LetValueNode             {let $approx: round($distance, 2) /}",
            "      LetContentNode           {let $formatted kind='tex[...]pprox} light years{/let}",
            "        PrintNode              {$approx}",
            "        RawTextNode            light years",
            "      RawTextNode              Approximately",
            "      PrintNode                {$formatted}",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .approximateDistance}",
            "  {@param distance: number}",
            "  {let $approx: round($distance, 2) /}",
            "  {let $formatted kind='text'}",
            "    {$approx} light years",
            "  {/let}",
            "  Approximately {$formatted}",
            "{/template}",
            ""));
  }

  @Test
  public void testLiteral() throws Exception {
    assertSourceRanges(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          {template .void kind='css[...]; }{/literal}{/template}",
            "      RawTextNode              {literal}body { display: none; }{/literal}",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .void kind='css'}",
            "  {literal}",
            "    body { display: none; }",
            "  {/literal}",
            "{/template}",
            ""));
  }

  @Test
  public void testI18nNodes() throws Exception {
    assertSourceRanges(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          {template .moonCount}{@pa[...] 1 moon{/msg}{/template}",
            "      MsgFallbackGroupNode     {msg desc='Generic messag[...]t Earth has 1 moon{/msg}",
            "        MsgNode                {msg desc='Generic messag[...] {$count} moons{/plural}",
            "          MsgPluralNode        {plural $count}{case 0}Pl[...] {$count} moons{/plural}",
            "            MsgPluralCaseNode  {case 0}Planet {$planet} has no moons",
            "              RawTextNode      Planet",
            "              MsgPlaceholderNode {$planet}",
            "                PrintNode      {$planet}",
            "              RawTextNode      has no moons",
            "            MsgPluralCaseNode  {case 1}Planet {$planet} has 1 moon",
            "              RawTextNode      Planet",
            "              MsgPlaceholderNode {$planet}",
            "                PrintNode      {$planet}",
            "              RawTextNode      has 1 moon",
            "            MsgPluralDefaultNode {default}Planet {$planet} has {$count} moons",
            "              RawTextNode      Planet",
            "              MsgPlaceholderNode {$planet}",
            "                PrintNode      {$planet}",
            "              RawTextNode      has",
            "              MsgPlaceholderNode {$count}",
            "                PrintNode      {$count}",
            "              RawTextNode      moons",
            "        MsgNode                {fallbackmsg desc='Specif[...]}Planet Earth has 1 moon",
            "          RawTextNode          Planet Earth has 1 moon",
            "    TemplateBasicNode          {template .moonName}{@par[...]select}{/msg}{/template}",
            "      MsgFallbackGroupNode     {msg desc='The name of a [...]t}{$moon}{/select}{/msg}",
            "        MsgNode                {msg desc='The name of a [...]default}{$moon}{/select}",
            "          MsgSelectNode        {select $moon}{case 'Luna[...]default}{$moon}{/select}",
            "            MsgSelectCaseNode  {case 'Luna'}Earth's moon",
            "              RawTextNode      Earth's moon",
            "            MsgSelectDefaultNode {default}{$moon}",
            "              MsgPlaceholderNode {$moon}",
            "                PrintNode      {$moon}",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .moonCount}",
            "  {@param planet: string}",
            "  {@param count: int}",
            "  {msg desc='Generic message about the amount of moons'}",
            "    {plural $count}",
            "      {case 0}Planet {$planet} has no moons",
            "      {case 1}Planet {$planet} has 1 moon",
            "      {default}Planet {$planet} has {$count} moons",
            "    {/plural}",
            "  {fallbackmsg desc='Specific message about Earth'}",
            "    Planet Earth has 1 moon",
            "  {/msg}",
            "{/template}",
            "",
            "{template .moonName}",
            "  {@param moon: string}",
            "  {msg desc='The name of a moon of the solar system'}",
            "    {select $moon}",
            "      {case 'Luna'}",
            "        Earth's moon",
            "      {default}",
            "        {$moon}",
            "    {/select}",
            "  {/msg}",
            "{/template}",
            ""));
  }

  @Test
  public void testTrailingCommentsInNonClosingNodes() {
    assertSourceRanges(
        JOINER.join(
            "SoyFileSetNode",
            "  SoyFileNode",
            "    TemplateBasicNode          {template .foo}{@param fo[...]comment{/msg}{/template}",
            "      ForNode                  {for $foo in $foolist}{if[...]ld include this...{/for}",
            "        ForNonemptyNode        {for $foo in $foolist}{if[...]uld include this comm...",
            "          IfNode               {if $foo == 'a'}a // TODO[...] include this co...{/if}",
            "            IfCondNode         {if $foo == 'a'}a",
            "              RawTextNode      a",
            "            IfCondNode         {elseif $foo == 'b'}b",
            "              RawTextNode      b",
            "            IfElseNode         {else}{switch $foo}{case [...] include...{/switch}text",
            "              SwitchNode       {switch $foo}{case 'c'}c [...]ould include...{/switch}",
            "                SwitchCaseNode {case 'c'}c",
            "                  RawTextNode  c",
            "                SwitchDefaultNode {default}d",
            "                  RawTextNode  d",
            "              RawTextNode      text",
            "        ForIfemptyNode         {ifempty}empty",
            "          RawTextNode          empty",
            "      MsgFallbackGroupNode     {msg desc='bar'}bar // TO[...]clude this comment{/msg}",
            "        MsgNode                {msg desc='bar'}bar",
            "          RawTextNode          bar",
            "        MsgNode                {fallbackmsg desc='baz'}{[...]tNode should...{/plural}",
            "          MsgPluralNode        {plural length($foolist)}[...]tNode should...{/plural}",
            "            MsgPluralCaseNode  {case 0}0",
            "              RawTextNode      0",
            "            MsgPluralDefaultNode {default}n",
            "              RawTextNode      n",
            "      MsgFallbackGroupNode     {msg desc='baz'}{select $[...]clude this comment{/msg}",
            "        MsgNode                {msg desc='baz'}{select $[...]ultNode shou...{/select}",
            "          MsgSelectNode        {select $foolist[0]}{case[...]ultNode shou...{/select}",
            "            MsgSelectCaseNode  {case 'foo'}foo",
            "              RawTextNode      foo",
            "            MsgSelectDefaultNode {default}baz",
            "              RawTextNode      baz",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .foo}",
            "{@param foolist: list<string>}",
            "  {for $foo in $foolist}",
            "    {if $foo == 'a'}",
            "      a // TODO(b/147886598): Location of the IfCondNode should include this comment",
            "    {elseif $foo == 'b'}",
            "      b // TODO(b/147886598): Location of the IfCondNode should include this comment",
            "    {else}",
            "      {switch $foo}",
            "        {case 'c'}",
            "          c // TODO(b/147886598): Location of the SwitchCaseNode should include th...",
            "        {default}",
            "          d // TODO(b/147886598): Location of the SwitchDefaultNode should include...",
            "      {/switch}",
            "      text // TODO(b/147886598): Location of the IfElseNode should include this co...",
            "    {/if}",
            "    // TODO(b/147886598): Location of the ForNonemptyNode should include this comm...",
            "  {ifempty}",
            "    empty // TODO(b/147886598): Location of the ForIfemptyNode should include this...",
            "  {/for}",
            "  {msg desc='bar'}",
            "    bar // TODO(b/147886598): Location of the MsgNode should include this comment",
            "  {fallbackmsg desc='baz'}",
            "    {plural length($foolist)}",
            "      {case 0}0 // TODO(b/147886598): Location of the MsgPluralCaseNode should inc...",
            "      {default}n // TODO(b/147886598): Location of the MsgPluralDefaultNode should...",
            "    {/plural}",
            "    // TODO(b/147886598): Location of the MsgNode should include this comment",
            "  {/msg}",
            "  {msg desc='baz'}",
            "    {select $foolist[0]}",
            "      {case 'foo'}foo // TODO(b/147886598): Location of the MsgSelectCaseNode shou...",
            "      {default}baz // TODO(b/147886598): Location of the MsgSelectDefaultNode shou...",
            "    {/select}",
            "    // TODO(b/147886598): Location of the SelectNode should include this comment",
            "  {/msg}",
            "{/template}",
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
            SoyFileSupplier.Factory.create("{template t}\nHello, World!\n", "broken.soy"))
        .errorReporter(reporter)
        .parse();
    assertThat(reporter.getErrors()).isNotEmpty();
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

    // Check location of {sp} command character.
    assertThat(rawText.getRawText().substring(6, 7)).isEqualTo(" ");
    loc = rawText.substringLocation(6, 7);
    assertThat(loc.getBeginLine()).isEqualTo(3);
    assertThat(loc.getBeginColumn()).isEqualTo(9);

    // Check location of {\n} command character.
    assertThat(rawText.getRawText().substring(7, 8)).isEqualTo("\n");
    loc = rawText.substringLocation(7, 8);
    assertThat(loc.getBeginLine()).isEqualTo(4);
    assertThat(loc.getBeginColumn()).isEqualTo(3);

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

  private static void assertSourceRanges(String asciiArtExpectedOutput, String soySourceCode) {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(soySourceCode, "/example/file.soy"))
            .parse()
            .fileSet();

    assertThat(soyTree.numChildren()).isEqualTo(1);
    SoyFileNode soyFile = soyTree.getChild(0);
    assertThat(soyFile.numChildren()).isGreaterThan(0);
    // Verify that the filename is correctly stored in the SourceLocation of each node.
    for (TemplateNode templateNode : soyFile.getChildren()) {
      for (SoyNode node : SoyTreeUtils.getAllNodesOfType(templateNode, SoyNode.class)) {
        assertWithMessage("Wrong file path for node %s", node)
            .that(node.getSourceLocation().getFilePath())
            .isEqualTo("/example/file.soy");
      }
    }

    String actual = new AsciiArtVisitor(soySourceCode).exec(soyTree);
    assertEquals(
        // Make the message be something copy-pasteable to make it easier to update this test when
        // fixing source locations bugs.
        "REPLACE_WITH:\n\"" + actual.replaceAll("\n", "\",\n\"") + "\"\n\n",
        asciiArtExpectedOutput,
        actual);
  }

  /** Generates a concise readable summary of a soy tree and its source locations. */
  private static class AsciiArtVisitor extends AbstractSoyNodeVisitor<String> {
    private final String[] soySourceCode;
    final StringBuilder sb = new StringBuilder();
    int depth;

    public AsciiArtVisitor(String soySourceCode) {
      this.soySourceCode = soySourceCode.split("\n");
    }

    @Override
    public String exec(SoyNode node) {
      visit(node);
      return sb.toString();
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      // Output a header like:
      //   <indent> <node class>                    {code fragment}
      // or
      //   <indent> <node class>                    @ <location>
      // where indent is 2 spaces per level, and the @ sign is indented to the 31st column
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
        sb.append(' ');
        StringBuilder codeFragment = getCodeFragment(node.getSourceLocation());
        if (codeFragment.length() == 0) {
          sb.append("@ ").append(node.getSourceLocation());
        } else {
          sb.append(codeFragment);
        }
      }
      sb.append('\n');

      if (node instanceof ParentSoyNode<?>) {
        ++depth;
        visitChildren((ParentSoyNode<?>) node);
        --depth;
      }
    }

    private StringBuilder getCodeFragment(SourceLocation location) {
      if (location.getBeginLine() == location.getEndLine()) {
        String line = this.soySourceCode[location.getBeginLine() - 1];
        return new StringBuilder(
            line.substring(location.getBeginColumn() - 1, location.getEndColumn()).trim());
      }
      StringBuilder sb = new StringBuilder();
      sb.append(
          this.soySourceCode[location.getBeginLine() - 1]
              .substring(location.getBeginColumn() - 1)
              .trim());
      for (int i = location.getBeginLine() + 1; i < location.getEndLine(); i++) {
        sb.append(this.soySourceCode[i - 1].trim());
      }
      sb.append(
          this.soySourceCode[location.getEndLine() - 1]
              .substring(0, location.getEndColumn())
              .trim());
      if (sb.length() > 54) {
        // Add an ellipsis to bring the fragment to a length of 54.
        return sb.replace(25, sb.length() - 24, "[...]");
      }
      return sb;
    }
  }
}
