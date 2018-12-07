/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.passes.htmlmatcher;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode.EdgeKind;
import com.google.template.soy.soytree.IfCondNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HtmlMatcherIfConditionNodeTest {

  @Test
  public void testGetSoyNode() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");

    HtmlMatcherIfConditionNode testConditionNode = new HtmlMatcherIfConditionNode(soyNode);

    assertThat(testConditionNode.getSoyNode()).hasValue(soyNode);
  }

  @Test
  public void testLinkActiveEdgeToNode_trueEdge() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");
    HtmlMatcherIfConditionNode testIfConditionNode = new HtmlMatcherIfConditionNode(soyNode);
    HtmlMatcherIfConditionNode testIfElseConditionNode =
        new HtmlMatcherIfConditionNode(TestUtils.soyIfElseCondNode("$cond2Var"));

    testIfConditionNode.setActiveEdgeKind(EdgeKind.TRUE_EDGE);
    testIfConditionNode.linkActiveEdgeToNode(testIfElseConditionNode);

    assertThat(testIfConditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE))
        .hasValue(testIfElseConditionNode);
    assertThat(testIfConditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).isAbsent();
  }

  @Test
  public void testLinkActiveEdgeToNode_falseEdge() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");
    HtmlMatcherIfConditionNode testIfConditionNode = new HtmlMatcherIfConditionNode(soyNode);
    HtmlMatcherIfConditionNode testIfElseConditionNode =
        new HtmlMatcherIfConditionNode(TestUtils.soyIfElseCondNode("$cond2Var"));

    testIfConditionNode.setActiveEdgeKind(EdgeKind.FALSE_EDGE);
    testIfConditionNode.linkActiveEdgeToNode(testIfElseConditionNode);

    assertThat(testIfConditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE))
        .hasValue(testIfElseConditionNode);
    assertThat(testIfConditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isAbsent();
  }

  @Test
  public void testGetNodeForEdgeKind_defaultIsNull() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");
    HtmlMatcherIfConditionNode testConditionNode = new HtmlMatcherIfConditionNode(soyNode);

    assertThat(testConditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isAbsent();
    assertThat(testConditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).isAbsent();
  }

  @Test
  public void testLinkActiveEdgeToNode_cantLinkToSelfTrueEdge() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");
    HtmlMatcherIfConditionNode testConditionNode = new HtmlMatcherIfConditionNode(soyNode);

    assertThrows(
        IllegalStateException.class,
        () -> testConditionNode.linkEdgeToNode(EdgeKind.TRUE_EDGE, testConditionNode));
  }

  @Test
  public void testLinkActiveEdgeToNode_cantLinkToSelfFalseEdge() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");
    HtmlMatcherIfConditionNode testConditionNode = new HtmlMatcherIfConditionNode(soyNode);

    assertThrows(
        IllegalStateException.class,
        () -> testConditionNode.linkEdgeToNode(EdgeKind.FALSE_EDGE, testConditionNode));
  }

  @Test
  public void testLinkEdgeToNode_trueEdge() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");
    HtmlMatcherIfConditionNode ifConditionNode = new HtmlMatcherIfConditionNode(soyNode);
    HtmlMatcherGraphNode openNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    ifConditionNode.linkEdgeToNode(EdgeKind.TRUE_EDGE, openNode);

    assertThat(ifConditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).hasValue(openNode);
    assertThat(ifConditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).isAbsent();
  }

  @Test
  public void testLinkEdgeToNode_falseEdge() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");
    HtmlMatcherIfConditionNode ifConditionNode = new HtmlMatcherIfConditionNode(soyNode);
    HtmlMatcherGraphNode openNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    ifConditionNode.linkEdgeToNode(EdgeKind.FALSE_EDGE, openNode);

    assertThat(ifConditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isAbsent();
    assertThat(ifConditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).hasValue(openNode);
  }
}
