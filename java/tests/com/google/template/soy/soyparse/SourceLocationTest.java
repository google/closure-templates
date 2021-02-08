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
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.Point;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.io.StringReader;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that the Soy file and template parsers properly embed source locations. */
@RunWith(JUnit4.class)
public final class SourceLocationTest {

  private static final Joiner JOINER = Joiner.on('\n');
  private static final SourceFilePath FAKE_FILE_PATH = SourceFilePath.create("fakefile.soy");

  @Test
  public void testLocationsInParsedContent() throws Exception {
    assertSourceRanges(
        JOINER.join(
            "SoyFileNode",
            "  TemplateBasicNode            {template .foo}{@param wo[...]{call .bar /}{/template}",
            "    RawTextNode                Hello",
            "    RawTextNode                {lb}",
            "    PrintNode                  {print $world}",
            "      ExprRootNode             $world",
            "        VarRefNode             $world",
            "    RawTextNode                {rb}",
            "    RawTextNode                !",
            "    CallBasicNode              {call .bar /}",
            "      ExprRootNode             .bar",
            "        TemplateLiteralNode    .bar",
            "          VarRefNode           .bar",
            "  TemplateBasicNode            {template .bar}Gooodbye{/template}",
            "    RawTextNode                Gooodbye",
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
            "SoyFileNode",
            "  TemplateBasicNode            {template .foo}{call .pla[...]am}{/delcall}{/template}",
            "    CallBasicNode              {call .planet}{param inde[...]'}Jupiter{/param}{/call}",
            "      ExprRootNode             .planet",
            "        TemplateLiteralNode    .planet",
            "          VarRefNode           .planet",
            "      CallParamValueNode       {param index: 5 /}",
            "        ExprRootNode           5",
            "          IntegerNode          5",
            "      CallParamContentNode     {param name kind='text'}Jupiter{/param}",
            "        RawTextNode            Jupiter",
            "    CallDelegateNode           {delcall ns.maybePlanet}{[...]}Pluto{/param}{/delcall}",
            "      CallParamValueNode       {param index: 9 /}",
            "        ExprRootNode           9",
            "          IntegerNode          9",
            "      CallParamContentNode     {param name kind='text'}Pluto{/param}",
            "        RawTextNode            Pluto",
            "  TemplateBasicNode            {template .planet}{@param[...]ex}: {$name}.{/template}",
            "    RawTextNode                Planet #",
            "    PrintNode                  {$index}",
            "      ExprRootNode             $index",
            "        VarRefNode             $index",
            "    RawTextNode                :",
            "    PrintNode                  {$name}",
            "      ExprRootNode             $name",
            "        VarRefNode             $name",
            "    RawTextNode                .",
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
            "SoyFileNode",
            "  TemplateBasicNode            {template .foo}{@param i [...]ssy{/switch}!{/template}",
            "    RawTextNode                Hello,",
            "    SwitchNode                 {switch $i}{case 0}Mercur[...]s{default}Gassy{/switch}",
            "      ExprRootNode             $i",
            "        VarRefNode             $i",
            "      SwitchCaseNode           {case 0}Mercury",
            "        ExprRootNode           0",
            "          IntegerNode          0",
            "        RawTextNode            Mercury",
            "      SwitchCaseNode           {case 1}Venus",
            "        ExprRootNode           1",
            "          IntegerNode          1",
            "        RawTextNode            Venus",
            "      SwitchCaseNode           {case 2}Mars",
            "        ExprRootNode           2",
            "          IntegerNode          2",
            "        RawTextNode            Mars",
            "      SwitchDefaultNode        {default}Gassy",
            "        RawTextNode            Gassy",
            "    RawTextNode                !",
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
            "SoyFileNode",
            "  TemplateBasicNode            {template .foo}{@param ve[...]</h1>{/velog}{/template}",
            "    VeLogNode                  {velog $veData}<h1>Hello</h1>{/velog}",
            "      ExprRootNode             $veData",
            "        VarRefNode             $veData",
            "      HtmlOpenTagNode          <h1>",
            "        RawTextNode            h1",
            "      RawTextNode              Hello",
            "      HtmlCloseTagNode         </h1>",
            "        RawTextNode            h1",
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
            "SoyFileNode",
            "  TemplateBasicNode            {template .foo}Hello{for [...]r void{/for}!{/template}",
            "    RawTextNode                Hello",
            "    ForNode                    {for $planet in ['mercury[...] interstellar void{/for}",
            "      ExprRootNode             ['mercury', 'mars', 'venus']",
            "        ListLiteralNode        ['mercury', 'mars', 'venus']",
            "          StringNode           'mercury'",
            "          StringNode           'mars'",
            "          StringNode           'venus'",
            "      ForNonemptyNode          {for $planet in ['mercury[...]venus']},{print $planet}",
            "        RawTextNode            ,",
            "        PrintNode              {print $planet}",
            "          ExprRootNode         $planet",
            "            VarRefNode         $planet",
            "      ForIfemptyNode           {ifempty}lifeless interstellar void",
            "        RawTextNode            lifeless interstellar void",
            "    RawTextNode                !",
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
            "SoyFileNode",
            "  TemplateBasicNode            {template .foo}{@param sk[...]cinatti{/if}!{/template}",
            "    RawTextNode                Hello,",
            "    IfNode                     {if $skyIsBlue}Earth{else[...]nus{else}Cincinatti{/if}",
            "      IfCondNode               {if $skyIsBlue}Earth",
            "        ExprRootNode           $skyIsBlue",
            "          VarRefNode           $skyIsBlue",
            "        RawTextNode            Earth",
            "      IfCondNode               {elseif $isReallyReallyHot}Venus",
            "        ExprRootNode           $isReallyReallyHot",
            "          VarRefNode           $isReallyReallyHot",
            "        RawTextNode            Venus",
            "      IfElseNode               {else}Cincinatti",
            "        RawTextNode            Cincinatti",
            "    RawTextNode                !",
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
            "SoyFileNode",
            "  TemplateBasicNode            {template .approximateDis[...] {$formatted}{/template}",
            "    LetValueNode               {let $approx: round($distance, 2) /}",
            "      ExprRootNode             round($distance, 2)",
            "        FunctionNode           round($distance, 2)",
            "          VarRefNode           $distance",
            "          IntegerNode          2",
            "    LetContentNode             {let $formatted kind='tex[...]pprox} light years{/let}",
            "      PrintNode                {$approx}",
            "        ExprRootNode           $approx",
            "          VarRefNode           $approx",
            "      RawTextNode              light years",
            "    RawTextNode                Approximately",
            "    PrintNode                  {$formatted}",
            "      ExprRootNode             $formatted",
            "        VarRefNode             $formatted",
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
            "SoyFileNode",
            "  TemplateBasicNode            {template .void kind='css[...]; }{/literal}{/template}",
            "    RawTextNode                {literal}body { display: none; }{/literal}",
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
            "SoyFileNode",
            "  TemplateBasicNode            {template .moonCount}{@pa[...] 1 moon{/msg}{/template}",
            "    MsgFallbackGroupNode       {msg desc='Generic messag[...]t Earth has 1 moon{/msg}",
            "      MsgNode                  {msg desc='Generic messag[...] {$count} moons{/plural}",
            "        MsgPluralNode          {plural $count}{case 0}Pl[...] {$count} moons{/plural}",
            "          ExprRootNode         $count",
            "            VarRefNode         $count",
            "          MsgPluralCaseNode    {case 0}Planet {$planet} has no moons",
            "            RawTextNode        Planet",
            "            PrintNode          {$planet}",
            "              ExprRootNode     $planet",
            "                VarRefNode     $planet",
            "            RawTextNode        has no moons",
            "          MsgPluralCaseNode    {case 1}Planet {$planet} has 1 moon",
            "            RawTextNode        Planet",
            "            PrintNode          {$planet}",
            "              ExprRootNode     $planet",
            "                VarRefNode     $planet",
            "            RawTextNode        has 1 moon",
            "          MsgPluralDefaultNode {default}Planet {$planet} has {$count} moons",
            "            RawTextNode        Planet",
            "            PrintNode          {$planet}",
            "              ExprRootNode     $planet",
            "                VarRefNode     $planet",
            "            RawTextNode        has",
            "            PrintNode          {$count}",
            "              ExprRootNode     $count",
            "                VarRefNode     $count",
            "            RawTextNode        moons",
            "      MsgNode                  {fallbackmsg desc='Specif[...]}Planet Earth has 1 moon",
            "        RawTextNode            Planet Earth has 1 moon",
            "  TemplateBasicNode            {template .moonName}{@par[...]select}{/msg}{/template}",
            "    MsgFallbackGroupNode       {msg desc='The name of a [...]t}{$moon}{/select}{/msg}",
            "      MsgNode                  {msg desc='The name of a [...]default}{$moon}{/select}",
            "        MsgSelectNode          {select $moon}{case 'Luna[...]default}{$moon}{/select}",
            "          ExprRootNode         $moon",
            "            VarRefNode         $moon",
            "          MsgSelectCaseNode    {case 'Luna'}Earth's moon",
            "            RawTextNode        Earth's moon",
            "          MsgSelectDefaultNode {default}{$moon}",
            "            PrintNode          {$moon}",
            "              ExprRootNode     $moon",
            "                VarRefNode     $moon",
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
  public void testExpressions() throws Exception {
    assertSourceRanges(
        JOINER.join(
            "SoyFileNode",
            "  TemplateBasicNode            {template .math}{@param a[...]r.a.b?.c?[0]}{/template}",
            "    ExprRootNode               [1, 2, 3*4]",
            "      ListLiteralNode          [1, 2, 3*4]",
            "        IntegerNode            1",
            "        IntegerNode            2",
            "        TimesOpNode            3*4",
            "          IntegerNode          3",
            "          IntegerNode          4",
            "    PrintNode                  {$a + $b + $c}",
            "      ExprRootNode             $a + $b + $c",
            "        PlusOpNode             $a + $b + $c",
            "          PlusOpNode           $a + $b",
            "            VarRefNode         $a",
            "            VarRefNode         $b",
            "          VarRefNode           $c",
            "    PrintNode                  {$a + -$b}",
            "      ExprRootNode             $a + -$b",
            "        PlusOpNode             $a + -$b",
            "          VarRefNode           $a",
            "          NegativeOpNode       -$b",
            "            VarRefNode         $b",
            "    PrintNode                  {$a / $b % $c}",
            "      ExprRootNode             $a / $b % $c",
            "        ModOpNode              $a / $b % $c",
            "          DivideByOpNode       $a / $b",
            "            VarRefNode         $a",
            "            VarRefNode         $b",
            "          VarRefNode           $c",
            "    PrintNode                  {$a / ($b % $c)}",
            "      ExprRootNode             $a / ($b % $c)",
            "        DivideByOpNode         $a / ($b % $c)",
            "          VarRefNode           $a",
            "          GroupNode            ($b % $c)",
            "            ModOpNode          $b % $c",
            "              VarRefNode       $b",
            "              VarRefNode       $c",
            "    PrintNode                  {($a / $b) % $c}",
            "      ExprRootNode             ($a / $b) % $c",
            "        ModOpNode              ($a / $b) % $c",
            "          GroupNode            ($a / $b)",
            "            DivideByOpNode     $a / $b",
            "              VarRefNode       $a",
            "              VarRefNode       $b",
            "          VarRefNode           $c",
            "    PrintNode                  {$l[$a * $b] < $c and not[...]?: $b ? length($l) : $c}",
            "      ExprRootNode             $l[$a * $b] < $c and not [...] ?: $b ? length($l) : $c",
            "        NullCoalescingOpNode   $l[$a * $b] < $c and not [...] ?: $b ? length($l) : $c",
            "          AndOpNode            $l[$a * $b] < $c and not $l?[$c]",
            "            LessThanOpNode     $l[$a * $b] < $c",
            "              ItemAccessNode   $l[$a * $b]",
            "                VarRefNode     $l",
            "                TimesOpNode    $a * $b",
            "                  VarRefNode   $a",
            "                  VarRefNode   $b",
            "              VarRefNode       $c",
            "            NotOpNode          not $l?[$c]",
            "              ItemAccessNode   $l?[$c]",
            "                VarRefNode     $l",
            "                VarRefNode     $c",
            "          ConditionalOpNode    $b ? length($l) : $c",
            "            VarRefNode         $b",
            "            FunctionNode       length($l)",
            "              VarRefNode       $l",
            "            VarRefNode         $c",
            "    PrintNode                  {$r.a.b.c}",
            "      ExprRootNode             $r.a.b.c",
            "        FieldAccessNode        $r.a.b.c",
            "          FieldAccessNode      $r.a.b",
            "            FieldAccessNode    $r.a",
            "              VarRefNode       $r",
            "    PrintNode                  {$r?.a.b.c}",
            "      ExprRootNode             $r?.a.b.c",
            "        FieldAccessNode        $r?.a.b.c",
            "          FieldAccessNode      $r?.a.b",
            "            FieldAccessNode    $r?.a",
            "              VarRefNode       $r",
            "    PrintNode                  {$r.a.b?.c}",
            "      ExprRootNode             $r.a.b?.c",
            "        FieldAccessNode        $r.a.b?.c",
            "          FieldAccessNode      $r.a.b",
            "            FieldAccessNode    $r.a",
            "              VarRefNode       $r",
            "    PrintNode                  {$r?.a.b?.c}",
            "      ExprRootNode             $r?.a.b?.c",
            "        FieldAccessNode        $r?.a.b?.c",
            "          FieldAccessNode      $r?.a.b",
            "            FieldAccessNode    $r?.a",
            "              VarRefNode       $r",
            "    PrintNode                  {$r.a.b.c?[0]}",
            "      ExprRootNode             $r.a.b.c?[0]",
            "        ItemAccessNode         $r.a.b.c?[0]",
            "          FieldAccessNode      $r.a.b.c",
            "            FieldAccessNode    $r.a.b",
            "              FieldAccessNode  $r.a",
            "                VarRefNode     $r",
            "          IntegerNode          0",
            "    PrintNode                  {$r.a.b?.c?[0]}",
            "      ExprRootNode             $r.a.b?.c?[0]",
            "        ItemAccessNode         $r.a.b?.c?[0]",
            "          FieldAccessNode      $r.a.b?.c",
            "            FieldAccessNode    $r.a.b",
            "              FieldAccessNode  $r.a",
            "                VarRefNode     $r",
            "          IntegerNode          0",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .math}",
            "  {@param a: int}",
            "  {@param b: int}",
            "  {@param c: int}",
            "  {@param l: list<number> = [1, 2, 3*4]}",
            "  {@param? r: [a: null|[b: null|[c: null|list<null|string>]]]}",
            "  {$a + $b + $c}",
            "  {$a + -$b}",
            "  {$a / $b % $c}",
            "  {$a / ($b % $c)}",
            "  {($a / $b) % $c}",
            "  {$l[$a * $b] < $c and not $l?[$c] ?: $b ? length($l) : $c}",
            "  {$r.a.b.c}",
            "  {$r?.a.b.c}",
            "  {$r.a.b?.c}",
            "  {$r?.a.b?.c}",
            "  {$r.a.b.c?[0]}",
            "  {$r.a.b?.c?[0]}",
            "{/template}",
            ""));
  }

  @Test
  public void testHtmlComment() {
    assertSourceRanges(
        JOINER.join(
            "SoyFileNode",
            "  TemplateBasicNode            {template .foo}<!-- some [...]-><div></div>{/template}",
            "    HtmlCommentNode            <!-- some html comment -->",
            "      RawTextNode              some html comment",
            "    HtmlOpenTagNode            <div>",
            "      RawTextNode              div",
            "    HtmlCloseTagNode           </div>",
            "      RawTextNode              div",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .foo}",
            "  <!-- some html comment -->",
            "  <div></div>",
            "{/template}",
            ""));
  }

  @Test
  public void testTrailingCommentsInNonClosingNodes() {
    assertSourceRanges(
        JOINER.join(
            "SoyFileNode",
            "  TemplateBasicNode            {template .foo}{@param fo[...]comment{/msg}{/template}",
            "    ForNode                    {for $foo in $foolist}{if[...]ld include this...{/for}",
            "      ExprRootNode             $foolist",
            "        VarRefNode             $foolist",
            "      ForNonemptyNode          {for $foo in $foolist}{if[...]ude this co...{/if}// C3",
            "        IfNode                 {if $foo == 'a'}a // C1{e[...] include this co...{/if}",
            "          IfCondNode           {if $foo == 'a'}a // C1",
            "            ExprRootNode       $foo == 'a'",
            "              EqualOpNode      $foo == 'a'",
            "                VarRefNode     $foo",
            "                StringNode     'a'",
            "            RawTextNode        a",
            "          IfCondNode           {elseif $foo == 'b'}b // C2",
            "            ExprRootNode       $foo == 'b'",
            "              EqualOpNode      $foo == 'b'",
            "                VarRefNode     $foo",
            "                StringNode     'b'",
            "            RawTextNode        b",
            "          IfElseNode           {else}{switch $foo}{case [...]hould include this co...",
            "            SwitchNode         {switch $foo}{case 'c'}c [...]}d // Comment D{/switch}",
            "              ExprRootNode     $foo",
            "                VarRefNode     $foo",
            "              SwitchCaseNode   {case 'c'}c // Comment C",
            "                ExprRootNode   'c'",
            "                  StringNode   'c'",
            "                RawTextNode    c",
            "              SwitchDefault... {default}d // Comment D",
            "                RawTextNode    d",
            "            RawTextNode        text",
            "      ForIfemptyNode           {ifempty}empty",
            "        RawTextNode            empty",
            "    MsgFallbackGroupNode       {msg desc='bar'}bar // TO[...]clude this comment{/msg}",
            "      MsgNode                  {msg desc='bar'}bar",
            "        RawTextNode            bar",
            "      MsgNode                  {fallbackmsg desc='baz'}{[...]tNode should...{/plural}",
            "        MsgPluralNode          {plural length($foolist)}[...]tNode should...{/plural}",
            "          ExprRootNode         length($foolist)",
            "            FunctionNode       length($foolist)",
            "              VarRefNode       $foolist",
            "          MsgPluralCaseNode    {case 0}0",
            "            RawTextNode        0",
            "          MsgPluralDefaultNode {default}n",
            "            RawTextNode        n",
            "    MsgFallbackGroupNode       {msg desc='baz'}{select $[...]clude this comment{/msg}",
            "      MsgNode                  {msg desc='baz'}{select $[...]ultNode shou...{/select}",
            "        MsgSelectNode          {select $foolist[0]}{case[...]ultNode shou...{/select}",
            "          ExprRootNode         $foolist[0]",
            "            ItemAccessNode     $foolist[0]",
            "              VarRefNode       $foolist",
            "              IntegerNode      0",
            "          MsgSelectCaseNode    {case 'foo'}foo",
            "            RawTextNode        foo",
            "          MsgSelectDefaultNode {default}baz",
            "            RawTextNode        baz",
            ""),
        JOINER.join(
            "{namespace ns}",
            "{template .foo}",
            "{@param foolist: list<string>}",
            "  {for $foo in $foolist}",
            "    {if $foo == 'a'}",
            "      a // C1",
            "    {elseif $foo == 'b'}",
            "      b // C2",
            "    {else}",
            "      {switch $foo}",
            "        {case 'c'}",
            "          c // Comment C",
            "        {default}",
            "          d // Comment D",
            "      {/switch}",
            "      text // TODO(b/147886598): Location of the IfElseNode should include this co...",
            "    {/if}",
            "    // C3",
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
            SoyFileSupplier.Factory.create(
                "{template t}\nHello, World!\n", SourceFilePath.create("broken.soy")))
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
        (TemplateNode)
            SoyFileSetParserBuilder.forFileContents(template)
                .parse()
                .fileSet()
                .getChild(0)
                .getChild(0);
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
  public void testIsAdjacentOrOverlappingWith() throws Exception {
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
            "{/template}");
    TemplateNode templateNode =
        (TemplateNode)
            SoyFileSetParserBuilder.forFileContents(template)
                .parse()
                .fileSet()
                .getChild(0)
                .getChild(0);
    List<PrintNode> nodes = SoyTreeUtils.getAllNodesOfType(templateNode, PrintNode.class);
    assertThat(nodes).hasSize(6);

    // Next to each other
    PrintNode foo1 = nodes.get(0);
    PrintNode bar1 = nodes.get(1);
    assertTrue(foo1.getSourceLocation().isAdjacentOrOverlappingWith(bar1.getSourceLocation()));
    assertTrue(bar1.getSourceLocation().isAdjacentOrOverlappingWith(foo1.getSourceLocation()));

    // Not quite adjacent.
    PrintNode foo2 = nodes.get(2);
    PrintNode bar2 = nodes.get(3);
    assertFalse(foo2.getSourceLocation().isAdjacentOrOverlappingWith(bar2.getSourceLocation()));
    assertFalse(bar2.getSourceLocation().isAdjacentOrOverlappingWith(foo2.getSourceLocation()));

    // Definitely not adjacent.
    PrintNode foo3 = nodes.get(4);
    PrintNode bar3 = nodes.get(5);
    assertFalse(foo3.getSourceLocation().isAdjacentOrOverlappingWith(bar3.getSourceLocation()));
    assertFalse(bar3.getSourceLocation().isAdjacentOrOverlappingWith(foo3.getSourceLocation()));

    // Overlapping ranges.
    SourceLocation overlappingLoc1 =
        new SourceLocation(
            foo1.getSourceLocation().getFilePath(), Point.create(1, 3), Point.create(8, 7));
    SourceLocation overlappingLoc2 =
        new SourceLocation(
            foo1.getSourceLocation().getFilePath(), Point.create(5, 4), Point.create(10, 7));
    assertTrue(overlappingLoc1.isAdjacentOrOverlappingWith(overlappingLoc2));
    assertTrue(overlappingLoc2.isAdjacentOrOverlappingWith(overlappingLoc1));

    // One is a subset of another.
    SourceLocation outerRange =
        new SourceLocation(
            foo1.getSourceLocation().getFilePath(), Point.create(1, 3), Point.create(8, 7));
    SourceLocation innerRange =
        new SourceLocation(
            foo1.getSourceLocation().getFilePath(), Point.create(2, 4), Point.create(5, 7));
    assertTrue(outerRange.isAdjacentOrOverlappingWith(innerRange));
    assertTrue(innerRange.isAdjacentOrOverlappingWith(outerRange));
  }

  @Test
  public void testOverlapWith() {
    // Overlapping ranges.
    SourceLocation overlappingLoc1 =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(8, 7));
    SourceLocation overlappingLoc2 =
        new SourceLocation(FAKE_FILE_PATH, Point.create(5, 4), Point.create(10, 7));

    assertThat(overlappingLoc1.getOverlapWith(overlappingLoc2).get())
        .isEqualTo(new SourceLocation(FAKE_FILE_PATH, Point.create(5, 4), Point.create(8, 7)));
    assertThat(overlappingLoc2.getOverlapWith(overlappingLoc1).get())
        .isEqualTo(new SourceLocation(FAKE_FILE_PATH, Point.create(5, 4), Point.create(8, 7)));

    // One is a subset of another.
    SourceLocation outerRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(8, 7));
    SourceLocation innerRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(2, 4), Point.create(5, 7));
    assertThat(outerRange.getOverlapWith(innerRange).get()).isEqualTo(innerRange);
    assertThat(innerRange.getOverlapWith(outerRange).get()).isEqualTo(innerRange);

    // Only overlaps by one point.
    overlappingLoc1 = new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(8, 7));
    overlappingLoc2 = new SourceLocation(FAKE_FILE_PATH, Point.create(8, 7), Point.create(10, 7));
    assertThat(overlappingLoc1.getOverlapWith(overlappingLoc2).get())
        .isEqualTo(new SourceLocation(FAKE_FILE_PATH, Point.create(8, 7), Point.create(8, 7)));

    // Not overlapping.
    SourceLocation range1 =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(2, 3));
    SourceLocation range2 =
        new SourceLocation(FAKE_FILE_PATH, Point.create(2, 4), Point.create(5, 7));
    assertThat(range1.getOverlapWith(range2)).isEmpty();
    assertThat(range2.getOverlapWith(range1)).isEmpty();
  }

  @Test
  public void testCreateSuperRangeWith_disjointLocation() {
    SourceLocation range1 =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(2, 3));
    SourceLocation range2 =
        new SourceLocation(FAKE_FILE_PATH, Point.create(5, 4), Point.create(5, 7));

    SourceLocation expectedRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(5, 7));
    assertThat(range1.createSuperRangeWith(range2)).isEqualTo(expectedRange);
    assertThat(range2.createSuperRangeWith(range1)).isEqualTo(expectedRange);
  }

  @Test
  public void testCreateSuperRangeWith_overlappingLocation() {
    SourceLocation range1 =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(2, 3));
    SourceLocation range2 =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 5), Point.create(5, 7));

    SourceLocation expectedRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(5, 7));
    assertThat(range1.createSuperRangeWith(range2)).isEqualTo(expectedRange);
    assertThat(range2.createSuperRangeWith(range1)).isEqualTo(expectedRange);
  }

  @Test
  public void testCreateSuperRangeWith_adjacentLocations() {
    SourceLocation range1 =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(2, 3));
    SourceLocation range2 =
        new SourceLocation(FAKE_FILE_PATH, Point.create(2, 3), Point.create(5, 7));

    SourceLocation expectedRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(5, 7));
    assertThat(range1.createSuperRangeWith(range2)).isEqualTo(expectedRange);
    assertThat(range2.createSuperRangeWith(range1)).isEqualTo(expectedRange);
  }

  @Test
  public void testCreateSuperRangeWith_subLocation() {
    SourceLocation range1 =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(2, 3));
    SourceLocation range2 =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 5), Point.create(1, 8));

    SourceLocation expectedRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(2, 3));
    assertThat(range1.createSuperRangeWith(range2)).isEqualTo(expectedRange);
    assertThat(range2.createSuperRangeWith(range1)).isEqualTo(expectedRange);
  }

  @Test
  public void testUnion() throws Exception {
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
            "{/template}");
    TemplateNode templateNode =
        (TemplateNode)
            SoyFileSetParserBuilder.forFileContents(template)
                .parse()
                .fileSet()
                .getChild(0)
                .getChild(0);
    List<PrintNode> nodes = SoyTreeUtils.getAllNodesOfType(templateNode, PrintNode.class);
    assertThat(nodes).hasSize(6);

    // Next to each other
    PrintNode foo1 = nodes.get(0);
    PrintNode bar1 = nodes.get(1);
    SourceLocation expectedUnion =
        new SourceLocation(
            foo1.getSourceLocation().getFilePath(), Point.create(5, 3), Point.create(5, 14));
    assertThat(foo1.getSourceLocation().unionWith(bar1.getSourceLocation()))
        .isEqualTo(expectedUnion);
    assertThat(bar1.getSourceLocation().unionWith(foo1.getSourceLocation()))
        .isEqualTo(expectedUnion);

    // Not adjacent.
    PrintNode foo2 = nodes.get(2);
    PrintNode bar2 = nodes.get(3);
    Exception e =
        assertThrows(
            IllegalStateException.class,
            () -> foo2.getSourceLocation().unionWith(bar2.getSourceLocation()));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Cannot compute union of nonadjacent source locations: 6:3 - 6:8 and 6:10 - 6:15");
    assertThrows(
        IllegalStateException.class,
        () -> bar2.getSourceLocation().unionWith(foo2.getSourceLocation()));

    // Overlapping ranges.
    SourceLocation overlappingLoc1 =
        new SourceLocation(
            foo1.getSourceLocation().getFilePath(), Point.create(1, 3), Point.create(8, 7));
    SourceLocation overlappingLoc2 =
        new SourceLocation(
            foo1.getSourceLocation().getFilePath(), Point.create(5, 4), Point.create(10, 7));
    expectedUnion =
        new SourceLocation(
            foo1.getSourceLocation().getFilePath(), Point.create(1, 3), Point.create(10, 7));
    assertThat(overlappingLoc1.unionWith(overlappingLoc2)).isEqualTo(expectedUnion);
    assertThat(overlappingLoc2.unionWith(overlappingLoc1)).isEqualTo(expectedUnion);

    // One is a subset of another.
    SourceLocation outerRange =
        new SourceLocation(
            foo1.getSourceLocation().getFilePath(), Point.create(1, 3), Point.create(8, 7));
    SourceLocation innerRange =
        new SourceLocation(
            foo1.getSourceLocation().getFilePath(), Point.create(2, 4), Point.create(5, 7));

    assertThat(outerRange.unionWith(innerRange)).isEqualTo(outerRange);
    assertThat(innerRange.unionWith(outerRange)).isEqualTo(outerRange);
  }

  @Test
  public void testFullyContainsRange() throws Exception {
    // One is a subset of another.
    SourceLocation outerRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(8, 7));
    SourceLocation innerRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(2, 4), Point.create(5, 7));

    assertThat(outerRange.fullyContainsRange(innerRange)).isTrue();
    assertThat(innerRange.fullyContainsRange(outerRange)).isFalse();
  }

  @Test
  public void testFullyContainsRange_sameStartPoint() throws Exception {
    // One is a subset of another.
    SourceLocation outerRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(8, 7));
    SourceLocation innerRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(5, 7));

    assertThat(outerRange.fullyContainsRange(innerRange)).isTrue();
    assertThat(innerRange.fullyContainsRange(outerRange)).isFalse();
  }

  @Test
  public void testFullyContainsRange_sameEndPoint() throws Exception {
    // One is a subset of another.
    SourceLocation outerRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(8, 7));
    SourceLocation innerRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 5), Point.create(8, 7));

    assertThat(outerRange.fullyContainsRange(innerRange)).isTrue();
    assertThat(innerRange.fullyContainsRange(outerRange)).isFalse();
  }

  @Test
  public void testFullyContainsRange_sameRange() throws Exception {
    // One is a subset of another.
    SourceLocation outerRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(8, 7));
    SourceLocation innerRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(8, 7));

    assertThat(outerRange.fullyContainsRange(innerRange)).isTrue();
    assertThat(innerRange.fullyContainsRange(outerRange)).isTrue();
  }

  @Test
  public void testFullyContainsRange_failsIfEndsAfter() throws Exception {
    // One is not a subset of another.
    SourceLocation outerRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(8, 7));
    SourceLocation innerRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 5), Point.create(10, 7));

    assertThat(outerRange.fullyContainsRange(innerRange)).isFalse();
    assertThat(innerRange.fullyContainsRange(outerRange)).isFalse();
  }

  @Test
  public void testFullyContainsRange_failsIfBeginsBefore() throws Exception {
    // One is a not subset of another.
    SourceLocation outerRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(8, 7));
    SourceLocation innerRange =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 1), Point.create(6, 7));

    assertThat(outerRange.fullyContainsRange(innerRange)).isFalse();
    assertThat(innerRange.fullyContainsRange(outerRange)).isFalse();
  }

  @Test
  public void testFullyContainsRange_failsIfBothUnknown() throws Exception {
    SourceLocation range1 =
        new SourceLocation(FAKE_FILE_PATH, Point.UNKNOWN_POINT, Point.UNKNOWN_POINT);
    SourceLocation range2 =
        new SourceLocation(FAKE_FILE_PATH, Point.UNKNOWN_POINT, Point.UNKNOWN_POINT);

    assertThat(range1.fullyContainsRange(range2)).isFalse();
    assertThat(range2.fullyContainsRange(range1)).isFalse();
  }

  @Test
  public void testFullyContainsRange_failsIfOneUnknown() throws Exception {
    SourceLocation range1 =
        new SourceLocation(FAKE_FILE_PATH, Point.create(1, 3), Point.create(5, 8));
    SourceLocation range2 =
        new SourceLocation(FAKE_FILE_PATH, Point.UNKNOWN_POINT, Point.UNKNOWN_POINT);

    assertThat(range1.fullyContainsRange(range2)).isFalse();
    assertThat(range2.fullyContainsRange(range1)).isFalse();
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
            ((TemplateNode)
                    SoyFileSetParserBuilder.forFileContents(template)
                        .parse()
                        .fileSet()
                        .getChild(0)
                        .getChild(0))
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
    SoyFileNode soyFile =
        new SoyFileParser(
                new IncrementingIdGenerator(),
                new StringReader(soySourceCode),
                SourceFilePath.create("/example/file.soy"),
                ErrorReporter.createForTest())
            .parseSoyFile();

    assertThat(soyFile.numChildren()).isGreaterThan(0);
    // Verify that the filename is correctly stored in the SourceLocation of each node.
    for (TemplateNode templateNode : SoyTreeUtils.getAllNodesOfType(soyFile, TemplateNode.class)) {
      for (SoyNode node : SoyTreeUtils.getAllNodesOfType(templateNode, SoyNode.class)) {
        assertWithMessage("Wrong file path for node %s", node)
            .that(node.getSourceLocation().getFilePath().path())
            .isEqualTo("/example/file.soy");
      }
    }

    String actual = new AsciiArtNodeVisitor(soySourceCode).exec(soyFile);
    assertEquals(
        // Make the message be something copy-pasteable to make it easier to update this test when
        // fixing source locations bugs.
        "REPLACE_WITH:\n\"" + actual.replace("\n", "\",\n\"") + "\"\n\n",
        asciiArtExpectedOutput,
        actual);
  }

  /** Generates a concise readable summary of a soy tree and its source locations. */
  private static class AsciiArtNodeVisitor extends AbstractSoyNodeVisitor<String> {
    private final AsciiArtPrinter printer;
    private final AsciiArtExprVisitor exprVisitor;

    public AsciiArtNodeVisitor(String soySourceCode) {
      this.printer = new AsciiArtPrinter(soySourceCode);
      this.exprVisitor = new AsciiArtExprVisitor(this.printer);
    }

    @Override
    public String exec(SoyNode node) {
      visit(node);
      return printer.toString();
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      printer.printNode(node);

      if (node instanceof ExprHolderNode) {
        printer.openIndent();
        for (ExprNode expr : ((ExprHolderNode) node).getExprList()) {
          visit(expr);
        }
        printer.closeIndent();
      }
      if (node instanceof ParentSoyNode<?>) {
        printer.openIndent();
        visitChildren((ParentSoyNode<?>) node);
        printer.closeIndent();
      }
    }

    private void visit(ExprNode node) {
      exprVisitor.exec(node);
    }
  }

  private static class AsciiArtExprVisitor extends AbstractExprNodeVisitor<String> {
    private final AsciiArtPrinter printer;

    public AsciiArtExprVisitor(AsciiArtPrinter printer) {
      this.printer = printer;
    }

    @Override
    public String exec(ExprNode node) {
      visit(node);
      return printer.toString();
    }

    @Override
    protected void visitExprNode(ExprNode node) {
      printer.printNode(node);

      if (node instanceof ParentExprNode) {
        printer.openIndent();
        visitChildren((ParentExprNode) node);
        printer.closeIndent();
      }
    }
  }

  private static class AsciiArtPrinter {
    private final String[] soySourceCode;
    private final StringBuilder sb = new StringBuilder();
    private int depth = 0;

    public AsciiArtPrinter(String soySourceCode) {
      this.soySourceCode = soySourceCode.split("\n");
    }

    public void openIndent() {
      depth++;
    }

    public void closeIndent() {
      depth--;
    }

    void printNode(Node node) {
      // Output a header like:
      //   <indent> <node class>                    {code fragment}
      // or
      //   <indent> <node class>                    @ <location>
      // where indent is 2 spaces per level, and the @ sign is indented to the 31st column
      for (int indent = depth; indent > 0; indent--) {
        sb.append("  ");
      }
      String typeName = node.getClass().getSimpleName();
      if (typeName.length() + depth * 2 > 30) {
        typeName = typeName.substring(0, 27 - depth * 2) + "...";
      }
      sb.append(typeName);
      // SoyFileSetNode and SoyFileNode don't have source locations.
      if (!(node instanceof SoyFileSetNode) && !(node instanceof SoyFileNode)) {
        int pos = typeName.length() + 2 * depth;
        while (pos < 30) {
          sb.append(' ');
          ++pos;
        }
        sb.append(' ');
        sb.append(getCodeFragment(node.getSourceLocation()));
      }
      sb.append('\n');
    }

    private StringBuilder getCodeFragment(SourceLocation location) {
      StringBuilder sb = new StringBuilder();
      if (location.getBeginLine() == location.getEndLine()) {
        String line = this.soySourceCode[location.getBeginLine() - 1];
        sb.append(line.substring(location.getBeginColumn() - 1, location.getEndColumn()).trim());
      } else {
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
      }
      if (sb.length() > 54) {
        // Add an ellipsis to bring the fragment to a length of 54.
        return sb.replace(25, sb.length() - 24, "[...]");
      }
      return sb;
    }

    @Override
    public String toString() {
      return this.sb.toString();
    }
  }
}
