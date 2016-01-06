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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;

import junit.framework.TestCase;

import java.util.List;

/**
 * Test for {@link ContentSecurityPolicyPass}.
 *
 */
public final class ContentSecurityPolicyPassTest extends TestCase {

  private static final String NONCE = "{if $ij.csp_nonce} nonce=\"{$ij.csp_nonce}\"{/if}";

  public void testTrivialTemplate() {
    assertInjected(
        join(
            "{template .foo}\n",
            "Hello, World!\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "Hello, World!\n",
            "{/template}"));
  }

  public void testOneScriptWithBody() {
    assertInjected(
        join(
            "{template .foo}\n",
            "<script" + NONCE + ">alert('Hello, World!')</script>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "<script>alert('Hello, World!')</script>\n",
            "{/template}"));
  }

  public void testOneSrcedScript() {
    assertInjected(
        join(
            "{template .foo}\n",
            "<script src=\"app.js\"" + NONCE + "></script>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "<script src=\"app.js\"></script>\n",
            "{/template}"));
  }

  public void testManyScripts() {
    assertInjected(
        join(
            "{template .foo}\n",
            "<script src=\"one.js\"" + NONCE + "></script>",
            "<script src=two.js" + NONCE + "></script>",
            "<script src=three.js " + NONCE + "/></script>",
            "<h1>Not a script</h1>",
            "<script type='text/javascript'" + NONCE + ">main()</script>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "<script src=\"one.js\"></script>",
            "<script src=two.js></script>",
            "<script src=three.js /></script>",
            "<h1>Not a script</h1>",
            "<script type='text/javascript'>main()</script>\n",
            "{/template}"));
  }

  public void testFakeScripts() {
    assertInjected(
        join(
            "{template .foo}\n",
            "<noscript></noscript>",
            "<script" + NONCE + ">alert('Hi');</script>",
            "<!-- <script>notAScript()</script> -->",
            "<textarea><script>notAScript()</script></textarea>",
            "<script is-script=yes>document.write('<script>not()<\\/script>');</script>",
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

  public void testPrintDirectiveInScriptTag() {
    assertInjected(
        join(
            "{template .foo}\n",
            "  {@param appScriptUrl: ?}\n",
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

  public void testOneStyleTag() {
    assertInjected(
        join(
            "{template .foo}\n",
            "<style type=text/css", NONCE, ">",
            "p {lb} color: purple {rb}",
            "</style>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "<style type=text/css>p {lb} color: purple {rb}</style>\n",
            "{/template}"));
  }

  public void testTrailingSlashes() {
    assertInjected(
        join(
            "{template .foo}\n",
            "<script src=//example.com/unquoted/url/" + NONCE + "></script>\n",
            "{/template}"),
        join(
            "{template .foo}\n",
            "<script src=//example.com/unquoted/url/></script>\n",
            "{/template}"));
  }

  public void testInlineEventHandlersAndStyles() {
    assertInjected(
        join(
            "{template .foo}\n",
            "  {@param height: int}\n",
            "<a href='#' style='",
            "{if $ij.csp_nonce}",
              "/*{$ij.csp_nonce}*/",
            "{/if}",
            "height:{$height |filterCssValue |escapeHtmlAttribute}px;'",
            " onclick='",
            "{if $ij.csp_nonce}",
              "/*{$ij.csp_nonce}*/",
            "{/if}",
            "foo() &amp;& bar(\"baz\")'",
            ">",

            // Don't bless unquoted attributes since we can't
            // be confident that they end where they're supposed to,
            // so aren't sure that we aren't also blessing an
            // untrusted suffix.
            "<a href='#' onmouseover=foo()",
            " style=color:red>",

            "<input checked ONCHANGE = \"",
            "{if $ij.csp_nonce}",
              "/*{$ij.csp_nonce}*/",
            "{/if}",
            "Panic()\"",
            ">",

            "<script onerror= '",
            "{if $ij.csp_nonce}",
              "/*{$ij.csp_nonce}*/",
            "{/if}",
            "scriptError()'",
            "{if $ij.csp_nonce}",
              " nonce=\"{$ij.csp_nonce}\"",
            "{/if}",
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


  private static String join(String... lines) {
    return Joiner.on("").join(lines);
  }

  private SoyFileSetNode parseAndApplyCspPass(String input) {
    String namespace = "{namespace ns autoescape=\"deprecated-contextual\"}\n\n";
    ErrorReporter boom = ExplodingErrorReporter.get();
    ParseResult parseResult =
        SoyFileSetParserBuilder.forFileContents(namespace + input).errorReporter(boom).parse();

    ContextualAutoescaper contextualAutoescaper =
        new ContextualAutoescaper(ImmutableMap.<String, SoyPrintDirective>of());
    List<TemplateNode> extras =
        contextualAutoescaper.rewrite(parseResult.fileSet(), parseResult.registry(), boom);

    SoyFileNode file = parseResult.fileSet().getChild(parseResult.fileSet().numChildren() - 1);
    file.addChildren(file.numChildren(), extras);

    ContentSecurityPolicyPass.blessAuthorSpecifiedScripts(
        contextualAutoescaper.getSlicedRawTextNodes());
    return parseResult.fileSet();
  }

  /**
   * Returns the contextually rewritten and injected source.
   *
   * The Soy tree may have multiple files, but only the source code for the first is returned.
   */
  private void assertInjected(String expectedOutput, String input)
    throws SoyAutoescapeException {
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
