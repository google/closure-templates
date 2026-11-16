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
import com.google.template.soy.error.ErrorReporter;
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
  private static final String DEFN_STYLE =
      "  {@inject? csp_style_nonce: any}"
          + "  /** Created by ContentSecurityPolicyNonceInjectionPass. */\n";
  private static final String NONCE =
      "{if $csp_nonce} nonce=\"{$csp_nonce |filterCspNonceValue |escapeHtmlAttribute}\"{/if}";
  private static final String NONCE_STYLE =
      "{if $csp_style_nonce}"
          + " nonce=\"{$csp_style_nonce |filterCspNonceValue |escapeHtmlAttribute}\"{/if}";

  @Test
  public void testTrivialTemplate() {
    assertInjected(
        join("{template foo}\n", "Hello, World!\n", "{/template}"),
        join("{template foo}\n", "Hello, World!\n", "{/template}"));
  }

  @Test
  public void testOneScriptWithBody() {
    assertInjected(
        join(
            "{template foo}\n",
            DEFN,
            "<script" + NONCE + ">alert('Hello, World!')</script>\n",
            "{/template}"),
        join("{template foo}\n", "<script>alert('Hello, World!')</script>\n", "{/template}"));
  }

  @Test
  public void testOneSrcedScript() {
    assertInjected(
        join(
            "{template foo}\n",
            DEFN,
            "<script src=\"app.js\"" + NONCE + "></script>\n",
            "{/template}"),
        join("{template foo}\n", "<script src=\"app.js\"></script>\n", "{/template}"));
  }

  @Test
  public void testManyScripts() {
    assertInjected(
        join(
            "{template foo}\n",
            DEFN,
            "<script src=\"one.js\"" + NONCE + "></script>",
            "<script src=two.js" + NONCE + "></script>",
            "<script src=three.js" + NONCE + "></script>",
            "<h1>Not a script</h1>",
            "<script type='text/javascript'" + NONCE + ">main()</script>\n",
            "{/template}"),
        join(
            "{template foo}\n",
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
            "{template foo}\n",
            "  {@param jsUrls: list<trusted_resource_uri>}\n",
            DEFN,
            "{for $jsUrl in $jsUrls}",
            "<script type=\"text/javascript\" ",
            "src='{$jsUrl |filterTrustedResourceUri |escapeHtmlAttribute}'",
            NONCE + "></script>",
            "{/for}\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "  {@param jsUrls: list<trusted_resource_uri>}\n",
            "{for $jsUrl in $jsUrls}\n",
            "<script type=\"text/javascript\" src='{$jsUrl}'></script>\n",
            "{/for}\n",
            "{/template}"));
  }

  @Test
  public void testFakeScripts() {
    assertInjected(
        join(
            "{template foo}\n",
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
            "{template foo}\n",
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
            "{template foo}\n",
            "  {@param appScriptUrl: ?}\n",
            DEFN,
            "<script src=",
            "'{$appScriptUrl |filterTrustedResourceUri |escapeHtmlAttribute}'",
            NONCE + ">",
            "alert('Hello, World!')</script>\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "  {@param appScriptUrl: ?}\n",
            "<script src='{$appScriptUrl}'>",
            "alert('Hello, World!')</script>\n",
            "{/template}"));
  }

  @Test
  public void testOneStyleTag() {
    assertInjected(
        join(
            "{template foo}\n",
            DEFN_STYLE,
            "<style type=text/css",
            NONCE_STYLE,
            ">",
            "p {lb} color: purple {rb}",
            "</style>\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "<style type=text/css>p {lb} color: purple {rb}</style>\n",
            "{/template}"));
  }

  @Test
  public void testTrailingSlashes() {
    assertInjected(
        join(
            "{template foo}\n",
            DEFN,
            "<script src=//example.com/unquoted/url/" + NONCE + "></script>\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "<script src=//example.com/unquoted/url/></script>\n",
            "{/template}"));
  }

  @Test
  public void testInlineEventHandlersAndStyles() {
    assertInjected(
        join(
            "{template foo}\n",
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
            "{template foo}\n",
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
            "{template foo}\n",
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
            "{template foo}\n",
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
            "{template foo}\n",
            DEFN,
            "<link rel=\'Import\' href=\"foo.html\"" + NONCE + ">\n",
            "{/template}"),
        join("{template foo}\n", "<link rel=\'Import\' href=\"foo.html\">\n", "{/template}"));
  }

  @Test
  public void testHtmlImport_multipleChildren() {
    assertInjected(
        join(
            "{template foo}\n",
            "<link rel=\'import{1 |escapeHtmlAttribute}\' href=\"foo.html\">\n",
            "{/template}"),
        join("{template foo}\n", "<link rel=\'import{1}\' href=\"foo.html\">\n", "{/template}"));
  }

  @Test
  public void testHtmlImport_emptyRel() {
    assertInjected(
        join("{template foo}\n", "<link rel href=\"foo.html\">\n", "{/template}"),
        join("{template foo}\n", "<link rel href=\"foo.html\">\n", "{/template}"));
  }

  @Test
  public void testStylesheet() {
    assertInjected(
        join(
            "{template foo}\n",
            DEFN_STYLE,
            "<link rel='stylesheet' href='foo.css'" + NONCE_STYLE + ">\n",
            "{/template}"),
        join("{template foo}\n", "<link rel='stylesheet' href='foo.css'>\n", "{/template}"));
  }

  @Test
  public void testScriptAndStylesheet() {
    assertInjected(
        join(
            "{template foo}\n",
            DEFN,
            DEFN_STYLE,
            "<script" + NONCE + ">alert('Hello, World!')</script>",
            "<link rel='stylesheet' href='foo.css'" + NONCE_STYLE + ">\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "<script>alert('Hello, World!')</script>",
            "<link rel='stylesheet' href='foo.css'>\n",
            "{/template}"));
  }

  @Test
  public void testPreload_as_script() {
    assertInjected(
        join(
            "{template foo}\n",
            DEFN,
            "<link rel='preload' as='script' href='foo.js'" + NONCE + ">\n",
            "{/template}"),
        join(
            "{template foo}\n", "<link rel='preload' as='script' href='foo.js'>\n", "{/template}"));
  }

  @Test
  public void testPreload_as_style() {
    assertInjected(
        join(
            "{template foo}\n",
            DEFN_STYLE,
            "<link rel='preload' as='style' href='foo.js'" + NONCE_STYLE + ">\n",
            "{/template}"),
        join("{template foo}\n", "<link rel='preload' as='style' href='foo.js'>\n", "{/template}"));
  }

  @Test
  public void testPreload_as_wrong() {
    // as='css' is not a valid attribute for link[rel=preload]
    assertInjected(
        join("{template foo}\n", "<link rel='preload' as='css' href='foo.js'>\n", "{/template}"),
        join("{template foo}\n", "<link rel='preload' as='css' href='foo.js'>\n", "{/template}"));
  }

  @Test
  public void testModulePreload_default() {
    assertInjected(
        join(
            "{template foo}\n",
            DEFN,
            "<link rel='modulepreload' href='foo.js'" + NONCE + ">",
            "<link rel='ModulePreload' href='foo.js'" + NONCE + ">\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "<link rel='modulepreload' href='foo.js'>",
            "<link rel='ModulePreload' href='foo.js'>\n",
            "{/template}"));
  }

  @Test
  public void testModulePreload_as_empty() {
    assertInjected(
        join(
            "{template foo}\n",
            DEFN,
            "<link rel='modulepreload' as='' href='foo.js'" + NONCE + ">\n",
            "{/template}"),
        join(
            "{template foo}\n", "<link rel='modulepreload' as='' href='foo.js'>\n", "{/template}"));
  }

  @Test
  public void testModulePreload_as_script() {
    assertInjected(
        join(
            "{template foo}\n",
            DEFN,
            "<link rel='modulepreload' as='script' href='foo.js'" + NONCE + ">",
            "<link rel='modulepreload' as='SCRIPT' href='foo.js'" + NONCE + ">\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "<link rel='modulepreload' as='script' href='foo.js'>",
            "<link rel='modulepreload' as='SCRIPT' href='foo.js'>\n",
            "{/template}"));
  }

  @Test
  public void testModulePreload_as_style() {
    assertInjected(
        join(
            "{template foo}\n",
            DEFN_STYLE,
            "<link rel='modulepreload' as='style' href='foo.css'" + NONCE_STYLE + ">",
            "<link rel='modulepreload' as='STYLE' href='foo.css'" + NONCE_STYLE + ">\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "<link rel='modulepreload' as='style' href='foo.css'>",
            "<link rel='modulepreload' as='STYLE' href='foo.css'>\n",
            "{/template}"));
  }

  @Test
  public void testModulePreload_as_nonNonceable() {
    assertInjected(
        join(
            "{template foo}\n",
            "<link rel='modulepreload' as='json' href='foo.json'>",
            "<link rel='modulepreload' as='css' href='foo.css'>\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "<link rel='modulepreload' as='json' href='foo.json'>",
            "<link rel='modulepreload' as='css' href='foo.css'>\n",
            "{/template}"));
  }

  @Test
  public void testModulePreload_as_dynamicAndValueless() {
    assertInjected(
        join(
            "{template foo}\n",
            "  {@param dest: string}\n",
            "<link rel='modulepreload' as='{$dest |escapeHtmlAttribute}' href='foo.js'>",
            "<link rel='modulepreload' as href='foo.js'>\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "  {@param dest: string}\n",
            "<link rel='modulepreload' as='{$dest}' href='foo.js'>",
            "<link rel='modulepreload' as href='foo.js'>\n",
            "{/template}"));
  }

  @Test
  public void testModulePreload_conditionalUnrelatedAttribute() {
    assertInjected(
        join(
            "{template foo}\n",
            "  {@param c: bool}\n",
            DEFN,
            "<link rel='modulepreload' href='foo.js'{if $c} crossorigin='use-credentials'{/if}"
                + NONCE
                + ">\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "  {@param c: bool}\n",
            "<link rel='modulepreload' href='foo.js' {if $c}crossorigin='use-credentials'{/if}>\n",
            "{/template}"));
  }

  @Test
  public void testModulePreload_as_conditionalOrDynamic() {
    assertInjected(
        join(
            "{template foo}\n",
            "  {@param c: bool}\n",
            "  {@param x: int}\n",
            "  {@param xs: list<string>}\n",
            "  {@param attrs: attributes}\n",
            "<link rel='modulepreload' href='foo.js'{if $c} as='style'{/if}>",
            "<link rel='modulepreload' href='foo.js'{if $c} as='script'{else} as='style'{/if}>",
            "<link rel='modulepreload' href='foo.js'{switch $x}{case 1} as='style'{/switch}>",
            "<link rel='modulepreload' href='foo.js'{for $item in $xs} as='json'"
                + " data-i='{$item |escapeHtmlAttribute}'{/for}>",
            "<link rel='modulepreload' href='foo.js'{$attrs |filterHtmlAttributes"
                + " |whitespaceHtmlAttributes}>",
            "<link rel='modulepreload' href='foo.js'{if $c}{$attrs |filterHtmlAttributes"
                + " |whitespaceHtmlAttributes}{/if}>",
            "<link rel='modulepreload' href='foo.js'{call myAttrs /}>\n",
            "{/template}\n\n",
            "{template myAttrs kind=\"attributes\"}\n",
            "as='style'\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "  {@param c: bool}\n",
            "  {@param x: int}\n",
            "  {@param xs: list<string>}\n",
            "  {@param attrs: attributes}\n",
            "<link rel='modulepreload' href='foo.js' {if $c}as='style'{/if}>",
            "<link rel='modulepreload' href='foo.js' {if $c}as='script'{else}as='style'{/if}>",
            "<link rel='modulepreload' href='foo.js' {switch $x}{case 1}as='style'{/switch}>",
            "<link rel='modulepreload' href='foo.js' {for $item in $xs}as='json'"
                + " data-i='{$item}'{/for}>",
            "<link rel='modulepreload' href='foo.js' {$attrs}>",
            "<link rel='modulepreload' href='foo.js' {if $c}{$attrs}{/if}>",
            "<link rel='modulepreload' href='foo.js' {call myAttrs /}>\n",
            "{/template}\n\n",
            "{template myAttrs kind=\"attributes\"}\n",
            "as='style'\n",
            "{/template}"));
  }

  @Test
  public void testModulePreload_as_elementAttributes() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                join(
                    "{namespace ns}\n\n",
                    "{template extraAttrs kind=\"html<link>\"}\n",
                    "  {@attribute *}\n",
                    "<link rel='modulepreload' href='foo.js'>\n",
                    "{/template}\n\n",
                    "{template attrParam kind=\"html<link>\"}\n",
                    "  {@attribute? as: string}\n",
                    "<link rel='modulepreload' @as href='foo.js'>\n",
                    "{/template}"))
            .runAutoescaper(true)
            .parse()
            .fileSet();

    String output = soyTree.getChild(0).toSourceString();
    assertThat(output).doesNotContain("csp_nonce");
    assertThat(output).doesNotContain("nonce=");
  }

  @Test
  public void testPreload_as_withDynamicAttributes() {
    assertInjected(
        join(
            "{template foo}\n",
            "  {@param attrs: attributes}\n",
            DEFN_STYLE,
            "<link rel='preload' as='style' href='foo.css'{$attrs |filterHtmlAttributes"
                + " |whitespaceHtmlAttributes}"
                + NONCE_STYLE
                + ">",
            "<link rel='modulepreload' as='style' href='foo.css'{$attrs |filterHtmlAttributes"
                + " |whitespaceHtmlAttributes}"
                + NONCE_STYLE
                + ">\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "  {@param attrs: attributes}\n",
            "<link rel='preload' as='style' href='foo.css' {$attrs}>",
            "<link rel='modulepreload' as='style' href='foo.css' {$attrs}>\n",
            "{/template}"));
  }

  @Test
  public void testModulePreload_manualNonceError() {
    ErrorReporter errorReporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(
            join(
                "{namespace ns}\n\n",
                "{template foo}\n",
                "<link rel='modulepreload' href='foo.js' nonce='abc'>",
                "<link rel='modulepreload' as='script' href='foo.js' nonce='abc'>",
                "<link rel='modulepreload' as='' href='foo.js' nonce='abc'>",
                "<link rel='modulepreload' as='style' href='foo.css' nonce='abc'>\n",
                "{/template}"))
        .errorReporter(errorReporter)
        .runAutoescaper(true)
        .parse();

    assertThat(errorReporter.getErrors()).hasSize(4);
    errorReporter
        .getErrors()
        .forEach(error ->
            assertThat(error.message())
                .contains(
                    "Found a 'nonce' attribute on a tag that is supported by Soy auto-nonce"
                        + " support."));
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
