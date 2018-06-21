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
import com.google.common.truth.StringSubject;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DesugarHtmlNodesPassTest {

  @Test
  public void testNoOpRewrites() throws Exception {
    assertNoOp("<p>");
    assertNoOp("<p class>");
    assertNoOp("<p class=foo></p>");
    assertNoOp("{let $foo kind=\"attributes\"}class=foo{/let}");
    assertNoOp("<hr/>");
    assertNoOp("<hr class=foo/>");
    // we used to rewrite this as foo/>, which is wrong the trailing space is important.
    assertNoOp("<hr class=foo />");
    // Html comment nodes should be no op.
    assertNoOp("<!--foo-->");
    assertNoOp("{let $foo : '' /}<!--{$foo}-->");
    assertNoOp("{let $foo : '' /}{let $bar : '' /}<!--{$foo}hello{$bar}-->");
    assertNoOp("{let $foo : '' /}<!--{if $foo}hello{/if}-->");
    assertNoOp("<!--<script>test</script>-->");
  }

  // The only time we don't perfectly preserve things is in the presense of whitespace.  This is
  // intentional
  @Test
  public void testRewrites() {
    assertRewrite("<p     class='foo'>").isEqualTo("<p class='foo'>");
    assertRewrite("<p {if true}class='foo'{/if} id='2'>")
        .isEqualTo("<p{if true} class='foo'{/if} id='2'>");

    // notice that the space moves inside the conditional
    assertRewrite("<p {if true}class='foo'{else}style='baz'{/if}>")
        .isEqualTo("<p{if true} class='foo'{else} style='baz'{/if}>");

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
                + "{let $t : 1 /}\n"
                + "<{$t ? 'div' : 'span'} {if $t}onclick=\"foo()\"{/if}>\n"
                + "</{$t ? 'div' : 'span'}>")
        .isEqualTo(
            ""
                + "{let $t : 1 /}"
                + "<{$t ? 'div' : 'span'}{if $t} onclick=\"foo()\"{/if}></{$t ? 'div' : 'span'}>");
  }

  // This is a regression test for a bug where we failed to handle pcdata blocks containing only
  // raw text with no tags
  @Test
  public void testRewrites_handleBlocksWithPcDataContent() {
    assertRewrite("{let $t : 1 /}{if $t}hello{else}world{/if}")
        .isEqualTo("{let $t : 1 /}{if $t}hello{else}world{/if}");
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
    SoyFileNode node =
        SoyFileSetParserBuilder.forFileContents(soyFile)
            .desugarHtmlNodes(true)
            .parse()
            .fileSet()
            .getChild(0);
    assertThat(SoyTreeUtils.hasHtmlNodes(node)).isFalse();
    StringBuilder sb = new StringBuilder();
    node.getChild(0).appendSourceStringForChildren(sb);
    return sb.toString();
  }
}
