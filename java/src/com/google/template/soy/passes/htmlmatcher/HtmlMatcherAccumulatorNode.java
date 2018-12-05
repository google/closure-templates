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

import com.google.common.base.Optional;
import com.google.template.soy.soytree.SoyNode;
import javax.annotation.Nullable;

/**
 * Synthetic node to join multiple graph nodes when many code paths lead to one place.
 *
 * <p>This is similar to the phi function described in <a
 * href="https://en.wikipedia.org/wiki/Static_single_assignment_form">Static_single_assignment_form</a>
 */
public final class HtmlMatcherAccumulatorNode implements HtmlMatcherGraphNode {

  @Nullable private HtmlMatcherGraphNode nextNode = null;

  @Override
  public Optional<SoyNode> getSoyNode() {
    return Optional.absent();
  }

  @Nullable
  @Override
  public Optional<HtmlMatcherGraphNode> getNodeForEdgeKind(EdgeKind edgeKind) {
    switch (edgeKind) {
      case TRUE_EDGE:
        return Optional.fromNullable(nextNode);
      case FALSE_EDGE:
        throw new IllegalArgumentException("Accumulator nodes do not have a FALSE_EDGE.");
    }
    return Optional.absent();
  }

  @Override
  public void setActiveEdgeKind(EdgeKind edgeKind) {
    throw new UnsupportedOperationException("Cannot set the edge kind of an Accumulator node.");
  }

  @Override
  public void linkActiveEdgeToNode(HtmlMatcherGraphNode node) {
    checkState(!this.equals(node), "Can't link a node to self.");
    nextNode = node;
  }
}
