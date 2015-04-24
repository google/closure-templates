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

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.SoyJsSrcOptions.CodeStyle;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;

import junit.framework.TestCase;

/**
 * Unit tests for IsComputableAsJsExprsVisitor.
 *
 */
public final class IsComputableAsJsExprsVisitorTest extends TestCase {


  private static SoyJsSrcOptions jsSrcOptions;


  @Override protected void setUp() {
    jsSrcOptions = new SoyJsSrcOptions();
  }


  public void testAlwaysTrueNodes() {

    runTestHelper("Blah blah.", true);

    runTestHelper("{msg desc=\"\"}Blah{/msg}", true, 1);  // GoogMsgRefNode

    runTestHelper("{$boo.foo}", true);

    runTestHelper("{xid selected-option}", true);

    runTestHelper("{css selected-option}", true);
  }


  public void testAlwaysFalseNodes() {

    runTestHelper("{msg desc=\"\"}Blah{/msg}", false, 0);  // GoogMsgDefNode

    runTestHelper("{switch $boo}{case 0}Blah{case 1}Bleh{default}Bluh{/switch}", false);

    runTestHelper("{foreach $boo in $booze}{$boo}{/foreach}", false);

    runTestHelper("{for $i in range(4)}{$i + 1}{/for}", false);
  }


  public void testMsgHtmlTagNode() {

    runTestHelper("{msg desc=\"\"}<a href=\"{$url}\">Click here</a>{/msg}", true, 0, 0, 0);

    runTestHelper("{msg desc=\"\"}<a href=\"{$url}\">Click here</a>{/msg}", true, 0, 0, 2);

    runTestHelper(
        "{msg desc=\"\"}<span id=\"{for $i in range(3)}{$i}{/for}\">{/msg}", false, 0, 0, 0);
  }


  public void testIfNode() {

    runTestHelper("{if $boo}Blah{elseif $foo}Bleh{else}Bluh{/if}", true);

    runTestHelper("{if $goo}{foreach $moo in $moose}{$moo}{/foreach}{/if}", false);
  }


  public void testCallNode() {

    jsSrcOptions.setCodeStyle(CodeStyle.STRINGBUILDER);
    runTestHelper("{call name=\".foo\" data=\"all\" /}", false);

    jsSrcOptions.setCodeStyle(CodeStyle.CONCAT);
    runTestHelper("{call name=\".foo\" data=\"all\" /}", true);

    runTestHelper("{call name=\".foo\" data=\"$boo\"}{param key=\"goo\" value=\"$moo\" /}{/call}",
                  true);

    runTestHelper("{call name=\".foo\" data=\"$boo\"}{param key=\"goo\"}Blah{/param}{/call}",
                  true);

    runTestHelper("{call name=\".foo\" data=\"$boo\"}" +
                  "{param key=\"goo\"}{foreach $moo in $moose}{$moo}{/foreach}{/param}" +
                  "{/call}",
                  false);
  }


  private static void runTestHelper(String soyNodeCode, boolean expectedResult) {
    runTestHelper(soyNodeCode, expectedResult, 0);
  }


  /**
   * @param indicesToNode Series of indices for walking down to the node we want to test.
   */
  private static void runTestHelper(
      String soyCode, boolean expectedResult, int... indicesToNode) {
    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forTemplateContents(soyCode)
        .errorReporter(boom)
        .parse();
    // Several tests have msg nodes.
    new ReplaceMsgsWithGoogMsgsVisitor(boom).exec(soyTree);
    SoyNode node = SharedTestUtils.getNode(soyTree, indicesToNode);
    assertThat(new IsComputableAsJsExprsVisitor(jsSrcOptions, boom).exec(node))
        .isEqualTo(expectedResult);
  }

}
