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

package com.google.template.soy.parsepasses;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.SoyFileSetNode;

import junit.framework.TestCase;


/**
 * Unit tests for ChangeCallsToPassAllDataVisitor,
 *
 * @author Kai Huang
 */
public class ChangeCallsToPassAllDataVisitorTest extends TestCase {


  public void testChangedCall() throws Exception {

    String callCode =
        "{call .foo}\n" +
        "  {param xxx: $xxx /}\n" +
        "  {param yyyZzz: $yyyZzz /}\n" +
        "{/call}\n";
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyCode(callCode);
    (new ChangeCallsToPassAllDataVisitor()).exec(soyTree);
    assertEquals(
        "{call .foo data=\"all\" /}",
        SharedTestUtils.getNode(soyTree, 0).toSourceString());

    callCode =
        "{call .foo data=\"all\"}\n" +
        "  {param xxx: $xxx /}\n" +
        "{/call}\n";
    soyTree = SharedTestUtils.parseSoyCode(callCode);
    (new ChangeCallsToPassAllDataVisitor()).exec(soyTree);
    assertEquals(
        "{call .foo data=\"all\" /}",
        SharedTestUtils.getNode(soyTree, 0).toSourceString());
  }


  public void testUnchangedCall() throws Exception {

    String callCode =
        "{call .foo /}\n";
    testUnchangedCallHelper(callCode);

    callCode =
        "{call .foo data=\"$goo\" /}\n";
    testUnchangedCallHelper(callCode);

    callCode =
        "{call .foo data=\"$goo\"}\n" +
        "  {param xxx: $xxx /}\n" +
        "  {param yyyZzz: $yyyZzz /}\n" +
        "{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode =
        "{call .foo}\n" +
        "  {param xxx: $xxx0 /}\n" +
        "{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode =
        "{call .foo}\n" +
        "  {param xxx: xxx /}\n" +
        "{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode =
        "{call .foo}\n" +
        "  {param xxx: $goo.xxx /}\n" +
        "{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode =
        "{call .foo}\n" +
        "  {param xxx: $xxx.goo /}\n" +
        "{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode =
        "{call .foo}\n" +
        "  {param xxx: 'xxx' /}\n" +
        "{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode =
        "{call .foo}\n" +
        "  {param xxx}{$xxx}{/param}\n" +
        "{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode =
        "{call .foo}\n" +
        "  {param xxx}xxx{/param}\n" +
        "{/call}\n";
    testUnchangedCallHelper(callCode);

    callCode =
        "{call .foo}\n" +
        "  {param xxx: $xxx /}\n" +
        "  {param yyyZzz: $xxx.yyyZzz /}\n" +
        "{/call}\n";
    testUnchangedCallHelper(callCode);
  }


  private void testUnchangedCallHelper(String callCode) throws Exception {

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyCode(callCode);
    CallNode callNodeBeforePass = (CallNode) SharedTestUtils.getNode(soyTree, 0);
    callNodeBeforePass.setEscapingDirectiveNames(ImmutableList.of("|escapeHtml"));
    (new ChangeCallsToPassAllDataVisitor()).exec(soyTree);
    CallNode callNodeAfterPass = (CallNode) SharedTestUtils.getNode(soyTree, 0);
    assertEquals(callNodeBeforePass, callNodeAfterPass);
    assertEquals("Escaping directives should be preserved",
        ImmutableList.of("|escapeHtml"),
        callNodeAfterPass.getEscapingDirectiveNames());
  }


  public void testUnchangedCallWithLoopVar() throws Exception {

    String soyCode =
        "{call .foo}\n" +  // should be changed
        "  {param xxx: $xxx /}\n" +
        "{/call}\n" +
        "{foreach $xxx in $xxxs}\n" +
        "  {call .foo}\n" +  // should not be changed (param references loop var)
        "    {param xxx: $xxx /}\n" +
        "  {/call}\n" +
        "{/foreach}";
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyCode(soyCode);

    CallNode callNodeOutsideLoopBeforePass = (CallNode) SharedTestUtils.getNode(soyTree, 0);
    CallNode callNodeInsideLoopBeforePass = (CallNode) SharedTestUtils.getNode(soyTree, 1, 0, 0);
    (new ChangeCallsToPassAllDataVisitor()).exec(soyTree);
    CallNode callNodeOutsideLoopAfterPass = (CallNode) SharedTestUtils.getNode(soyTree, 0);
    CallNode callNodeInsideLoopAfterPass = (CallNode) SharedTestUtils.getNode(soyTree, 1, 0, 0);

    assertNotSame(callNodeOutsideLoopBeforePass, callNodeOutsideLoopAfterPass);
    assertSame(callNodeInsideLoopBeforePass, callNodeInsideLoopAfterPass);
  }

}
