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

import com.google.common.base.Optional;
import javax.annotation.Nullable;

public final class HtmlMatcherGraph {

  @Nullable private HtmlMatcherGraphNode rootNode = null;

  /** Pointer to where all new nodes are added in the graph. */
  @Nullable private HtmlMatcherGraphNode graphCursorNode = null;

  public Optional<HtmlMatcherGraphNode> getRootNode() {
    return Optional.fromNullable(rootNode);
  }

  /**
   * Attaches a new HTML tag node to the graph at the current cursor position.
   *
   * @param node the node to add
   */
  public void addNode(HtmlMatcherGraphNode node) {
    checkNotNull(node);
    if (graphCursorNode != null) {
      graphCursorNode.linkActiveEdgeToNode(node);
    }
    setGraphCursorNode(node);
  }

  private void setGraphCursorNode(HtmlMatcherGraphNode node) {
    graphCursorNode = node;
    if (rootNode == null) {
      rootNode = graphCursorNode;
    }
  }
}
