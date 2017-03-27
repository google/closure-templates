/*
 * Copyright 2016 Google Inc.
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
import com.google.common.collect.ImmutableList;
import com.google.common.truth.StringSubject;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyTypeRegistry;
import java.io.StringReader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DesugarHtmlNodesPassTest {

  @Test
  public void testNoOpRewrites() throws Exception {
    assertNoOp("<div>");
    assertNoOp("<div class>");
    assertNoOp("<div class=foo></div>");
    assertNoOp("{let $foo kind=\"attributes\"}class=foo{/let}");
  }

  // The only time we don't perfectly preserve things is in the presense of whitespace.  This is
  // intentional
  @Test
  public void testRewrites() {
    assertRewrite("<div     class='foo'>").isEqualTo("<div class='foo'>");
    assertRewrite("<div {if true}class='foo'{/if} id='2'>")
        .isEqualTo("<div{if true} class='foo'{/if} id='2'>");
    assertRewrite("{let $foo kind=\"attributes\"}     class=foo    {/let}")
        .isEqualTo("{let $foo kind=\"attributes\"}class=foo{/let}");
  }

  // This is a regression test for a bug where we failed to correctly remove whitespace seen in the
  // middle of an html tag that appeared by itself.  in particular the whitespace character between
  // the print node and the if node was preserved and then moved!.
  @Test
  public void testRewrites_handle_whitespace() {
    assertRewrite(
            "\n"
                + "{let $t: 1 /}\n"
                + "<{$t ? 'div' : 'span'} {if $t}onclick=\"foo()\"{/if}>\n"
                + "</{$t ? 'div' : 'span'}>")
        .isEqualTo(
            ""
                + "{let $t: 1 /}"
                + "<{$t ? 'div' : 'span'}{if $t} onclick=\"foo()\"{/if}></{$t ? 'div' : 'span'}>");
  }

  // This is a regression test for a bug where we failed to handle pcdata blocks containing only
  // raw text with no tags
  @Test
  public void testRewrites_handleBlocksWithPcDataContent() {
    assertRewrite("{let $t: 1 /}{if $t}hello{else}world{/if}")
        .isEqualTo("{let $t: 1 /}{if $t}hello{else}world{/if}");
  }

  private static StringSubject assertRewrite(String input) {
    return assertThat(runPass(input));
  }

  private static void assertNoOp(String input) {
    assertThat(runPass(input)).isEqualTo(input);
  }

  /**
   * Parses the given input as a template content, runs the HtmlRewrite pass and the Desugar Passes
   * and returns the resulting source string of the template body
   */
  private static String runPass(String input) {
    String soyFile = Joiner.on('\n').join("{namespace ns}", "{template .t}", input, "{/template}");
    IncrementingIdGenerator nodeIdGen = new IncrementingIdGenerator();
    SoyFileNode node =
        new SoyFileParser(
                new SoyTypeRegistry(),
                nodeIdGen,
                new StringReader(soyFile),
                SoyFileKind.SRC,
                "test.soy",
                ExplodingErrorReporter.get())
            .parseSoyFile();
    new HtmlRewritePass(ImmutableList.of("stricthtml"), ExplodingErrorReporter.get())
        .run(node, nodeIdGen);
    new DesugarHtmlNodesPass().run(node, nodeIdGen);
    assertThat(hasHtmlNodes(node)).isFalse();
    StringBuilder sb = new StringBuilder();
    node.getChild(0).appendSourceStringForChildren(sb);
    return sb.toString();
  }

  private static boolean hasHtmlNodes(SoyNode node) {
    return SoyTreeUtils.hasNodesOfType(
        node,
        HtmlOpenTagNode.class,
        HtmlCloseTagNode.class,
        HtmlAttributeNode.class,
        HtmlAttributeValueNode.class);
  }
}
