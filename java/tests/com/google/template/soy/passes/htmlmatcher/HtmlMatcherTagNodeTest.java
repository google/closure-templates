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
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode.EdgeKind;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherTagNode.TagKind;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HtmlMatcherTagNodeTest {

  @Test
  public void testGetSoyNode() {
    HtmlOpenTagNode soyNode = TestUtils.soyHtmlOpenTagNode();
    HtmlMatcherTagNode testOpenTagNode = TestUtils.htmlMatcherOpenTagNode(soyNode);

    assertThat(testOpenTagNode.getSoyNode()).hasValue(soyNode);
  }

  @Test
  public void testGetTagKind() {
    HtmlMatcherTagNode testOpenTagNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    assertThat(testOpenTagNode.getTagKind()).isEqualTo(TagKind.OPEN_TAG);

    HtmlMatcherTagNode testCloseTagNode =
        TestUtils.htmlMatcherCloseTagNode(TestUtils.soyHtmlCloseTagNode());

    assertThat(testCloseTagNode.getTagKind()).isEqualTo(TagKind.CLOSE_TAG);
  }

  @Test
  public void testSetActiveEdgeKindThrows_trueEdge() {
    HtmlMatcherTagNode testMatcherTagNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    assertThrows(
        UnsupportedOperationException.class,
        () -> testMatcherTagNode.setActiveEdgeKind(EdgeKind.TRUE_EDGE));
  }

  @Test
  public void testSetActiveEdgeKindThrows_falseEdge() {
    HtmlMatcherTagNode testMatcherTagNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    assertThrows(
        UnsupportedOperationException.class,
        () -> testMatcherTagNode.setActiveEdgeKind(EdgeKind.FALSE_EDGE));
  }

  @Test
  public void testGetNodeForEdgeKind_defaultIsAbsent() {
    HtmlMatcherTagNode testMatcherTagNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    assertThat(testMatcherTagNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isEmpty();
  }

  @Test
  public void testGetNodeForEdgeKind_falseEdgeAbsent() {
    HtmlMatcherTagNode testMatcherTagNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());

    assertThat(testMatcherTagNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).isEmpty();
  }

  @Test
  public void testLinkActiveEdgeToNode() {
    HtmlMatcherTagNode testOpenTagNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());
    HtmlMatcherTagNode testCloseTagNode =
        TestUtils.htmlMatcherCloseTagNode(TestUtils.soyHtmlCloseTagNode());

    testOpenTagNode.linkActiveEdgeToNode(testCloseTagNode);

    assertThat(testOpenTagNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).hasValue(testCloseTagNode);
  }

  @Test
  public void testLinkEdgeToNode_trueBranch() {
    HtmlMatcherTagNode testOpenTagNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());
    HtmlMatcherTagNode testCloseTagNode =
        TestUtils.htmlMatcherCloseTagNode(TestUtils.soyHtmlCloseTagNode());

    testOpenTagNode.linkEdgeToNode(EdgeKind.TRUE_EDGE, testCloseTagNode);

    assertThat(testOpenTagNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).hasValue(testCloseTagNode);
  }

  @Test
  public void testLinkEdgeToNode_falseBranchThrows() {
    HtmlMatcherTagNode testOpenTagNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());
    HtmlMatcherTagNode testCloseTagNode =
        TestUtils.htmlMatcherCloseTagNode(TestUtils.soyHtmlCloseTagNode());

    assertThrows(
        IllegalStateException.class,
        () -> testOpenTagNode.linkEdgeToNode(EdgeKind.FALSE_EDGE, testCloseTagNode));
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
}
