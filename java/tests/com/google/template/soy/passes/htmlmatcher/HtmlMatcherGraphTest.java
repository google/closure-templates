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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HtmlMatcherGraphTest {

  /**
   * The default exception policy: no exceptions thrown.
   *
   * <p>Note: in junit 4.13, this has been replaced with assertThrows(). Our opensource bundle still
   * uses junit 4.11.
   */
  @Rule public final ExpectedException exceptionPolicy = ExpectedException.none();

  @Test
  public void testAddNodeToEmptyGraph_udatesRootNode() {
    HtmlMatcherGraph matcherGraph = new HtmlMatcherGraph();

    assertThat(matcherGraph.getRootNode()).isAbsent();

    HtmlMatcherGraphNode openNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());
    matcherGraph.addNode(openNode);

    assertThat(matcherGraph.getRootNode()).hasValue(openNode);
  }

  @Test
  public void testAddNode_updatesCursor() {
    HtmlMatcherGraph matcherGraph = new HtmlMatcherGraph();

    assertThat(matcherGraph.getNodeAtCursor()).isAbsent();

    HtmlMatcherGraphNode openNode =
        TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode());
    matcherGraph.addNode(openNode);

    assertThat(matcherGraph.getNodeAtCursor()).hasValue(openNode);
  }

  @Test
  public void testSaveGraphCursor_doesntChangeCursor() {
    HtmlMatcherGraph matcherGraph = simpleMatcherGraph();

    assertThat(matcherGraph.getNodeAtCursor()).isPresent();

    HtmlMatcherGraphNode nodeAtCursor = matcherGraph.getNodeAtCursor().get();
    matcherGraph.saveCursor();

    assertThat(matcherGraph.getNodeAtCursor()).hasValue(nodeAtCursor);
  }

  @Test
  public void testSaveRestoreEmptyGraph() {
    HtmlMatcherGraph emptyGraph = new HtmlMatcherGraph();

    assertThat(emptyGraph.getRootNode()).isAbsent();

    emptyGraph.saveCursor();

    assertThat(emptyGraph.getNodeAtCursor()).isAbsent();

    emptyGraph.restoreCursor();

    assertThat(emptyGraph.getNodeAtCursor()).isAbsent();
  }

  @Test
  public void testSaveRestoreCursor() {
    HtmlMatcherGraph matcherGraph = simpleMatcherGraph();

    assertThat(matcherGraph.getNodeAtCursor()).isPresent();

    HtmlMatcherGraphNode savedNode = matcherGraph.getNodeAtCursor().get();
    matcherGraph.saveCursor();
    matcherGraph.addNode(new HtmlMatcherAccumulatorNode());

    assertThat(matcherGraph.getNodeAtCursor().get()).isNotEqualTo(savedNode);

    matcherGraph.restoreCursor();

    assertThat(matcherGraph.getNodeAtCursor()).hasValue(savedNode);
  }

  @Test
  public void testRestoreCursor_underflowsSimpleGraph() {
    HtmlMatcherGraph matcherGraph = simpleMatcherGraph();

    assertThat(matcherGraph.getNodeAtCursor()).isPresent();
    exceptionPolicy.expect(IllegalStateException.class);
    matcherGraph.restoreCursor();
  }

  @Test
  public void testRestoreCursor_underflowsEmptyGraph() {
    HtmlMatcherGraph emptyGraph = new HtmlMatcherGraph();

    exceptionPolicy.expect(IllegalStateException.class);
    emptyGraph.restoreCursor();
  }

  private static HtmlMatcherGraph simpleMatcherGraph() {
    HtmlMatcherGraph matcherGraph = new HtmlMatcherGraph();
    matcherGraph.addNode(TestUtils.htmlMatcherOpenTagNode(TestUtils.soyHtmlOpenTagNode()));
    matcherGraph.addNode(TestUtils.htmlMatcherCloseTagNode(TestUtils.soyHtmlCloseTagNode()));
    return matcherGraph;
  }
}
