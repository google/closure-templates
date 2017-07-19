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
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link IsComputableAsJsExprsVisitor}.
 *
 */
@RunWith(JUnit4.class)
public final class IsComputableAsJsExprsVisitorTest {

  @Test
  public void testAlwaysTrueNodes() {

    runTestHelper("Blah blah.", true);

    runTestHelper("{msg desc=\"\"}Blah{/msg}", true, 1); // GoogMsgRefNode

    runTestHelper("{@param boo: ?}\n{$boo.foo}", true);

    runTestHelper("{xid('selected-option')}", true);

    runTestHelper("{css('selected-option')}", true);
  }

  @Test
  public void testAlwaysFalseNodes() {

    runTestHelper("{msg desc=\"\"}Blah{/msg}", false, 0); // GoogMsgDefNode

    runTestHelper(
        "{@param boo: ?}\n{switch $boo}{case 0}Blah{case 1}Bleh{default}Bluh{/switch}", false);

    runTestHelper(join("{@param booze: ?}", "{foreach $boo in $booze}{$boo}{/foreach}"), false);

    runTestHelper("{for $i in range(4)}{$i + 1}{/for}", false);
  }

  @Test
  public void testMsgHtmlTagNode() {

    runTestHelper(
        join("{@param url:?}", "{msg desc=\"\"}<a href=\"{$url}\">Click here</a>{/msg}"),
        true,
        0,
        0,
        0,
        0);

    runTestHelper(
        join("{@param url:?}", "{msg desc=\"\"}<a href=\"{$url}\">Click here</a>{/msg}"),
        true,
        0,
        0,
        0,
        2);

    runTestHelper(
        "{msg desc=\"\"}<span id=\"{for $i in range(3)}{$i}{/for}\">{/msg}", false, 0, 0, 0, 0);
  }

  @Test
  public void testIfNode() {

    runTestHelper(
        join("{@param boo: ?}", "{@param foo: ?}", "{if $boo}Blah{elseif $foo}Bleh{else}Bluh{/if}"),
        true);

    runTestHelper(
        join(
            "{@param goo: ?}",
            "{@param moose: ?}",
            "{if $goo}{foreach $moo in $moose}{$moo}{/foreach}{/if}"),
        false);
  }

  @Test
  public void testCallNode() {
    runTestHelper("{call .foo data=\"all\" /}", true);

    runTestHelper(
        join(
            "{@param boo: ?}",
            "{@param moo: ?}",
            "{call .foo data=\"$boo\"}",
            "{param goo : $moo /}",
            "{/call}"),
        true);

    runTestHelper("{@param boo: ?}\n{call .foo data=\"$boo\"}{param goo}Blah{/param}{/call}", true);

    runTestHelper(
        join(
            "{@param boo: ?}",
            "{@param moose: ?}",
            "{call .foo data=\"$boo\"}",
            "  {param goo}",
            "  {foreach $moo in $moose}",
            "    {$moo}",
            "{/foreach}",
            "{/param}",
            "{/call}"),
        false);
  }

  private static String join(String... lines) {
    return Joiner.on('\n').join(lines);
  }

  private static void runTestHelper(String soyNodeCode, boolean expectedResult) {
    runTestHelper(soyNodeCode, expectedResult, 0);
  }

  /** @param indicesToNode Series of indices for walking down to the node we want to test. */
  private static void runTestHelper(String soyCode, boolean expectedResult, int... indicesToNode) {
    ErrorReporter boom = ErrorReporter.exploding();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(boom).parse().fileSet();
    // Several tests have msg nodes.
    new ExtractMsgVariablesVisitor().exec(soyTree);
    SoyNode node = SharedTestUtils.getNode(soyTree, indicesToNode);
    assertThat(new IsComputableAsJsExprsVisitor().exec(node)).isEqualTo(expectedResult);
  }
}
