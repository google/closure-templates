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
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode.EdgeKind;
import com.google.template.soy.soytree.SoyNode;
import javax.annotation.Nullable;

/**
 * Block nodes are either for loop blocks, ifempty blocks, or message blocks. We require that every
 * one of these blocks is internally balanced, to do that we recursively call into ourselves to
 * build a new independent graph.
 */
public final class HtmlMatcherBlockNode extends HtmlMatcherGraphNode {

  private final HtmlMatcherGraph graph;

  private final String parentBlockType;

  @Nullable private HtmlMatcherGraphNode nextNode;

  public HtmlMatcherBlockNode(HtmlMatcherGraph graph, String parentBlockType) {
    this.graph = graph;
    this.parentBlockType = parentBlockType;
  }

  public HtmlMatcherGraph getGraph() {
    return graph;
  }

  public String getParentBlockType() {
    return parentBlockType;
  }

  // ------ HtmlMatcherGraphNode implementation ------

  @Override
  public Optional<SoyNode> getSoyNode() {
    return Optional.absent();
  }

  @Override
  public Optional<HtmlMatcherGraphNode> getNodeForEdgeKind(EdgeKind edgeKind) {
    if (edgeKind == EdgeKind.TRUE_EDGE) {
      return Optional.fromNullable(nextNode);
    }
    return Optional.absent();
  }

  @Override
  public void setActiveEdgeKind(EdgeKind edgeKind) {
    throw new UnsupportedOperationException("Cannot set the edge kind of a Block node.");
  }

  @Override
  public void linkEdgeToNode(EdgeKind edgeKind, HtmlMatcherGraphNode node) {
    checkState(edgeKind == EdgeKind.TRUE_EDGE, "HTML Block nodes only have a true branch.");
    checkState(!this.equals(node), "Can't link a node to itsself.");
    checkNotNull(node);
    nextNode = node;
  }
}
