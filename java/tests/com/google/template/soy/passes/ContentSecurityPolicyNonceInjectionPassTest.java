/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link ContentSecurityPolicyNonceInjectionPass}. */
@RunWith(JUnit4.class)
public final class ContentSecurityPolicyNonceInjectionPassTest {

  private static final String DEFN =
      "  {@inject? csp_nonce: any}  /** Created by ContentSecurityPolicyNonceInjectionPass. */\n";
  private static final String NONCE =
      "{if $csp_nonce} nonce=\"{$csp_nonce |filterCspNonceValue |escapeHtmlAttribute}\"{/if}";

  @Test
  public void testTrivialTemplate() {
    assertInjected(
        join("{template .foo}\n", "Hello, World!\n", "{/template}"),
        join("{template .foo}\n", "Hello, World!\n", "{/template}"));
  }

  @Test
  public void testOneScriptWithBody() {
    assertInjected(
        join(
            "{template .foo}\n",
            DEFN,
            "<script" + NONCE + ">alert('Hello, World!')</script>\n",
            "{/template}"),
        join("{template .foo}\n", "<script>alert('Hello, World!')</script>\n", "{/template}"));
  }

  @Test
  public void testOneSrcedScript() {
    assertInjected(
        join(
            "{template .foo}\n",
            DEFN,
            "<script src=\"app.js\"" + NONCE + "></script>\n",
            "{/template}"),
        join("{template .foo}\n", "<script src=\"app.js\"></script>\n", "{/template}"));
  }

  @Test
  public void testManyScripts() {
    assertInjected(
        join(
            "{template .foo}\n",
            DEFN,
            "<script src=\"one.js\"" + NONCE + "></script>",
            "<script src=two.js" + NONCE + "></script>",
            "<script src=three.js" + NONCE + "></script>",
            "<h1>Not a script</h1>",
            "<script type='text/javascript'" + NONCE + ">main()</script>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "<script src=\"one.js\"></script>",
            "<script src=two.js></script>",
            "<script src=three.js ></script>",
            "<h1>Not a script</h1>",
            "<script type='text/javascript'>main()</script>\n",
            "{/template}"));
  }

  @Test
  public void testTooManyNonces() {
    assertInjected(
        join(
            "{template .foo}\n",
            "  {@param jsUrls: list<string>}\n",
            DEFN,
            "{for $jsUrl in $jsUrls}",
            "<script type=\"text/javascript\" ",
            "src='{$jsUrl |filterTrustedResourceUri |escapeHtmlAttribute}'",
            NONCE + "></script>",
            "{/for}\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "  {@param jsUrls: list<string>}\n",
            "{for $jsUrl in $jsUrls}\n",
            "<script type=\"text/javascript\" src='{$jsUrl}'></script>\n",
            "{/for}\n",
            "{/template}"));
  }

  @Test
  public void testFakeScripts() {
    assertInjected(
        join(
            "{template .foo}\n",
            DEFN,
            "<noscript></noscript>",
            "<script" + NONCE + ">alert('Hi');</script>",
            "<!-- <script>notAScript()</script> -->",
            "<textarea><script>notAScript()</script></textarea>",
            "<script is-script=yes"
                + NONCE
                + ">document.write('<\\script>not()<\\/script>');</script>",
            "<a href=\"//google.com/search?q=<script>hi()</script>\">Link</a>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "<noscript></noscript>",
            // An actual script in a sea of imposters.
            "<script>alert('Hi');</script>",
            // Injecting a nonce into something that is not a script might be bad.
            "<!-- <script>notAScript()</script> -->",
            "<textarea><script>notAScript()</script></textarea>",
            "<script is-script=yes>document.write('<\\script>not()<\\/script>');</script>",
            "<a href=\"//google.com/search?q=<script>hi()</script>\">Link</a>\n",
            "{/template}"));
  }

  @Test
  public void testPrintDirectiveInScriptTag() {
    assertInjected(
        join(
            "{template .foo}\n",
            "  {@param appScriptUrl: ?}\n",
            DEFN,
            "<script src=",
            "'{$appScriptUrl |filterTrustedResourceUri |escapeHtmlAttribute}'",
            NONCE + ">",
            "alert('Hello, World!')</script>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "  {@param appScriptUrl: ?}\n",
            "<script src='{$appScriptUrl}'>",
            "alert('Hello, World!')</script>\n",
            "{/template}"));
  }

  @Test
  public void testOneStyleTag() {
    assertInjected(
        join(
            "{template .foo}\n",
            DEFN,
            "<style type=text/css",
            NONCE,
            ">",
            "p {lb} color: purple {rb}",
            "</style>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "<style type=text/css>p {lb} color: purple {rb}</style>\n",
            "{/template}"));
  }

  @Test
  public void testTrailingSlashes() {
    assertInjected(
        join(
            "{template .foo}\n",
            DEFN,
            "<script src=//example.com/unquoted/url/" + NONCE + "></script>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "<script src=//example.com/unquoted/url/></script>\n",
            "{/template}"));
  }

  @Test
  public void testInlineEventHandlersAndStyles() {
    assertInjected(
        join(
            "{template .foo}\n",
            "  {@param height: int}\n",
            DEFN,
            "<a href='#' style='",
            "height:{$height |filterCssValue |escapeHtmlAttribute}px;'",
            " onclick='",
            "foo() &amp;& bar(\"baz\")'",
            "></a>",

            // Don't bless unquoted attributes since we can't
            // be confident that they end where they're supposed to,
            // so aren't sure that we aren't also blessing an
            // untrusted suffix.
            "<a href='#' onmouseover=foo()",
            " style=color:red></a>",
            // stricthtml mode doesn't preserve the whitespace around the equals sign
            "<input checked ONCHANGE=\"",
            "Panic()\"",
            ">",
            // ditto
            "<script onerror='",
            "scriptError()'",
            NONCE,
            ">baz()</script>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "  {@param height: int}\n",
            "<a href='#' style='height:{$height}px;' onclick='foo() &amp;& bar(\"baz\")'></a>",
            "<a href='#' onmouseover=foo() style=color:red></a>",
            "<input checked ONCHANGE = \"Panic()\">",
            "<script onerror= 'scriptError()'>baz()</script>\n",
            "{/template}"));
  }

  // regression test for a bug where an attacker controlled csp_nonce variable could introduce an
  // XSS because no escaping directives were applied.  Generally csp_nonce variables should not be
  // attacker controlled, but since applications are responsible for configuring them they may in
  // fact be.  So we need to escape them.
  @Test
  public void testEscaping_script() {
    SoyFileSet.Builder builder = SoyFileSet.builder();
    builder.add(
        join(
            "{namespace ns}\n",
            "{template .foo}\n",
            "<script>var innocentJs=\"foo\"</script>\n",
            "{/template}"),
        "test.soy");
    String renderedValue =
        builder
            .build()
            .compileTemplates()
            .renderTemplate("ns.foo")
            .setIj(ImmutableMap.of("csp_nonce", "\">alert('hello')</script><script data-foo=\""))
            .renderHtml()
            .get()
            .getContent();
    assertThat(renderedValue).isEqualTo("<script nonce=\"zSoyz\">var innocentJs=\"foo\"</script>");
  }

  @Test
  public void testEscaping_inline() {
    SoyFileSet.Builder builder = SoyFileSet.builder();
    builder.add(
        join(
            "{namespace ns}\n",
            "{template .foo}\n",
            "<a href='#' onmouseover='foo()'>click me</a>\n",
            "{/template}"),
        "test.soy");
    String renderedValue =
        builder
            .build()
            .compileTemplates()
            .renderTemplate("ns.foo")
            .setIj(ImmutableMap.of("csp_nonce", "*/alert('hello');/*"))
            .renderHtml()
            .get()
            .getContent();
    // We don't inject into inline event handlers anymore
    assertThat(renderedValue).isEqualTo("<a href='#' onmouseover='foo()'>click me</a>");
  }

  @Test
  public void testHtmlImport() {
    assertInjected(
        join(
            "{template .foo}\n",
            DEFN,
            "<link rel=\'Import\' href=\"foo.html\"" + NONCE + ">\n",
            "{/template}"),
        join("{template .foo}\n", "<link rel=\'Import\' href=\"foo.html\">\n", "{/template}"));
  }

  @Test
  public void testHtmlImport_multipleChildren() {
    assertInjected(
        join(
            "{template .foo}\n",
            "<link rel=\'import{1 |escapeHtmlAttribute}\' href=\"foo.html\">\n",
            "{/template}"),
        join("{template .foo}\n", "<link rel=\'import{1}\' href=\"foo.html\">\n", "{/template}"));
  }

  @Test
  public void testHtmlImport_emptyRel() {
    assertInjected(
        join("{template .foo}\n", "<link rel href=\"foo.html\">\n", "{/template}"),
        join("{template .foo}\n", "<link rel href=\"foo.html\">\n", "{/template}"));
  }

  @Test
  public void testPreload_as_script() {
    assertInjected(
        join(
            "{template .foo}\n",
            DEFN,
            "<link rel='preload' as='script' href='foo.js'" + NONCE + ">\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "<link rel='preload' as='script' href='foo.js'>\n",
            "{/template}"));
  }

  @Test
  public void testPreload_as_style() {
    assertInjected(
        join(
            "{template .foo}\n",
            DEFN,
            "<link rel='preload' as='style' href='foo.js'" + NONCE + ">\n",
            "{/template}"),
        join(
            "{template .foo}\n", "<link rel='preload' as='style' href='foo.js'>\n", "{/template}"));
  }

  @Test
  public void testPreload_as_wrong() {
    // as='css' is not a valid attribute for link[rel=preload]
    assertInjected(
        join("{template .foo}\n", "<link rel='preload' as='css' href='foo.js'>\n", "{/template}"),
        join("{template .foo}\n", "<link rel='preload' as='css' href='foo.js'>\n", "{/template}"));
  }

  private static String join(String... lines) {
    return Joiner.on("").join(lines);
  }

  private void assertInjected(String expectedOutput, String input) {
    String namespace = "{namespace ns}\n\n";
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(namespace + input)
            .runAutoescaper(true)
            .parse()
            .fileSet();

    StringBuilder src = new StringBuilder();
    src.append(soyTree.getChild(0).toSourceString());

    String output = src.toString().trim();
    if (output.startsWith("{namespace ns")) {
      output = output.substring(output.indexOf('}') + 1).trim();
    }

    assertThat(output).isEqualTo(expectedOutput);
  }
}
