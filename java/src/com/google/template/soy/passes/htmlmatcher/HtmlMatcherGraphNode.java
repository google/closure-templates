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

import com.google.common.base.Optional;
import com.google.template.soy.soytree.SoyNode;

/**
 * Nodes in the HTML tag matcher graph.
 *
 * <p>Each node represents a {@link SoyNode} in the AST and holds an edge to the syntactically next
 * node used when matching HTML tags.
 *
 * <p>Each node can have up to two edges: a true edge and a false edge. If the associated Soy node
 * is an HTML tag, then there is at most one edge - the true edge. Otherwise, if the associated Soy
 * node is a condition branch, there can be up to two edges.
 */
public abstract class HtmlMatcherGraphNode {

  /**
   * The edge kinds for edges leading from this node.
   *
   * <p>The node has at most one of each kind of edge.
   */
  public enum EdgeKind {
    TRUE_EDGE,
    FALSE_EDGE
  }

  /** Returns the associated {@link SoyNode} */
  public abstract Optional<SoyNode> getSoyNode();

  /** Returns the {@link HtmlMatcherGraphNode} linked by {@link EdgeKind} */
  public abstract Optional<HtmlMatcherGraphNode> getNodeForEdgeKind(EdgeKind edgeKind);

  /** Links this node to the given node along the specified edge. */
  public abstract void linkEdgeToNode(EdgeKind edgeKind, HtmlMatcherGraphNode node);

  /** Sets the edge kind for future calls to {@link #linkActiveEdgeToNode(HtmlMatcherGraphNode)} */
  public abstract void setActiveEdgeKind(EdgeKind edgeKind);

  /** Returns the active edge kind. */
  public EdgeKind getActiveEdgeKind() {
    return EdgeKind.TRUE_EDGE;
  }

  /** Links this node to the given node along this node's active edge. */
  public void linkActiveEdgeToNode(HtmlMatcherGraphNode node) {
    linkEdgeToNode(getActiveEdgeKind(), node);
  }
}
