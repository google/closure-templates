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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.shared.internal.SharedTestUtils;
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

  public final void testTrivialTemplate() throws Exception {
    assertInjected(
        join(
            "{template foo}\n",
            "Hello, World!\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "Hello, World!\n",
            "{/template}"));
  }

  public final void testOneScriptWithBody() throws Exception {
    assertInjected(
        join(
            "{template foo}\n",
            "<script" + NONCE + ">alert('Hello, World!')</script>\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "<script>alert('Hello, World!')</script>\n",
            "{/template}"));
  }

  public final void testOneSrcedScript() throws Exception {
    assertInjected(
        join(
            "{template foo}\n",
            "<script src=\"app.js\"" + NONCE + "></script>\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "<script src=\"app.js\"></script>\n",
            "{/template}"));
  }

  public final void testManyScripts() throws Exception {
    assertInjected(
        join(
            "{template foo}\n",
            "<script src=\"one.js\"" + NONCE + "></script>",
            "<script src=two.js" + NONCE + "></script>",
            "<script src=three.js " + NONCE + "/></script>",
            "<h1>Not a script</h1>",
            "<script type='text/javascript'" + NONCE + ">main()</script>\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "<script src=\"one.js\"></script>",
            "<script src=two.js></script>",
            "<script src=three.js /></script>",
            "<h1>Not a script</h1>",
            "<script type='text/javascript'>main()</script>\n",
            "{/template}"));
  }

  public final void testFakeScripts() throws Exception {
    assertInjected(
        join(
            "{template foo}\n",
            "<noscript></noscript>",
            "<script" + NONCE + ">alert('Hi');</script>",
            "<!-- <script>notAScript()</script> -->",
            "<textarea><script>notAScript()</script></textarea>",
            "<script is-script=yes>document.write('<script>not()<\\/script>');</script>",
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
            "<script is-script=yes>document.write('<script>not()<\\/script>');</script>",
            "<a href=\"//google.com/search?q=<script>hi()</script>\">Link</a>\n",
            "{/template}"));
  }

  public final void testPrintDirectiveInScriptTag() throws Exception {
    assertInjected(
        join(
            "{template foo}\n",
            "<script src='{$appScriptUrl |filterNormalizeUri |escapeHtmlAttribute}'",
            NONCE + ">",
            "alert('Hello, World!')</script>\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "<script src='{$appScriptUrl}'>",
            "alert('Hello, World!')</script>\n",
            "{/template}"));
  }

  public final void testOneStyleTag() throws Exception {
    assertInjected(
        join(
            "{template foo}\n",
            "<style type=text/css", NONCE, ">",
            "p {lb} color: purple {rb}",
            "</style>\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "<style type=text/css>p {lb} color: purple {rb}</style>\n",
            "{/template}"));
  }

  public final void testTrailingSlashes() throws Exception {
    assertInjected(
        join(
            "{template foo}\n",
            "<script src=//example.com/unquoted/url/" + NONCE + "></script>\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "<script src=//example.com/unquoted/url/></script>\n",
            "{/template}"));
  }

  public final void testInlineEventHandlersAndStyles() throws Exception {
    assertInjected(
        join(
            "{template foo}\n",
            "<a href='#' style='",
            "{if $ij.csp_nonce}",
              "/*{$ij.csp_nonce}*/",
            "{/if}",
            "font-weight:bold'",
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
            "{template foo}\n",
            "<a href='#' style='font-weight:bold' onclick='foo() &amp;& bar(\"baz\")'>",
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
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(namespace + input);

    ContextualAutoescaper contextualAutoescaper = new ContextualAutoescaper(
        ImmutableMap.<String, SoyPrintDirective>of());
    List<TemplateNode> extras = contextualAutoescaper.rewrite(soyTree);

    SoyFileNode file = soyTree.getChild(soyTree.numChildren() - 1);
    file.addChildren(file.numChildren(), extras);

    ContentSecurityPolicyPass.blessAuthorSpecifiedScripts(
        contextualAutoescaper.getSlicedRawTextNodes());
    return soyTree;
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

    assertEquals(expectedOutput, output);
  }
}
