/*
 * Copyright 2011 Google Inc.
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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for ChangeCallsToPassAllDataVisitor,
 *
 */
@RunWith(JUnit4.class)
public final class ChangeCallsToPassAllDataVisitorTest {

  private static final ErrorReporter FAIL = ErrorReporter.exploding();

  @Test
  public void testChangedCall() {
    String callCode =
        "{@param xxx : ? }\n"
            + "{@param yyyZzz : ? }\n"
            + "{call .foo}\n"
            + "  {param xxx: $xxx /}\n"
            + "  {param yyyZzz: $yyyZzz /}\n"
            + "{/call}\n";
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(callCode).errorReporter(FAIL).parse().fileSet();
    new ChangeCallsToPassAllDataVisitor().exec(soyTree);
    assertThat(SharedTestUtils.getNode(soyTree, 0).toSourceString())
        .isEqualTo("{call .foo data=\"all\" /}");

    callCode =
        "{@param xxx : ? }\n"
            + "{call .foo data=\"all\"}\n"
            + "  {param xxx: $xxx /}\n"
            + "{/call}\n";
    soyTree =
        SoyFileSetParserBuilder.forTemplateContents(callCode).errorReporter(FAIL).parse().fileSet();
    new ChangeCallsToPassAllDataVisitor().exec(soyTree);
    assertThat(SharedTestUtils.getNode(soyTree, 0).toSourceString())
        .isEqualTo("{call .foo data=\"all\" /}");
  }

  @Test
  public void testUnchangedCall() {

    String callCode = "{call .foo /}\n";
    testUnchangedCallHelper(callCode);

    callCode = "{@param goo : ? }\n{call .foo data=\"$goo\" /}\n";
    testUnchangedCallHelper(callCode);

    callCode =
        "{@param goo : ? }\n"
            + "{@param xxx : ? }\n"
            + "{@param yyyZzz : ? }\n"
            + "{call .foo data=\"$goo\"}\n"
            + "  {param xxx: $xxx /}\n"
            + "  {param yyyZzz: $yyyZzz /}\n"
            + "{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode = "{@param xxx0 : ? }\n{call .foo}\n  {param xxx: $xxx0 /}\n{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode = "{call .foo}\n  {param xxx: 'xxx' /}\n{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode = "{@param goo: ? }\n{call .foo}\n  {param xxx: $goo.xxx /}\n{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode = "{@param xxx : ? }\n{call .foo}\n  {param xxx: $xxx.goo /}\n{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode = "{@param xxx : ? }\n{call .foo}\n  {param xxx}{$xxx}{/param}\n{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode = "{call .foo}\n  {param xxx}xxx{/param}\n{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode =
        "{@param xxx : ? }\n"
            + "{call .foo}\n"
            + "  {param xxx: $xxx /}\n"
            + "  {param yyyZzz: $xxx.yyyZzz /}\n"
            + "{/call}\n";
    testUnchangedCallHelper(callCode);
  }

  private void testUnchangedCallHelper(String callCode) {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(callCode).errorReporter(FAIL).parse().fileSet();
    CallNode callNodeBeforePass = (CallNode) SharedTestUtils.getNode(soyTree, 0);
    callNodeBeforePass.setEscapingDirectiveNames(ImmutableList.of("|escapeHtml"));
    new ChangeCallsToPassAllDataVisitor().exec(soyTree);
    CallNode callNodeAfterPass = (CallNode) SharedTestUtils.getNode(soyTree, 0);
    assertThat(callNodeAfterPass).isEqualTo(callNodeBeforePass);
    assertWithMessage("Escaping directives should be preserved")
        .that(callNodeAfterPass.getEscapingDirectiveNames())
        .isEqualTo(ImmutableList.of("|escapeHtml"));
  }

  @Test
  public void testUnchangedCallWithLoopVar() {
    String soyCode =
        "{@param xxxs : ? }\n"
            + "{foreach $xxx in $xxxs}\n"
            + "  {call .foo}\n"
            + // should not be changed (param references loop var)
            "    {param xxx: $xxx /}\n"
            + "  {/call}\n"
            + "{/foreach}";
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(FAIL).parse().fileSet();

    CallNode callNodeInsideLoopBeforePass = (CallNode) SharedTestUtils.getNode(soyTree, 0, 0, 0);
    new ChangeCallsToPassAllDataVisitor().exec(soyTree);
    CallNode callNodeInsideLoopAfterPass = (CallNode) SharedTestUtils.getNode(soyTree, 0, 0, 0);

    assertThat(callNodeInsideLoopAfterPass).isSameAs(callNodeInsideLoopBeforePass);
  }
}
