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

package com.google.template.soy.parsepasses.contextautoesc;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test for {@link ContentSecurityPolicyPass}.
 *
 */
@RunWith(Parameterized.class)
public final class ContentSecurityPolicyPassTest {

  @Parameters(name = "strictHtml {0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {{false}, {true}});
  }

  private final boolean strictHtmlEnabled;

  // this constructor will be injected by the test runner
  // note, we cannot use @Parameter because it isn't supported by our version of junit.
  public ContentSecurityPolicyPassTest(boolean strictHtmlEnabled) {
    this.strictHtmlEnabled = strictHtmlEnabled;
  }

  private static final String OLD_NONCE =
      "{if $ij.csp_nonce} nonce=\"{$ij.csp_nonce |filterCspNonceValue}\"{/if}";
  private static final String STRICT_HTML_NONCE =
      "{if $ij.csp_nonce} nonce=\"{$ij.csp_nonce |escapeHtmlAttribute}\"{/if}";

  private String nonce() {
    return strictHtmlEnabled ? STRICT_HTML_NONCE : OLD_NONCE;
  }

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
            "<script" + nonce() + ">alert('Hello, World!')</script>\n",
            "{/template}"),
        join("{template .foo}\n", "<script>alert('Hello, World!')</script>\n", "{/template}"));
  }

  @Test
  public void testOneSrcedScript() {
    assertInjected(
        join(
            "{template .foo}\n",
            "<script src=\"app.js\"" + nonce() + "></script>\n",
            "{/template}"),
        join("{template .foo}\n", "<script src=\"app.js\"></script>\n", "{/template}"));
  }

  @Test
  public void testManyScripts() {
    // TODO(b/31770394): some of these tests are disabled in stricthtml mode because the autoescaper
    // rejects the code injected even though it is safe.  It is better to wait for when the
    // autoescaper is rewritten to account for the html nodes than to try to make it compatible.
    assertInjected(
        join(
            "{template .foo}\n",
            "<script src=\"one.js\"" + nonce() + "></script>",
            strictHtmlEnabled ? "" : "<script src=two.js" + nonce() + "></script>",
            strictHtmlEnabled ? "" : "<script src=three.js " + nonce() + "/></script>",
            "<h1>Not a script</h1>",
            "<script type='text/javascript'" + nonce() + ">main()</script>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "<script src=\"one.js\"></script>",
            strictHtmlEnabled ? "" : "<script src=two.js></script>",
            strictHtmlEnabled ? "" : "<script src=three.js /></script>",
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
            "{foreach $jsUrl in $jsUrls}",
            "<script type=\"text/javascript\" ",
            "src='{$jsUrl |filterTrustedResourceUri |escapeHtmlAttribute}'",
            nonce() + "></script>",
            "{/foreach}\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "  {@param jsUrls: list<string>}\n",
            "{foreach $jsUrl in $jsUrls}\n",
            "<script type=\"text/javascript\" src='{$jsUrl}'></script>\n",
            "{/foreach}\n",
            "{/template}"));
  }

  @Test
  public void testFakeScripts() {
    assertInjected(
        join(
            "{template .foo}\n",
            "<noscript></noscript>",
            "<script" + nonce() + ">alert('Hi');</script>",
            "<!-- <script>notAScript()</script> -->",
            "<textarea><script>notAScript()</script></textarea>",
            "<script is-script=yes"
                + nonce()
                + ">document.write('<script>not()<\\/script>');</script>",
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
            "<script is-script=yes>document.write('<script>not()<\\/script>');</script>",
            "<a href=\"//google.com/search?q=<script>hi()</script>\">Link</a>\n",
            "{/template}"));
  }

  @Test
  public void testPrintDirectiveInScriptTag() {
    assertInjected(
        join(
            "{template .foo}\n",
            "  {@param appScriptUrl: ?}\n",
            "<script src=",
            "'{$appScriptUrl |filterTrustedResourceUri |escapeHtmlAttribute}'",
            nonce() + ">",
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
            "<style type=text/css",
            nonce(),
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
    if (strictHtmlEnabled) {
      // This test is disabled since the autoescaper doesn't trust the code we inject
      // even though it is safe
      // TODO(b/31770394): reenable
      return;
    }
    assertInjected(
        join(
            "{template .foo}\n",
            "<script src=//example.com/unquoted/url/" + nonce() + "></script>\n",
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
            "<a href='#' style='",
            "height:{$height |filterCssValue |escapeHtmlAttribute}px;'",
            " onclick='",
            "foo() &amp;& bar(\"baz\")'",
            ">",

            // Don't bless unquoted attributes since we can't
            // be confident that they end where they're supposed to,
            // so aren't sure that we aren't also blessing an
            // untrusted suffix.
            "<a href='#' onmouseover=foo()",
            " style=color:red>",
            // stricthtml mode doesn't preserve the whitespace around the equals sign
            "<input checked ONCHANGE" + (strictHtmlEnabled ? "=" : " = ") + "\"",
            "Panic()\"",
            ">",
            // ditto
            "<script onerror=" + (strictHtmlEnabled ? "" : " ") + "'",
            "scriptError()'",
            nonce(),
            ">baz()</script>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "  {@param height: int}\n",
            "<a href='#' style='height:{$height}px;' onclick='foo() &amp;& bar(\"baz\")'>",
            "<a href='#' onmouseover=foo() style=color:red>",
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
            .render()
            .get();
    assertEquals("<script nonce=\"zSoyz\">var innocentJs=\"foo\"</script>", renderedValue);
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
            .render()
            .get();
    // We don't inject into inline event handlers anymore
    assertEquals("<a href='#' onmouseover='foo()'>click me</a>", renderedValue);
  }

  private static String join(String... lines) {
    return Joiner.on("").join(lines);
  }

  private SoyFileSetNode parseAndApplyCspPass(String input) {
    String namespace = "{namespace ns autoescape=\"deprecated-contextual\"}\n\n";
    ErrorReporter boom = ExplodingErrorReporter.get();
    // in stricthtml mode insertion is handled by the normal passmanager
    if (strictHtmlEnabled) {
      ParseResult parseResult =
          SoyFileSetParserBuilder.forFileContents(namespace + input)
              .options(
                  new SoyGeneralOptions().setExperimentalFeatures(ImmutableList.of("stricthtml")))
              .errorReporter(boom)
              .parse();
      autoescape(boom, parseResult);
      return parseResult.fileSet();
    } else {
      ParseResult parseResult =
          SoyFileSetParserBuilder.forFileContents(namespace + input).errorReporter(boom).parse();

      ContextualAutoescaper contextualAutoescaper = autoescape(boom, parseResult);

      ContentSecurityPolicyPass.blessAuthorSpecifiedScripts(
          contextualAutoescaper.getSlicedRawTextNodes());
      return parseResult.fileSet();
    }
  }

  private ContextualAutoescaper autoescape(ErrorReporter reporter, ParseResult parseResult) {
    ContextualAutoescaper contextualAutoescaper =
        new ContextualAutoescaper(ImmutableMap.<String, SoyPrintDirective>of());
    List<TemplateNode> extras =
        contextualAutoescaper.rewrite(parseResult.fileSet(), parseResult.registry(), reporter);

    SoyFileNode file = parseResult.fileSet().getChild(parseResult.fileSet().numChildren() - 1);
    file.addChildren(file.numChildren(), extras);
    return contextualAutoescaper;
  }

  /**
   * Returns the contextually rewritten and injected source.
   *
   * <p>The Soy tree may have multiple files, but only the source code for the first is returned.
   */
  private void assertInjected(String expectedOutput, String input) {
    SoyFileSetNode soyTree = parseAndApplyCspPass(input);

    StringBuilder src = new StringBuilder();
    src.append(soyTree.getChild(0).toSourceString());

    String output = src.toString().trim();
    if (output.startsWith("{namespace ns")) {
      output = output.substring(output.indexOf('}') + 1).trim();
    }

    assertThat(output).isEqualTo(expectedOutput);
  }
}
