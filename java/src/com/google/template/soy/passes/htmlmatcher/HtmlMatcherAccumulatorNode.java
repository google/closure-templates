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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.soytree.SoyNode;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Synthetic node to join multiple graph nodes when many code paths lead to one place.
 *
 * <p>This is similar to the phi function described in <a
 * href="https://en.wikipedia.org/wiki/Static_single_assignment_form">Static_single_assignment_form</a>
 */
public final class HtmlMatcherAccumulatorNode extends HtmlMatcherGraphNode {

  @Nullable private HtmlMatcherGraphNode nextNode = null;

  @Override
  public Optional<SoyNode> getSoyNode() {
    return Optional.empty();
  }

  @Override
  public Optional<HtmlMatcherGraphNode> getNodeForEdgeKind(EdgeKind edgeKind) {
    if (edgeKind == EdgeKind.TRUE_EDGE) {
      return Optional.ofNullable(nextNode);
    }
    return Optional.empty();
  }

  @Override
  public void setActiveEdgeKind(EdgeKind edgeKind) {
    throw new UnsupportedOperationException("Cannot set the edge kind of an Accumulator node.");
  }

  @Override
  public void linkEdgeToNode(EdgeKind edgeKind, HtmlMatcherGraphNode node) {
    checkState(edgeKind == EdgeKind.TRUE_EDGE, "Accumulator nodes only have a true branch.");
    checkState(!this.equals(node), "Can't link a node to itsself.");
    nextNode = node;
  }

  /**
   * Links the all the given active edges to this node.
   *
   * <p>This produces a many-to-one linkage in the HTML matcher graph.
   *
   * <p>Note that a {@link HtmlMatcherGraphNode} may occur in the list more than once, each time
   * with a different active edge. For example, this Soy template body:
   *
   * <pre>
   *   &lt;span&gt;
   *     {if $cond1}Content1{/if}  // No HTML tags in the If-block.
   *   &lt;/span&gt;
   * </pre>
   *
   * will add two occurences of an {@link HtmlMatcherConditionNode} to the {@code activeEdges} list,
   * one with a {@code TRUE} active edge, and one with a {@code FALSE} active edge.
   *
   * @param activeEdges the list of all active edges that will point to this {@link
   *     HtmlMatcherAccumulatorNode}. Note that a {@link HtmlMatcherGraphNode} may occur in the list
   *     more than once.
   */
  public void accumulateActiveEdges(ImmutableList<ActiveEdge> activeEdges) {
    for (ActiveEdge accEdge : activeEdges) {
      accEdge.getGraphNode().linkEdgeToNode(accEdge.getActiveEdge(), this);
    }
  }
}
