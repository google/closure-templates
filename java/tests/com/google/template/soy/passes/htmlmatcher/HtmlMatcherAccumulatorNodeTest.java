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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode.EdgeKind;
import com.google.template.soy.soytree.IfCondNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HtmlMatcherAccumulatorNodeTest {

  @Test
  public void testGetSoyNodeDoesNotExist() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();

    assertThat(accNode.getSoyNode()).isAbsent();
  }

  @Test
  public void testSetActiveEdgeKindThrows_trueEdge() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();

    assertThrows(
        UnsupportedOperationException.class, () -> accNode.setActiveEdgeKind(EdgeKind.TRUE_EDGE));
  }

  @Test
  public void testSetActiveEdgeKindThrows_falseEdge() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();

    assertThrows(
        UnsupportedOperationException.class, () -> accNode.setActiveEdgeKind(EdgeKind.FALSE_EDGE));
  }

  @Test
  public void testGetNodeForEdgeKind_defaultIsAbsent() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();

    assertThat(accNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isAbsent();
  }

  @Test
  public void testGetNodeForEdgeKind_falseEdgeAbsent() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();
    HtmlMatcherAccumulatorNode nextNode = new HtmlMatcherAccumulatorNode();

    accNode.linkActiveEdgeToNode(nextNode);

    assertThat(accNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).isAbsent();
  }

  @Test
  public void testLinkActiveEdgeToNode() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();
    HtmlMatcherAccumulatorNode nextNode = new HtmlMatcherAccumulatorNode();

    accNode.linkActiveEdgeToNode(nextNode);

    assertThat(accNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).hasValue(nextNode);
  }

  @Test
  public void testLinkEdgeToNode_trueBranch() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();
    HtmlMatcherTagNode openTagNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    accNode.linkEdgeToNode(EdgeKind.TRUE_EDGE, openTagNode);

    assertThat(accNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).hasValue(openTagNode);
  }

  @Test
  public void testLinkEdgeToNode_falseBranchThrows() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();
    HtmlMatcherTagNode openTagNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    assertThrows(
        IllegalStateException.class,
        () -> accNode.linkEdgeToNode(EdgeKind.FALSE_EDGE, openTagNode));
  }

  @Test
  public void testLinkActiveEdgeToNode_cantLinkToSelf() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();

    assertThrows(IllegalStateException.class, () -> accNode.linkActiveEdgeToNode(accNode));
  }

  @Test
  public void testLinkEdgeToNode_cantLinkToSelfTrueEdge() {
    HtmlMatcherTagNode testOpenTagNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());
    assertThrows(
        IllegalStateException.class,
        () -> testOpenTagNode.linkEdgeToNode(EdgeKind.TRUE_EDGE, testOpenTagNode));
  }

  @Test
  public void testLinkEdgeToNode_cantLinkToSelfFalseEdge() {
    HtmlMatcherTagNode testOpenTagNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    assertThrows(
        IllegalStateException.class,
        () -> testOpenTagNode.linkEdgeToNode(EdgeKind.FALSE_EDGE, testOpenTagNode));
  }

  @Test
  public void testAccumulateNodes_nonConditionalNodes() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();
    HtmlMatcherTagNode openNode = TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());
    HtmlMatcherTagNode closeNode =
        TestUtils.htmlMatcherCloseTagNode(TestUtils.soyHtmlCloseTagNode());
    ImmutableList<ActiveEdge> activeEdges =
        ImmutableList.of(
            ActiveEdge.create(openNode, EdgeKind.TRUE_EDGE),
            ActiveEdge.create(closeNode, EdgeKind.TRUE_EDGE));

    accNode.accumulateActiveEdges(activeEdges);

    assertThat(openNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).hasValue(accNode);
    assertThat(closeNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).hasValue(accNode);
  }

  @Test
  public void testAccumulateNodes_conditionalNodes() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();
    IfCondNode node = TestUtils.soyIfCondNode("$condVar");
    HtmlMatcherConditionNode ifCondNode = new HtmlMatcherConditionNode(node, node.getExpr());
    ImmutableList<ActiveEdge> activeEdges =
        ImmutableList.of(
            ActiveEdge.create(ifCondNode, EdgeKind.TRUE_EDGE),
            ActiveEdge.create(ifCondNode, EdgeKind.FALSE_EDGE));

    accNode.accumulateActiveEdges(activeEdges);

    assertThat(ifCondNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).hasValue(accNode);
    assertThat(ifCondNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).hasValue(accNode);
  }
}
