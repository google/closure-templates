/*
 * Copyright 2017 Google Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.StringSubject;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AddHtmlCommentsForDebugPassTest {

  @Test
  public void testNoOpForNonHtmlContent() throws Exception {
    assertTemplate("{template .t kind=\"text\"}{/template}").isEmpty();
    assertTemplate("{template .t kind=\"css\"}{/template}").isEmpty();
    assertTemplate("{template .t kind=\"js\"}{/template}").isEmpty();
    assertTemplate("{template .t kind=\"attributes\"}{/template}").isEmpty();
  }

  @Test
  public void testForTextCallHtml() throws Exception {
    ImmutableMap<String, String> result =
        runPass(
            "{template .t kind=\"text\"}{call .t2 /}{/template}\n" + "{template .t2}{/template}");
    // Both templates should not be rewritten
    assertThat(result.get("ns.t")).isEqualTo("{call .t2 /}");
    assertThat(result.get("ns.t2")).isEmpty();

    result =
        runPass(
            "{template .t kind=\"text\"}{call .t2 /}{/template}\n"
                + "{template .t2 kind=\"attributes\"}{/template}");
    // Both templates should not be rewritten
    assertThat(result.get("ns.t")).isEqualTo("{call .t2 /}");
    assertThat(result.get("ns.t2")).isEmpty();

    result =
        runPass(
            "{template .t kind=\"text\"}{call .t2 /}{/template}\n"
                + "{template .t2 kind=\"html\"}<div>foo</div>{/template}");
    // ns.t should not be rewritten since it has kind="text"
    assertThat(result.get("ns.t")).isEqualTo("{call .t2 /}");
    // ns.t2 should still be rewritten
    assertThat(result.get("ns.t2"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t2, test.soy, 2)-->{/if}"
                + "<div>foo</div>"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t2)-->{/if}");
  }

  @Test
  public void testNoOpForNonStrictAutoEscapeMode() throws Exception {
    assertTemplate("{template .t autoescape=\"deprecated-contextual\"}{/template}").isEmpty();
    assertTemplate("{template .t autoescape=\"deprecated-noncontextual\"}{/template}").isEmpty();
  }

  @Test
  public void testNoOpForTemplatesWithoutAnyTags() throws Exception {
    // Templates that explicitly set ContentKind and/or AutoescapeMode.
    assertTemplate("{template .t kind=\"html\"}{/template}").isEmpty();
    assertTemplate("{template .t kind=\"html\" autoescape=\"strict\"}{/template}").isEmpty();
    // Templates that does not contain any html tags.
    assertTemplate("{template .t}{/template}").isEmpty();
    assertTemplate("{template .t}foo{/template}").isEqualTo("foo");
    assertTemplate("{template .t}{@param foo: string}{$foo}{/template}").isEqualTo("{$foo}");
  }

  @Test
  public void testNoOpForCalls() throws Exception {
    ImmutableMap<String, String> result =
        runPass("{template .t}{call .t2 /}{/template}\n" + "{template .t2}foo{/template}");
    assertThat(result.get("ns.t")).isEqualTo("{call .t2 /}");
    assertThat(result.get("ns.t2")).isEqualTo("foo");

    result =
        runPass(
            "{template .t}{call .t2 /}{/template}\n"
                + "{template .t2 kind=\"text\"}<p>{/template}");
    assertThat(result.get("ns.t")).isEqualTo("{call .t2 /}");
    assertThat(result.get("ns.t2")).isEqualTo("<p>");
  }

  @Test
  public void testNoOpForDelcalls() throws Exception {
    ImmutableMap<String, String> result =
        runPass(
            "{template .t}{delcall ns.t2 /}{/template}\n" + "{deltemplate ns.t2}foo{/deltemplate}");
    assertThat(result.get("ns.t")).isEqualTo("{delcall ns.t2 /}");
    assertThat(result.get("ns.t2")).isEqualTo("foo");

    result =
        runPass(
            "{template .t}{delcall ns.t2 /}{/template}\n"
                + "{deltemplate ns.t2 kind=\"text\"}<p>{/deltemplate}");
    assertThat(result.get("ns.t")).isEqualTo("{delcall ns.t2 /}");
    assertThat(result.get("ns.t2")).isEqualTo("<p>");
  }

  @Test
  public void testRewrites() {
    assertTemplate("{template .t}<div>foo</div>{/template}")
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t, test.soy, 1)-->{/if}"
                + "<div>foo</div>"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t)-->{/if}");
    assertTemplate("{template .t}{@param foo: bool}{if $foo}<p>bar{/if}{/template}")
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t, test.soy, 1)-->{/if}"
                + "{if $foo}<p>bar{/if}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t)-->{/if}");
  }

  @Test
  public void testRewritesForCalls() throws Exception {
    ImmutableMap<String, String> result =
        runPass(
            "{template .t}{call .t2 /}{/template}\n" + "{template .t2}<div>foo</div>{/template}");
    assertThat(result.get("ns.t"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t, test.soy, 1)-->{/if}"
                + "{call .t2 /}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t)-->{/if}");
    assertThat(result.get("ns.t2"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t2, test.soy, 2)-->{/if}"
                + "<div>foo</div>"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t2)-->{/if}");

    result =
        runPass(
            "{template .t}{call .t2 /}{/template}\n" + "{template .t2}<!--comments-->{/template}");
    assertThat(result.get("ns.t"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t, test.soy, 1)-->{/if}"
                + "{call .t2 /}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t)-->{/if}");
    assertThat(result.get("ns.t2"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t2, test.soy, 2)-->{/if}"
                + "<!--comments-->"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t2)-->{/if}");
  }

  @Test
  public void testRwritesForDelcalls() throws Exception {
    ImmutableMap<String, String> result =
        runPass(
            "{template .t}{delcall ns.t2 /}{/template}\n"
                + "{deltemplate ns.t2}<div>foo</div>{/deltemplate}");
    assertThat(result.get("ns.t"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t, test.soy, 1)-->{/if}"
                + "{delcall ns.t2 /}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t)-->{/if}");
    assertThat(result.get("ns.t2"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t2, test.soy, 2)-->{/if}"
                + "<div>foo</div>"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t2)-->{/if}");
  }

  @Test
  public void testRwritesForHtmlParams() throws Exception {
    assertTemplate("{template .t}{@param foo: html}{$foo}{/template}")
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t, test.soy, 1)-->{/if}"
                + "{$foo}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t)-->{/if}");
  }

  @Test
  public void testRewritesForTransitiveCalls() throws Exception {
    ImmutableMap<String, String> result =
        runPass(
            "{template .a}{call .b /}{/template}\n"
                + "{template .b}{call .c /}{/template}\n"
                + "{template .c}<p>{/template}\n");
    assertThat(result.get("ns.a"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.a, test.soy, 1)-->{/if}"
                + "{call .b /}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.a)-->{/if}");
    assertThat(result.get("ns.b"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.b, test.soy, 2)-->{/if}"
                + "{call .c /}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.b)-->{/if}");
    assertThat(result.get("ns.c"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.c, test.soy, 3)-->{/if}"
                + "<p>"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.c)-->{/if}");
  }

  @Test
  public void testRecursiveTemplates() throws Exception {
    assertTemplate("{template .t}{call .t /}{/template}\n").isEqualTo("{call .t /}");
    ImmutableMap<String, String> result =
        runPass("{template .a}{call .b /}{/template}\n" + "{template .b}{call .a /}{/template}\n");
    assertThat(result.get("ns.a")).isEqualTo("{call .b /}");
    assertThat(result.get("ns.b")).isEqualTo("{call .a /}");
    result =
        runPass(
            "{template .a}{call .b /}{call .c /}{/template}\n"
                + "{template .b}{call .a /}{/template}\n"
                + "{template .c}<p>{/template}");
    assertThat(result.get("ns.a"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.a, test.soy, 1)-->{/if}"
                + "{call .b /}{call .c /}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.a)-->{/if}");

    assertThat(result.get("ns.b"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.b, test.soy, 2)-->{/if}"
                + "{call .a /}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.b)-->{/if}");

    assertThat(result.get("ns.c"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.c, test.soy, 3)-->{/if}"
                + "<p>"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.c)-->{/if}");
  }

  @Test
  public void testFilePathIsEscaped() {
    ImmutableMap<String, String> result =
        runPass("{template .t kind=\"html\"}<p>{/template}", "-->.soy");
    assertThat(result.get("ns.t"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t, --&gt;.soy, 1)-->{/if}"
                + "<p>"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t)-->{/if}");
  }

  private static StringSubject assertTemplate(String input) {
    return assertThat(runPass(input).get("ns.t"));
  }

  private static ImmutableMap<String, String> runPass(String input) {
    return runPass(input, "test.soy");
  }

  /**
   * Parses the given input as a Soy file content, runs the AddHtmlCommentsForDebug pass and returns
   * a map that contains pairs of template names and rewritten template body.
   */
  private static ImmutableMap<String, String> runPass(String input, String fileName) {
    String soyFile = "{namespace ns}" + input;
    IncrementingIdGenerator nodeIdGen = new IncrementingIdGenerator();

    ErrorReporter boom = ErrorReporter.exploding();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(soyFile, SoyFileKind.SRC, fileName))
            .errorReporter(boom)
            .parse()
            .fileSet();
    TemplateRegistry registry = new TemplateRegistry(soyTree, boom);
    // We need to run HtmlRewritePass to produce HTML nodes. Otherwise we will not add any comments.
    new HtmlRewritePass(boom).run(soyTree.getChild(0), nodeIdGen);
    new AddHtmlCommentsForDebugPass().run(soyTree, registry);

    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (TemplateNode node : soyTree.getChild(0).getChildren()) {
      StringBuilder sb = new StringBuilder();
      node.appendSourceStringForChildren(sb);
      String templateName =
          (node instanceof TemplateDelegateNode)
              ? ((TemplateDelegateNode) node).getDelTemplateName()
              : node.getTemplateName();
      builder.put(templateName, sb.toString());
    }
    return builder.build();
  }
}
