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

package com.google.template.soy.pysrc.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;

import junit.framework.TestCase;

/**
 * Unit tests for IsComputableAsPyExprVisitor.
 *
 */
public class IsComputableAsPyExprVisitorTest extends TestCase {

  public void testAlwaysTrueNodes() {
    runTestHelper("Blah blah.", true);
    runTestHelper("{$boo.foo}", true);
    // TODO(dcphillips): Add tests for other nodes (such as messages) when support is available.
  }

  public void testAlwaysFalseNodes() {
    runTestHelper("{let $data: 'foo'/}", false);
    runTestHelper("{switch $boo}{case 0}Blah{case 1}Bleh{default}Bluh{/switch}", false);
    runTestHelper("{foreach $boo in $booze}{$boo}{/foreach}", false);
    runTestHelper("{for $i in range(4)}{$i + 1}{/for}", false);
  }

  public void testIfNode() {
    runTestHelper("{if $boo}Blah{elseif $foo}Bleh{else}Bluh{/if}", true);
    runTestHelper("{if $goo}{foreach $moo in $moose}{$moo}{/foreach}{/if}", false);
  }

  public void testCallNode() {
    runTestHelper("{call name=\".foo\" data=\"all\" /}", true);
    runTestHelper("{call name=\".foo\" data=\"$boo\"}{param key=\"goo\" value=\"$moo\" /}{/call}",
                  true);
    runTestHelper("{call name=\".foo\" data=\"$boo\"}{param key=\"goo\"}Blah{/param}{/call}",
                  true);
    runTestHelper(
        "{call name=\".foo\" data=\"$boo\"}"
        + "  {param key=\"goo\"}"
        + "    {foreach $moo in $moose}"
        + "      {$moo}"
        + "    {/foreach}"
        + "  {/param}"
        + "{/call}",
        false);
  }

  private static void runTestHelper(String soyNodeCode, boolean expectedResult) {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forTemplateContents(soyNodeCode).parse();
    SoyNode node = SharedTestUtils.getNode(soyTree, 0);
    assertThat(new IsComputableAsPyExprVisitor(ExplodingErrorReporter.get()).exec(node))
        .isEqualTo(expectedResult);
  }
}
