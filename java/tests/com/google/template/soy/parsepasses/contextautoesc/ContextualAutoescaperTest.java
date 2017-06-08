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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import junit.framework.ComparisonFailure;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ContextualAutoescaperTest {

  /** Custom print directives used in tests below. */
  private static final ImmutableMap<String, SoyPrintDirective> SOY_PRINT_DIRECTIVES =
      ImmutableMap.of(
          "|customEscapeDirective",
              new SoyPrintDirective() {
                @Override
                public String getName() {
                  return "|customEscapeDirective";
                }

                @Override
                public Set<Integer> getValidArgsSizes() {
                  return ImmutableSet.of(0);
                }

                @Override
                public boolean shouldCancelAutoescape() {
                  return true;
                }
              },
          "|customOtherDirective",
              new SoyPrintDirective() {
                @Override
                public String getName() {
                  return "|customOtherDirective";
                }

                @Override
                public Set<Integer> getValidArgsSizes() {
                  return ImmutableSet.of(0);
                }

                @Override
                public boolean shouldCancelAutoescape() {
                  return false;
                }
              },
          "|noAutoescape",
              new SoyPrintDirective() {
                @Override
                public String getName() {
                  return "|noAutoescape";
                }

                @Override
                public Set<Integer> getValidArgsSizes() {
                  return ImmutableSet.of(0);
                }

                @Override
                public boolean shouldCancelAutoescape() {
                  return true;
                }
              },
          "|bidiSpanWrap", new FakeBidiSpanWrapDirective());

  @Test
  public void testStrictModeIsDefault() {
    assertRewriteFails(
        "In file no-path:5:4, template ns.main: "
            + "noAutoescape is not allowed in strict autoescaping mode. Instead, pass in a {param} "
            + "with kind=\"html\" or SanitizedContent.",
        join(
            "{namespace ns}\n\n",
            "{template .main}\n",
            "  {@param foo: ?}\n",
            "<b>{$foo|noAutoescape}</b>\n",
            "{/template}"));
  }

  @Test
  public void testTrivialTemplate() throws Exception {
    assertContextualRewriting(
        join("{namespace ns}\n\n", "{template .foo}\n", "Hello, World!\n", "{/template}"),
        join("{namespace ns}\n\n", "{template .foo}\n", "Hello, World!\n", "{/template}"));
  }

  @Test
  public void testUriCallTemplate() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo}\n",
            "<a href={call .uri /} title={call .title /}>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo}\n",
            "<a href={call .uri /} title={call .title /}>\n",
            "{/template}"));
  }

  @Test
  public void testPrintInText() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param world: ?}\n",
            "Hello, {$world |escapeHtml}!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param world: ?}\n",
            "Hello, {$world}!\n",
            "{/template}"));
  }

  @Test
  public void testPrivateTemplate() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .privateFoo autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param world: ?}\n",
            "Hello, {$world |escapeHtml}!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .privateFoo autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param world: ?}\n",
            "Hello, {$world}!\n",
            "{/template}"));
  }

  @Test
  public void testPrintInTextAndLink() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param world: ?}\n",
            "Hello,",
            "<a href='worlds?world={$world |escapeUri}'>",
            "{$world |escapeHtml}",
            "</a>!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
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
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            //"<meta http-equiv=refresh content='{$x |filterNormalizeUri |escapeHtmlAttribute}'>",
            "<a xml:base='{$x |filterNormalizeUri |escapeHtmlAttribute}' href='/foo'>link</a>",
            "<button formaction='{$x |filterNormalizeUri |escapeHtmlAttribute}'>do</button>",
            "<command icon='{$x |filterNormalizeUri |escapeHtmlAttribute}'></command>",
            "<object data='{$x |filterNormalizeUri |escapeHtmlAttribute}'></object>",
            "<video poster='{$x |filterNormalizeUri |escapeHtmlAttribute}'></video>",
            "<video src='{$x |filterNormalizeUri |escapeHtmlAttribute}'></video>",
            "<source src='{$x |filterNormalizeUri |escapeHtmlAttribute}'>",
            "<audio src='{$x |filterNormalizeUri |escapeHtmlAttribute}'></audio>",
            "<script src='{$x |filterTrustedResourceUri |escapeHtmlAttribute}'",
            "></script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            // TODO(msamuel): Re-enable content since it is often (but often not) used to convey
            // URLs in place of <link rel> once we can figure out a good way to distinguish the
            // URL use-cases from others.
            //"<meta http-equiv=refresh content='{$x}'>\n",
            "<a xml:base='{$x}' href='/foo'>link</a>\n",
            "<button formaction='{$x}'>do</button>\n",
            "<command icon='{$x}'></command>\n",
            "<object data='{$x}'></object>\n",
            "<video poster='{$x}'></video>\n",
            "<video src='{$x}'></video>\n",
            "<source src='{$x}'>\n",
            "<audio src='{$x}'></audio>\n",
            "<script src='{$x}'></script>",
            "{/template}\n"));
  }

  @Test
  public void testConditional() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {@param z: ?}\n",
            "Hello,",
            "{if $x == 1}",
            "{$y |escapeHtml}",
            "{elseif $x == 2}",
            "<script>foo({$z |escapeJsValue})</script>",
            "{else}",
            "World!",
            "{/if}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
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
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param url: ?}\n",
            "  {@param name: ?}\n",
            "  {@param value: ?}\n",
            "<a",
            "{if $url}",
            " href='{$url |filterNormalizeUri |escapeHtmlAttribute}'",
            "{elseif $name}",
            " name='{$name |escapeHtmlAttribute}'",
            "{/if}>",
            " onclick='alert({$value |escapeHtml})'\n", // Not escapeJsValue.
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param url: ?}\n",
            "  {@param name: ?}\n",
            "  {@param value: ?}\n",
            "<a",
            // Each of these branches independently closes the tag.
            "{if $url}",
            " href='{$url}'",
            "{elseif $name}",
            " name='{$name}'",
            "{/if}>",
            // So now make something that looks like a script attribute but which actually
            // appears in a PCDATA.  If the context merge has properly happened is is escaped as
            // PCDATA.
            " onclick='alert({$value})'\n",
            "{/template}"));

    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template .bar}\n",
            "  {@param p: ?}\n",
            "<input{if $p} disabled{/if}>\n",
            "{/template}"));

    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template .bar}\n",
            "  {@param p: ?}\n",
            "  {@param p2: ?}\n",
            "<input{if $p} disabled{/if}{if $p2} checked{/if}>\n",
            "{/template}"));

    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template .bar}\n",
            "  {@param p: ?}\n",
            "  {@param p2: ?}\n",
            "<input {if $p}disabled{/if}{if $p2} checked{/if}>\n",
            "{/template}"));

    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template .good4}\n",
            "  {@param p: ?}\n",
            "<div{if $p} x=x{/if} x=y>\n",
            "{/template}"));

    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template .good4}\n",
            "  {@param p: ?}\n",
            "<div {if $p}onclick=foo() {/if} x=y>\n",
            "{/template}"));

    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template .good4}\n",
            "  {@param p: ?}\n",
            "<div foo=bar {if $p}onclick=foo() {/if} x=y>\n",
            "{/template}"));

    assertContextualRewriting(
        "{namespace ns}\n\n"
            + "{template .good4}\n"
            + "  {@param x: ?}\n"
            + "<input{if $x} onclick={$x |escapeJsValue |escapeHtmlAttributeNospace}{/if}>\n"
            + "{/template}",
        join(
            "{namespace ns}\n\n",
            "{template .good4}\n",
            "  {@param x: ?}\n",
            "\n" + "<input{if $x} onclick={$x}{/if}>\n",
            "{/template}"));

    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template .good4}\n",
            "  {@param p: ?}\n",
            "<input {if $p}disabled=\"true\"{/if}>",
            "<input {if $p}onclick=\"foo()\"{/if}>\n",
            "{/template}"));
  }

  @Test
  public void testSwitch() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {@param z: ?}\n",
            "Hello,",
            "{switch $x}",
            "{case 1}",
            "{$y |escapeHtml}",
            "{case 2}",
            "<script>foo({$z |escapeJsValue})</script>",
            "{default}",
            "World!",
            "{/switch}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
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
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param a: ?}\n",
            "  {@param b: ?}\n",
            "  {@param c: ?}\n",
            "  {@param d: ?}\n",
            "  {@param e: ?}\n",
            "  {@param f: ?}\n",
            "<script>",
            "foo({$a |escapeJsValue}); ",
            "bar(\"{$b |escapeJsString}\"); ",
            "baz(\'{$c |escapeJsString}\'); ",
            "boo(/{$d |escapeJsRegex}/.test(s) ? 1 / {$e |escapeJsValue}",
            " : /{$f |escapeJsRegex}/);",
            "</script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
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
            "</script>\n",
            "{/template}"));
  }

  @Test
  public void testPrintInsideJsCommentRejected() throws Exception {
    assertRewriteFails(
        "In file no-path:5:12, template ns.foo: " + "JS comments cannot contain dynamic values.",
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            // NOTE: Lack of whitespace before "//" makes sure it's not interpreted as a Soy
            // comment.
            "<script>// {$x}</script>\n",
            "{/template}"));
  }

  @Test
  public void testJsStringInsideQuotesRejected() throws Exception {
    assertRewriteFails(
        "In file no-path:5:22, template ns.foo: "
            + "Escaping modes [ESCAPE_JS_VALUE] not compatible with"
            + " (Context JS_SQ_STRING).",
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param world: ?}\n",
            "<script>alert('Hello {$world |escapeJsValue}');</script>\n",
            "{/template}"));
  }

  @Test
  public void testLiteral() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "<script>",
            "{lb}$a{rb}",
            "</script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
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
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param n: ?}\n",
            "<style>",
            "{for $i in range(0, $n, 1)}",
            ".foo{$i |filterCssValue}:before {lb}",
            "content: '{$i |escapeCssString}'",
            "{rb}",
            "{/for}",
            "</style>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
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
        "In file no-path:6:5, template ns.bar: "
            + "{for} command changes context so it cannot be reentered.",
        join(
            "{namespace ns}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param n: ?}\n",
            "  <style>\n",
            "    {for $i in range($n)}\n",
            "      .foo{$i |filterCssValue}:before {lb}\n",
            "        content: '{$i |escapeCssString}\n", // Missing close quote.
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
            "{template .baz autoescape=\"deprecated-contextual\"}\n",
            "  {@param foo: ?}\n",
            "<ol>",
            "{foreach $x in $foo}",
            "<li>{$x |escapeHtml}</li>",
            "{/foreach}",
            "</ol>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .baz autoescape=\"deprecated-contextual\"}\n",
            "  {@param foo: ?}\n",
            "  <ol>\n",
            "    {foreach $x in $foo}\n",
            "      <li>{$x}</li>\n",
            "    {/foreach}\n",
            "  </ol>\n",
            "{/template}"));
  }

  @Test
  public void testForeachLoopWithIfempty() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .baz autoescape=\"deprecated-contextual\"}\n",
            "  {@param foo: ?}\n",
            "<ol>",
            "{foreach $x in $foo}",
            "<li>{$x |escapeHtml}</li>",
            "{ifempty}",
            "<li><i>Nothing</i></li>",
            "{/foreach}",
            "</ol>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .baz autoescape=\"deprecated-contextual\"}\n",
            "  {@param foo: ?}\n",
            "  <ol>\n",
            "    {foreach $x in $foo}\n",
            "      <li>{$x}</li>\n",
            "    {ifempty}\n",
            "      <li><i>Nothing</i></li>\n",
            "    {/foreach}\n",
            "  </ol>\n",
            "{/template}"));
  }

  @Test
  public void testCall() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param world: ?}\n",
            "{call .bar data=\"all\" /}\n",
            "{/template}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param world: ?}\n",
            "Hello, {$world |escapeHtml}!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param world: ?}\n",
            "  {call .bar data=\"all\" /}\n",
            "{/template}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param world: ?}\n",
            "  Hello, {$world}!\n",
            "{/template}"));
  }

  @Test
  public void testCallWithParams() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param? x: ?}\n",
            "{call .bar}{param world : $x + 1 /}{/call}\n",
            "{/template}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param? world: ?}\n",
            "Hello, {$world |escapeHtml}!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param? x: ?}\n",
            "{call .bar}{param world : $x + 1 /}{/call}\n",
            "{/template}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param? world: ?}\n",
            "Hello, {$world}!\n",
            "{/template}"));
  }

  @Test
  public void testSameTemplateCalledInDifferentContexts() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param world: ?}\n",
            "{call .bar data=\"all\" /}",
            "<script>",
            "alert('{call ns.bar__C15 data=\"all\" /}');",
            "</script>\n",
            "{/template}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param world: ?}\n",
            "Hello, {$world |escapeHtml}!\n",
            "{/template}\n\n",
            "{template .bar__C15 autoescape=\"deprecated-contextual\"}\n",
            "  {@param world: ?}\n",
            "Hello, {$world |escapeJsString}!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param world: ?}\n",
            "  {call .bar data=\"all\" /}\n",
            "  <script>\n",
            "  alert('{call .bar data=\"all\" /}');\n",
            "  </script>\n",
            "{/template}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param world: ?}\n",
            "  Hello, {$world}!\n",
            "{/template}"));
  }

  @Test
  public void testRecursiveTemplateGuessWorks() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "<script>",
            "x = [{call ns.countDown__C4011 data=\"all\" /}]",
            "</script>\n",
            "{/template}\n\n",
            "{template .countDown autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "{if $x > 0}",
            "{print --$x |escapeHtml},",
            "{call .countDown}{param x : $x - 1 /}{/call}",
            "{/if}\n",
            "{/template}\n\n",
            "{template .countDown__C4011 autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "{if $x > 0}",
            "{print --$x |escapeJsValue},",
            "{call ns.countDown__C4011}{param x : $x - 1 /}{/call}",
            "{/if}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "  <script>\n",
            "    x = [{call .countDown data=\"all\" /}]\n",
            "  </script>\n",
            "{/template}\n\n",
            "{template .countDown autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "  {if $x > 0}{print --$x},{call .countDown}{param x : $x - 1 /}{/call}{/if}\n",
            "{/template}"));
  }

  @Test
  public void testTemplateWithUnknownJsSlash() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param declare: ?}\n",
            "<script>",
            "{if $declare}var {/if}",
            "x = {call ns.bar__C4011 /}{\\n}",
            "y = 2",
            "  </script>\n",
            "{/template}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param? declare: ?}\n",
            "42",
            "{if $declare}",
            " , ",
            "{/if}\n",
            "{/template}\n\n",
            "{template .bar__C4011 autoescape=\"deprecated-contextual\"}\n",
            "  {@param? declare: ?}\n",
            "42",
            "{if $declare}",
            " , ",
            "{/if}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param declare: ?}\n",
            "  <script>\n",
            "    {if $declare}var{sp}{/if}\n",
            "    x = {call .bar /}{\\n}\n",
            // At this point we don't know whether or not a slash would start
            // a RegExp or not, but we don't see a slash so it doesn't matter.
            "    y = 2",
            "  </script>\n",
            "{/template}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param? declare: ?}\n",
            // A slash following 42 would be a division operator.
            "  42\n",
            // But a slash following a comma would be a RegExp.
            "  {if $declare} , {/if}\n", //
            "{/template}"));
  }

  @Test
  public void testTemplateUnknownJsSlashMatters() throws Exception {
    assertRewriteFails(
        "In file no-path:8:5, template ns.foo: "
            + "Slash (/) cannot follow the preceding branches since it is unclear whether the slash"
            + " is a RegExp literal or division operator."
            + "  Please add parentheses in the branches leading to `/ 2  </script>`",
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param? declare : ?}\n",
            "  <script>\n",
            "    {if $declare}var{sp}{/if}\n",
            "    x = {call .bar /}\n",
            // At this point we don't know whether or not a slash would start
            // a RegExp or not, so this constitutes an error.
            "    / 2",
            "  </script>\n",
            "{/template}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "  {@param? declare : ?}\n",
            // A slash following 42 would be a division operator.
            "  42\n",
            // But a slash following a comma would be a RegExp.
            "  {if $declare} , {/if}\n", //
            "{/template}"));
  }

  @Test
  public void testUrlContextJoining() throws Exception {
    // This is fine.  The ambiguity about
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param c: ?}\n",
            "<a href=\"",
            "{if $c}",
            "/foo?bar=baz",
            "{else}",
            "/boo",
            "{/if}",
            "\">\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path:6:50, template ns.foo: Cannot determine which part of the URL this dynamic"
            + " value is in. Most likely, a preceding conditional block began a ?query or "
            + "#fragment, but only on one branch.",
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "  {@param c: ?}\n",
            "<a href=\"",
            "{if $c}",
            "/foo?bar=baz&boo=",
            "{else}",
            "/boo/",
            "{/if}",
            "{$x}",
            "\">\n",
            "{/template}"));
  }

  @Test
  public void testUrlMaybeVariableSchemePrintStatement() throws Exception {
    assertRewriteFails(
        "In file no-path:6:14, template ns.foo: Soy can't prove this URI concatenation has a safe"
            + " scheme at compile time. Either combine adjacent print statements (e.g. {$x + $y}"
            + " instead of {$x}{$y}), or introduce disambiguating characters"
            + " (e.g. {$x}/{$y}, {$x}?y={$y}, {$x}&y={$y}, {$x}#{$y})",
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "<a href=\"{$x}{$y}\">Test</a>\n",
            "{/template}"));
  }

  @Test
  public void testUrlMaybeVariableSchemeColon() throws Exception {
    assertRewriteFails(
        "In file no-path:5:14, template ns.foo: Soy can't safely process a URI that might start "
            + "with a variable scheme. For example, {$x}:{$y} could have an XSS if $x is "
            + "'javascript' and $y is attacker-controlled. Either use a hard-coded scheme, or "
            + "introduce disambiguating characters (e.g. http://{$x}:{$y}, ./{$x}:{$y}, "
            + "or {$x}?foo=:{$y})",
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<a href=\"{$x}:foo()\">Test</a>\n",
            "{/template}"));
  }

  @Test
  public void testUrlMaybeSchemePrintStatement() throws Exception {
    assertRewriteFails(
        "In file no-path:5:13, template ns.foo:"
            + " Soy can't prove this URI has a safe scheme at compile time. Either make sure one of"
            + " ':', '/', '?', or '#' comes before the dynamic value (e.g. foo/{$bar}), or move the"
            + " print statement to the start of the URI to enable runtime validation"
            + " (e.g. href=\"{'foo' + $bar}\" instead of href=\"foo{$bar}\").",
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
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
            + " SanitizedContent or SafeUri object.";
    assertRewriteFails(
        "In file no-path:5:26, template ns.foo: " + message,
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<a href=\"javas{nil}cript:{$x}\">\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path:5:29, template ns.foo: " + message,
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<style>url('javas{nil}cript:{$x}')</style>\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path:5:24, template ns.foo: " + message,
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<style>url(\"javascript:{$x}\")</style>\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path:5:30, template ns.foo: " + message,
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<style>url(\"javascript:alert({$x})\")</style>\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path:5:23, template ns.foo: " + message,
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<style>url(javascript:{$x})</style>\n",
            "{/template}"));

    assertRewriteFails(
        "In file no-path:5:17, template ns.foo: " + message,
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<style>url(data:{$x})</style>\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path:5:15, template ns.foo: " + message,
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<a href=\"data:{$x}\">\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path:5:15, template ns.foo: " + message,
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<a href=\"blob:{$x}\">\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path:5:21, template ns.foo: " + message,
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<a href=\"filesystem:{$x}\">\n",
            "{/template}"));

    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<a href=\"not-javascript:{$x |escapeHtmlAttribute}\">Test</a>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<a href=\"not-javascript:{$x}\">Test</a>\n",
            "{/template}"));
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<a href=\"javascript-foo:{$x |escapeHtmlAttribute}\">Test</a>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<a href=\"javascript-foo:{$x}\">Test</a>\n",
            "{/template}"));
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<a href=\"not?javascript:{$x |escapeUri}\">Test</a>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<a href=\"not?javascript:{$x}\">Test</a>\n",
            "{/template}"));
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "<a href=\"javascript:hardcoded()\">Test</a>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "<a href=\"javascript:hardcoded()\">Test</a>\n",
            "{/template}"));
  }

  @Test
  public void testRecursiveTemplateGuessFails() throws Exception {
    assertRewriteFails(
        "In file no-path:5:5, template ns.foo: Error while re-contextualizing template ns.quot in"
            + " context (Context JS REGEX):"
            + "\n- In file no-path:10:27, template ns.quot__C4011: Error while re-contextualizing"
            + " template ns.quot in context (Context JS_DQ_STRING):"
            + "\n- In file no-path:10:5, template ns.quot__C14: {if} command without {else} changes"
            + " context.",
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  <script>\n",
            "    {call .quot data=\"all\" /}\n",
            "  </script>\n",
            "{/template}\n\n",
            "{template .quot autoescape=\"deprecated-contextual\"}\n",
            "  \" {if randomInt(10) < 5}{call .quot data=\"all\" /}{/if}\n",
            "{/template}"));
  }

  @Test
  public void testUris() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
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
            "{template .bar autoescape=\"deprecated-contextual\"}\n",
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
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param start: ?}\n",
            "  {@param path: ?}\n",
            "  {@param query: ?}\n",
            "  {@param fragment: ?}\n",
            "<script src='{$start |filterTrustedResourceUri ",
            "|escapeHtmlAttribute}/{$path |filterTrustedResourceUri |escapeHtmlAttribute}?",
            "q={$query |filterTrustedResourceUri |escapeUri}#{$fragment |filterTrustedResourceUri ",
            "|escapeHtmlAttribute}'></script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param start: ?}\n",
            "  {@param path: ?}\n",
            "  {@param query: ?}\n",
            "  {@param fragment: ?}\n",
            "<script src='{$start}/{$path}?q={$query}#{$fragment}'></script>",
            "{/template}\n"));
  }

  @Test
  public void testBlessStringAsTrustedResourceUrlForLegacy() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param start: ?}\n",
            "  {@param path: ?}\n",
            "  {@param query: ?}\n",
            "  {@param fragment: ?}\n",
            "<script src='",
            "{$start |blessStringAsTrustedResourceUrlForLegacy ",
            "|filterNormalizeUri |escapeHtmlAttribute}",
            "/{$path |blessStringAsTrustedResourceUrlForLegacy ",
            "|escapeHtmlAttribute}",
            "?q={$query |blessStringAsTrustedResourceUrlForLegacy ",
            "|escapeUri}",
            "#{$fragment |blessStringAsTrustedResourceUrlForLegacy ",
            "|escapeHtmlAttribute}'></script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param start: ?}\n",
            "  {@param path: ?}\n",
            "  {@param query: ?}\n",
            "  {@param fragment: ?}\n",
            "<script src='{$start |blessStringAsTrustedResourceUrlForLegacy}",
            "/{$path |blessStringAsTrustedResourceUrlForLegacy}",
            "?q={$query |blessStringAsTrustedResourceUrlForLegacy}",
            "#{$fragment |blessStringAsTrustedResourceUrlForLegacy}'></script>",
            "{/template}\n"));
  }

  @Test
  public void testCss() throws Exception {
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "{css foo}\n",
            "{/template}"));
  }

  @Test
  public void testXid() throws Exception {
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "{xid foo}\n",
            "{/template}"));
  }

  @Test
  public void testAlreadyEscaped() throws Exception {
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param FOO: ?}\n",
            "<script>a = \"{$FOO |escapeUri}\";</script>\n",
            "{/template}"));
  }

  @Test
  public void testExplicitNoescapeNoop() throws Exception {
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param FOO: ?}\n",
            "<script>a = \"{$FOO |noAutoescape}\";</script>\n",
            "{/template}"));
  }

  @Test
  public void testCustomDirectives() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "{$x |customEscapeDirective} - {$y |customOtherDirective |escapeHtml}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {$x |customEscapeDirective} - {$y |customOtherDirective}\n",
            "{/template}"));
  }

  @Test
  public void testExternTemplates() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "<script>",
            "var x = {call .bar /},", // Not defined in this compilation unit.
            "y = {$y |escapeJsValue};",
            "</script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "<script>",
            "var x = {call .bar /},", // Not defined in this compilation unit.
            "y = {$y};",
            "</script>\n",
            "{/template}"));
  }

  @Test
  public void testNonContextualCallers() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param? x: ?}\n",
            "{$x |escapeHtml}\n",
            "{/template}\n\n",
            "{template .bar autoescape=\"deprecated-noncontextual\"}\n",
            "  {@param y: ?}\n",
            "<b>{call .foo /}</b> {$y |escapeHtml}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param? x: ?}\n",
            "{$x}\n",
            "{/template}\n\n",
            "{template .bar autoescape=\"deprecated-noncontextual\"}\n",
            "  {@param y: ?}\n",
            "<b>{call .foo /}</b> {$y}\n",
            "{/template}"));
  }

  @Test
  public void testUnquotedAttributes() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param msg: ?}\n",
            "<button onclick=alert({$msg |escapeJsValue |escapeHtmlAttributeNospace})>",
            "Launch</button>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param msg: ?}\n",
            "<button onclick=alert({$msg})>Launch</button>\n",
            "{/template}"));
  }

  @Test
  public void testMessagesWithEmbeddedTags() throws Exception {
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
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
            "{template .main autoescape=\"deprecated-contextual\"}\n",
            "<title>{call soy.examples.codelab.pagenum__C81 data=\"all\" /}</title>",
            "",
            "<script>",
            "var pagenum = \"{call soy.examples.codelab.pagenum__C14 data=\"all\" /}\"; ",
            "...",
            "</script>\n",
            "{/template}\n\n",
            "/**\n",
            " * @param pageIndex 0-indexed index of the current page.\n",
            " * @param pageCount Total count of pages.  Strictly greater than pageIndex.\n",
            " */\n",
            "{template .pagenum autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "{$pageIndex |escapeHtml} of {$pageCount |escapeHtml}\n",
            "{/template}\n\n",
            "{template .pagenum__C81 autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "{$pageIndex |escapeHtmlRcdata} of {$pageCount |escapeHtmlRcdata}\n",
            "{/template}\n\n",
            "{template .pagenum__C14 autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "{$pageIndex |escapeJsString} of {$pageCount |escapeJsString}\n",
            "{/template}"),
        join(
            "{namespace soy.examples.codelab}\n\n",
            "/** */\n",
            "{template .main autoescape=\"deprecated-contextual\"}\n",
            "  <title>{call .pagenum data=\"all\" /}</title>\n",
            "  <script>\n",
            "    var pagenum = \"{call .pagenum data=\"all\" /}\";\n",
            "    ...\n",
            "  </script>\n",
            "{/template}\n\n",
            "/**\n",
            " * @param pageIndex 0-indexed index of the current page.\n",
            " * @param pageCount Total count of pages.  Strictly greater than pageIndex.\n",
            " */\n",
            "{template .pagenum autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {$pageIndex} of {$pageCount}\n",
            "{/template}"));
  }

  @Test
  public void testConditionalAttributes() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param className: ?}\n",
            "<div{if $className} class=\"{$className |escapeHtmlAttribute}\"{/if}>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param className: ?}\n",
            "<div{if $className} class=\"{$className}\"{/if}>\n",
            "{/template}"));
  }

  @Test
  public void testExtraSpacesInTag() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param className: ?}\n",
            "<div {if $className} class=\"{$className |escapeHtmlAttribute}\"{/if} id=x>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param className: ?}\n",
            "<div {if $className} class=\"{$className}\"{/if} id=x>\n",
            "{/template}"));
  }

  @Test
  public void testOptionalAttributes() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .icontemplate autoescape=\"deprecated-contextual\"}\n",
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
            "{if $alt or $alt == ''}",
            "{$alt |escapeHtmlAttribute}",
            "{elseif $title}",
            "{$title |escapeHtmlAttribute}",
            "{/if}\"",
            ">\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .icontemplate autoescape=\"deprecated-contextual\"}\n",
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
            "{if $alt or $alt == ''}",
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
            "{template .image autoescape=\"deprecated-contextual\"}\n",
            "  {@param iconPath: ?}\n",
            "<svg>",
            "<image xlink:href=\"{$iconPath |filterNormalizeMediaUri |escapeHtmlAttribute}\">",
            "</svg>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .image autoescape=\"deprecated-contextual\"}\n",
            "  {@param iconPath: ?}\n",
            "<svg>",
            "<image xlink:href=\"{$iconPath}\">",
            "</svg>\n",
            "{/template}"));
  }

  @Test
  public void testDynamicAttrName() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param baz: ?}\n",
            "<img src=\"bar\" {$baz |filterHtmlAttributes}=\"boo\">\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param baz: ?}\n",
            "<img src=\"bar\" {$baz}=\"boo\">\n",
            "{/template}"));
  }

  @Test
  public void testDynamicAttributes() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param baz: ?}\n",
            "<img src=\"bar\" {$baz |filterHtmlAttributes}>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param baz: ?}\n",
            "<img src=\"bar\" {$baz}>\n",
            "{/template}"));
  }

  @Test
  public void testDynamicAttributeValue() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo}\n",
            "  {@param baz: ?}\n",
            "<img x=x{$baz |escapeHtmlAttributeNospace}x>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo}\n",
            "  {@param baz: ?}\n",
            "<img x=x{$baz}x>\n",
            "{/template}"));

    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo}\n",
            "  {@param baz: ?}\n",
            "<img x='x{$baz |escapeHtmlAttribute}x'>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo}\n",
            "  {@param baz: ?}\n",
            "<img x='x{$baz}x'>\n",
            "{/template}"));

    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo}\n",
            "  {@param baz: ?}\n",
            "<img x=\"x{$baz |escapeHtmlAttribute}x\">\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo}\n",
            "  {@param baz: ?}\n",
            "<img x=\"x{$baz}x\">\n",
            "{/template}"));
  }

  @Test
  public void testDynamicElementName() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo}\n",
            "  {@param x: ?}\n",
            "<{$x |filterHtmlElementName}>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo}\n",
            "  {@param x: ?}\n",
            "<{$x}>\n",
            "{/template}"));
  }

  @Test
  public void testTagNameEdgeCases() {
    assertRewriteFails(
        "In file no-path:4:1, template ns.foo: "
            + "Saw unmatched close tag for context-changing tag: script",
        join("{namespace ns}\n\n", "{template .foo}\n", "</script>\n", "{/template}"));
    assertRewriteFails(
        "In file no-path:4:1, template ns.foo: "
            + "Saw unmatched close tag for context-changing tag: xmp",
        join("{namespace ns}\n\n", "{template .foo}\n", "</xmp>\n", "{/template}"));
    assertRewriteFails(
        "In file no-path:4:1, template ns.foo: Invalid end-tag name.",
        join("{namespace ns}\n\n", "{template .foo}\n", "</3>\n", "{/template}"));
  }

  @Test
  public void testOptionalValuelessAttributes() throws Exception {
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "<input {if c}checked{/if}>",
            "<input {if c}id={id |customEscapeDirective}{/if}>\n",
            "{/template}"));
  }

  @Test
  public void testDirectivesOrderedProperly() throws Exception {
    // The |bidiSpanWrap directive takes HTML and produces HTML, so the |escapeHTML
    // should appear first.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "{$x |escapeHtml |bidiSpanWrap}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "{$x |bidiSpanWrap}\n",
            "{/template}"));

    // But if we have a |bidiSpanWrap directive in a non HTML context, then don't reorder.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "<script>var html = {$x |bidiSpanWrap |escapeJsValue}</script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "<script>var html = {$x |bidiSpanWrap}</script>\n",
            "{/template}"));
  }

  @Test
  public void testDelegateTemplatesAreEscaped() throws Exception {
    assertContextualRewriting(
        join(
            "{delpackage dp}\n",
            "{namespace ns}\n\n",
            "/** @param x */\n",
            "{deltemplate ns.foo autoescape=\"deprecated-contextual\"}\n",
            "{$x |escapeHtml}\n",
            "{/deltemplate}"),
        join(
            "{delpackage dp}\n",
            "{namespace ns}\n\n",
            "/** @param x */\n",
            "{deltemplate ns.foo autoescape=\"deprecated-contextual\"}\n",
            "{$x}\n",
            "{/deltemplate}"));
  }

  @Test
  public void testTypedLetBlockIsContextuallyEscaped() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .t autoescape=\"deprecated-contextual\"}\n",
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
            "{template .t autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "<script> var y = '\n",
            "{let $l kind=\"html\"}\n",
            "<div>{$y}</div>",
            "{/let}",
            "{$y}'</script>\n",
            "{/template}"));
  }

  @Test
  public void testUntypedLetBlockIsContextuallyEscaped() {
    // Test that the behavior for let blocks without kind attribute is unchanged (i.e., they are
    // contextually escaped in the context the {let} command appears in).
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .t autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "<script> var y = '",
            "{let $l}",
            "<div>{$y |escapeJsString}</div>",
            "{/let}",
            "{$y |escapeJsString}'</script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .t autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "<script> var y = '\n",
            "{let $l}\n",
            "<div>{$y}</div>",
            "{/let}",
            "{$y}'</script>\n",
            "{/template}"));
  }

  @Test
  public void testTypedLetBlockIsStrictModeAutoescaped() {
    assertRewriteFails(
        "In file no-path:6:4, template ns.t: "
            + "Autoescape-cancelling print directives like |customEscapeDirective are only allowed "
            + "in kind=\"text\" blocks. If you really want to over-escape, try using a let block: "
            + "{let $foo kind=\"text\"}{$y |customEscapeDirective}{/let}{$foo}.",
        join(
            "{namespace ns}\n\n",
            "{template .t autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "{let $l kind=\"html\"}\n",
            "<b>{$y |customEscapeDirective}</b>",
            "{/let}\n",
            "{/template}"));

    assertRewriteFails(
        "In file no-path:6:4, template ns.t: "
            + "noAutoescape is not allowed in strict autoescaping mode. Instead, pass in a {param} "
            + "with kind=\"html\" or SanitizedContent.",
        join(
            "{namespace ns}\n\n",
            // Strict templates never allow noAutoescape.
            "{template .t autoescape=\"strict\"}\n",
            "  {@param y: ?}\n",
            "{let $l kind=\"html\"}\n",
            "<b>{$y |noAutoescape}</b>",
            "{/let}\n",
            "{/template}"));

    assertRewriteFails(
        "In file no-path:6:9, template ns.t: "
            + "noAutoescape is not allowed in strict autoescaping mode. Instead, pass in a {param} "
            + "with kind=\"js\" or SanitizedContent.",
        join(
            // Throw in a red-herring namespace, just to check things.
            "{namespace ns autoescape=\"deprecated-contextual\"}\n\n",
            // Strict templates never allow noAutoescape.
            "{template .t autoescape=\"strict\"}\n",
            "  {@param y: ?}\n",
            "{let $l kind=\"html\"}\n",
            "<script>{$y |noAutoescape}</script>",
            "{/let}\n",
            "{/template}"));

    assertRewriteFails(
        "In file no-path:5:4, template ns.t: "
            + "Soy strict autoescaping currently forbids calls to non-strict templates, unless the "
            + "context is kind=\"text\", since there's no guarantee the callee is safe.",
        join(
            "{namespace ns}\n\n",
            "{template .t autoescape=\"deprecated-contextual\"}\n",
            "{let $l kind=\"html\"}\n",
            "<b>{call .other data=\"all\"/}</b>",
            "{/let}\n",
            "{/template}\n\n",
            "{template .other autoescape=\"deprecated-contextual\"}\n",
            "Hello World\n",
            "{/template}"));

    assertRewriteFails(
        "In file no-path:5:4, template ns.t: "
            + "Soy strict autoescaping currently forbids calls to non-strict templates, unless the "
            + "context is kind=\"text\", since there's no guarantee the callee is safe.",
        join(
            "{namespace ns}\n\n",
            "{template .t autoescape=\"deprecated-contextual\"}\n",
            "{let $l kind=\"html\"}\n",
            "<b>{call .other data=\"all\"/}</b>",
            "{/let}\n",
            "{/template}\n\n",
            "{template .other autoescape=\"deprecated-contextual\"}\n",
            "Hello World\n",
            "{/template}"));

    // Non-autoescape-cancelling directives are allowed.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .t autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "{let $l kind=\"html\"}",
            "<b>{$y |customOtherDirective |escapeHtml}</b>",
            "{/let}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .t autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "{let $l kind=\"html\"}\n",
            "<b>{$y |customOtherDirective}</b>",
            "{/let}\n",
            "{/template}"));
  }

  @Test
  public void testNonTypedParamGetsContextuallyAutoescaped() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"deprecated-contextual\"}\n",
            "  {@param query: ?}\n",
            "{call .callee}",
            "{param fooHtml}",
            "<a href=\"http://google.com/search?q={$query |escapeUri}\" ",
            "onclick=\"alert('{$query |escapeJsString |escapeHtmlAttribute}')\">",
            "Search for {$query |escapeHtml}",
            "</a>",
            "{/param}",
            "{/call}",
            "\n{/template}\n\n",
            "{template .callee autoescape=\"deprecated-contextual\"}\n",
            "  {@param? fooHTML: ?}\n",
            "{$fooHTML |noAutoescape}",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"deprecated-contextual\"}\n",
            "  {@param query: ?}\n",
            "  {call .callee}\n",
            "    {param fooHtml}\n",
            "      <a href=\"http://google.com/search?q={$query}\"\n",
            "         onclick=\"alert('{$query}')\">\n",
            "        Search for {$query}\n",
            "      </a>\n",
            "    {/param}\n",
            "  {/call}\n",
            "{/template}\n\n",
            "{template .callee autoescape=\"deprecated-contextual\"}\n",
            "  {@param? fooHTML: ?}\n",
            "  {$fooHTML |noAutoescape}\n",
            "{/template}"));
  }

  @Test
  public void testTypedParamBlockIsContextuallyEscaped() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "<div>",
            "{call .callee}",
            "{param x kind=\"html\"}",
            "<script> var y ='{$y |escapeJsString}';</script>",
            "{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template .callee autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x |escapeHtml}</b>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "<div>",
            "{call .callee}{param x kind=\"html\"}",
            "<script> var y ='{$y}';</script>",
            "{/param}{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template .callee autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}"));
  }

  @Test
  public void testTypedParamBlockIsStrictModeAutoescaped() {
    assertRewriteFails(
        "In file no-path:5:44, template ns.caller: "
            + "Autoescape-cancelling print directives like |customEscapeDirective are only allowed "
            + "in kind=\"text\" blocks. If you really want to over-escape, try using a let block: "
            + "{let $foo kind=\"text\"}{$y |customEscapeDirective}{/let}{$foo}.",
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"strict\"}\n",
            "  {@param y: ?}\n",
            "<div>",
            "{call .callee}",
            "{param x kind=\"html\"}<b>{$y |customEscapeDirective}</b>{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template .callee autoescape=\"strict\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}"));

    // noAutoescape has a special error message.
    assertRewriteFails(
        "In file no-path:5:44, template ns.caller: "
            + "noAutoescape is not allowed in strict autoescaping mode. Instead, pass in a {param} "
            + "with kind=\"html\" or SanitizedContent.",
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"strict\"}\n",
            "  {@param y: ?}\n",
            "<div>",
            "{call .callee}",
            "{param x kind=\"html\"}<b>{$y |noAutoescape}</b>{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template .callee autoescape=\"strict\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}"));

    // NOTE: This error only works for non-extern templates.
    assertRewriteFails(
        "In file no-path:5:41, template ns.caller: "
            + "Soy strict autoescaping currently forbids calls to non-strict templates, unless the "
            + "context is kind=\"text\", since there's no guarantee the callee is safe.",
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<div>",
            "{call .callee}",
            "{param x kind=\"html\"}{call .subCallee data=\"all\"/}{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template .callee autoescape=\"strict\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}\n\n",
            "{template .subCallee autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}"));

    // Non-escape-cancelling directives are allowed.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"strict\"}\n",
            "  {@param y: ?}\n",
            "<div>",
            "{call .callee}",
            "{param x kind=\"html\"}<b>{$y |customOtherDirective |escapeHtml}</b>{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template .callee autoescape=\"strict\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x |escapeHtml}</b>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"strict\"}\n",
            "  {@param y: ?}\n",
            "<div>",
            "{call .callee}",
            "{param x kind=\"html\"}<b>{$y |customOtherDirective}</b>{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template .callee autoescape=\"strict\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}"));
  }

  @Test
  public void testTransitionalTypedParamBlock() {
    // In non-strict contextual templates, param blocks employ "transitional" strict autoescaping,
    // which permits noAutoescape. This helps teams migrate the callees to strict even if not all
    // the callers can be fixed.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "<div>",
            "{call .callee}",
            "{param x kind=\"html\"}<b>{$y |noAutoescape}</b>{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template .callee autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x |escapeHtml}</b>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "<div>",
            "{call .callee}",
            "{param x kind=\"html\"}<b>{$y |noAutoescape}</b>{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template .callee autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}"));

    // Other escape-cancelling directives are still not allowed.
    assertRewriteFails(
        "In file no-path:5:44, template ns.caller: "
            + "Autoescape-cancelling print directives like |customEscapeDirective are only allowed "
            + "in kind=\"text\" blocks. If you really want to over-escape, try using a let block: "
            + "{let $foo kind=\"text\"}{$y |customEscapeDirective}{/let}{$foo}.",
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "<div>",
            "{call .callee}",
            "{param x kind=\"html\"}<b>{$y |customEscapeDirective}</b>{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template .callee autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}"));

    // NOTE: This error only works for non-extern templates.
    assertRewriteFails(
        "In file no-path:4:41, template ns.caller: "
            + "Soy strict autoescaping currently forbids calls to non-strict templates, unless the "
            + "context is kind=\"text\", since there's no guarantee the callee is safe.",
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"deprecated-contextual\"}\n",
            "<div>",
            "{call .callee}",
            "{param x kind=\"html\"}{call .subCallee data=\"all\"/}{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template .callee autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}\n\n",
            "{template .subCallee autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}"));

    // Non-escape-cancelling directives are allowed.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "<div>",
            "{call .callee}",
            "{param x kind=\"html\"}<b>{$y |customOtherDirective |escapeHtml}</b>{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template .callee autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x |escapeHtml}</b>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"deprecated-contextual\"}\n",
            "  {@param y: ?}\n",
            "<div>",
            "{call .callee}",
            "{param x kind=\"html\"}<b>{$y |customOtherDirective}</b>{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template .callee autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}"));
  }

  @Test
  public void testTypedTextParamBlock() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {@param z: ?}\n",
            "<div>",
            "{call .callee}",
            "{param x kind=\"text\"}",
            "Hello {$x |text} <{$y |text}, \"{$z |text}\">",
            "{/param}",
            "{/call}",
            "</div>\n",
            "{/template}\n",
            "\n",
            "{template .callee autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x |escapeHtml}</b>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .caller autoescape=\"deprecated-contextual\"}\n",
            "  {@param x: ?}\n",
            "  {@param y: ?}\n",
            "  {@param z: ?}\n",
            "<div>",
            "{call .callee}{param x kind=\"text\"}",
            "Hello {$x} <{$y}, \"{$z}\">",
            "{/param}{/call}",
            "</div>\n",
            "{/template}\n",
            "\n",
            "{template .callee autoescape=\"deprecated-contextual\" private=\"true\"}\n",
            "  {@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}"));
  }

  @Test
  public void testTypedTextLetBlock() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
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
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
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
  public void testStrictModeRejectsAutoescapeCancellingDirectives() {
    assertRewriteFails(
        "In file no-path:5:4, template ns.main: "
            + "Autoescape-cancelling print directives like |customEscapeDirective are only allowed "
            + "in kind=\"text\" blocks. If you really want to over-escape, try using a let block: "
            + "{let $foo kind=\"text\"}{$foo |customEscapeDirective}{/let}{$foo}.",
        join(
            "{namespace ns}\n\n",
            "{template .main autoescape=\"strict\"}\n",
            "  {@param foo: ?}\n",
            "<b>{$foo|customEscapeDirective}</b>\n",
            "{/template}"));

    assertRewriteFails(
        "In file no-path:5:4, template ns.main: "
            + "noAutoescape is not allowed in strict autoescaping mode. Instead, pass in a {param} "
            + "with kind=\"html\" or SanitizedContent.",
        join(
            "{namespace ns}\n\n",
            "{template .main autoescape=\"strict\"}\n",
            "  {@param foo: ?}\n",
            "<b>{$foo|noAutoescape}</b>\n",
            "{/template}"));

    assertRewriteFails(
        "In file no-path:5:10, template ns.main: "
            + "noAutoescape is not allowed in strict autoescaping mode. Instead, pass in a {param} "
            + "with kind=\"uri\" or SanitizedContent.",
        join(
            "{namespace ns}\n\n",
            "{template .main autoescape=\"strict\"}\n",
            "  {@param foo: ?}\n",
            "<a href=\"{$foo|noAutoescape}\">Test</a>\n",
            "{/template}"));

    assertRewriteFails(
        "In file no-path:5:6, template ns.main: "
            + "noAutoescape is not allowed in strict autoescaping mode. Instead, pass in a {param} "
            + "with kind=\"attributes\" or SanitizedContent.",
        join(
            "{namespace ns}\n\n",
            "{template .main autoescape=\"strict\"}\n",
            "  {@param foo: ?}\n",
            "<div {$foo|noAutoescape}>Test</div>\n",
            "{/template}"));

    assertRewriteFails(
        "In file no-path:5:9, template ns.main: "
            + "noAutoescape is not allowed in strict autoescaping mode. Instead, pass in a {param} "
            + "with kind=\"js\" or SanitizedContent.",
        join(
            "{namespace ns}\n\n",
            "{template .main autoescape=\"strict\"}\n",
            "  {@param foo: ?}\n",
            "<script>{$foo|noAutoescape}</script>\n",
            "{/template}"));

    // NOTE: There's no recommended context for textarea, since it's really essentially text.
    assertRewriteFails(
        "In file no-path:5:11, template ns.main: "
            + "noAutoescape is not allowed in strict autoescaping mode. Instead, pass in a {param} "
            + "with appropriate kind=\"...\" or SanitizedContent.",
        join(
            "{namespace ns}\n\n",
            "{template .main autoescape=\"strict\"}\n",
            "  {@param foo: ?}\n",
            "<textarea>{$foo|noAutoescape}</textarea>\n",
            "{/template}"));
  }

  @Test
  public void testStrictModeRejectsNonStrictCalls() {
    assertRewriteFails(
        "In file no-path:4:4, template ns.main: "
            + "Soy strict autoescaping currently forbids calls to non-strict templates, unless the "
            + "context is kind=\"text\", since there's no guarantee the callee is safe.",
        join(
            "{namespace ns}\n\n",
            "{template .main autoescape=\"strict\" kind=\"html\"}\n",
            "<b>{call .bar data=\"all\"/}\n",
            "{/template}\n\n" + "{template .bar autoescape=\"deprecated-contextual\"}\n",
            "Hello World\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path-0:4:1, template ns.main: "
            + "Soy strict autoescaping currently forbids calls to non-strict templates, unless the "
            + "context is kind=\"text\", since there's no guarantee the callee is safe.",
        join(
            "{namespace ns}\n\n",
            "{template .main autoescape=\"strict\"}\n",
            "{delcall ns.foo}\n",
            "{param x: '' /}\n",
            "{/delcall}\n",
            "{/template}"),
        join(
            "{delpackage dp1}\n",
            "{namespace ns}\n\n",
            "/** @param x */\n",
            "{deltemplate ns.foo autoescape=\"deprecated-contextual\"}\n",
            "<b>{$x}</b>\n",
            "{/deltemplate}"),
        join(
            "{delpackage dp2}\n",
            "{namespace ns}\n\n",
            "/** @param x */\n",
            "{deltemplate ns.foo autoescape=\"deprecated-contextual\"}\n",
            "<i>{$x}</i>\n",
            "{/deltemplate}"));
  }

  @Test
  public void testContextualCannotCallStrictOfWrongContext() {
    // Can't call a text template ns.from a strict context.
    assertRewriteFails(
        "In file no-path:4:1, template ns.main: "
            + "Cannot call strictly autoescaped template ns.foo of kind=\"text\" from incompatible "
            + "context (Context HTML_PCDATA). Strict templates generate extra code to safely call "
            + "templates of other content kinds, but non-strict templates do not.",
        join(
            "{namespace ns}\n\n",
            "{template .main autoescape=\"deprecated-contextual\"}\n",
            "{call .foo}\n",
            "{param x: '' /}\n",
            "{/call}\n",
            "{/template}\n\n",
            "{template .foo autoescape=\"strict\" kind=\"text\"}\n",
            "{@param x: ?}\n",
            "<b>{$x}</b>\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path-0:4:1, template ns.main: "
            + "Cannot call strictly autoescaped template ns.foo of kind=\"text\" from incompatible "
            + "context (Context HTML_PCDATA). Strict templates generate extra code to safely call "
            + "templates of other content kinds, but non-strict templates do not.",
        join(
            "{namespace ns}\n\n",
            "{template .main autoescape=\"deprecated-contextual\"}\n",
            "{delcall ns.foo}\n",
            "{param x: '' /}\n",
            "{/delcall}\n",
            "{/template}"),
        join(
            "{delpackage dp1}\n",
            "{namespace ns}\n\n",
            "/** @param x */\n",
            "{deltemplate ns.foo autoescape=\"strict\" kind=\"text\"}\n",
            "<b>{$x}</b>\n",
            "{/deltemplate}"),
        join(
            "{delpackage dp2}\n",
            "{namespace ns}\n\n",
            "/** @param x */\n",
            "{deltemplate ns.foo autoescape=\"strict\" kind=\"text\"}\n",
            "<i>{$x}</i>\n",
            "{/deltemplate}"));
  }

  @Test
  public void testStrictModeAllowsNonAutoescapeCancellingDirectives() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                join(
                    "{namespace ns}\n\n",
                    "{template .main autoescape=\"strict\"}\n",
                    "  {@param foo: ?}\n",
                    "<b>{$foo |customOtherDirective}</b>\n",
                    "{/template}"))
            .parse()
            .fileSet();
    String rewrittenTemplate = rewrittenSource(soyTree);
    assertThat(rewrittenTemplate.trim())
        .isEqualTo(
            join(
                "{namespace ns}\n\n",
                "{template .main autoescape=\"strict\"}\n",
                "  {@param foo: ?}\n",
                "<b>{$foo |customOtherDirective |escapeHtml}</b>\n",
                "{/template}"));
  }

  @Test
  public void testStrictModeRequiresStartAndEndToBeCompatible() {
    assertRewriteFails(
        "In file no-path:3:1, template ns.main: "
            + "A strict block of kind=\"js\" cannot end in context (Context JS_SQ_STRING). "
            + "Likely cause is an unterminated string literal.",
        join("{namespace ns}\n\n", "{template .main kind=\"js\"}\nvar x='\n{/template}\n"));
  }

  @Test
  public void testStrictUriMustNotBeEmpty() {
    assertRewriteFails(
        "In file no-path:3:1, template ns.main: "
            + "A strict block of kind=\"uri\" cannot end in context (Context URI START NORMAL). "
            + "Likely cause is an unterminated or empty URI.",
        join(
            "{namespace ns}\n\n",
            "{template .main autoescape=\"strict\" kind=\"uri\"}\n",
            "{/template}"));
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
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "<a href=\"{call .bar data=\"all\" /}\">Test</a>",
            "\n{/template}\n\n",
            "{template .bar autoescape=\"strict\" kind=\"uri\"}\n",
            "  {@param x: ?}\n",
            "http://www.google.com/search?q={$x |escapeUri}",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"deprecated-contextual\"}\n",
            "<a href=\"{call .bar data=\"all\" /}\">Test</a>",
            "\n{/template}\n\n",
            "{template .bar autoescape=\"strict\" kind=\"uri\"}\n",
            "  {@param x: ?}\n",
            "http://www.google.com/search?q={$x}",
            "\n{/template}"));
  }

  @Test
  public void testStrictAttributes() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\" kind=\"attributes\"}\n",
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
            "{template .foo autoescape=\"strict\" kind=\"attributes\"}\n",
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
    assertRewriteFails(
        "In file no-path:3:1, template ns.foo: "
            + "A strict block of kind=\"attributes\" cannot end in context "
            + "(Context JS SCRIPT SPACE_OR_TAG_END DIV_OP). "
            + "Likely cause is an unterminated attribute value, or ending with an unquoted "
            + "attribute.",
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\" kind=\"attributes\"}\n",
            "  {@param x: ?}\n",
            "onclick={$x}",
            "\n{/template}"));

    assertRewriteFails(
        "In file no-path:3:1, template ns.foo: "
            + "A strict block of kind=\"attributes\" cannot end in context "
            + "(Context HTML_NORMAL_ATTR_VALUE PLAIN_TEXT SPACE_OR_TAG_END). "
            + "Likely cause is an unterminated attribute value, or ending with an unquoted "
            + "attribute.",
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\" kind=\"attributes\"}\n",
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
            "{template .foo autoescape=\"strict\" kind=\"attributes\"}\n",
            "foo=bar checked",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\" kind=\"attributes\"}\n",
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
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<script>",
            "{call .bar /}/{$x |escapeJsValue}+/{$x |escapeJsRegex}/g",
            "</script>",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\"}\n",
            "  {@param x: ?}\n",
            "<script>",
            "{call .bar /}/{$x}+/{$x}/g",
            "</script>",
            "\n{/template}"));
  }

  @Test
  public void testStrictModeEscapesCallSites() {
    String source =
        "{namespace ns}\n\n"
            + "{template .main autoescape=\"strict\"}\n"
            + "{call .htmltemplate /}"
            + "<script>var x={call .htmltemplate /};</script>\n"
            + "<script>var x={call .jstemplate /};</script>\n"
            + "{call .externtemplate /}"
            + "\n{/template}\n\n"
            + "{template .htmltemplate autoescape=\"strict\"}\n"
            + "Hello World"
            + "\n{/template}\n\n"
            + "{template .jstemplate autoescape=\"strict\" kind=\"js\"}\n"
            + "foo()"
            + "\n{/template}";

    ErrorReporter boom = ExplodingErrorReporter.get();
    ParseResult parseResult =
        SoyFileSetParserBuilder.forFileContents(source).errorReporter(boom).parse();
    SoyFileSetNode soyTree = parseResult.fileSet();
    new ContextualAutoescaper(SOY_PRINT_DIRECTIVES).rewrite(soyTree, parseResult.registry(), boom);
    TemplateNode mainTemplate = soyTree.getChild(0).getChild(0);
    assertWithMessage("Sanity check").that(mainTemplate.getTemplateName()).isEqualTo("ns.main");
    final List<CallNode> callNodes = SoyTreeUtils.getAllNodesOfType(mainTemplate, CallNode.class);
    assertThat(callNodes).hasSize(4);
    assertWithMessage("HTML->HTML escaping should be pruned")
        .that(callNodes.get(0).getEscapingDirectiveNames())
        .isEqualTo(ImmutableList.of());
    assertWithMessage("JS -> HTML call should be escaped")
        .that(callNodes.get(1).getEscapingDirectiveNames())
        .isEqualTo(ImmutableList.of("|escapeJsValue"));
    assertWithMessage("JS -> JS pruned")
        .that(callNodes.get(2).getEscapingDirectiveNames())
        .isEqualTo(ImmutableList.of());
    assertWithMessage("HTML -> extern call should be escaped")
        .that(callNodes.get(3).getEscapingDirectiveNames())
        .isEqualTo(ImmutableList.of("|escapeHtml"));
  }

  @Test
  public void testStrictModeOptimizesDelegates() {
    String source =
        "{namespace ns}\n\n"
            + "{template .main autoescape=\"strict\"}\n"
            + "{delcall ns.delegateHtml /}"
            + "{delcall ns.delegateText /}"
            + "\n{/template}\n\n"
            + "/** A delegate returning HTML. */\n"
            + "{deltemplate ns.delegateHtml autoescape=\"strict\"}\n"
            + "Hello World"
            + "\n{/deltemplate}\n\n"
            + "/** A delegate returning JS. */\n"
            + "{deltemplate ns.delegateText autoescape=\"strict\" kind=\"text\"}\n"
            + "Hello World"
            + "\n{/deltemplate}";

    ErrorReporter boom = ExplodingErrorReporter.get();

    ParseResult parseResult =
        SoyFileSetParserBuilder.forFileContents(source).errorReporter(boom).parse();
    SoyFileSetNode soyTree = parseResult.fileSet();
    new ContextualAutoescaper(SOY_PRINT_DIRECTIVES).rewrite(soyTree, parseResult.registry(), boom);
    TemplateNode mainTemplate = soyTree.getChild(0).getChild(0);
    assertWithMessage("Sanity check").that(mainTemplate.getTemplateName()).isEqualTo("ns.main");
    final List<CallNode> callNodes = SoyTreeUtils.getAllNodesOfType(mainTemplate, CallNode.class);
    assertThat(callNodes).hasSize(2);
    assertWithMessage("We're compiling a complete set; we can optimize based on usages.")
        .that(callNodes.get(0).getEscapingDirectiveNames())
        .isEqualTo(ImmutableList.of());
    assertWithMessage("HTML -> TEXT requires escaping")
        .that(callNodes.get(1).getEscapingDirectiveNames())
        .isEqualTo(ImmutableList.of("|escapeHtml"));
  }

  private static String getForbiddenMsgError(String path, String template, String context) {
    return "In file "
        + path
        + ", template ns."
        + template
        + ": "
        + "Messages are not supported in this context, because it would mean asking translators to "
        + "write source code; if this is desired, try factoring the message into a {let} block: "
        + "(Context "
        + context
        + ")";
  }

  @Test
  public void testMsgForbiddenUriStartContext() {
    assertRewriteFails(
        getForbiddenMsgError("no-path:4:12", "main", "URI NORMAL URI DOUBLE_QUOTE START NORMAL"),
        join(
            "{namespace ns}\n\n",
            "{template .main}\n",
            "  <a href=\"{msg desc=\"foo\"}message{/msg}\">test</a>\n",
            "{/template}"));
    assertRewriteFails(
        getForbiddenMsgError("no-path:4:12", "main", "URI NORMAL URI DOUBLE_QUOTE START NORMAL"),
        join(
            "{namespace ns}\n\n",
            "{template .main autoescape=\"deprecated-contextual\"}\n",
            "  <a href=\"{msg desc=\"foo\"}message{/msg}\">test</a>\n",
            "{/template}"));
    assertRewriteFails(
        getForbiddenMsgError("no-path:4:3", "main", "URI START NORMAL"),
        join(
            "{namespace ns}\n\n",
            "{template .main kind=\"uri\"}\n",
            "  {msg desc=\"foo\"}message{/msg}\n",
            "{/template}"));
  }

  @Test
  public void testMsgForbiddenJsContext() {
    assertRewriteFails(
        getForbiddenMsgError("no-path:4:11", "main", "JS REGEX"),
        join(
            "{namespace ns}\n\n",
            "{template .main}\n",
            "  <script>{msg desc=\"foo\"}message{/msg}</script>\n",
            "{/template}"));
    assertRewriteFails(
        getForbiddenMsgError("no-path:4:11", "main", "JS REGEX"),
        join(
            "{namespace ns}\n\n",
            "{template .main autoescape=\"deprecated-contextual\"}\n",
            "  <script>{msg desc=\"foo\"}message{/msg}</script>\n",
            "{/template}"));
    assertRewriteFails(
        getForbiddenMsgError("no-path:4:3", "main", "JS REGEX"),
        join(
            "{namespace ns}\n\n",
            "{template .main kind=\"js\"}\n",
            "  {msg desc=\"foo\"}message{/msg}\n",
            "{/template}"));
  }

  @Test
  public void testMsgForbiddenHtmlContexts() {
    assertRewriteFails(
        getForbiddenMsgError("no-path:4:8", "main", "HTML_TAG NORMAL"),
        join(
            "{namespace ns}\n\n",
            "{template .main}\n",
            "  <div {msg desc=\"foo\"}attributes{/msg}>Test</div>\n",
            "{/template}"));
    assertRewriteFails(
        getForbiddenMsgError("no-path:4:3", "main", "HTML_TAG"),
        join(
            "{namespace ns}\n\n",
            "{template .main kind=\"attributes\"}\n",
            "  {msg desc=\"foo\"}message{/msg}\n",
            "{/template}"));
  }

  @Test
  public void testMsgForbiddenCssContext() {
    assertRewriteFails(
        getForbiddenMsgError("no-path:4:10", "main", "CSS"),
        join(
            "{namespace ns}\n\n",
            "{template .main}\n",
            "  <style>{msg desc=\"foo\"}message{/msg}</style>\n",
            "{/template}"));
    assertRewriteFails(
        getForbiddenMsgError("no-path:4:10", "main", "CSS"),
        join(
            "{namespace ns}\n\n",
            "{template .main autoescape=\"deprecated-contextual\"}\n",
            "  <style>{msg desc=\"foo\"}message{/msg}</style>\n",
            "{/template}"));
    assertRewriteFails(
        getForbiddenMsgError("no-path:4:3", "main", "CSS"),
        join(
            "{namespace ns}\n\n",
            "{template .main kind=\"css\"}\n",
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
            "{template .main}\n",
            "  {@param p: ?}\n",
            "<textarea>{$p |escapeHtmlRcdata}</div></textarea>",
            "{$p |escapeHtml}", // this used to be |escapeHtmlRcData
            "<textarea></textarea>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .main}\n",
            "  {@param p: ?}\n",
            "<textarea>{$p}</div></textarea>",
            "{$p}",
            // this is needed at the end to prevent the bug from causing an error
            "<textarea></textarea>",
            "{/template}"));
  }


  // TODO: Tests for dynamic attributes: <a on{$name}="...">,
  // <div data-{$name}={$value}>

  private static String join(String... lines) {
    return Joiner.on("").join(lines);
  }

  /**
   * Returns the contextually rewritten source.
   *
   * <p>The Soy tree may have multiple files, but only the source code for the first is returned.
   */
  private static String rewrittenSource(SoyFileSetNode soyTree) {

    FormattingErrorReporter reporter = new FormattingErrorReporter();
    List<TemplateNode> tmpls =
        new ContextualAutoescaper(SOY_PRINT_DIRECTIVES)
            .rewrite(soyTree, new TemplateRegistry(soyTree, reporter), reporter);

    if (!reporter.getErrorMessages().isEmpty()) {
      String message = reporter.getErrorMessages().get(0);
      if (message.startsWith(ContextualAutoescaper.AUTOESCAPE_ERROR_PREFIX)) {
        // Grab the part after the prefix (and the "- " used for indentation).
        message = message.substring(ContextualAutoescaper.AUTOESCAPE_ERROR_PREFIX.length() + 2);
        // Re-throw as an exception, so that tests are easier to write. I considered having the
        // tests explicitly check the error messages; however, there's a substantial risk that some
        // positive test might forget to check the error messages, and it leaves all callers of
        // this with two things to check.
        // TODO(gboyer): Once 100% of the contextual autoescaper's errors are migrated to the error
        // reporter, we can stop throwing and simply add explicit checks in the cases.
        throw SoyAutoescapeException.createWithoutMetaInfo(message);
      } else {
        throw new IllegalStateException("Unexpected error: " + message);
      }
    }

    StringBuilder src = new StringBuilder();
    src.append(soyTree.getChild(0).toSourceString());
    for (TemplateNode tn : tmpls) {
      src.append('\n').append(tn.toSourceString());
    }
    return src.toString();
  }

  private void assertContextualRewriting(String expectedOutput, String... inputs)
      throws SoyAutoescapeException {

    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(inputs)
            .errorReporter(boom)
            .allowUnboundGlobals(true)
            .parse()
            .fileSet();

    String source = rewrittenSource(soyTree);
    assertThat(source.trim()).isEqualTo(expectedOutput);
  }

  private void assertContextualRewritingNoop(String expectedOutput) throws SoyAutoescapeException {
    assertContextualRewriting(expectedOutput, expectedOutput);
  }

  /**
   * @param msg Message that should be reported to the template ns.author. Null means don't care.
   */
  private static void assertRewriteFails(@Nullable String msg, String... inputs) {
    SoyFileSupplier[] soyFileSuppliers = new SoyFileSupplier[inputs.length];
    for (int i = 0; i < inputs.length; ++i) {
      soyFileSuppliers[i] =
          SoyFileSupplier.Factory.create(
              inputs[i], SoyFileKind.SRC, inputs.length == 1 ? "no-path" : "no-path-" + i);
    }
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forSuppliers(soyFileSuppliers)
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .parse()
            .fileSet();

    try {
      rewrittenSource(soyTree);
    } catch (SoyAutoescapeException ex) {
      // Find the root cause; during contextualization, we re-wrap exceptions on the path to a
      // template.
      while (ex.getCause() instanceof SoyAutoescapeException) {
        ex = (SoyAutoescapeException) ex.getCause();
      }
      if (msg != null && !msg.equals(ex.getMessage())) {
        throw (ComparisonFailure) new ComparisonFailure("", msg, ex.getMessage()).initCause(ex);
      }
      return;
    }
    fail("Expected failure but was " + soyTree.getChild(0).toSourceString());
  }

  static final class FakeBidiSpanWrapDirective
      implements SoyPrintDirective, SanitizedContentOperator {
    @Override
    public String getName() {
      return "|bidiSpanWrap";
    }

    @Override
    public Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of(0);
    }

    @Override
    public boolean shouldCancelAutoescape() {
      return false;
    }

    @Override
    @Nonnull
    public SanitizedContent.ContentKind getContentKind() {
      return SanitizedContent.ContentKind.HTML;
    }
  }
}
