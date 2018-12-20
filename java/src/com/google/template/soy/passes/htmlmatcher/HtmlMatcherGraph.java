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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Optional;
import java.util.ArrayDeque;

public final class HtmlMatcherGraph {

  private Optional<HtmlMatcherGraphNode> rootNode = Optional.absent();

  /** Pointer to where all new nodes are added in the graph. */
  private Optional<HtmlMatcherGraphNode> graphCursor = Optional.absent();

  private final ArrayDeque<Optional<HtmlMatcherGraphNode>> cursorStack = new ArrayDeque<>();

  /** Returns the root node of the graph. */
  public Optional<HtmlMatcherGraphNode> getRootNode() {
    return rootNode;
  }

  /**
   * Returns the {@link com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode} at the
   * cursor.
   */
  public Optional<HtmlMatcherGraphNode> getNodeAtCursor() {
    return graphCursor;
  }

  /**
   * Saves the current cursor .
   *
   * <p>Does not change the value of the cursor. The matching call to {@link #restoreCursor()} will
   * move the cursor back to this position in the graph.
   */
  public void saveCursor() {
    cursorStack.push(graphCursor);
  }

  /**
   * Pops the cursor stack and moves the cursor to that value.
   *
   * <p>Must be paired with a call to {@link #saveCursor()}.
   */
  public void restoreCursor() {
    checkState(
        !cursorStack.isEmpty(),
        "Cursor stack underflow: restoreCursor() without matching saveCursor() call.");
    graphCursor = cursorStack.pop();
  }

  /**
   * Attaches a new HTML tag node to the graph at the current cursor position.
   *
   * @param node the node to add
   */
  public void addNode(HtmlMatcherGraphNode node) {
    checkNotNull(node);
    if (graphCursor.isPresent()) {
      graphCursor.get().linkActiveEdgeToNode(node);
    }
    setGraphCursorNode(node);
  }

  private void setGraphCursorNode(HtmlMatcherGraphNode node) {
    graphCursor = Optional.of(node);
    if (!rootNode.isPresent()) {
      rootNode = graphCursor;
    }
  }
}
