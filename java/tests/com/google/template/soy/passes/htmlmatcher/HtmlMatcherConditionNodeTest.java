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

import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode.EdgeKind;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HtmlMatcherConditionNodeTest {

  @Test
  public void testGetSoyNode_ifCondNode() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");

    HtmlMatcherConditionNode testConditionNode =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExpr());

    assertThat(testConditionNode.getSoyNode()).hasValue(soyNode);
  }

  @Test
  public void testGetSoyNode_switchCaseNode() {
    SwitchCaseNode soyNode = TestUtils.soySwitchCaseNode("$switchCase");

    HtmlMatcherConditionNode testConditionNode =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExprList().get(0));

    assertThat(testConditionNode.getSoyNode()).hasValue(soyNode);
  }

  @Test
  public void testLinkActiveEdgeToNode_ifTrueEdge() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");
    IfCondNode soyNodeTwo = TestUtils.soyIfElseCondNode("$cond2Var");
    HtmlMatcherConditionNode testIfConditionNode =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExpr());
    HtmlMatcherConditionNode testIfElseConditionNode =
        new HtmlMatcherConditionNode(
            TestUtils.soyIfElseCondNode("$cond2Var"), soyNodeTwo.getExpr());

    testIfConditionNode.setActiveEdgeKind(EdgeKind.TRUE_EDGE);
    testIfConditionNode.linkActiveEdgeToNode(testIfElseConditionNode);

    assertThat(testIfConditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE))
        .hasValue(testIfElseConditionNode);
    assertThat(testIfConditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).isEmpty();
  }

  @Test
  public void testLinkActiveEdgeToNode_switchTrueEdge() {
    SwitchCaseNode soyNode = TestUtils.soySwitchCaseNode("$case1");
    SwitchCaseNode soyNodeTwo = TestUtils.soySwitchCaseNode("$case2");
    HtmlMatcherConditionNode switchCase1Node =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExprList().get(0));
    HtmlMatcherConditionNode switchCase2Node =
        new HtmlMatcherConditionNode(soyNodeTwo, soyNodeTwo.getExprList().get(0));

    switchCase1Node.setActiveEdgeKind(EdgeKind.TRUE_EDGE);
    switchCase1Node.linkActiveEdgeToNode(switchCase2Node);

    assertThat(switchCase1Node.getNodeForEdgeKind(EdgeKind.TRUE_EDGE))
        .hasValue(switchCase2Node);
    assertThat(switchCase1Node.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).isEmpty();
  }

  @Test
  public void testLinkActiveEdgeToNode_ifFalseEdge() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");
    IfCondNode soyNodeTwo = TestUtils.soyIfElseCondNode("$cond2Var");
    HtmlMatcherConditionNode testIfConditionNode =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExpr());
    HtmlMatcherConditionNode testIfElseConditionNode =
        new HtmlMatcherConditionNode(soyNodeTwo, soyNodeTwo.getExpr());

    testIfConditionNode.setActiveEdgeKind(EdgeKind.FALSE_EDGE);
    testIfConditionNode.linkActiveEdgeToNode(testIfElseConditionNode);

    assertThat(testIfConditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE))
        .hasValue(testIfElseConditionNode);
    assertThat(testIfConditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isEmpty();
  }

  @Test
  public void testLinkActiveEdgeToNode_switchFalseEdge() {
    SwitchCaseNode soyNode = TestUtils.soySwitchCaseNode("$case1");
    SwitchCaseNode soyNodeTwo = TestUtils.soySwitchCaseNode("$case2");
    HtmlMatcherConditionNode switchCase1Node =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExprList().get(0));
    HtmlMatcherConditionNode switchCase2Node =
        new HtmlMatcherConditionNode(soyNodeTwo, soyNodeTwo.getExprList().get(0));

    switchCase1Node.setActiveEdgeKind(EdgeKind.FALSE_EDGE);
    switchCase1Node.linkActiveEdgeToNode(switchCase2Node);

    assertThat(switchCase1Node.getNodeForEdgeKind(EdgeKind.FALSE_EDGE))
        .hasValue(switchCase2Node);
    assertThat(switchCase1Node.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isEmpty();
  }

  @Test
  public void testGetNodeForEdgeKind_ifNodeDefaultIsNull() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");
    HtmlMatcherConditionNode conditionNode =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExpr());

    assertThat(conditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isEmpty();
    assertThat(conditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).isEmpty();
  }

  @Test
  public void testGetNodeForEdgeKind_switchNodeDefaultIsNull() {
    SwitchCaseNode soyNode = TestUtils.soySwitchCaseNode("$case");
    HtmlMatcherConditionNode conditionNode =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExprList().get(0));

    assertThat(conditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isEmpty();
    assertThat(conditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).isEmpty();
  }

  @Test
  public void testLinkActiveEdgeToNode_ifNodeCantLinkToSelfTrueEdge() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");
    HtmlMatcherConditionNode conditionNode =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExpr());

    assertThrows(
        IllegalStateException.class,
        () -> conditionNode.linkEdgeToNode(EdgeKind.TRUE_EDGE, conditionNode));
  }

  @Test
  public void testLinkActiveEdgeToNode_switchNodeCantLinkToSelfTrueEdge() {
    SwitchCaseNode soyNode = TestUtils.soySwitchCaseNode("$case1");
    HtmlMatcherConditionNode conditionNode =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExprList().get(0));

    assertThrows(
        IllegalStateException.class,
        () -> conditionNode.linkEdgeToNode(EdgeKind.TRUE_EDGE, conditionNode));
  }

  @Test
  public void testLinkActiveEdgeToNode_ifNodeCantLinkToSelfFalseEdge() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");
    HtmlMatcherConditionNode conditionNode =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExpr());

    assertThrows(
        IllegalStateException.class,
        () -> conditionNode.linkEdgeToNode(EdgeKind.FALSE_EDGE, conditionNode));
  }

  @Test
  public void testLinkActiveEdgeToNode_switchNodeCantLinkToSelfFalseEdge() {
    SwitchCaseNode soyNode = TestUtils.soySwitchCaseNode("$case1");
    HtmlMatcherConditionNode conditionNode =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExprList().get(0));

    assertThrows(
        IllegalStateException.class,
        () -> conditionNode.linkEdgeToNode(EdgeKind.FALSE_EDGE, conditionNode));
  }

  @Test
  public void testLinkEdgeToNode_ifNodeTrueEdge() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");
    HtmlMatcherConditionNode conditionNode =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExpr());
    HtmlMatcherGraphNode openNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    conditionNode.linkEdgeToNode(EdgeKind.TRUE_EDGE, openNode);

    assertThat(conditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).hasValue(openNode);
    assertThat(conditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).isEmpty();
  }

  @Test
  public void testLinkEdgeToNode_switchNodeTrueEdge() {
    SwitchCaseNode soyNode = TestUtils.soySwitchCaseNode("$case1");
    HtmlMatcherConditionNode conditionNode =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExprList().get(0));
    HtmlMatcherGraphNode openNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    conditionNode.linkEdgeToNode(EdgeKind.TRUE_EDGE, openNode);

    assertThat(conditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).hasValue(openNode);
    assertThat(conditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).isEmpty();
  }

  @Test
  public void testLinkEdgeToNode_ifNodeFalseEdge() {
    IfCondNode soyNode = TestUtils.soyIfCondNode("$condVar");
    HtmlMatcherConditionNode conditionNode =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExprList().get(0));
    HtmlMatcherGraphNode openNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    conditionNode.linkEdgeToNode(EdgeKind.FALSE_EDGE, openNode);

    assertThat(conditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isEmpty();
    assertThat(conditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).hasValue(openNode);
  }

  @Test
  public void testLinkEdgeToNode_switchNodeFalseEdge() {
    SwitchCaseNode soyNode = TestUtils.soySwitchCaseNode("$case1");
    HtmlMatcherConditionNode conditionNode =
        new HtmlMatcherConditionNode(soyNode, soyNode.getExprList().get(0));
    HtmlMatcherGraphNode openNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    conditionNode.linkEdgeToNode(EdgeKind.FALSE_EDGE, openNode);

    assertThat(conditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isEmpty();
    assertThat(conditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).hasValue(openNode);
  }
}
