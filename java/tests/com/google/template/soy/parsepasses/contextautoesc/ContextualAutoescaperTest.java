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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SoyFileKind;
import com.google.template.soy.base.SoyFileSupplier;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soyparse.SoyFileSetParser;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;

import junit.framework.ComparisonFailure;
import junit.framework.TestCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;


public final class ContextualAutoescaperTest extends TestCase {

  /** Custom print directives used in tests below. */
  private static final Map<String, SoyPrintDirective> SOY_PRINT_DIRECTIVES = ImmutableMap.of(
      "|customEscapeDirective", new SoyPrintDirective() {
        @Override public String getName() {
          return "|customEscapeDirective";
        }
        @Override public Set<Integer> getValidArgsSizes() {
          return ImmutableSet.of(0);
        }
        @Override public boolean shouldCancelAutoescape() {
          return true;
        }
      },
      "|customOtherDirective", new SoyPrintDirective() {
        @Override public String getName() {
          return "|customOtherDirective";
        }
        @Override public Set<Integer> getValidArgsSizes() {
          return ImmutableSet.of(0);
        }
        @Override public boolean shouldCancelAutoescape() {
          return false;
        }
      },
      "|bidiSpanWrap", new FakeBidiSpanWrapDirective());


  public final void testTrivialTemplate() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "Hello, World!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo}\n",
            "Hello, World!\n",
            "{/template}"));
  }

  public final void testPrintInText() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
            "Hello, {$world |escapeHtml}!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
            "Hello, {$world}!\n",
            "{/template}"));
  }

  public final void testPrintInTextAndLink() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "Hello,",
              "<a href='worlds?world={$world |escapeUri}'>",
                "{$world |escapeHtml}",
              "</a>!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "Hello,\n",
              "<a href='worlds?world={$world}'>\n",
                "{$world}\n",
              "</a>!\n",
            "{/template}\n"));
  }

  public final void testConditional() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
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
            "{template bar autoescape=\"contextual\"}\n",
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

  public final void testConditionalEndsInDifferentContext() throws Exception {
    // Make sure that branches that ends in consistently different contexts transition to
    // that different context.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
              "<a",
              "{if $url}",
                " href='{$url |filterNormalizeUri |escapeHtmlAttribute}'>",
              "{elseif $name}",
                " name='{$name |escapeHtmlAttribute}'>",
              "{else}",
                ">",
              "{/if}",
              " onclick='alert({$value |escapeHtml})'\n",  // Not escapeJsValue.
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
              "<a",
              // Each of these branches independently closes the tag.
              "{if $url}",
                " href='{$url}'>",
              "{elseif $name}",
                " name='{$name}'>",
              "{else}",
                ">",
              "{/if}",
              // So now make something that looks like a script attribute but which actually
              // appears in a PCDATA.  If the context merge has properly happened is is escaped as
              // PCDATA.
              " onclick='alert({$value})'\n",
            "{/template}"));
  }

  public final void testBrokenConditional() throws Exception {
    assertRewriteFails(
        "In file no-path:7, template bar: " +
        "{if} command branch ends in a different context than preceding branches: " +
        "{elseif $x == 2}<script>foo({$z})//</scrpit>",
        join(
            "{namespace ns}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
            "Hello,\n",
            "{if $x == 1}\n",
            "  {$y}\n",
            "{elseif $x == 2}\n",
            "  <script>foo({$z})//</scrpit>\n",  // Not closed so ends inside JS.
            "{else}\n",
            "  World!\n",
            "{/if}\n",
            "{/template}"));
  }

  public final void testSwitch() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
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
            "{template bar autoescape=\"contextual\"}\n",
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

  public final void testBrokenSwitch() throws Exception {
    assertRewriteFails(
        "In file no-path:8, template bar: " +
        "{switch} command case ends in a different context than preceding cases: " +
        "{case 2}<script>foo({$z})//</scrpit>",
        join(
            "{namespace ns}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
            "Hello,\n",
            "{switch $x}\n",
            "  {case 1}\n",
            "    {$y}\n",
            "  {case 2}\n",
            // Not closed so ends inside JS
            "    <script>foo({$z})//</scrpit>\n",
            "  {default}\n",
            "    World!\n",
            "{/switch}\n",
            "{/template}"));
  }

  public final void testPrintInsideScript() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
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
            "{template bar autoescape=\"contextual\"}\n",
              "<script>\n",
                "foo({$a});\n",
                "bar(\"{$b}\");\n",
                "baz(\'{$c}\');\n",
                "boo(/{$d}/.test(s) ? 1 / {$e} : /{$f}/);\n",
              "</script>\n",
            "{/template}"));
  }

  public final void testPrintInsideJsCommentRejected() throws Exception {
    assertRewriteFails(
        "In file no-path:4, template foo: " +
        "Don't put {print} or {call} inside comments : {$x}",
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<script>// {$x}</script>\n",
            "{/template}"));
  }

  public final void testJsStringInsideQuotesRejected() throws Exception {
    assertRewriteFails(
        "In file no-path:4, template foo: " +
        "Escaping modes [ESCAPE_JS_VALUE] not compatible with" +
        " (Context JS_SQ_STRING) : {$world |escapeJsValue}",
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<script>alert('Hello {$world |escapeJsValue}');</script>\n",
            "{/template}"));
  }

  public final void testLiteral() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
              "<script>",
                "{lb}$a{rb}",
              "</script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
              "<script>\n",
                "{literal}{$a}{/literal}\n",
              "</script>\n",
            "{/template}"));
  }

  public final void testForLoop() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
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
            "{template bar autoescape=\"contextual\"}\n",
              "<style>\n",
                "{for $i in range($n)}\n",
                "  .foo{$i}:before {lb}\n",
                "    content: '{$i}'\n",
                "  {rb}\n",
                "{/for}",
              "</style>\n",
            "{/template}"));
  }

  public final void testBrokenForLoop() throws Exception {
    assertRewriteFails(
        "In file no-path:5, template bar: " +
        "{for} command changes context so it cannot be reentered : " +
        "{for $i in range($n)}.foo{$i |filterCssValue}:before " +
        "{lb}content: '{$i |escapeCssString}{rb}{/for}",
        join(
            "{namespace ns}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
            "  <style>\n",
            "    {for $i in range($n)}\n",
            "      .foo{$i |filterCssValue}:before {lb}\n",
            "        content: '{$i |escapeCssString}\n",  // Missing close quote.
            "      {rb}\n",
            "    {/for}\n",
            "  </style>\n",
            "{/template}"));
  }

  public final void testForeachLoop() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template baz autoescape=\"contextual\"}\n",
              "<ol>",
                "{foreach $x in $foo}",
                  "<li>{$x |escapeHtml}</li>",
                "{/foreach}",
              "</ol>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template baz autoescape=\"contextual\"}\n",
            "  <ol>\n",
            "    {foreach $x in $foo}\n",
            "      <li>{$x}</li>\n",
            "    {/foreach}\n",
            "  </ol>\n",
            "{/template}"));
  }

  public final void testBrokenForeachLoop() throws Exception {
    assertRewriteFails(
        "In file no-path:5, template baz: " +
        "{foreach} body changes context : " +
        "{foreach $x in $foo}<li class={$x}{/foreach}",
        join(
            "{namespace ns}\n\n",
            "{template baz autoescape=\"contextual\"}\n",
            "  <ol>\n",
            "    {foreach $x in $foo}\n",
            "      <li class={$x}\n",
            "    {/foreach}\n",
            "  </ol>\n",
            "{/template}"));
  }

  public final void testForeachLoopWithIfempty() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template baz autoescape=\"contextual\"}\n",
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
            "{template baz autoescape=\"contextual\"}\n",
            "  <ol>\n",
            "    {foreach $x in $foo}\n",
            "      <li>{$x}</li>\n",
            "    {ifempty}\n",
            "      <li><i>Nothing</i></li>\n",
            "    {/foreach}\n",
            "  </ol>\n",
            "{/template}"));
  }

  public final void testCall() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "{call bar data=\"all\" /}\n",
            "{/template}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
              "Hello, {$world |escapeHtml}!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
            "  {call bar data=\"all\" /}\n",
            "{/template}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
            "  Hello, {$world}!\n",
            "{/template}"));
  }

  public final void testCallWithParams() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "{call bar}{param x: $x + 1 /}{/call}\n",
            "{/template}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
              "Hello, {$world |escapeHtml}!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
            "  {call bar}{param x: $x + 1 /}{/call}\n",
            "{/template}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
            "  Hello, {$world}!\n",
            "{/template}"));
  }

  public final void testSameTemplateCalledInDifferentContexts() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "{call bar data=\"all\" /}",
              "<script>",
              "alert('{call bar__C14 data=\"all\" /}');",
              "</script>\n",
            "{/template}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
              "Hello, {$world |escapeHtml}!\n",
            "{/template}\n\n",
            "{template bar__C14 autoescape=\"contextual\"}\n",
              "Hello, {$world |escapeJsString}!\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
            "  {call bar data=\"all\" /}\n",
            "  <script>\n",
            "  alert('{call bar data=\"all\" /}');\n",
            "  </script>\n",
            "{/template}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
            "  Hello, {$world}!\n",
            "{/template}"));
  }

  public final void testRecursiveTemplateGuessWorks() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<script>",
                "x = [{call countDown__C2010 data=\"all\" /}]",
              "</script>\n",
            "{/template}\n\n",
            "{template countDown autoescape=\"contextual\"}\n",
              "{if $x gt 0}",
                "{print --$x |escapeHtml},",
                "{call countDown /}",
              "{/if}\n",
            "{/template}\n\n",
            "{template countDown__C2010 autoescape=\"contextual\"}\n",
              "{if $x gt 0}",
                "{print --$x |escapeJsValue},",
                "{call countDown__C2010 /}",
              "{/if}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
            "  <script>\n",
            "    x = [{call countDown data=\"all\" /}]\n",
            "  </script>\n",
            "{/template}\n\n",
            "{template countDown autoescape=\"contextual\"}\n",
            "  {if $x gt 0}{print --$x},{call countDown /}{/if}\n",
            "{/template}"));
  }

  public final void testTemplateWithUnknownJsSlash() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<script>",
                "{if $declare}var {/if}",
                "x = {call bar__C2010 /}{\\n}",
                "y = 2",
            "  </script>\n",
            "{/template}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
              "42",
              "{if $declare}",
                " , ",
              "{/if}\n",
            "{/template}\n\n",
            "{template bar__C2010 autoescape=\"contextual\"}\n",
              "42",
              "{if $declare}",
                " , ",
              "{/if}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
            "  <script>\n",
            "    {if $declare}var{sp}{/if}\n",
            "    x = {call bar /}{\\n}\n",
            // At this point we don't know whether or not a slash would start
            // a RegExp or not, but we don't see a slash so it doesn't matter.
            "    y = 2",
            "  </script>\n",
            "{/template}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
            // A slash following 42 would be a division operator.
            "  42\n",
            // But a slash following a comma would be a RegExp.
            "  {if $declare} , {/if}\n",  //
            "{/template}"));

  }

  public final void testTemplateUnknownJsSlashMatters() throws Exception {
    assertRewriteFails(
        "In file no-path:7, template foo: " +
        "Slash (/) cannot follow the preceding branches since it is unclear whether the slash " +
        "is a RegExp literal or division operator." +
        "  Please add parentheses in the branches leading to `/ 2  </script>`",
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
            "  <script>\n",
            "    {if $declare}var{sp}{/if}\n",
            "    x = {call bar /}\n",
            // At this point we don't know whether or not a slash would start
            // a RegExp or not, so this constitutes an error.
            "    / 2",
            "  </script>\n",
            "{/template}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
            // A slash following 42 would be a division operator.
            "  42\n",
            // But a slash following a comma would be a RegExp.
            "  {if $declare} , {/if}\n",  //
            "{/template}"));
  }

  public final void testUrlContextJoining() throws Exception {
    // This is fine.  The ambiguity about
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<a href=\"",
                "{if c}",
                  "/foo?bar=baz",
                "{else}",
                  "/boo",
                "{/if}",
              "\">\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path:4, template foo: Cannot determine which part of the URL {$x} is in.",
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<a href=\"",
                "{if c}",
                  "/foo?bar=baz&boo=",
                "{else}",
                  "/boo/",
                "{/if}",
                "{$x}",
              "\">\n",
            "{/template}"));
  }

  public final void testRecursiveTemplateGuessFails() throws Exception {
    assertRewriteFails(
        "In file no-path:10, template quot__C13: " +
        "{if} command without {else} changes context : " +
        "{if Math.random() lt 0.5}{call quot data=\"all\" /}{/if}",
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
            "  <script>\n",
            "    {call quot data=\"all\" /}\n",
            "  </script>\n",
            "{/template}\n\n",
            "{template quot autoescape=\"contextual\"}\n",
            "  \" {if Math.random() lt 0.5}{call quot data=\"all\" /}{/if}\n",
            "{/template}"));
  }

  public final void testUris() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
              // We use filterNormalizeUri at the beginning,
              "<a href='{$url |filterNormalizeUri |escapeHtmlAttribute}'",
              " style='background:url({$bgimage |filterNormalizeUri |escapeHtmlAttribute})'>",
              "Hi</a>",
              "<a href='#{$anchor |escapeHtmlAttribute}'",
              // escapeUri for substitutions into queries.
              " style='background:url(&apos;/pic?q={$file |escapeUri}&apos;)'>",
                "Hi",
              "</a>",
              "<style>",
                "body {lb} background-image: url(\"{$bg |filterNormalizeUri}\"); {rb}",
                // and normalizeUri without the filter in the path.
                "table {lb} border-image: url(\"borders/{$brdr |normalizeUri}\"); {rb}",
              "</style>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template bar autoescape=\"contextual\"}\n",
              "<a href='{$url}' style='background:url({$bgimage})'>Hi</a>\n",
              "<a href='#{$anchor}'\n",
              " style='background:url(&apos;/pic?q={$file}&apos;)'>Hi</a>\n",
              "<style>\n",
                "body {lb} background-image: url(\"{$bg}\"); {rb}\n",
                "table {lb} border-image: url(\"borders/{$brdr}\"); {rb}\n",
              "</style>\n",
            "{/template}"));
  }

  public final void testCss() throws Exception {
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "{css foo}\n",
            "{/template}"));
  }

  public final void testAlreadyEscaped() throws Exception {
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<script>a = \"{$FOO |escapeUri}\";</script>\n",
            "{/template}"));
  }

  public final void testExplicitNoescapeNoop() throws Exception {
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<script>a = \"{$FOO |noAutoescape}\";</script>\n",
            "{/template}"));
  }

  public final void testCustomDirectives() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "{$x |customEscapeDirective} - {$y |customOtherDirective |escapeHtml}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
            "  {$x |customEscapeDirective} - {$y |customOtherDirective}\n",
            "{/template}"));
  }

  public final void testNoInterferenceWithNonContextualTemplates() throws Exception {
    // If a broken template calls a contextual template, object.
    assertRewriteFails(
        null,
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
            "  Hello {$world}\n",
            "{/template}\n\n",
            "{template bad}\n",
            "  {if $x}\n",
            "    <!--\n",
            "  {/if}\n",
            "  {call foo/}\n",
            "{/template}"));

    // But if it doesn't, it's none of our business.
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "Hello {$world |escapeHtml}\n",
            "{/template}\n\n",
            "{template bad}\n",
              "{if $x}",
                "<!--",
              "{/if}\n",
            // No call to foo in this version.
            "{/template}"));
  }

  public final void testExternTemplates() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<script>",
                "var x = {call bar /},",  // Not defined in this compilation unit.
                "y = {$y |escapeJsValue};",
              "</script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<script>",
                "var x = {call bar /},",  // Not defined in this compilation unit.
                "y = {$y};",
              "</script>\n",
            "{/template}"));
  }

  public final void testNonContextualCallers() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\" private=\"true\"}\n",
              "{$x |escapeHtml}\n",
            "{/template}\n\n",
            "{template bar}\n",
              "<b>{call foo /}</b> {$y}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\" private=\"true\"}\n",
              "{$x}\n",
            "{/template}\n\n",
            "{template bar}\n",
              "<b>{call foo /}</b> {$y}\n",
            "{/template}"));

    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"contextual\" private=\"true\"}\n",
              "{$x |escapeHtml}\n",
            "{/template}\n\n",
            "{template .bar autoescape=\"false\"}\n",
              "<b>{call .foo /}</b> {$y}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"contextual\" private=\"true\"}\n",
              "{$x}\n",
            "{/template}\n\n",
            "{template .bar autoescape=\"false\"}\n",
              "<b>{call .foo /}</b> {$y}\n",
            "{/template}"));
  }

  public final void testUnquotedAttributes() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<button onclick=alert({$msg |escapeJsValue |escapeHtmlAttributeNospace})>",
              "Launch</button>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<button onclick=alert({$msg})>Launch</button>\n",
            "{/template}"));
  }

  public final void testMessagesWithEmbeddedTags() throws Exception {
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "{msg desc=\"Say hello\"}Hello, <b>World</b>{/msg}\n",
            "{/template}"));
  }

  public final void testNamespaces() throws Exception {
    // Test calls in namespaced files.
    assertContextualRewriting(
        join(
            "{namespace soy.examples.codelab}\n\n",
            "/** */\n",
            "{template .main autoescape=\"contextual\"}\n",
              "<title>{call soy.examples.codelab.pagenum__C81 data=\"all\" /}</title>",
              "",
              "<script>",
                "var pagenum = \"{call soy.examples.codelab.pagenum__C13 data=\"all\" /}\"; ",
                "...",
              "</script>\n",
            "{/template}\n\n",
            "/**\n",
            " * @param pageIndex 0-indexed index of the current page.\n",
            " * @param pageCount Total count of pages.  Strictly greater than pageIndex.\n",
            " */\n",
            "{template .pagenum autoescape=\"contextual\" private=\"true\"}\n",
              "{$pageIndex} of {$pageCount}\n",
            "{/template}\n\n",
            "/**\n",
            " * @param pageIndex 0-indexed index of the current page.\n",
            " * @param pageCount Total count of pages.  Strictly greater than pageIndex.\n",
            " */\n",
            "{template .pagenum__C81 autoescape=\"contextual\" private=\"true\"}\n",
              "{$pageIndex |escapeHtmlRcdata} of {$pageCount |escapeHtmlRcdata}\n",
            "{/template}\n\n",
            "/**\n",
            " * @param pageIndex 0-indexed index of the current page.\n",
            " * @param pageCount Total count of pages.  Strictly greater than pageIndex.\n",
            " */\n",
            "{template .pagenum__C13 autoescape=\"contextual\" private=\"true\"}\n",
              "{$pageIndex |escapeJsString} of {$pageCount |escapeJsString}\n",
            "{/template}"),
        join(
            "{namespace soy.examples.codelab}\n\n",
            "/** */\n",
            "{template .main autoescape=\"contextual\"}\n",
            "  <title>{call .pagenum data=\"all\" /}</title>\n",
            "  <script>\n",
            "    var pagenum = \"{call name=\".pagenum\" data=\"all\" /}\";\n",
            "    ...\n",
            "  </script>\n",
            "{/template}\n\n",
            "/**\n",
            " * @param pageIndex 0-indexed index of the current page.\n",
            " * @param pageCount Total count of pages.  Strictly greater than pageIndex.\n",
            " */\n",
            "{template .pagenum autoescape=\"contextual\" private=\"true\"}\n",
            "  {$pageIndex} of {$pageCount}\n",
            "{/template}"));
  }

  public final void testConditionalAttributes() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<div{if $className} class=\"{$className |escapeHtmlAttribute}\"{/if}>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<div{if $className} class=\"{$className}\"{/if}>\n",
            "{/template}"));
  }

  public final void testExtraSpacesInTag() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<div {if $className} class=\"{$className |escapeHtmlAttribute}\"{/if} id=x>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<div {if $className} class=\"{$className}\"{/if} id=x>\n",
            "{/template}"));
  }

  public final void testOptionalAttributes() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template name=\"iconTemplate\" autoescape=\"contextual\"}\n",
              "<img class=\"{$iconClass |escapeHtmlAttribute}\"",
              "{if $iconId}",
                " id=\"{$iconId |escapeHtmlAttribute}\"",
              "{/if}",
              " src=",
              "{if $iconPath}",
                "\"{$iconPath |filterNormalizeUri |escapeHtmlAttribute}\"",
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
            "{template name=\"iconTemplate\" autoescape=\"contextual\"}\n",
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

  public final void testDynamicAttrName() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<img src=\"bar\" {$baz |filterHtmlAttributes}=\"boo\">\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<img src=\"bar\" {$baz}=\"boo\">\n",
            "{/template}"));
  }

  public final void testDynamicAttritubes() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<img src=\"bar\" {$baz |filterHtmlAttributes}>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<img src=\"bar\" {$baz}>\n",
            "{/template}"));
  }

  public final void testDynamicElementName() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<h{$headerLevel |filterHtmlElementName}>Header" +
              "</h{$headerLevel |filterHtmlElementName}>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<h{$headerLevel}>Header</h{$headerLevel}>\n",
            "{/template}"));
  }

  public final void testOptionalValuelessAttributes() throws Exception {
    assertContextualRewritingNoop(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<input {if c}checked{/if}>",
              "<input {if c}id={id |customEscapeDirective}{/if}>\n",
            "{/template}"));
  }

  public final void testDirectivesOrderedProperly() throws Exception {
    // The |bidiSpanWrap directive takes HTML and produces HTML, so the |escapeHTML
    // should appear first.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "{$x |escapeHtml |bidiSpanWrap}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "{$x |bidiSpanWrap}\n",
            "{/template}"));

    // But if we have a |bidiSpanWrap directive in a non HTML context, then don't reorder.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<script>var html = {$x |bidiSpanWrap |escapeJsValue}</script>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "<script>var html = {$x |bidiSpanWrap}</script>\n",
            "{/template}"));
  }

  public final void testDelegateTemplatesAreEscaped() throws Exception {
    assertContextualRewriting(
        join(
            "{delpackage dp}\n",
            "{namespace ns}\n\n",
            "/** @param x */\n",
            "{deltemplate foo autoescape=\"contextual\"}\n",
              "{$x |escapeHtml}\n",
            "{/deltemplate}"),
        join(
            "{delpackage dp}\n",
            "{namespace ns}\n\n",
            "/** @param x */\n",
            "{deltemplate foo autoescape=\"contextual\"}\n",
              "{$x}\n",
            "{/deltemplate}"));
  }

  public final void testDelegateTemplateCalledInNonPcdataContexts()
      throws Exception {
    assertContextualRewriting(
        join(
            "{delpackage dp}\n",
            "{namespace ns}\n\n",
            "{template main autoescape=\"contextual\"}\n",
              "<script>{delcall foo__C2010 /}</script>\n",
            "{/template}\n\n",
            "/** @param x */\n",
            "{deltemplate foo autoescape=\"contextual\"}\n",
              "x = {$x |escapeHtml}\n",
            "{/deltemplate}\n\n",
            "/** @param x */\n",
            "{deltemplate foo__C2010 autoescape=\"contextual\"}\n",
              "x = {$x |escapeJsValue}\n",
            "{/deltemplate}"),
        join(
            "{delpackage dp}\n",
            "{namespace ns}\n\n",
            "{template main autoescape=\"contextual\"}\n",
              "<script>{delcall foo /}</script>\n",
            "{/template}\n\n",
            "/** @param x */\n",
            "{deltemplate foo autoescape=\"contextual\"}\n",
              "x = {$x}\n",
            "{/deltemplate}"));
  }

  public final void testDelegateTemplatesReturnTypesUnioned()
      throws Exception {
    assertRewriteFails(
        "In file no-path-0:7, template main: " +
        "Slash (/) cannot follow the preceding branches since it is unclear whether the slash " +
        "is a RegExp literal or division operator.  " +
        "Please add parentheses in the branches leading to " +
        "`/foo/i.test(s) && alert(s);</script>`",
        join(
            "{namespace ns}\n\n",
            "{template main autoescape=\"contextual\"}\n",
              "{delcall foo}\n",
                "{param x: '' /}\n",
              "{/delcall}\n",
              // The / here is intended to start a regex, but if the version
              // from dp2 is used it won't be.
              "/foo/i.test(s) && alert(s);\n",
              "</script>\n",
            "{/template}"),
        join(
            "{delpackage dp1}\n",
            "{namespace ns}\n\n",
            "/** @param x */\n",
            "{deltemplate foo autoescape=\"contextual\"}\n",
              "<script>x = {$x};\n",  // semicolon terminated
            "{/deltemplate}"),
        join(
            "{delpackage dp2}\n",
            "{namespace ns}\n\n",
            "/** @param x */\n",
            "{deltemplate foo autoescape=\"contextual\"}\n",
              "<script>x = {$x}\n",  // not semicolon terminated
            "{/deltemplate}"));
  }


  public final void testTypedLetBlockIsContextuallyEscaped() {
    assertContextualRewriting(
        join(
            "{template t autoescape=\"contextual\"}\n",
            "<script> var y = '",
            // Note that the contents of the {let} block are escaped in HTML PCDATA context, even
            // though it appears in a JS string context in the template.
            "{let $l kind=\"html\"}",
              "<div>{$y |escapeHtml}</div>",
            "{/let}",
            "{$y |escapeJsString}'</script>\n",
            "{/template}"),
        join(
            "{template t autoescape=\"contextual\"}\n",
            "<script> var y = '\n",
            "{let $l kind=\"html\"}\n",
              "<div>{$y}</div>",
            "{/let}",
            "{$y}'</script>\n",
            "{/template}"));
  }


  public final void testUntypedLetBlockIsContextuallyEscaped() {
    // Test that the behavior for let blocks without kind attribute is unchanged (i.e., they are
    // contextually escaped in the context the {let} command appears in).
    assertContextualRewriting(
        join(
            "{template t autoescape=\"contextual\"}\n",
            "<script> var y = '",
            "{let $l}",
              "<div>{$y |escapeJsString}</div>",
            "{/let}",
            "{$y |escapeJsString}'</script>\n",
            "{/template}"),
        join(
            "{template t autoescape=\"contextual\"}\n",
            "<script> var y = '\n",
            "{let $l}\n",
              "<div>{$y}</div>",
            "{/let}",
            "{$y}'</script>\n",
            "{/template}"));
  }


  public final void testTypedLetBlockMustEndInStartContext() {
    assertRewriteFails(
        "In file no-path:2, template t: " +
        "A strict block of kind=\"html\" cannot end in context (Context JS REGEX). " +
        "Likely cause is an unclosed script block or attribute: " +
        "{let $l kind=\"html\"}",
        join(
            "{template t autoescape=\"contextual\"}\n",
            "{let $l kind=\"html\"}\n",
              "<script> var y ='{$y}';",
            "{/let}\n",
            "{/template}"));
  }


  public final void testTypedLetBlockIsStrictModeAutoescaped() {
    assertRewriteFails(
        "In file no-path:5, template t: " +
        "Autoescape-cancelling print directive |customEscapeDirective not allowed in strict " +
        "blocks of non-text kind: {$y |customEscapeDirective}",
        join(
            "{namespace ns}\n\n",
            "{template t autoescape=\"contextual\"}\n",
            "{let $l kind=\"html\"}\n",
              "<b>{$y |customEscapeDirective}</b>",
            "{/let}\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path:5, template t: " +
        "Soy strict autoescaping currently forbids calls to non-strict templates, unless the " +
        "context is kind=\"text\", since there's no guarantee the callee is safe: " +
        "{call .other data=\"all\" /}",
        join(
            "{namespace ns}\n\n",
            "{template t autoescape=\"contextual\"}\n",
            "{let $l kind=\"html\"}\n",
              "<b>{call .other data=\"all\"/}</b>",
            "{/let}\n",
            "{/template}\n\n",
            "{template .other autoescape=\"contextual\"}\n",
              "Hello World\n",
            "{/template}"));

    // Non-autoescape-cancelling directives are allowed.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template t autoescape=\"contextual\"}\n",
            "{let $l kind=\"html\"}",
              "<b>{$y |customOtherDirective |escapeHtml}</b>",
            "{/let}\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template t autoescape=\"contextual\"}\n",
            "{let $l kind=\"html\"}\n",
              "<b>{$y |customOtherDirective}</b>",
            "{/let}\n",
            "{/template}"));
  }


  public final void testTypedLetBlockNotAllowedInNonContextualTemplate() {
    assertRewriteFails(
        "In file no-path:2, template t: " +
        "{let} node with 'kind' attribute is only permitted in contextually autoescaped " +
        "templates: {let $l kind=\"html\"}<b>{$y}</b>{/let}",
        join(
            "{template t autoescape=\"true\"}\n",
            "{let $l kind=\"html\"}",
              "<b>{$y}</b>",
            "{/let}\n",
            "{/template}"));
  }


  public final void testNonTypedParamMustEndInHtmlContextButWasAttribute() throws Exception {
    assertRewriteFails(
        "In file no-path:5, template caller: " +
        "Blocks should start and end in HTML context: {param foo}",
        join(
            "{namespace ns}\n\n",
            "{template caller autoescape=\"contextual\"}\n",
            "  {call callee}\n",
            "    {param foo}<a href='{/param}\n",
            "  {/call}\n",
            "{/template}\n\n",
            "{template callee autoescape=\"contextual\"}\n",
            "  <b>{$foo}</b>\n",
            "{/template}\n"));
  }


  public final void testNonTypedParamMustEndInHtmlContextButWasScript() throws Exception {
    assertRewriteFails(
        "In file no-path:5, template caller: " +
        "Blocks should start and end in HTML context: {param foo}",
        join(
            "{namespace ns}\n\n",
            "{template caller autoescape=\"contextual\"}\n",
            "  {call callee}\n",
            "    {param foo}<script>var x={/param}\n",
            "  {/call}\n",
            "{/template}\n\n",
            "{template callee autoescape=\"contextual\"}\n",
            "  <b>{$foo}</b>\n",
            "{/template}\n"));
  }


  public final void testNonTypedParamGetsContextuallyAutoescaped() throws Exception {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template caller autoescape=\"contextual\"}\n",
              "{call callee}",
                "{param fooHtml}",
                  "<a href=\"http://google.com/search?q={$query |escapeUri}\" ",
                    "onclick=\"alert('{$query |escapeJsString |escapeHtmlAttribute}')\">",
                    "Search for {$query |escapeHtml}",
                  "</a>",
                "{/param}",
              "{/call}",
            "\n{/template}\n\n",
            "{template callee autoescape=\"contextual\"}\n",
              "{$fooHTML |noAutoescape}",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template caller autoescape=\"contextual\"}\n",
            "  {call callee}\n",
            "    {param fooHtml}\n",
            "      <a href=\"http://google.com/search?q={$query}\"\n",
            "         onclick=\"alert('{$query}')\">\n",
            "        Search for {$query}\n",
            "      </a>\n",
            "    {/param}\n",
            "  {/call}\n",
            "{/template}\n\n",
            "{template callee autoescape=\"contextual\"}\n",
            "  {$fooHTML |noAutoescape}\n",
            "{/template}"));
  }


  public final void testTypedParamBlockIsContextuallyEscaped() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template caller autoescape=\"contextual\"}\n",
            "<div>",
              "{call callee}",
                "{param x kind=\"html\"}",
                  "<script> var y ='{$y |escapeJsString}';</script>",
                "{/param}",
              "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template callee autoescape=\"contextual\" private=\"true\"}\n",
            "<b>{$x |escapeHtml}</b>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template caller autoescape=\"contextual\"}\n",
              "<div>",
                "{call callee}{param x kind=\"html\"}",
                  "<script> var y ='{$y}';</script>",
                "{/param}{/call}",
              "</div>\n",
            "{/template}\n\n",
            "{template callee autoescape=\"contextual\" private=\"true\"}\n",
              "<b>{$x}</b>\n",
            "{/template}"));
  }


  public final void testTypedParamBlockMustEndInStartContext() {
    assertRewriteFails(
        "In file no-path:4, template caller: " +
        "A strict block of kind=\"html\" cannot end in context (Context JS REGEX). " +
        "Likely cause is an unclosed script block or attribute: " +
        "{param x kind=\"html\"}",
        join(
            "{namespace ns}\n\n",
            "{template caller autoescape=\"contextual\"}\n",
              "<div>",
                "{call callee}{param x kind=\"html\"}<script> var y ='{$y}';{/param}{/call}",
              "</div>\n",
            "{/template}\n\n",
            "{template callee autoescape=\"contextual\" private=\"true\"}\n",
              "<b>{$x}</b>\n",
            "{/template}"));
  }


  public final void testTypedParamBlockIsStrictModeAutoescaped() {
    assertRewriteFails(
        "In file no-path:4, template caller: " +
        "Autoescape-cancelling print directive |customEscapeDirective not allowed in strict " +
        "blocks of non-text kind: {$y |customEscapeDirective}",
        join(
            "{namespace ns}\n\n",
            "{template caller autoescape=\"contextual\"}\n",
              "<div>",
                "{call callee}",
                  "{param x kind=\"html\"}<b>{$y |customEscapeDirective}</b>{/param}",
                "{/call}",
              "</div>\n",
            "{/template}\n\n",
            "{template callee autoescape=\"contextual\" private=\"true\"}\n",
              "<b>{$x}</b>\n",
            "{/template}"));

    // NOTE: This error only works for non-extern templates.
    assertRewriteFails(
        "In file no-path:4, template caller: " +
        "Soy strict autoescaping currently forbids calls to non-strict templates, unless the " +
        "context is kind=\"text\", since there's no guarantee the callee is safe: " +
        "{call subCallee data=\"all\" /}",
        join(
            "{namespace ns}\n\n",
            "{template caller autoescape=\"contextual\"}\n",
              "<div>",
                "{call callee}",
                  "{param x kind=\"html\"}{call subCallee data=\"all\"/}{/param}",
                "{/call}",
              "</div>\n",
            "{/template}\n\n",
            "{template callee autoescape=\"contextual\" private=\"true\"}\n",
              "<b>{$x}</b>\n",
            "{/template}\n\n",
            "{template subCallee autoescape=\"contextual\" private=\"true\"}\n",
              "<b>{$x}</b>\n",
            "{/template}"));

    // Non-escape-cancelling directives are allowed.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template caller autoescape=\"contextual\"}\n",
            "<div>",
              "{call callee}",
                "{param x kind=\"html\"}<b>{$y |customOtherDirective |escapeHtml}</b>{/param}",
              "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template callee autoescape=\"contextual\" private=\"true\"}\n",
              "<b>{$x |escapeHtml}</b>\n",
            "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template caller autoescape=\"contextual\"}\n",
            "<div>",
              "{call callee}",
                "{param x kind=\"html\"}<b>{$y |customOtherDirective}</b>{/param}",
              "{/call}",
            "</div>\n",
            "{/template}\n\n",
            "{template callee autoescape=\"contextual\" private=\"true\"}\n",
              "<b>{$x}</b>\n",
            "{/template}"));
  }


  public final void testTypedParamBlockNotAllowedInNonContextualTemplate() {
    assertRewriteFails(
        "In file no-path:4, template caller: " +
        "{param} node with 'kind' attribute is only permitted in contextually autoescaped " +
        "templates: {param x kind=\"html\"}<b>{$y}</b>;{/param}",
        join(
            "{namespace ns}\n\n",
            "{template caller autoescape=\"true\"}\n",
              "<div>",
                "{call callee}{param x kind=\"html\"}<b>{$y}</b>;{/param}{/call}",
              "</div>\n",
            "{/template}\n\n",
            "{template callee autoescape=\"contextual\" private=\"true\"}\n",
              "<b>{$x}</b>\n",
            "{/template}"));
  }


  public final void testTypedTextParamBlock() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template caller autoescape=\"contextual\"}\n",
            "<div>",
              "{call callee}",
                "{param x kind=\"text\"}",
                  "Hello {$x |text} <{$y |text}, \"{$z |text}\">",
                "{/param}",
              "{/call}",
            "</div>\n",
          "{/template}\n",
          "\n",
          "{template callee autoescape=\"contextual\" private=\"true\"}\n",
            "<b>{$x |escapeHtml}</b>\n",
          "{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template caller autoescape=\"contextual\"}\n",
              "<div>",
                "{call callee}{param x kind=\"text\"}",
                  "Hello {$x} <{$y}, \"{$z}\">",
                "{/param}{/call}",
              "</div>\n",
            "{/template}\n",
            "\n",
            "{template callee autoescape=\"contextual\" private=\"true\"}\n",
              "<b>{$x}</b>\n",
            "{/template}"));
  }


  public final void testTypedTextLetBlock() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "{let $a kind=\"text\"}",
                "Hello {$x |text} <{$y |text}, \"{$z |text}\">",
              "{/let}",
              "{$a |escapeHtml}",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"contextual\"}\n",
              "{let $a kind=\"text\"}",
                "Hello {$x} <{$y}, \"{$z}\">",
              "{/let}",
              "{$a}",
            "\n{/template}"));
  }


  // Tests for the initial, rudimentary strict contextual mode (which will be initially used only in
  // param and let nodes of non-text kind).
  // This basic strict mode
  // - does not allow autoescape-cancelling print directives
  // - does not allow any call/delcall commands
  // - is enabled using autoescape="strict"

  public void testStrictModeRejectsAutoescapeCancellingDirectives() {
    assertRewriteFails(
        "In file no-path:4, template main: " +
        "Autoescape-cancelling print directive |customEscapeDirective not allowed in strict " +
        "blocks of non-text kind: {$foo |customEscapeDirective}",
        join(
            "{namespace ns}\n\n",
            "{template main autoescape=\"strict\"}\n",
              "<b>{$foo|customEscapeDirective}</b>\n",
            "{/template}"));
  }


  public void testStrictModeRejectsNonStrictCalls() {
    assertRewriteFails(
        "In file no-path:4, template main: " +
        "Soy strict autoescaping currently forbids calls to non-strict templates, unless the " +
        "context is kind=\"text\", since there's no guarantee the callee is safe: " +
        "{call bar data=\"all\" /}",
        join(
            "{namespace ns}\n\n",
            "{template main autoescape=\"strict\" kind=\"html\"}\n",
              "<b>{call bar data=\"all\"/}\n",
            "{/template}\n\n" +
            "{template bar autoescape=\"contextual\"}\n",
              "Hello World\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path-0:4, template main: " +
        "Soy strict autoescaping currently forbids calls to non-strict templates, unless the " +
        "context is kind=\"text\", since there's no guarantee the callee is safe: " +
        "{delcall foo}",
        join(
            "{namespace ns}\n\n",
            "{template main autoescape=\"strict\"}\n",
              "{delcall foo}\n",
                "{param x: '' /}\n",
              "{/delcall}\n",
            "{/template}"),
        join(
            "{delpackage dp1}\n",
            "{namespace ns}\n\n",
            "/** @param x */\n",
            "{deltemplate foo autoescape=\"contextual\"}\n",
              "<b>{$x}</b>\n",
            "{/deltemplate}"),
        join(
            "{delpackage dp2}\n",
            "{namespace ns}\n\n",
            "/** @param x */\n",
            "{deltemplate foo autoescape=\"contextual\"}\n",
              "<i>{$x}</i>\n",
            "{/deltemplate}"));
  }


  public void testContextualCannotCallStrictOfWrongContext() {
    // Can't call a text template from a strict context.
    assertRewriteFails(
        "In file no-path:4, template main: " +
        "Cannot call strictly autoescaped template foo of kind=\"text\" from incompatible " +
        "context (Context HTML_PCDATA). Strict templates generate extra code to safely call " +
        "templates of other content kinds, but non-strict templates do not: " +
        "{call foo}",
        join(
            "{namespace ns}\n\n",
            "{template main autoescape=\"contextual\"}\n",
              "{call foo}\n",
                "{param x: '' /}\n",
              "{/call}\n",
            "{/template}\n\n",
            "{template foo autoescape=\"strict\" kind=\"text\"}\n",
              "<b>{$x}</b>\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path-0:4, template main: " +
        "Cannot call strictly autoescaped template foo of kind=\"text\" from incompatible " +
        "context (Context HTML_PCDATA). Strict templates generate extra code to safely call " +
        "templates of other content kinds, but non-strict templates do not: " +
        "{delcall foo}",
        join(
            "{namespace ns}\n\n",
            "{template main autoescape=\"contextual\"}\n",
              "{delcall foo}\n",
                "{param x: '' /}\n",
              "{/delcall}\n",
            "{/template}"),
        join(
            "{delpackage dp1}\n",
            "{namespace ns}\n\n",
            "/** @param x */\n",
            "{deltemplate foo autoescape=\"strict\" kind=\"text\"}\n",
              "<b>{$x}</b>\n",
            "{/deltemplate}"),
        join(
            "{delpackage dp2}\n",
            "{namespace ns}\n\n",
            "/** @param x */\n",
            "{deltemplate foo autoescape=\"strict\" kind=\"text\"}\n",
              "<i>{$x}</i>\n",
            "{/deltemplate}"));
  }


  public void testStrictModeAllowsNonAutoescapeCancellingDirectives() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(join(
        "{template main autoescape=\"strict\"}\n",
          "<b>{$foo |customOtherDirective}</b>\n",
        "{/template}"));
    String rewrittenTemplate = rewrittenSource(soyTree);
    assertEquals(
        join(
            "{template main autoescape=\"strict\"}\n",
              "<b>{$foo |customOtherDirective |escapeHtml}</b>\n",
            "{/template}"),
        rewrittenTemplate.trim());
  }


  public void testTextDirectiveBanned() {
    assertRewriteFails(
        "In file no-path:2, template main: " +
        "Print directive |text is only for internal use by the Soy compiler.",
        join(
            "{template main autoescape=\"contextual\"}\n",
              "{$foo |text}\n",
            "{/template}"));
  }


  public void testStrictModeDoesNotYetHaveDefaultParamKind() {
    assertRewriteFails(
        "In file no-path:4, template main: " +
        "In strict templates, {let}...{/let} blocks require an explicit kind=\"<type>\". " +
        "This restriction will be lifted soon once a reasonable default is chosen. " +
        "(Note that {let $x: $y /} is NOT subject to this restriction). " +
        "Cause: {let $x}",
        join(
            "{namespace ns}\n\n",
            "{template main autoescape=\"strict\"}\n",
              "{let $x}No Kind{/let}\n",
            "{/template}"));
    assertRewriteFails(
        "In file no-path:4, template main: " +
        "In strict templates, {param}...{/param} blocks require an explicit kind=\"<type>\". " +
        "This restriction will be lifted soon once a reasonable default is chosen. " +
        "(Note that {param x: $y /} is NOT subject to this restriction). " +
        "Cause: {param x}",
        join(
            "{namespace ns}\n\n",
            "{template main autoescape=\"strict\"}\n",
              "{call foo}{param x}No Kind{/param}{/call}\n",
            "{/template}"));
    // Test with a non-strict template but in a strict block.
    assertRewriteFails(
        "In file no-path:4, template main: " +
        "In strict templates, {let}...{/let} blocks require an explicit kind=\"<type>\". " +
        "This restriction will be lifted soon once a reasonable default is chosen. " +
        "(Note that {let $x: $y /} is NOT subject to this restriction). " +
        "Cause: {let $x}",
        join(
            "{namespace ns}\n\n",
            // Non-strict template.
            "{template main autoescape=\"contextual\"}\n",
              // Strict block in the non-strict template.
              "{let $y kind=\"html\"}",
                // Missing kind attribute in a let in a strict block.
                "{let $x}No Kind{/let}",
                "{$x}",
              "{/let}",
            "\n{/template}"));
  }


  public void testStrictModeRequiresStartAndEndToBeCompatible() {
    assertRewriteFails(
        "In file no-path:3, template main: " +
        "A strict block of kind=\"html\" cannot end in context (Context JS_SQ_STRING). " +
        "Likely cause is an unterminated string literal: " +
        "{template main autoescape=\"strict\"}",
        join(
            "{namespace ns}\n\n",
            "{template main autoescape=\"strict\"}\n",
              "<script>var x='\n",
            "{/template}"));
  }


  public void testStrictUriMustNotBeEmpty() {
    assertRewriteFails(
        "In file no-path:3, template main: " +
        "A strict block of kind=\"uri\" cannot end in context (Context URI START). " +
        "Likely cause is an unterminated or empty URI: " +
        "{template main autoescape=\"strict\" kind=\"uri\"}",
        join(
            "{namespace ns}\n\n",
            "{template main autoescape=\"strict\" kind=\"uri\"}\n",
            "{/template}"));
  }


  public void testContextualCanCallStrictModeUri() {
    // This ensures that a contextual template can use a strict URI -- specifically testing that
    // the contextual call site matching doesn't do an exact match on context (which would be
    // sensitive to whether single quotes or double quotes are used) but uses the logic in
    // Context.isValidStartContextForContentKind().
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"contextual\"}\n",
              "<a href=\"{call .bar data=\"all\" /}\">Test</a>",
            "\n{/template}\n\n",
            "{template .bar autoescape=\"strict\" kind=\"uri\"}\n",
              "http://www.google.com/search?q={$x |escapeUri}",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"contextual\"}\n",
              "<a href=\"{call .bar data=\"all\" /}\">Test</a>",
            "\n{/template}\n\n",
            "{template .bar autoescape=\"strict\" kind=\"uri\"}\n",
              "http://www.google.com/search?q={$x}",
            "\n{/template}"));
  }


  public void testStrictAttributes() {
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\" kind=\"attributes\"}\n",
              "onclick={$x |escapeJsValue |escapeHtmlAttributeNospace} ",
              "style='{$y |filterCssValue |escapeHtmlAttribute}' ",
              "checked ",
              "foo=\"bar\" ",
              "title='{$z |escapeHtmlAttribute}'",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\" kind=\"attributes\"}\n",
              "onclick={$x} ",
              "style='{$y}' ",
              "checked ",
              "foo=\"bar\" ",
              "title='{$z}'",
            "\n{/template}"));
  }


  public void testStrictAttributesMustBeTerminated() {
    // Basic "forgot to close attribute" issue.
    assertRewriteFails(
        "In file no-path:3, template ns.foo: " +
        "A strict block of kind=\"attributes\" cannot end in context " +
        "(Context HTML_NORMAL_ATTR_VALUE PLAIN_TEXT DOUBLE_QUOTE). " +
        "Likely cause is an unterminated attribute value, or ending with an unquoted attribute: " +
        "{template .foo autoescape=\"strict\" kind=\"attributes\"}",
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\" kind=\"attributes\"}\n",
              "foo=\"{$x}",
            "\n{/template}"));
  }


  public void testStrictAttributesMustNotEndInUnquotedAttributeValue() {
    // Ensure that any final attribute-value pair is quoted -- otherwise, if the use site of the
    // value forgets to add spaces, the next attribute will be swallowed.
    assertRewriteFails(
        "In file no-path:3, template ns.foo: " +
        "A strict block of kind=\"attributes\" cannot end in context " +
        "(Context JS SCRIPT SPACE_OR_TAG_END DIV_OP). " +
        "Likely cause is an unterminated attribute value, or ending with an unquoted attribute: " +
        "{template .foo autoescape=\"strict\" kind=\"attributes\"}",
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\" kind=\"attributes\"}\n",
              "onclick={$x}",
            "\n{/template}"));

    assertRewriteFails(
        "In file no-path:3, template ns.foo: " +
        "A strict block of kind=\"attributes\" cannot end in context " +
        "(Context HTML_NORMAL_ATTR_VALUE PLAIN_TEXT SPACE_OR_TAG_END). " +
        "Likely cause is an unterminated attribute value, or ending with an unquoted attribute: " +
        "{template .foo autoescape=\"strict\" kind=\"attributes\"}",
        join(
            "{namespace ns}\n\n",
            "{template .foo autoescape=\"strict\" kind=\"attributes\"}\n",
              "title={$x}",
            "\n{/template}"));
  }

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


  public void testStrictModeJavascriptRegexHandling() {
    // NOTE: This ensures that the call site is treated as a dynamic value, such that it switches
    // from "before regexp" context to "before division" context. Note this isn't foolproof (such
    // as when the expression leads to a full statement) but is generally going to be correct more
    // often.
    assertContextualRewriting(
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"strict\"}\n",
              "<script>",
                "{call .bar /}/{$x |escapeJsValue}+/{$x |escapeJsRegex}/g",
              "</script>",
            "\n{/template}"),
        join(
            "{namespace ns}\n\n",
            "{template foo autoescape=\"strict\"}\n",
              "<script>",
                "{call .bar /}/{$x}+/{$x}/g",
              "</script>",
            "\n{/template}"));
  }


  public void testStrictModeEscapesCallSites() {
    String source =
        "{namespace ns}\n\n" +
        "{template .main autoescape=\"strict\"}\n" +
          "{call .htmlTemplate /}" +
          "<script>var x={call .htmlTemplate /};</script>\n" +
          "<script>var x={call .jsTemplate /};</script>\n" +
          "{call .externTemplate /}" +
        "\n{/template}\n\n" +
        "{template .htmlTemplate autoescape=\"strict\"}\n" +
          "Hello World" +
        "\n{/template}\n\n" +
        "{template .jsTemplate autoescape=\"strict\" kind=\"js\"}\n" +
          "foo()" +
        "\n{/template}";
    
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(source);
    new CheckEscapingSanityVisitor().exec(soyTree);
    new ContextualAutoescaper(SOY_PRINT_DIRECTIVES).rewrite(
        soyTree, false /* assumeNoExternalCalls */);
    TemplateNode mainTemplate = soyTree.getChild(0).getChild(0);
    assertEquals("Sanity check", "ns.main", mainTemplate.getTemplateName());
    final List<CallNode> callNodes = SharedTestUtils.getAllNodesOfType(
        mainTemplate, CallNode.class);
    assertEquals(4, callNodes.size());
    assertEquals("HTML->HTML escaping should be pruned",
        ImmutableList.of(), callNodes.get(0).getEscapingDirectiveNames());
    assertEquals("JS -> HTML call should be escaped",
        ImmutableList.of("|escapeJsValue"), callNodes.get(1).getEscapingDirectiveNames());
    assertEquals("JS -> JS pruned",
        ImmutableList.of(), callNodes.get(2).getEscapingDirectiveNames());
    assertEquals("HTML -> extern call should be escaped",
        ImmutableList.of("|escapeHtml"), callNodes.get(3).getEscapingDirectiveNames());
  }


  public void testStrictModeCarefulWithDelegates() {
    String source =
        "{namespace ns}\n\n" +
        "{template .main autoescape=\"strict\"}\n" +
          "{delcall ns.delegate /}" +
        "\n{/template}\n\n" +
        "/** A delegate returning HTML. */\n" +
        "{deltemplate ns.delegate autoescape=\"strict\"}\n" +
          "Hello World" +
        "\n{/deltemplate}";
    
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(source);
    new CheckEscapingSanityVisitor().exec(soyTree);
    new ContextualAutoescaper(SOY_PRINT_DIRECTIVES).rewrite(
        soyTree, false /* assumeNoExternalCalls */);
    TemplateNode mainTemplate = soyTree.getChild(0).getChild(0);
    assertEquals("Sanity check", "ns.main", mainTemplate.getTemplateName());
    final List<CallNode> callNodes = SharedTestUtils.getAllNodesOfType(
        mainTemplate, CallNode.class);
    assertEquals(1, callNodes.size());
    assertEquals("Escaping should be there since there might be extern delegates",
        ImmutableList.of("|escapeHtml"), callNodes.get(0).getEscapingDirectiveNames());
  }


  public void testStrictModeOptimizesDelegatesWhenExternalCallsBanned() {
    String source =
        "{namespace ns}\n\n" +
        "{template .main autoescape=\"strict\"}\n" +
          "{delcall ns.delegateHtml /}" +
          "{delcall ns.delegateText /}" +
        "\n{/template}\n\n" +
        "/** A delegate returning HTML. */\n" +
        "{deltemplate ns.delegateHtml autoescape=\"strict\"}\n" +
          "Hello World" +
        "\n{/deltemplate}\n\n" +
        "/** A delegate returning JS. */\n" +
        "{deltemplate ns.delegateText autoescape=\"strict\" kind=\"text\"}\n" +
          "Hello World" +
        "\n{/deltemplate}";
    
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(source);
    new CheckEscapingSanityVisitor().exec(soyTree);
    new ContextualAutoescaper(SOY_PRINT_DIRECTIVES).rewrite(
        soyTree, true /* assumeNoExternalCalls */);
    TemplateNode mainTemplate = soyTree.getChild(0).getChild(0);
    assertEquals("Sanity check", "ns.main", mainTemplate.getTemplateName());
    final List<CallNode> callNodes = SharedTestUtils.getAllNodesOfType(
        mainTemplate, CallNode.class);
    assertEquals(2, callNodes.size());
    assertEquals("We're compiling a complete set; we can optimize based on usages.",
        ImmutableList.of(), callNodes.get(0).getEscapingDirectiveNames());
    assertEquals("HTML -> TEXT requires escaping",
        ImmutableList.of("|escapeHtml"), callNodes.get(1).getEscapingDirectiveNames());
  }


  // TODO: Tests for dynamic attributes: <a on{$name}="...">,
  // <div data-{$name}={$value}>

  private static String join(String... lines) {
    return Joiner.on("").join(lines);
  }

  /**
   * Returns the contextually rewritten source.
   *
   * The Soy tree may have multiple files, but only the source code for the first is returned.
   */
  private String rewrittenSource(SoyFileSetNode soyTree)
      throws SoyAutoescapeException {

    List<TemplateNode> tmpls = new ContextualAutoescaper(SOY_PRINT_DIRECTIVES).rewrite(
        soyTree, false /* assumeNoExternalCalls */);

    StringBuilder src = new StringBuilder();
    src.append(soyTree.getChild(0).toSourceString());
    for (TemplateNode tn : tmpls) {
      src.append('\n').append(tn.toSourceString());
    }
    return src.toString();
  }

  private void assertContextualRewriting(String expectedOutput, String... inputs)
      throws SoyAutoescapeException {

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(inputs);
    new CheckEscapingSanityVisitor().exec(soyTree);

    String source = rewrittenSource(soyTree);
    assertEquals(expectedOutput, source.trim());

    // Make sure that the transformation is idempotent.
    if (!source.contains("__C")) {  // Skip if there are extern extra templates.
      assertEquals(expectedOutput, rewrittenSource(soyTree).trim());
    }

    // And idempotent from a normalized input if the templates are not autoescape="contextual".
    String input = join(inputs);
    String inputWithoutAutoescape = input
        .replaceAll("\\s+autoescape\\s*=\\s*\"(contextual|strict)\"", "")
        .replaceAll("\\s+kind\\s*=\\s*\"([a-z]*)\"", "");
    SoyFileSetNode soyTree2 = SharedTestUtils.parseSoyFiles(inputWithoutAutoescape);
    String original = soyTree2.getChild(0).toSourceString();
    assertEquals(original, rewrittenSource(soyTree2));
  }

  private void assertContextualRewritingNoop(String expectedOutput) throws SoyAutoescapeException {
    assertContextualRewriting(expectedOutput, expectedOutput);
  }

  /**
   * @param msg Message that should be reported to the template author.
   *     Null means don't care.
   */
  private void assertRewriteFails(
      @Nullable String msg,
      String... inputs) {
    List<SoyFileSupplier> soyFileSuppliers = Lists.newArrayList();
    for (int i = 0; i < inputs.length; ++i) {
      soyFileSuppliers.add(SoyFileSupplier.Factory.create(
          inputs[i], SoyFileKind.SRC, inputs.length == 1 ? "no-path" : "no-path-" + i));
    }
    SoyFileSetNode soyTree = new SoyFileSetParser(soyFileSuppliers)
        .setDoRunInitialParsingPasses(true)
        .setDoRunCheckingPasses(true)
        .setDoEnforceSyntaxVersionV2(false)
        .parse();

    try {
      new CheckEscapingSanityVisitor().exec(soyTree);
      rewrittenSource(soyTree);
    } catch (SoyAutoescapeException ex) {
      if (msg != null && !msg.equals(ex.getMessage())) {
        throw (ComparisonFailure) new ComparisonFailure("", msg, ex.getMessage()).initCause(ex);
      }
      return;
    }
    fail("Expected failure but was " + soyTree.getChild(0).toSourceString());
  }


  final static class FakeBidiSpanWrapDirective
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
    public SanitizedContent.ContentKind getContentKind() {
      return SanitizedContent.ContentKind.HTML;
    }
  }
}
