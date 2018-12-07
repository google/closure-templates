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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode.EdgeKind;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HtmlMatcherAccumulatorNodeTest {

  /**
   * The default exception policy: no exceptions thrown.
   *
   * <p>Note: in junit 4.13, this has been replaced with assertThrows(). Our opensource bundle still
   * uses junit 4.11.
   */
  @Rule public final ExpectedException exceptionPolicy = ExpectedException.none();

  @Test
  public void testGetSoyNodeIsEmptyOptional() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();

    assertThat(accNode.getSoyNode()).isAbsent();
  }

  @Test
  public void testSetActiveEdgeKindThrows_trueEdge() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();

    exceptionPolicy.expect(UnsupportedOperationException.class);
    accNode.setActiveEdgeKind(EdgeKind.TRUE_EDGE);
  }

  @Test
  public void testSetActiveEdgeKindThrows_falseEdge() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();

    exceptionPolicy.expect(UnsupportedOperationException.class);
    accNode.setActiveEdgeKind(EdgeKind.FALSE_EDGE);
  }

  @Test
  public void testGetNodeForEdgeKind_defaultIsEmptyOptional() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();

    assertThat(accNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isAbsent();
  }

  @Test
  public void testLinkActiveEdgeToNode() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();
    HtmlMatcherAccumulatorNode nextNode = new HtmlMatcherAccumulatorNode();

    accNode.linkActiveEdgeToNode(nextNode);

    assertThat(accNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).hasValue(nextNode);
  }

  @Test
  public void testLinkActiveEdgeToNode_cantLinkToSelf() {
    HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();

    exceptionPolicy.expect(IllegalStateException.class);
    accNode.linkActiveEdgeToNode(accNode);
  }

  @Test
  public void testLinkEdgeToNode_cantLinkToSelfTrueEdge() {
    HtmlMatcherTagNode testOpenTagNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());
    exceptionPolicy.expect(IllegalStateException.class);
    testOpenTagNode.linkEdgeToNode(EdgeKind.TRUE_EDGE, testOpenTagNode);
  }

  @Test
  public void testLinkEdgeToNode_cantLinkToSelfFalseEdge() {
    HtmlMatcherTagNode testOpenTagNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    exceptionPolicy.expect(IllegalStateException.class);
    testOpenTagNode.linkEdgeToNode(EdgeKind.FALSE_EDGE, testOpenTagNode);
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
    HtmlMatcherIfConditionNode ifCondNode =
        new HtmlMatcherIfConditionNode(TestUtils.soyIfCondNode("$condVar"));
    ImmutableList<ActiveEdge> activeEdges =
        ImmutableList.of(
            ActiveEdge.create(ifCondNode, EdgeKind.TRUE_EDGE),
            ActiveEdge.create(ifCondNode, EdgeKind.FALSE_EDGE));

    accNode.accumulateActiveEdges(activeEdges);

    assertThat(ifCondNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).hasValue(accNode);
    assertThat(ifCondNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).hasValue(accNode);
  }
}
