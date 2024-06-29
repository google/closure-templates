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

package com.google.template.soy.parsepasses.contextautoesc;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import junit.framework.ComparisonFailure;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ContextualAutoescaperTest {

  /** Custom print directives used in tests below. */
  private static final ImmutableList<SoyPrintDirective> SOY_PRINT_DIRECTIVES =
      ImmutableList.of(
          new SoyPrintDirective() {
            @Override
            public String getName() {
              return "|customOtherDirective";
            }

            @Override
            public Set<Integer> getValidArgsSizes() {
              return ImmutableSet.of(0);
            }
          });

  @Test
  public void testTrivialTemplate() throws Exception {
    assertContextualRewriting(
        join("{namespace ns}\n\n", "{template foo}\n", "Hello, World!\n", "{/template}"),
        join("{namespace ns}\n\n", "{template foo}\n", "Hello, World!\n", "{/template}"));
  }

  @Test
  public void testUriCallTemplate() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "<a href={call uri /} title={call title /}></a>\n",
            "{/template}",
            "\n\n{template uri kind='trusted_resource_uri'}\n/\n{/template}",
            "\n\n{template title kind='text'}\ntitle\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "<a href={call uri /} title={call title /}></a>\n",
            "{/template}",
            "\n\n{template uri kind='trusted_resource_uri'}\n/\n{/template}",
            "\n\n{template title kind='text'}\ntitle\n{/template}"));
  }

  @Test
  public void testHtmlHtmlAttributePosition() throws Exception {
    assertRewriteFails(
        "HTML attribute values containing HTML can use dynamic expressions only at the start "
            + "of the value.",
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "  <iframe srcdoc='&lt;script&gt;{$x}&lt;/script&gt;'></iframe>\n",
            "{/template}"));
  }

  @Test
  public void testPrintInText() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param world: ?}\n",
            "Hello, {$world |escapeHtml}!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param world: ?}\n",
            "Hello, {$world}!\n",
            "{/template}"));
  }

  @Test
  public void testPrivateTemplate() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template privateFoo visibility=\"private\"}\n",
            "  {@param world: ?}\n",
            "Hello, {$world |escapeHtml}!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template privateFoo visibility=\"private\"}\n",
            "  {@param world: ?}\n",
            "Hello, {$world}!\n",
            "{/template}"));
  }

  @Test
  public void testPrintInTextAndLink() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param world: ?}\n",
            "Hello,",
            "<a href='worlds?world={$world |escapeUri}'>",
            "{$world |escapeHtml}",
            "</a>!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param world: ?}\n",
            "Hello,\n",
            "<a href='worlds?world={$world}'>\n",
            "{$world}\n",
            "</a>!\n",
            "{/template}\n"));
  }

  @Test
  public void testObscureUrlAttributes() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<meta http-equiv=refresh content='{$x |filterNumber}'>",
            "<meta http-equiv=refresh content='"
                + "{$x |filterNumber}; URL={$x |filterNormalizeRefreshUri |escapeHtmlAttribute}'>",
            "<a xml:base='{$x |filterNormalizeUri |escapeHtmlAttribute}' href='/foo'>link</a>",
            "<button formaction='{$x |filterNormalizeUri |escapeHtmlAttribute}'>do</button>",
            "<command icon='{$x |filterNormalizeUri |escapeHtmlAttribute}'>",
            "<object data='{$x |filterTrustedResourceUri |escapeHtmlAttribute}'></object>",
            "<video poster='{$x |filterNormalizeUri |escapeHtmlAttribute}'></video>",
            "<video src='{$x |filterNormalizeUri |escapeHtmlAttribute}'></video>",
            "<source src='{$x |filterNormalizeUri |escapeHtmlAttribute}'>",
            "<audio src='{$x |filterNormalizeUri |escapeHtmlAttribute}'></audio>",
            "<base href='{$x |filterTrustedResourceUri |escapeHtmlAttribute}'>",
            "<iframe src='{$x |filterTrustedResourceUri |escapeHtmlAttribute}'></iframe>",
            "<iframe srcdoc='{$x |escapeHtmlHtmlAttribute |escapeHtmlAttribute}'></iframe>",
            "<iframe srcdoc={$x |escapeHtmlHtmlAttribute |escapeHtmlAttributeNospace}></iframe>",
            "<link rel='shortcut icon' href='{$x |filterNormalizeUri |escapeHtmlAttribute}'>",
            "<link rel='stylesheet' href='{$x |filterTrustedResourceUri |escapeHtmlAttribute}'>",
            "<link rel='{$x |escapeHtmlAttribute}' "
                + "href='{$x |filterTrustedResourceUri |escapeHtmlAttribute}'>",
            "<link itemprop='url' href='{$x |filterNormalizeUri |escapeHtmlAttribute}'>",
            "<link rel='{$x |escapeHtmlAttribute}' itemprop='url' "
                + "href='{$x |filterTrustedResourceUri |escapeHtmlAttribute}'>",
            "<script>{$x |escapeJsValue |filterHtmlScriptPhrasingData}</script>",
            "<script src='{$x |filterTrustedResourceUri |escapeHtmlAttribute}'></script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<meta http-equiv=refresh content='{$x}'>",
            "<meta http-equiv=refresh content='{$x}; URL={$x}'>",
            "<a xml:base='{$x}' href='/foo'>link</a>\n",
            "<button formaction='{$x}'>do</button>\n",
            "<command icon='{$x}'>\n",
            "<object data='{$x}'></object>\n",
            "<video poster='{$x}'></video>\n",
            "<video src='{$x}'></video>\n",
            "<source src='{$x}'>\n",
            "<audio src='{$x}'></audio>\n",
            "<base href='{$x}'>\n",
            "<iframe src='{$x}'></iframe>",
            "<iframe srcdoc='{$x}'></iframe>",
            "<iframe srcdoc={$x}></iframe>",
            "<link rel='shortcut icon' href='{$x}'>\n",
            "<link rel='stylesheet' href='{$x}'>\n",
            "<link rel='{$x}' href='{$x}'>\n",
            "<link itemprop='url' href='{$x}'>",
            "<link rel='{$x}' itemprop='url' href='{$x}'>",
            "<script>{$x}</script>",
            "<script src='{$x}'></script>\n",
            "{/template}\n"));
  }

  @Test
  public void testConditional() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {@param z: ?}\n",
            "Hello,",
            "{if $x == 1}",
            "{$y |escapeHtml}",
            "{elseif $x == 2}",
            "<script>foo({$z |escapeJsValue |filterHtmlScriptPhrasingData})</script>",
            "{else}",
            "World!",
            "{/if}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {@param z: ?}\n",
            "Hello,\n",
            "{if $x == 1}\n",
            "  {$y}\n",
            "{elseif $x == 2}\n",
            "  <script>foo({$z})</script>\n",
            "{else}\n",
            "  World!\n",
            "{/if}\n",
            "{/template}"));
  }

  @Test
  public void testConditionalEndsInDifferentContext() throws Exception {
    // Make sure that branches that ends in consistently different contexts transition to
    // that different context.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param url: ?}\n",
            "  {@param name: ?}\n",
            "  {@param value: ?}\n",
            "<a",
            "{if $url}",
            " href='{$url |filterNormalizeUri |escapeHtmlAttribute}'",
            "{elseif $name}",
            " name='{$name |escapeHtmlAttribute}'",
            "{/if}></a>",
            " onclick='alert({$value |escapeHtml})'\n", // Not escapeJsValue.
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param url: ?}\n",
            "  {@param name: ?}\n",
            "  {@param value: ?}\n",
            "<a",
            // Each of these branches independently closes the tag.
            "{if $url}",
            " href='{$url}'",
            "{elseif $name}",
            " name='{$name}'",
            "{/if}></a>",
            // So now make something that looks like a script attribute but which actually
            // appears in a PCDATA.  If the context merge has properly happened is escaped as
            // PCDATA.
            " onclick='alert({$value})'\n",
            "{/template}"));

    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param p: ?}\n",
            "<input{if $p} disabled{/if}>\n",
            "{/template}"));

    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param p: ?}\n",
            "  {@param p2: ?}\n",
            "<input{if $p} disabled{/if}{if $p2} checked{/if}>\n",
            "{/template}"));

    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param p: ?}\n",
            "  {@param p2: ?}\n",
            "<input{if $p} disabled{/if}{if $p2} checked{/if}>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param p: ?}\n",
            "  {@param p2: ?}\n",
            "<input {if $p}disabled{/if}{if $p2} checked{/if}>\n",
            "{/template}"));

    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template good4}\n",
            "  {@param p: ?}\n",
            "<p{if $p} x=x{/if} x=y>\n",
            "{/template}"));

    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template good4}\n",
            "  {@param p: ?}\n",
            "<p{if $p} onclick=foo(){/if} x=y>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template good4}\n",
            "  {@param p: ?}\n",
            "<p {if $p}onclick=foo() {/if} x=y>\n",
            "{/template}"));

    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template good4}\n",
            "  {@param p: ?}\n",
            "<p foo=bar{if $p} onclick=foo(){/if} x=y>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template good4}\n",
            "  {@param p: ?}\n",
            "<p foo=bar {if $p}onclick=foo() {/if} x=y>\n",
            "{/template}"));

    assertContextualRewriting(
        "{namespace ns}\n\n"
            + "{template good4}\n"
            + "  {@param x: ?}\n"
            + "<input{if $x} onclick={$x |escapeJsValue |escapeHtmlAttributeNospace}{/if}>\n"
            + "{/template}",
        join(
            "{namespace ns}\n\n",
            "{template good4}\n",
            "  {@param x: ?}\n",
            "\n" + "<input{if $x} onclick={$x}{/if}>\n",
            "{/template}"));

    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template good4}\n",
            "  {@param p: ?}\n",
            "<input{if $p} disabled=\"true\"{/if}>",
            "<input{if $p} onclick=\"foo()\"{/if}>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template good4}\n",
            "  {@param p: ?}\n",
            "<input {if $p}disabled=\"true\"{/if}>",
            "<input {if $p}onclick=\"foo()\"{/if}>\n",
            "{/template}"));
  }

  @Test
  public void testBranchesEndInDifferentQuotingContexts() {
    // branches end in different contexts
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template good4}\n",
            "  {@param p: ?}\n",
            "<input class={if $p}\"x\"{else}'y'{/if}>\n",
            "{/template}"));
  }

  @Test
  public void testSwitch() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {@param z: ?}\n",
            "Hello,",
            "{switch $x}",
            "{case 1}",
            "{$y |escapeHtml}",
            "{case 2}",
            "<script>foo({$z |escapeJsValue |filterHtmlScriptPhrasingData})</script>",
            "{default}",
            "World!",
            "{/switch}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {@param z: ?}\n",
            "Hello,\n",
            "{switch $x}\n",
            "  {case 1}\n",
            "    {$y}\n",
            "  {case 2}\n",
            "    <script>foo({$z})</script>\n",
            "  {default}\n",
            "    World!\n",
            "{/switch}\n",
            "{/template}"));
  }

  @Test
  public void testPrintInsideScript() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param a: ?}\n",
            "  {@param b: ?}\n",
            "  {@param c: ?}\n",
            "  {@param d: ?}\n",
            "  {@param e: ?}\n",
            "  {@param f: ?}\n",
            "<script>",
            "foo({$a |escapeJsValue |filterHtmlScriptPhrasingData}); ",
            "bar(\"{$b |escapeJsString}\"); ",
            "baz(\'{$c |escapeJsString}\'); ",
            "boo(/{$d |escapeJsRegex}/.test(s) ? 1 / {$e |escapeJsValue"
                + " |filterHtmlScriptPhrasingData}",
            " : /{$f |escapeJsRegex}/); ",
            "/* {$a |escapeJsString} */ ",
            "// {$a |escapeJsString}",
            "</script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param a: ?}\n",
            "  {@param b: ?}\n",
            "  {@param c: ?}\n",
            "  {@param d: ?}\n",
            "  {@param e: ?}\n",
            "  {@param f: ?}\n",
            "<script>\n",
            "foo({$a});\n",
            "bar(\"{$b}\");\n",
            "baz(\'{$c}\');\n",
            "boo(/{$d}/.test(s) ? 1 / {$e} : /{$f}/);\n",
            "/{nil}* {$a} */\n",
            "/{nil}/ {$a}\n",
            "</script>\n",
            "{/template}"));
  }

  @Test
  public void testJsTemplateStrings() {
    // these first three examples used to fail because the '/' in '</div' would be interpreted as a
    // regex and then we would get an error for exiting the script or template in js regex.

    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "<script>",
            // the ' " and / characters are important to make sure we detect the '}' first
            "`{\\n}<div a=\"q\">${lb}foo{rb}\"'</div>{\\n}`;",
            "</script>\n",
            "{/template}"));

    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "<script>",
            "`<div a=\"q\"></div>`;{\\n}",
            "</script>\n",
            "{/template}"));

    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template foo kind=\"js\"}\n",
            "`<div a=\"q\"></div>`;{\\n}\n",
            "{/template}"));

    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo kind=\"js\"}\n",
            "  {@param foo: ?}\n",
            "`<div a=\"q\">${lb} {$foo |escapeJsValue |filterHtmlScriptPhrasingData} {rb}</div>`\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo kind=\"js\"}\n",
            "  {@param foo: ?}\n",
            "`<div a=\"q\">${lb} {$foo} {rb}</div>`\n",
            "{/template}"));

    assertRewriteFails(
        "Js template literals cannot contain dynamic values",
        join(
            "{namespace ns}\n\n",
            "{template foo kind=\"js\"}\n",
            "  {@param foo: ?}\n",
            "`<div a=\"q\">{$foo}</div>`\n",
            "{/template}"));

    // can't merge across different template depths
    assertRewriteFails(
        "{if} command branch ends in a different context "
            + "than preceding branches: {else}</div>",
        join(
            "{namespace ns}\n\n",
            "{template foo kind=\"js\"}\n",
            "  {@param foo: ?}\n",
            "{if $foo}`<div a=\"q\">{else}</div>{/if}`\n",
            "{/template}"));
  }

  @Test
  public void testLiteral() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "<script>",
            "{lb}$a{rb}",
            "</script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "<script>\n",
            "{literal}{$a}{/literal}\n",
            "</script>\n",
            "{/template}"));
  }

  @Test
  public void testForLoop() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param n: ?}\n",
            "<style>",
            "{for $i in range($n)}",
            ".foo{$i |filterCssValue}:before {lb}",
            "content: '{$i |escapeCssString}'",
            "{rb}",
            "{/for}",
            "</style>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param n: ?}\n",
            "<style>\n",
            "{for $i in range($n)}\n",
            "  .foo{$i}:before {lb}\n",
            "    content: '{$i}'\n",
            "  {rb}\n",
            "{/for}",
            "</style>\n",
            "{/template}"));
  }

  @Test
  public void testBrokenForLoop() throws Exception {
    assertRewriteFails(
        "{for} body does not end in the same context after repeated entries.",
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param n: ?}\n",
            "  <style>\n",
            "    {for $i in range($n)}\n",
            "      .foo{$i}:before {lb}\n",
            "        content: '{$i}\n", // Missing close quote.
            "      {rb}\n",
            "    {/for}\n",
            "  </style>\n",
            "{/template}"));
  }

  @Test
  public void testForeachLoop() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template baz}\n",
            "  {@param foo: ?}\n",
            "<ol>",
            "{for $x in $foo}",
            "<li>{$x |escapeHtml}</li>",
            "{/for}",
            "</ol>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template baz}\n",
            "  {@param foo: ?}\n",
            "  <ol>\n",
            "    {for $x in $foo}\n",
            "      <li>{$x}</li>\n",
            "    {/for}\n",
            "  </ol>\n",
            "{/template}"));
  }

  @Test
  public void testCall() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param world: ?}\n",
            "{call bar data=\"all\" /}\n",
            "{/template}\n\n",
            "{template bar}\n",
            "  {@param world: ?}\n",
            "Hello, {$world |escapeHtml}!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param world: ?}\n",
            "  {call bar data=\"all\" /}\n",
            "{/template}\n\n",
            "{template bar}\n",
            "  {@param world: ?}\n",
            "  Hello, {$world}!\n",
            "{/template}"));
  }

  @Test
  public void testCallWithParams() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param? x: ?}\n",
            "{call bar}{param world: $x + 1 /}{/call}\n",
            "{/template}\n\n",
            "{template bar}\n",
            "  {@param? world: ?}\n",
            "Hello, {$world |escapeHtml}!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param? x: ?}\n",
            "{call bar}{param world: $x + 1 /}{/call}\n",
            "{/template}\n\n",
            "{template bar}\n",
            "  {@param? world: ?}\n",
            "Hello, {$world}!\n",
            "{/template}"));
  }

  @Test
  public void testTemplateUnknownJsSlashMatters() throws Exception {
    assertRewriteFails(
        "Slash (/) cannot follow the preceding branches since it is unclear whether the slash"
            + " is a RegExp literal or division operator."
            + "  Consider adding parentheses to disambiguate",
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param? declare : ?}\n",
            "  <script>\n",
            "    {if $declare}var{sp}{/if}\n",
            "    x = 42\n",
            "        {if $declare} ,{/if}\n",
            // At this point we don't know whether or not a slash would start
            // a RegExp or not, so this constitutes an error.
            "    / 2",
            "  </script>\n",
            "{/template}\n"));
  }

  @Test
  public void testUrlContextJoining() throws Exception {
    // This is fine.  The ambiguity about
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param c: ?}\n",
            "<a href=\"",
            "{if $c}",
            "/foo?bar=baz",
            "{else}",
            "/boo",
            "{/if}",
            "\"></a>\n",
            "{/template}"));
    assertRewriteFails(
        "Cannot determine which part of the URL this dynamic"
            + " value is in. Most likely, a preceding conditional block began a ?query or "
            + "#fragment, but only on one branch.",
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "  {@param c: ?}\n",
            "<a href=\"",
            "{if $c}",
            "/foo?bar=baz&boo=",
            "{else}",
            "/boo/",
            "{/if}",
            "{$x}",
            "\"></a>\n",
            "{/template}"));
  }

  @Test
  public void testUrlMaybeVariableSchemePrintStatement() throws Exception {
    assertRewriteFails(
        "Soy can't prove this URI concatenation has a safe"
            + " scheme at compile time. Either combine adjacent print statements (e.g. {$x + $y}"
            + " instead of {$x}{$y}), or introduce disambiguating characters"
            + " (e.g. {$x}/{$y}, {$x}?y={$y}, {$x}&y={$y}, {$x}#{$y})",
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "<a href=\"{$x}{$y}\">Test</a>\n",
            "{/template}"));
  }

  @Test
  public void testUrlMaybeVariableSchemeColon() throws Exception {
    assertRewriteFails(
        "Soy can't safely process a URI that might start "
            + "with a variable scheme. For example, {$x}:{$y} could have an XSS if $x is "
            + "'javascript' and $y is attacker-controlled. Either use a hard-coded scheme, or "
            + "introduce disambiguating characters (e.g. http://{$x}:{$y}, ./{$x}:{$y}, "
            + "or {$x}?foo=:{$y})",
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<a href=\"{$x}:foo()\">Test</a>\n",
            "{/template}"));
  }

  @Test
  public void testUrlMaybeSchemePrintStatement() throws Exception {
    assertRewriteFails(
        ""
            + "Soy can't prove this URI has a safe scheme at compile time. Either make sure one of"
            + " ':', '/', '?', or '#' comes before the dynamic value (e.g. foo/{$bar}), or move the"
            + " print statement to the start of the URI to enable runtime validation"
            + " (e.g. href=\"{'foo' + $bar}\" instead of href=\"foo{$bar}\").",
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<a href=\"foo{$x}\">Test</a>\n",
            "{/template}"));
  }

  @Test
  public void testUrlDangerousSchemeForbidden() throws Exception {
    String message =
        "Soy can't properly escape for this URI scheme. For image sources, you can print full"
            + " data and blob URIs directly (e.g. src=\"{$someDataUri}\")."
            + " Otherwise, hardcode the full URI in the template or pass a complete"
            + " SanitizedContent or SafeUrl object.";
    assertRewriteFails(
        message,
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<a href=\"javas{nil}cript:{$x}\"></a>\n",
            "{/template}"));
    assertRewriteFails(
        message,
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<style>url('javas{nil}cript:{$x}')</style>\n",
            "{/template}"));
    assertRewriteFails(
        message,
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<style>url(\"javascript:{$x}\")</style>\n",
            "{/template}"));
    assertRewriteFails(
        message,
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<style>url(\"javascript:alert({$x})\")</style>\n",
            "{/template}"));
    assertRewriteFails(
        message,
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<style>url(javascript:{$x})</style>\n",
            "{/template}"));

    assertRewriteFails(
        message,
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<style>url(data:{$x})</style>\n",
            "{/template}"));
    assertRewriteFails(
        message,
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<a href=\"data:{$x}\"></a>\n",
            "{/template}"));
    assertRewriteFails(
        message,
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<a href=\"blob:{$x}\"></a>\n",
            "{/template}"));
    assertRewriteFails(
        message,
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<a href=\"filesystem:{$x}\"></a>\n",
            "{/template}"));

    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<a href=\"not-javascript:{$x |escapeHtmlAttribute}\">Test</a>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<a href=\"not-javascript:{$x}\">Test</a>\n",
            "{/template}"));
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<a href=\"javascript-foo:{$x |escapeHtmlAttribute}\">Test</a>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<a href=\"javascript-foo:{$x}\">Test</a>\n",
            "{/template}"));
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<a href=\"not?javascript:{$x |escapeUri}\">Test</a>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<a href=\"not?javascript:{$x}\">Test</a>\n",
            "{/template}"));
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "<a href=\"javascript:hardcoded()\">Test</a>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "<a href=\"javascript:hardcoded()\">Test</a>\n",
            "{/template}"));
  }

  @Test
  public void testUris() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param url: ?}\n",
            "  {@param bgimage: ?}\n",
            "  {@param anchor: ?}\n",
            "  {@param file: ?}\n",
            "  {@param brdr: ?}\n",
            // We use filterNormalizeUri at the beginning,
            "<a href='{$url |filterNormalizeUri |escapeHtmlAttribute}'",
            " style='background:url({$bgimage |filterNormalizeMediaUri |escapeHtmlAttribute})'>",
            "Hi</a>",
            "<a href='#{$anchor |escapeHtmlAttribute}'",
            // escapeUri for substitutions into queries.
            " style='background:url(&apos;/pic?q={$file |escapeUri}&apos;)'>",
            "Hi",
            "</a>",
            "<style>",
            "body {lb} background-image: url(\"{$bgimage |filterNormalizeMediaUri}\"); {rb}",
            // and normalizeUri without the filter in the path.
            "table {lb} border-image: url(\"borders/{$brdr |normalizeUri}\"); {rb}",
            "</style>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template bar}\n",
            "  {@param url: ?}\n",
            "  {@param bgimage: ?}\n",
            "  {@param anchor: ?}\n",
            "  {@param file: ?}\n",
            "  {@param brdr: ?}\n",
            "<a href='{$url}' style='background:url({$bgimage})'>Hi</a>\n",
            "<a href='#{$anchor}'\n",
            " style='background:url(&apos;/pic?q={$file}&apos;)'>Hi</a>\n",
            "<style>\n",
            "body {lb} background-image: url(\"{$bgimage}\"); {rb}\n",
            "table {lb} border-image: url(\"borders/{$brdr}\"); {rb}\n",
            "</style>\n",
            "{/template}"));
  }

  @Test
  public void testTrustedResourceUri() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param start: ?}\n",
            "  {@param path: ?}\n",
            "  {@param query: ?}\n",
            "  {@param fragment: ?}\n",
            "<script src='{$start |filterTrustedResourceUri |escapeHtmlAttribute}",
            "/{$path |escapeUri}?q={$query |escapeUri}#{$fragment |escapeUri}'></script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param start: ?}\n",
            "  {@param path: ?}\n",
            "  {@param query: ?}\n",
            "  {@param fragment: ?}\n",
            "<script src='{$start}/{$path}?q={$query}#{$fragment}'></script>",
            "{/template}\n"));
  }

  @Test
  public void testTrustedResourceUrlKindBlocks() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "{let $src kind=\"trusted_resource_uri\"}/foo.js{/let}",
            "<script src='{$src |escapeHtmlAttribute}'></script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "{let $src kind=\"trusted_resource_uri\"}/foo.js{/let}",
            "<script src='{$src}'></script>\n",
            "{/template}"));
  }

  @Test
  public void testCss() throws Exception {
    assertContextualRewriting(
        join("{namespace ns}\n\n", "{template foo}\n", "{css('foo') |escapeHtml}\n", "{/template}"),
        join("{namespace ns}\n\n", "{template foo}\n", "{css('foo')}\n", "{/template}"));
  }

  @Test
  public void testXid() throws Exception {
    assertContextualRewriting(
        join("{namespace ns}\n\n", "{template foo}\n", "{xid('foo') |escapeHtml}\n", "{/template}"),
        join("{namespace ns}\n\n", "{template foo}\n", "{xid('foo')}\n", "{/template}"));
  }

  @Test
  public void testAlreadyEscaped() throws Exception {
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param FOO: ?}\n",
            "<script>a = \"{$FOO |escapeUri}\";</script>\n",
            "{/template}"));
  }

  @Test
  public void testCustomDirectives() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "{$x |customOtherDirective |escapeHtml}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "  {$x |customOtherDirective}\n",
            "{/template}"));
  }

  @Test
  public void testUnquotedAttributes() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param msg: ?}\n",
            "<button onclick=alert({$msg |escapeJsValue |escapeHtmlAttributeNospace})>",
            "Launch</button>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param msg: ?}\n",
            "<button onclick=alert({$msg})>Launch</button>\n",
            "{/template}"));
  }

  @Test
  public void testMessagesWithEmbeddedTags() throws Exception {
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "{msg desc=\"Say hello\"}Hello, <b>World</b>{/msg}\n",
            "{/template}"));
  }

  @Test
  public void testNamespaces() throws Exception {
    // Test calls in namespaced files.
    assertContextualRewriting(
        join(
            "{namespace soy.examples.codelab}\n\n",
            "/** */\n",
            "{template main}\n",
            "<title>{call pagenum data=\"all\" /}</title>",
            "",
            "<script>",
            "var pagenum = \"{call pagenum data=\"all\" /}\"; ",
            "...",
            "</script>\n",
            "{/template}\n\n",
            "{template pagenum visibility=\"private\" kind=\"text\"}\n",
            "  {@param pageIndex: ?}\n",
            "  {@param pageCount: ?}\n",
            "{$pageIndex |text} of {$pageCount |text}\n",
            "{/template}"),
        join(
            "{namespace soy.examples.codelab}\n\n",
            "/** */\n",
            "{template main}\n",
            "  <title>{call pagenum data=\"all\" /}</title>\n",
            "  <script>\n",
            "    var pagenum = \"{call pagenum data=\"all\" /}\";\n",
            "    ...\n",
            "  </script>\n",
            "{/template}\n\n",
            "{template pagenum visibility=\"private\" kind=\"text\"}\n",
            "  {@param pageIndex: ?}\n",
            "  {@param pageCount: ?}\n",
            "  {$pageIndex} of {$pageCount}\n",
            "{/template}"));
  }

  @Test
  public void testConditionalAttributes() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param className: ?}\n",
            "<div{if $className} class=\"{$className |escapeHtmlAttribute}\"{/if}></div>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param className: ?}\n",
            "<div{if $className} class=\"{$className}\"{/if}></div>\n",
            "{/template}"));
  }

  @Test
  public void testExtraSpacesInTag() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param className: ?}\n",
            "<div{if $className} class=\"{$className |escapeHtmlAttribute}\"{/if} id=x></div>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param className: ?}\n",
            "<div {if $className} class=\"{$className}\"{/if} id=x></div>\n",
            "{/template}"));
  }

  @Test
  public void testOptionalAttributes() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template icontemplate}\n",
            "  {@param iconId: ?}\n",
            "  {@param iconClass: ?}\n",
            "  {@param iconPath: ?}\n",
            "  {@param title: ?}\n",
            "  {@param alt: ?}\n",
            "<img class=\"{$iconClass |escapeHtmlAttribute}\"",
            "{if $iconId}",
            " id=\"{$iconId |escapeHtmlAttribute}\"",
            "{/if}",
            " src=",
            "{if $iconPath}",
            "\"{$iconPath |filterNormalizeMediaUri |escapeHtmlAttribute}\"",
            "{else}",
            "\"images/cleardot.gif\"",
            "{/if}",
            "{if $title}",
            " title=\"{$title |escapeHtmlAttribute}\"",
            "{/if}",
            " alt=\"",
            "{if $alt || $alt == ''}",
            "{$alt |escapeHtmlAttribute}",
            "{elseif $title}",
            "{$title |escapeHtmlAttribute}",
            "{/if}\"",
            ">\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template icontemplate}\n",
            "  {@param iconId: ?}\n",
            "  {@param iconClass: ?}\n",
            "  {@param iconPath: ?}\n",
            "  {@param title: ?}\n",
            "  {@param alt: ?}\n",
            "<img class=\"{$iconClass}\"",
            "{if $iconId}",
            " id=\"{$iconId}\"",
            "{/if}",
            // Double quotes inside if/else.
            " src=",
            "{if $iconPath}",
            "\"{$iconPath}\"",
            "{else}",
            "\"images/cleardot.gif\"",
            "{/if}",
            "{if $title}",
            " title=\"{$title}\"",
            "{/if}",
            " alt=\"",
            "{if $alt || $alt == ''}",
            "{$alt}",
            "{elseif $title}",
            "{$title}",
            "{/if}\"",
            ">\n",
            "{/template}"));
  }

  @Test
  public void testSvgImage() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template image}\n",
            "  {@param iconPath: ?}\n",
            "<svg>",
            "<image xlink:href=\"{$iconPath |filterNormalizeMediaUri |escapeHtmlAttribute}\">",
            "</image>",
            "</svg>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template image}\n",
            "  {@param iconPath: ?}\n",
            "<svg>",
            "<image xlink:href=\"{$iconPath}\"></image>",
            "</svg>\n",
            "{/template}"));
  }

  @Test
  public void testDynamicAttrName() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param baz: ?}\n",
            "<img src=\"bar\" {$baz |filterHtmlAttributes}=\"boo\">\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param baz: ?}\n",
            "<img src=\"bar\" {$baz}=\"boo\">\n",
            "{/template}"));
  }

  @Test
  public void testDynamicAttributes() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param baz: ?}\n",
            "<img src=\"bar\"{$baz |filterHtmlAttributes |whitespaceHtmlAttributes}>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param baz: ?}\n",
            "<img src=\"bar\" {$baz}>\n",
            "{/template}"));
  }

  @Test
  public void testDynamicAttributeValue() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param baz: ?}\n",
            "<img x=x{$baz |escapeHtmlAttributeNospace}x>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param baz: ?}\n",
            "<img x=x{$baz}x>\n",
            "{/template}"));

    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param baz: ?}\n",
            "<img x='x{$baz |escapeHtmlAttribute}x'>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param baz: ?}\n",
            "<img x='x{$baz}x'>\n",
            "{/template}"));

    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param baz: ?}\n",
            "<img x=\"x{$baz |escapeHtmlAttribute}x\">\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param baz: ?}\n",
            "<img x=\"x{$baz}x\">\n",
            "{/template}"));
  }

  @Test
  public void testDynamicElementName() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo stricthtml=\"false\"}\n",
            "  {@param x: ?}\n",
            "<{$x |filterHtmlElementName}>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo stricthtml=\"false\"}\n",
            "  {@param x: ?}\n",
            "<{legacyDynamicTag($x)}>\n",
            "{/template}"));
  }

  @Test
  public void testDirectivesOrderedProperly() throws Exception {
    // The |bidiSpanWrap directive takes HTML and produces HTML, so the |escapeHTML
    // should appear first.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "{$x |escapeHtml |bidiSpanWrap}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "{$x |bidiSpanWrap}\n",
            "{/template}"));
  }

  @Test
  public void testTypedLetBlockIsContextuallyEscaped() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template t}\n",
            "  {@param y: ?}\n",
            "<script> var y = '",
            // Note that the contents of the {let} block are escaped in HTML PCDATA context, even
            // though it appears in a JS string context in the template.
            "{let $l kind=\"html\"}",
            "<div>{$y |escapeHtml}</div>",
            "{/let}",
            "{$y |escapeJsString}'</script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template t}\n",
            "  {@param y: ?}\n",
            "<script> var y = '\n",
            "{let $l kind=\"html\"}\n",
            "<div>{$y}</div>",
            "{/let}",
            "{$y}'</script>\n",
            "{/template}"));
  }

  @Test
  public void testTypedParamBlockIsContextuallyEscaped() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template caller}\n",
            "  {@param y: ?}\n",
            "<div>",
            "{call callee}",
            "{param x kind=\"html\"}",
            "<script> var y ='{$y |escapeJsString}';</script>",
            "{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template callee visibility=\"private\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x |escapeHtml}</b>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template caller}\n",
            "  {@param y: ?}\n",
            "<div>",
            "{call callee}{param x kind=\"html\"}",
            "<script> var y ='{$y}';</script>",
            "{/param}{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template callee visibility=\"private\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}"));
  }

  @Test
  public void testTypedTextParamBlock() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template caller}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {@param z: ?}\n",
            "<div>",
            "{call callee}",
            "{param x kind=\"text\"}",
            "Hello {$x |text} <{$y |text}, \"{$z |text}\">",
            "{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n",
            "\n",
            "{template callee visibility=\"private\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x |escapeHtml}</b>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template caller}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {@param z: ?}\n",
            "<div>",
            "{call callee}{param x kind=\"text\"}",
            "Hello {$x} <{$y}, \"{$z}\">",
            "{/param}{/call}",
            "</div>\n",
            "{/template}\n",
            "\n",
            "{template callee visibility=\"private\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}"));
  }

  @Test
  public void testTypedTextLetBlock() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {@param z: ?}\n",
            "{let $a kind=\"text\"}",
            "Hello {$x |text} <{$y |text}, \"{$z |text}\">",
            "{/let}",
            "{$a |escapeHtml}",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {@param z: ?}\n",
            "{let $a kind=\"text\"}",
            "Hello {$x} <{$y}, \"{$z}\">",
            "{/let}",
            "{$a}",
            "\n{/template}"));
  }

  @Test
  public void testStrictModeAllowsNonAutoescapeCancellingDirectives() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param foo: ?}\n",
            "<b>{$foo |customOtherDirective |escapeHtml}</b>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param foo: ?}\n",
            "<b>{$foo |customOtherDirective}</b>\n",
            "{/template}"));
  }

  @Test
  public void testStrictModeRequiresStartAndEndToBeCompatible() {
    assertRewriteFails(
        "A block of kind=\"js\" cannot end in context (Context JS_SQ_STRING). "
            + "Likely cause is an unterminated string literal.",
        join("{namespace ns}\n\n", "{template main kind=\"js\"}\nvar x='\n{/template}\n"));
  }

  @Test
  public void testStrictUriMustNotBeEmpty() {
    assertRewriteFails(
        "A block of kind=\"uri\" cannot end in context (Context URI START NORMAL). "
            + "Likely cause is an unterminated or empty URI.",
        join("{namespace ns}\n\n", "{template main kind=\"uri\"}\n", "{/template}"));
  }

  @Test
  public void testContextualCanCallStrictModeUri() {
    // This ensures that a contextual template ns.can use a strict URI -- specifically testing that
    // the contextual call site matching doesn't do an exact match on context (which would be
    // sensitive to whether single quotes or double quotes are used) but uses the logic in
    // Context.isValidStartContextForContentKindLoose().
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "<a href=\"{call bar data=\"all\" /}\">Test</a>",
            "\n{/template}\n\n",
            "{template bar kind=\"uri\"}\n",
            "  {@param x: ?}\n",
            "http://www.google.com/search?q={$x |escapeUri}",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "<a href=\"{call bar data=\"all\" /}\">Test</a>",
            "\n{/template}\n\n",
            "{template bar kind=\"uri\"}\n",
            "  {@param x: ?}\n",
            "http://www.google.com/search?q={$x}",
            "\n{/template}"));
  }

  @Test
  public void testStrictAttributes() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo kind=\"attributes\"}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {@param z: ?}\n",
            "onclick={$x |escapeJsValue |escapeHtmlAttributeNospace} ",
            "style='{$y |filterCssValue |escapeHtmlAttribute}' ",
            "checked ",
            "foo=\"bar\" ",
            "title='{$z |escapeHtmlAttribute}'",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo kind=\"attributes\"}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {@param z: ?}\n",
            "onclick={$x} ",
            "style='{$y}' ",
            "checked ",
            "foo=\"bar\" ",
            "title='{$z}'",
            "\n{/template}"));
  }

  @Test
  public void testStrictAttributesMustNotEndInUnquotedAttributeValue() {
    // Ensure that any final attribute-value pair is quoted -- otherwise, if the use site of the
    // value forgets to add spaces, the next attribute will be swallowed.
    // The html rewriting mode allows for this since there is no way to interpolate this template
    // into another tag attribute that creates ambiguity.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo kind=\"attributes\"}\n",
            "  {@param x: ?}\n",
            "onclick={$x |escapeJsValue |escapeHtmlAttributeNospace}",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo kind=\"attributes\"}\n",
            "  {@param x: ?}\n",
            "onclick={$x}",
            "\n{/template}"));

    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo kind=\"attributes\"}\n",
            "  {@param x: ?}\n",
            "title={$x |escapeHtmlAttributeNospace}",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo kind=\"attributes\"}\n",
            "  {@param x: ?}\n",
            "title={$x}",
            "\n{/template}"));
  }

  @Test
  public void testStrictAttributesCanEndInValuelessAttribute() {
    // Allow ending in a valueless attribute like "checked". Unfortunately a sloppy user might end
    // up having this collide with another attribute name.
    // TODO: In the future, we might automatically add a space to the end of strict attributes.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo kind=\"attributes\"}\n",
            "foo=bar checked",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo kind=\"attributes\"}\n",
            "foo=bar checked",
            "\n{/template}"));
  }

  @Test
  public void testStrictModeJavascriptRegexHandling() {
    // NOTE: This ensures that the call site is treated as a dynamic value, such that it switches
    // from "before regexp" context to "before division" context. Note this isn't foolproof (such
    // as when the expression leads to a full statement) but is generally going to be correct more
    // often.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<script>",
            "{call bar /}/{$x |escapeJsValue |filterHtmlScriptPhrasingData}+/{$x |escapeJsRegex}/g",
            "</script>",
            "\n{/template}",
            "\n\n{template bar kind='text'}\n\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "  {@param x: ?}\n",
            "<script>",
            "{call bar /}/{$x}+/{$x}/g",
            "</script>",
            "\n{/template}",
            "\n\n{template bar kind='text'}\n\n{/template}"));
  }

  @Test
  public void testStrictModeEscapesCallSites() {
    String source =
        "{namespace ns}\n\n"
            + "{template main}\n"
            + "{call htmltemplate /}"
            + "<script>var x={call jstemplate /};</script>\n"
            + "\n{/template}\n\n"
            + "{template htmltemplate}\n"
            + "Hello World"
            + "\n{/template}\n\n"
            + "{template jstemplate kind=\"js\"}\n"
            + "foo()"
            + "\n{/template}";

    TemplateNode mainTemplate = (TemplateNode) rewrite(source).getChild(0);
    assertWithMessage("Sanity check").that(mainTemplate.getTemplateName()).isEqualTo("ns.main");
    List<CallNode> callNodes = SoyTreeUtils.getAllNodesOfType(mainTemplate, CallNode.class);
    assertThat(callNodes).hasSize(2);
    assertWithMessage("HTML->HTML escaping should be pruned")
        .that(callNodes.get(0).getEscapingDirectives())
        .isEmpty();
    assertWithMessage("JS -> JS pruned").that(callNodes.get(1).getEscapingDirectives()).isEmpty();
  }

  private ImmutableList<String> getDirectiveNames(
      ImmutableList<SoyPrintDirective> escapingDirectives) {
    return escapingDirectives.stream().map(SoyPrintDirective::getName).collect(toImmutableList());
  }

  private static String getForbiddenMsgError(String context) {
    return "Messages are not supported in this context, because it would mean asking translators "
        + "to write source code; if this is desired, try factoring the message into a {let} block: "
        + "(Context "
        + context
        + ")";
  }

  @Test
  public void testMsgForbiddenUriStartContext() {
    assertRewriteFails(
        getForbiddenMsgError("URI NORMAL URI DOUBLE_QUOTE START NORMAL"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  <a href=\"{msg desc=\"foo\"}message{/msg}\">test</a>\n",
            "{/template}"));
    assertRewriteFails(
        getForbiddenMsgError("URI NORMAL URI DOUBLE_QUOTE START NORMAL"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  <a href=\"{msg desc=\"foo\"}message{/msg}\">test</a>\n",
            "{/template}"));
    assertRewriteFails(
        getForbiddenMsgError("URI START NORMAL"),
        join(
            "{namespace ns}\n\n",
            "{template main kind=\"uri\"}\n",
            "  {msg desc=\"foo\"}message{/msg}\n",
            "{/template}"));
  }

  @Test
  public void testMsgForbiddenJsContext() {
    assertRewriteFails(
        getForbiddenMsgError("JS REGEX"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  <script>{msg desc=\"foo\"}message{/msg}</script>\n",
            "{/template}"));
    assertRewriteFails(
        getForbiddenMsgError("JS REGEX"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  <script>{msg desc=\"foo\"}message{/msg}</script>\n",
            "{/template}"));
    assertRewriteFails(
        getForbiddenMsgError("JS REGEX"),
        join(
            "{namespace ns}\n\n",
            "{template main kind=\"js\"}\n",
            "  {msg desc=\"foo\"}message{/msg}\n",
            "{/template}"));
  }

  @Test
  public void testMsgForbiddenHtmlContexts() {
    assertRewriteFails(
        getForbiddenMsgError("HTML_TAG NORMAL"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  <div {msg desc=\"foo\"}attributes{/msg}>Test</div>\n",
            "{/template}"));
    assertRewriteFails(
        getForbiddenMsgError("HTML_TAG"),
        join(
            "{namespace ns}\n\n",
            "{template main kind=\"attributes\"}\n",
            "  {msg desc=\"foo\"}message{/msg}\n",
            "{/template}"));
  }

  @Test
  public void testMsgForbiddenHtmlAttributeContexts() {
    assertRewriteFails(
        "Messages are not supported in this context because a space in the translation would "
            + "end the attribute value. Wrap the attribute value into quotes.",
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  <p title={msg desc=\"\"}a{/msg}>\n",
            "{/template}"));
  }

  @Test
  public void testMsgForbiddenCssContext() {
    assertRewriteFails(
        getForbiddenMsgError("CSS"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  <style>{msg desc=\"foo\"}message{/msg}</style>\n",
            "{/template}"));
    assertRewriteFails(
        getForbiddenMsgError("CSS"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  <style>{msg desc=\"foo\"}message{/msg}</style>\n",
            "{/template}"));
    assertRewriteFails(
        getForbiddenMsgError("CSS"),
        join(
            "{namespace ns}\n\n",
            "{template main kind=\"css\"}\n",
            "  {msg desc=\"foo\"}message{/msg}\n",
            "{/template}"));
  }

  // Regression test for a bug where we would enter rcdata area but not exit it, so things after it
  // would be stuck in rcdata context.  the issue is that the 'close tag' inside of rcdata would
  // cause use to early exit the search for transition points
  @Test
  public void testExitSpecialRcDataArea() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param p: ?}\n",
            "<textarea>{$p |escapeHtmlRcdata}</div></textarea>",
            "{$p |escapeHtml}", // this used to be |escapeHtmlRcData
            "<textarea></textarea>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param p: ?}\n",
            "<textarea>{$p}</div></textarea>",
            "{$p}",
            // this is needed at the end to prevent the bug from causing an error
            "<textarea></textarea>",
            "{/template}"));
  }

  @Test
  public void testUnescapeAttributeValues() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param p: ?}\n",
            "<div style='url(\"{$p |filterNormalizeUri |escapeHtmlAttribute}\");'></div>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param p: ?}\n",
            "<div style='url(\"{$p}\");'></div>\n",
            "{/template}"));
    // it works even if the internal quotation marks are escaped, since we unescape as part of
    // ordaining
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param p: ?}\n",
            "<div style='url(&quot;{$p |filterNormalizeUri |escapeHtmlAttribute}&quot;);'></div>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param p: ?}\n",
            "<div style='url(&quot;{$p}&quot;);'></div>\n",
            "{/template}"));
  }

  // Test that certain constructs generate data that we trust at compile time.
  @Test
  public void testShortCircuitableDirectives() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param untrusted: html}\n",
            "{let $trusted kind=\"html\"}foo{/let}",
            // parameters are not currently trusted but local variables are.
            "{$untrusted |escapeHtml}{$trusted}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param untrusted: html}\n",
            "{let $trusted kind=\"html\"}foo{/let}\n",
            "{$untrusted}{$trusted}\n",
            "{/template}"));

    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param untrusted: html}\n",
            "{call sub}{param trusted kind=\"html\"}foo{/param}{/call}",
            "{call sub2}{param untrusted: $untrusted /}{/call}\n",
            "{/template}\n\n",
            "{template sub visibility=\"private\"}\n",
            "  {@param trusted: html}\n",
            "{$trusted}\n",
            "{/template}\n\n",
            "{template sub2 visibility=\"private\"}\n",
            "  {@param untrusted: html}\n",
            "{$untrusted |escapeHtml}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param untrusted: html}\n",
            "{call sub}{param trusted kind=\"html\"}foo{/param}{/call}",
            "{call sub2}{param untrusted: $untrusted /}{/call}\n",
            "{/template}\n\n",
            "{template sub visibility=\"private\"}\n",
            "  {@param trusted: html}\n",
            "{$trusted}\n",
            "{/template}\n\n",
            "{template sub2 visibility=\"private\"}\n",
            "  {@param untrusted: html}\n",
            "{$untrusted}\n",
            "{/template}"));
  }

  @Test
  public void testScriptPhrasingData() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param untrusted: ?}\n",
            "<script>{$untrusted |escapeJsValue |filterHtmlScriptPhrasingData}</script>",
            "<script type='text/svg'>{$untrusted |filterHtmlScriptPhrasingData}</script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param untrusted: ?}\n",
            "<script>{$untrusted}</script>",
            "<script type='text/svg'>{$untrusted}</script>\n",
            "{/template}"));
  }

  @Test
  public void testJsonScript() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param untrusted: ?}\n",
            "<script type='text/json'>{$untrusted |escapeJsValue"
                + " |filterHtmlScriptPhrasingData}</script>",
            "<script type='text/json'>{lb} 'foo': '{$untrusted |escapeJsString}'{rb}</script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template main}\n",
            "  {@param untrusted: ?}\n",
            "<script type='text/json'>{$untrusted}</script>",
            "<script type='text/json'>{lb} 'foo': '{$untrusted}'{rb}</script>\n",
            "{/template}"));
  }

  private static String join(String... lines) {
    return Joiner.on("").join(lines);
  }

  private void assertContextualRewriting(String expectedOutput, String... inputs) {
    String source = rewrite(inputs).toSourceString();
    // remove the nonce, it is just distracting
    source = source.replace(NONCE_DECLARATION, "");
    source = source.replace(NONCE_DECLARATION_STYLE, "");
    source = source.replace(NONCE, "");
    source = source.replace(NONCE_STYLE, "");
    assertThat(source.trim()).isEqualTo(expectedOutput);
  }

  public SoyFileNode rewrite(String... inputs) {
    ErrorReporter reporter = ErrorReporter.create();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(inputs)
            .errorReporter(reporter)
            .allowUnboundGlobals(true)
            .addPrintDirectives(SOY_PRINT_DIRECTIVES)
            .runAutoescaper(true)
            .parse()
            .fileSet();

    if (!reporter.getErrors().isEmpty()) {
      SoyError soyError = reporter.getErrors().get(0);
      String message = soyError.message();
      if (message.startsWith(ContextualAutoescaper.AUTOESCAPE_ERROR_PREFIX)) {
        // Grab the part after the prefix (and the "- " used for indentation).
        message = message.substring(ContextualAutoescaper.AUTOESCAPE_ERROR_PREFIX.length() + 2);
        // Re-throw as an exception, so that tests are easier to write. I considered having the
        // tests explicitly check the error messages; however, there's a substantial risk that some
        // positive test might forget to check the error messages, and it leaves all callers of
        // this with two things to check.
        // TODO(gboyer): Once 100% of the contextual autoescaper's errors are migrated to the error
        // reporter, we can stop throwing and simply add explicit checks in the cases.
        throw new RewriteError(soyError, message);
      } else {
        throw new IllegalStateException("Unexpected error: " + message);
      }
    }
    return soyTree.getChild(0);
  }

  private static final class RewriteError extends RuntimeException {
    final SoyError error;
    final String origMessage;

    RewriteError(SoyError error, String message) {
      super(error.toString());
      this.error = error;
      this.origMessage = message;
    }
  }

  private static final String NONCE_DECLARATION =
      "  {@inject? csp_nonce: any}  /** Created by ContentSecurityPolicyNonceInjectionPass. */\n";

  private static final String NONCE_DECLARATION_STYLE =
      "  {@inject? csp_style_nonce: any}"
          + "  /** Created by ContentSecurityPolicyNonceInjectionPass. */\n";

  private static final String NONCE =
      "{if $csp_nonce} nonce=\"{$csp_nonce |filterCspNonceValue |escapeHtmlAttribute}\"{/if}";

  private static final String NONCE_STYLE =
      "{if $csp_style_nonce}"
          + " nonce=\"{$csp_style_nonce |filterCspNonceValue |escapeHtmlAttribute}\"{/if}";

  private void assertContextualRewritingNoop(String expectedOutput) {
    assertContextualRewriting(expectedOutput, expectedOutput);
  }

  /**
   * @param msg Message that should be reported to the template ns.author. Null means don't care.
   */
  private void assertRewriteFails(@Nullable String msg, String... inputs) {
    try {
      rewrite(inputs);
      fail();
    } catch (RewriteError ex) {
      if (msg != null && !msg.equals(ex.origMessage)) {
        ComparisonFailure comparisonFailure = new ComparisonFailure("", msg, ex.origMessage);
        comparisonFailure.initCause(ex);
        throw comparisonFailure;
      }
    }
  }
}
