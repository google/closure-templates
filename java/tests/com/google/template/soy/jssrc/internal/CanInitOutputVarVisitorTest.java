/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.testing.SharedTestUtils;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for CanInitOutputVarVisitor.
 *
 */
@RunWith(JUnit4.class)
public class CanInitOutputVarVisitorTest {

  @Test
  public void testSameValueAsIsComputableAsJsExprsVisitor() {

    runTestHelper("Blah blah.", true);

    runTestHelper(
        "{@param url: ? }\n{msg desc=\"\"}<a href=\"{$url}\">Click here</a>{/msg}",
        true,
        0, // MsgFallbackGroupNode
        0, // MsgNode
        0); // MsgHtmlTagNode

    runTestHelper(
        "{@param url: ? }\n{msg desc=\"\"}<a href=\"{$url}\">Click here</a>{/msg}",
        true,
        0, // MsgFallbackGroupNode
        0, // MsgNode
        2); // MsgHtmlTagNode

    runTestHelper(
        "{msg desc=\"\"}<span id=\"{for $i in range(3)}{$i}{/for}\">{/msg}",
        true,
        0, // MsgFallbackGroupNode
        0, // MsgNode
        0); // MsgHtmlTagNode

    runTestHelper("{@param boo: ? }\n{$boo.foo}", true);

    runTestHelper("{xid('selected-option')}", true);

    runTestHelper("{css('selected-option')}", true);

    runTestHelper(
        "{@param boo: ? }\n{switch $boo}{case 0}Blah{case 1}Bleh{default}Bluh{/switch}", true);

    runTestHelper("{@param booze: ? }\n{for $boo in $booze}{$boo}{/for}", true);

    runTestHelper("{for $i in range(4)}{$i + 1}{/for}", true);

    runTestHelper(
        "{@param boo: ?}\n{@param foo: ?}\n{if $boo}Blah{elseif $foo}Bleh{else}Bluh{/if}", true);

    runTestHelper(
        "{@param goo: ?}\n"
            + "{@param moose: ?}\n"
            + "{if $goo}{for $moo in $moose}{$moo}{/for}{/if}",
        true);

    runTestHelper("{call .foo data=\"all\" /}", true);

    runTestHelper(
        "{@param boo: ?}\n"
            + "{@param moo: ?}\n"
            + "{call .foo data=\"$boo\"}{param goo : $moo /}{/call}",
        true);

    runTestHelper(
        "{@param boo: ?}\n{call .foo data=\"$boo\"}{param goo kind=\"text\"}Blah{/param}{/call}",
        true);
  }

  @Test
  public void testNotSameValueAsIsComputableAsJsExprsVisitor() {
    runTestHelper(
        Joiner.on('\n')
            .join(
                "{@param boo : ?}",
                "{@param moose : ?}",
                "{call .foo data=\"$boo\"}",
                "  {param goo kind=\"text\"}{for $moo in $moose}{$moo}{/for}{/param}",
                "{/call}"),
        false);
    runTestHelper("{msg desc=\"\"}hello{/msg}", false);
  }

  private static void runTestHelper(
      String soyNodeCode, boolean isSameValueAsIsComputableAsJsExprsVisitor) {
    runTestHelper(soyNodeCode, isSameValueAsIsComputableAsJsExprsVisitor, 0);
  }

  /** @param indicesToNode Series of indices for walking down to the node we want to test. */
  private static void runTestHelper(
      String soyCode, boolean isSameValueAsIsComputableAsJsExprsVisitor, int... indicesToNode) {
    String fileContents = SharedTestUtils.buildTestSoyFileContent(soyCode);
    if (soyCode.contains("{call .foo")) {
      fileContents += "{template foo}{@param? goo: ?}{/template}";
    }
    ErrorReporter boom = ErrorReporter.exploding();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(fileContents).errorReporter(boom).parse().fileSet();
    SoyNode node = SharedTestUtils.getNode(soyTree, indicesToNode);

    IsComputableAsJsExprsVisitor icajev = new IsComputableAsJsExprsVisitor();
    CanInitOutputVarVisitor ciovv = new CanInitOutputVarVisitor(icajev);
    assertThat(ciovv.exec(node).equals(icajev.exec(node)))
        .isEqualTo(isSameValueAsIsComputableAsJsExprsVisitor);
  }
}
