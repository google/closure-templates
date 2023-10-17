/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.sharedpasses.opti;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.StringSubject;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimplifyVisitorTest {

  private boolean desugarIdomFeatures;
  private boolean desugarHtmlNodes;

  @Before
  public void setUp() {
    desugarIdomFeatures = true;
    desugarHtmlNodes = true;
  }

  @Test
  public void testMsgBlockNodeChildrenAreNotReplaced() throws Exception {

    String soyFileContent =
        "{namespace boo}\n"
            + "\n"
            + "{template foo}\n"
            + "\n"
            + "  {msg desc=\"\"}\n"
            + "    blah\n"
            + "    {'blah'}\n"
            + "    blah\n"
            + "    {call aaa /}\n"
            + "    blah\n"
            + "    <div class=\"{call aaa /}\">\n"
            + "    </div>\n"
            + "    blah\n"
            + "  {/msg}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template aaa}\n"
            + "  blah\n"
            + "{/template}";

    MsgNode msgNode =
        ((MsgFallbackGroupNode)
                ((TemplateNode) simplifySoyFiles(soyFileContent).getChild(0).getChild(0))
                    .getChild(0))
            .getChild(0);
    assertThat(msgNode.numChildren()).isEqualTo(8);
    // The MsgPlaceholderNode children are not replaced.
    assertThat(msgNode.getChild(1)).isInstanceOf(MsgPlaceholderNode.class);
    assertThat(msgNode.getChild(3)).isInstanceOf(MsgPlaceholderNode.class);
    assertThat(msgNode.getChild(5)).isInstanceOf(MsgPlaceholderNode.class);
    assertThat(msgNode.getChild(6)).isInstanceOf(MsgPlaceholderNode.class);
    // But the contents within the MsgPlaceholderNode children can be replaced.
    assertThat(((MsgPlaceholderNode) msgNode.getChild(1)).getChild(0))
        .isInstanceOf(RawTextNode.class);
  }

  @Test
  public void testSimplifyPrintNode() throws Exception {
    assertSimplification("{'foo'}").isEqualTo("foo");

    assertSimplification("{'abcdefgh' |insertWordBreaks:5}").isEqualTo("abcde<wbr>fgh");

    // Doesn't simplify PrintNode with non-constant expression (but expression is simplified).
    assertSimplification("{@param boo : ?}\n" + "{1 + 3 + $boo}")
        .isEqualTo("{@param boo: ?}\n{4 + $boo}");

    // formatNum is not annotated as a SoyPureFunction, so it should not be simplified.
    assertSimplification("{formatNum(5)}").isEqualTo("{formatNum(5)}");

    // Doesn't simplify PrintNode with non-constant directive arg.
    assertSimplification("{@param boo : ?}\n" + "{'0123456789' |insertWordBreaks:$boo}")
        .isEqualTo("{@param boo: ?}\n{'0123456789' |insertWordBreaks:$boo}");
  }

  @Test
  public void testSimplifyIfNode() throws Exception {

    assertSimplification(
            "{if not false}", "  111", "{/if}", "{if true and false}", "  222", "{/if}")
        .isEqualTo("111");

    assertSimplification("{if ''}", "  111", "{elseif not 1}", "  222", "{else}", "  333", "{/if}")
        .isEqualTo("333");

    assertSimplification(
            "{if false}", "  111", "{elseif true}", "  222", "{else}", "  333", "{/if}")
        .isEqualTo("222");

    assertSimplification(
            "{@param boo : ?}",
            "{if false}",
            "  111",
            "{elseif $boo}",
            "  222",
            "{elseif true}",
            "  333",
            "{else}",
            "  444",
            "{/if}")
        .isEqualTo("{@param boo: ?}\n{if $boo}222{else}333{/if}");

    assertSimplification(
            "{@param boo : ?}",
            "{if 0}",
            "  111",
            "{elseif 1}",
            "  {if true}",
            "    {if $boo}",
            "      222",
            "    {elseif ''}",
            "      333",
            "    {elseif 'blah'}",
            "      444",
            "    {else}",
            "      555",
            "    {/if}",
            "  {else}",
            "    666",
            "  {/if}",
            "{else}",
            "  777",
            "{/if}")
        .isEqualTo("{@param boo: ?}\n{if $boo}222{else}444{/if}");
  }

  @Test
  public void simplifyIfWithEmptyChildren_delete() {
    assertSimplification("{@param boo: ?}", "{if $boo}", "  {let $baz:2/}", "{/if}")
        .isEqualTo("{@param boo: ?}");
  }

  @Test
  public void simplifyIfWithEmptyChildren_elseToIf() {
    assertSimplification(
            "{@param boo: ?}", "{if $boo}", "  {let $baz:2/}", "{else}", "hello", "{/if}")
        .isEqualTo("{@param boo: ?}\n{if not $boo}hello{/if}");
  }

  @Test
  public void simplifyIfWithEmptyChildren_invertNextIf() {
    assertSimplification(
            "{@param boo: ?}",
            "{@param qoo: ?}",
            "{if $boo}",
            "  {let $baz:2/}",
            "{elseif $qoo}",
            "  hello",
            "{/if}")
        .isEqualTo("{@param boo: ?}\n  {@param qoo: ?}\n{if not $boo and $qoo}hello{/if}");
  }

  @Test
  public void simplifyIfWithEmptyChildren_invertAllFollowingIfs() {
    // If it is the first once, we can surround with an if
    assertSimplification(
            "{@param boo: ?}",
            "{@param qoo: ?}",
            "{@param soo: ?}",
            "{if $boo}",
            "  {nil}",
            "{elseif $qoo}",
            "  hello",
            "{elseif $soo}",
            "  goodbye",
            "{/if}")
        .isEqualTo(
            "{@param boo: ?}\n"
                + "  {@param qoo: ?}\n"
                + "  {@param soo: ?}\n"
                + "{if not $boo}{if $qoo}hello{elseif $soo}goodbye{/if}{/if}");

    assertSimplification(
            "{@param boo: ?}",
            "{@param qoo: ?}",
            "{@param soo: ?}",
            "{if $boo}",
            "  hello",
            "{elseif $qoo}",
            "  {nil}",
            "{elseif $soo}",
            "  goodbye",
            "{/if}")
        .isEqualTo(
            "{@param boo: ?}\n"
                + "  {@param qoo: ?}\n"
                + "  {@param soo: ?}\n"
                + "{if $boo}hello{elseif not $qoo and $soo}goodbye{/if}");

    // If it is in the middle, we need to introduce an ele-if
    assertSimplification(
            "{@param boo: ?}",
            "{@param qoo: ?}",
            "{@param soo: ?}",
            "{if $boo}",
            "  hello",
            "{elseif $qoo}",
            "  {nil}",
            "{elseif $soo}",
            "  goodbye",
            "{else}",
            "  still here?",
            "{/if}")
        .isEqualTo(
            "{@param boo: ?}\n"
                + "  {@param qoo: ?}\n"
                + "  {@param soo: ?}\n"
                + "{if $boo}hello{elseif not $qoo}{if $soo}goodbye{else}still here?{/if}{/if}");

    // If there are multipole we need to handle them all.
    assertSimplification(
            "{@param boo: ?}",
            "{@param qoo: ?}",
            "{@param soo: ?}",
            "{@param foo: ?}",
            "{if $boo}",
            "  hello",
            "{elseif $qoo}",
            "  {nil}",
            "{elseif $soo}",
            "  goodbye",
            "{elseif $foo}",
            "  {nil}",
            "{else}",
            "  still here?",
            "{/if}")
        .isEqualTo(
            "{@param boo: ?}\n"
                + "  {@param qoo: ?}\n"
                + "  {@param soo: ?}\n"
                + "  {@param foo: ?}\n"
                + "{if $boo}hello"
                + "{elseif not $qoo}"
                + "{if $soo}goodbye{elseif not $foo}still here?{/if}"
                + "{/if}");
  }

  @Test
  public void testSimplifySwitchNode() throws Exception {

    assertSimplification(
            "{@param boo : ?}",
            "{switch 1 + 2}",
            "  {case 1}111",
            "  {case 2, 3}222333",
            "  {case $boo}444",
            "  {default}goo",
            "{/switch}")
        .isEqualTo("{@param boo: ?}\n222333");

    assertSimplification(
            "{switch 1 + 2}", "  {case 1}111", "  {case 2}222", "  {default}333", "{/switch}")
        .isEqualTo("333");

    assertSimplification(
            "{@param boo : ?}",
            "{switch 1 + 2}",
            "  {case $boo}111",
            "  {case 2}222",
            "  {case 3}333",
            "  {default}444",
            "{/switch}")
        .isEqualTo("{@param boo: ?}\n{switch 3}{case $boo}111{default}333{/switch}");
  }

  @Test
  public void testRewriteContentNodes_let() {
    assertSimplification("{let $foo kind='text'}hello{/let}{$foo}{$foo}")
        .isEqualTo("{let $foo : 'hello' /}{$foo}{$foo}");

    assertSimplification("{let $foo kind='text'}{xid('foo')}:{xid('bar')}{/let}{$foo}{$foo}")
        .isEqualTo("{let $foo : '' + xid('foo') + ':' + xid('bar') /}{$foo}{$foo}");
  }

  @Test
  public void testRewriteContentNodes_callParam() {
    assertSimplification(
            "{@param p: ?}",
            "{call t}",
            "  {param p kind='text'}",
            "    hello world {$p}",
            "  {/param}",
            "{/call}")
        .isEqualTo("{@param p: ?}\n{call t}{param p: 'hello world ' + $p /}{/call}");
  }

  @Test
  public void testCallParamWithLoggingFunctionNotRewritten() {
    assertSimplification(
            "<{t2()} data-ved=\"{currentVed()}\"></>",
            "{/template}",
            "{template t2 kind=\"html<?>\"}",
            "  {@attribute? data-ved: string|null}",
            "  <div @data-ved></div>")
        .isEqualTo(
            "{call t2}{param dataVed kind=\"text\"}{currentVed()"
                + " |escapeHtmlAttribute}{/param}{/call}");
  }

  @Test
  public void testCallBind() {
    assertSimplification(
            "{@param tpl: (a: string, b: string) => html<?>}",
            "{call $tpl.bind(record(a:'anA'))}",
            "  {param b: 'aB' /}",
            "{/call}")
        .isEqualTo(
            "{@param tpl: (a: string, b: string) => html<?>}\n"
                + "{call $tpl}{param a: 'anA' /}{param b: 'aB' /}{/call}");

    assertSimplification(
            "{@param tpl: (a: string, b: string) => html<?>}",
            "{call $tpl.bind(record(a:'anA', b:'aB')) /}")
        .isEqualTo(
            "{@param tpl: (a: string, b: string) => html<?>}\n"
                + "{call $tpl}{param a: 'anA' /}{param b: 'aB' /}{/call}");
  }

  @Test
  public void testInlineLets() {
    assertSimplification("{@param p: ?}", "{let $a : $p /}", "{let $b : $a /}", "{$b}")
        .isEqualTo(normalized("{@param p: ?}", "{$p}"));

    assertNoOp("{@param p: ?}", "{let $b : $p /}{$b + $b}");

    assertSimplification(
            "{@param p: ?}",
            "{let $b : $p + 1 /}",
            "{for $i in range(10)}",
            "{let $c : $i + 1 /}",
            "{$b + $c}",
            "{/for}")
        .isEqualTo(
            normalized(
                "{@param p: ?}",
                "{let $b : $p + 1 /}", // b doesn't move inside the loop
                "{for $i in range(10)}",
                "{$b + ($i + 1)}", // c does because it is designed inside the loop
                "{/for}"));
  }

  @Test
  public void testInlineLets_trivialValuesMoveInsideLoops() {
    assertSimplification(
            "{let $b : 1 /}", "{for $i in range(10)}", "{let $c : $i + 1 /}", "{$b + $c}", "{/for}")
        .isEqualTo(normalized("{for $i in range(10)}", "{1 + ($i + 1)}", "{/for}"));
  }

  @Test
  public void testInliningUnlocksFurtherOptimization() {
    // First the two lets should get turned into letvaluenodes
    // Then foo will be inlined
    // Then the if we be calulated, deleting the else branch
    // then bar will be inlined
    // Then the print node will be eliminated.
    assertSimplification(
            "{let $foo kind='text'}foo{/let}",
            "{let $bar kind='text'}bar{/let}",
            "{if $foo}Hello {$bar}{else}Goodbye {$bar}{/if}")
        .isEqualTo(normalized("Hello bar"));
  }

  @Test
  public void testInlineIntoMsg() {
    assertSimplification("{msg desc='...'}Hello {'foo' phname=\"FOO\"}{/msg}")
        .isEqualTo(normalized("{msg desc=\"...\"}Hello foo{/msg}"));
    assertSimplification("{let $foo kind='text'}foo{/let}", "{msg desc='...'}Hello {$foo}{/msg}")
        .isEqualTo(normalized("{msg desc=\"...\"}Hello foo{/msg}"));
  }

  @Test
  public void testMoveLetPastPrintingNodes() {
    assertSimplification("{let $name : 'hello'/}Hello {$name} Goodbye:{$name}")
        .isEqualTo(normalized("Hello {let $name : 'hello'/}{$name} Goodbye:{$name}"));
    assertSimplification(
            "{let $greeting: 'something' /}{let $name : 'hello'/}{$greeting} {$name}"
                + " {$greeting}:{$name}")
        .isEqualTo(
            normalized(
                "{let $greeting : 'something' /}{$greeting} {let $name : 'hello' /}{$name}"
                    + " {$greeting}:{$name}"));
  }

  @Test
  public void testMoveLetIntoIfStatement() {
    assertSimplification(
            "{let $name : 'hello'/}{if randomInt(2) == 1}Hello {$name} Goodbye:{$name}{/if}")
        .isEqualTo(
            normalized(
                " {if randomInt(2) == 1}Hello {let $name : 'hello' /}{$name}"
                    + " Goodbye:{$name}{/if}"));
  }

  @Test
  public void testDontMoveLetIntoLoop() {
    assertNoOp("{let $name: randomInt(10) /}{for $i in range(10)}{$name}{/for}");
  }

  @Test
  public void testDontMoveLetPastSkipNode() {
    desugarIdomFeatures = false;
    desugarHtmlNodes = false;
    assertNoOp("{let $name: randomInt(10) /}<div {skip}>{$name}</div>{$name}");
    desugarIdomFeatures = true;
    desugarHtmlNodes = true;
    assertSimplification("{let $name: randomInt(10) /}<div {skip}>{$name}</div>{$name}")
        .isEqualTo(normalized("<div {skip}>{let $name : randomInt(10) /}{$name}</div>{$name}"));
  }

  @Test
  public void testMoveMultipleDependentLets() {
    assertSimplification(
            "{let $foo: 'hello'/}{let $bar : $foo + 2/}{if $foo}Hello {$foo+$bar}"
                + " Goodbye:{$bar}{/if}")
        .isEqualTo(
            normalized(
                "{let $foo: 'hello' /}{if $foo}Hello {let $bar : $foo + 2 /}{$foo + $bar}"
                    + " Goodbye:{$bar}{/if}"));
  }

  @Test
  public void testMoveMultipleDependentLets_preserve_order() {
    // foo and bar could be reordered but we shouldn't do that.
    assertNoOp("{let $foo: 'hello'/}{let $bar :'goodbye'/}{if $foo}{$bar}{elseif $bar}{$foo}{/if}");
  }

  @Test
  public void testMoveMultipleIndependentLets() {
    assertSimplification("{let $h : 'hello'/}{let $g :'goodbye'/}{$h}{$g}{$h}{$g}")
        .isEqualTo(normalized("{let $h : 'hello'/}{$h}{let $g :'goodbye'/}{$g}{$h}{$g}"));
  }

  @Test
  public void testEvalutationOrder() {
    TemplateBasicNode template =
        (TemplateBasicNode) parse("{let $foo : 'a'/}hello{$foo}").getChild(0).getChild(0);
    LetNode let = (LetNode) template.getChild(0);
    PrintNode print = (PrintNode) template.getChild(2);
    assertThat(SimplifyVisitor.evaluationOrder(let, print)).containsExactly(template.getChild(1));
  }

  @Test
  public void testEvalutationOrder_goesOverBlockNodes() {
    TemplateBasicNode template =
        (TemplateBasicNode)
            parse("{let $foo : 'a'/}{if true}hello{/if}{$foo}").getChild(0).getChild(0);
    LetNode let = (LetNode) template.getChild(0);
    PrintNode print = (PrintNode) template.getChild(2);
    // The path marks the if but not the ifs children
    assertThat(SimplifyVisitor.evaluationOrder(let, print)).containsExactly(template.getChild(1));
  }

  @Test
  public void testEvalutationOrder_goesIntoBlockNodes() {
    TemplateBasicNode template =
        (TemplateBasicNode)
            parse("{let $foo : 'a'/}{if true}hello {$foo}{/if}").getChild(0).getChild(0);
    LetNode let = (LetNode) template.getChild(0);
    IfNode ifNode = (IfNode) template.getChild(1);
    PrintNode print = (PrintNode) ifNode.getChild(0).getChild(1);
    // The path marks the ifnode, the ifcondnode and the RawTextNode
    assertThat(SimplifyVisitor.evaluationOrder(let, print))
        .containsExactly(ifNode, ifNode.getChild(0), (RawTextNode) ifNode.getChild(0).getChild(0));
  }

  @Test
  public void testEvalutationOrder_letStartsInOddLocation() {
    desugarHtmlNodes = false;
    TemplateBasicNode template =
        (TemplateBasicNode)
            parse("<a href=\"{let $foo : 'a'/}weird\">{$foo}</a>").getChild(0).getChild(0);
    var attributeNode =
        (HtmlAttributeValueNode)
            ((HtmlAttributeNode) ((HtmlOpenTagNode) template.getChild(0)).getChild(1)).getChild(1);
    LetNode let = (LetNode) attributeNode.getChild(0);
    PrintNode print = (PrintNode) template.getChild(1);
    assertThat(SimplifyVisitor.evaluationOrder(let, print))
        .containsExactly(attributeNode.getChild(1));
  }

  private StringSubject assertSimplification(String... input) {
    SoyFileSetNode node = parse(join(input));
    SimplifyVisitor.create(
            node.getNodeIdGenerator(),
            ImmutableList.copyOf(node.getChildren()),
            ErrorReporter.exploding())
        .simplify(node.getChild(0));
    return assertThat(toString(node.getChild(0).getChild(0)));
  }

  private void assertNoOp(String... input) {
    SoyFileSetNode node = parse(join(input));
    String original = toString(node.getChild(0).getChild(0));
    SimplifyVisitor.create(
            node.getNodeIdGenerator(),
            ImmutableList.copyOf(node.getChildren()),
            ErrorReporter.exploding())
        .simplify(node.getChild(0));
    String rewritten = toString(node.getChild(0).getChild(0));
    assertThat(rewritten).isEqualTo(original);
  }

  private SoyFileSetNode parse(String input) {
    return SoyFileSetParserBuilder.forFileContents(
            join("{namespace ns}", "{template t}", input, "{/template}"))
        .runOptimizer(false)
        .desugarIdomFeatures(desugarIdomFeatures)
        .desugarHtmlNodes(desugarHtmlNodes)
        .addSoySourceFunction(new CurrentVedFunction())
        .parse()
        .fileSet();
  }

  private static String toString(SoyNode node) {
    String string = node.toSourceString();
    return string.replace("{template t}\n", "").replace("\n{/template}", "").trim();
  }

  private String normalized(String... args) {
    return toString(parse(join(args)).getChild(0).getChild(0));
  }

  private static String join(String... args) {
    return Joiner.on('\n').join(args);
  }

  private SoyFileSetNode simplifySoyFiles(String... soyFileContents) throws Exception {
    SoyFileSetNode fileSet =
        SoyFileSetParserBuilder.forFileContents(soyFileContents).parse().fileSet();
    SimplifyVisitor simplifyVisitor =
        SimplifyVisitor.create(
            fileSet.getNodeIdGenerator(),
            ImmutableList.copyOf(fileSet.getChildren()),
            ErrorReporter.exploding());
    for (SoyFileNode file : fileSet.getChildren()) {
      simplifyVisitor.simplify(file);
    }
    return fileSet;
  }

  @SoyFunctionSignature(name = "currentVed", value = @Signature(returnType = "string"))
  private static final class CurrentVedFunction implements LoggingFunction {

    @Override
    public String getPlaceholder() {
      return "";
    }
  }
}
