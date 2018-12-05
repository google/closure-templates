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
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.SoyNode;
import javax.annotation.Nullable;

public final class HtmlMatcherIfConditionNode implements HtmlMatcherGraphNode {

  private final IfCondNode conditionNode;

  @Nullable private HtmlMatcherGraphNode trueBranchNode = null;

  @Nullable private HtmlMatcherGraphNode falseBranchNode = null;

  private EdgeKind activeEdgeKind = EdgeKind.TRUE_EDGE;

  public HtmlMatcherIfConditionNode(SoyNode conditionNode) {
    checkState(
        conditionNode instanceof IfCondNode,
        "HtmlMatcherCondition nodes must be constructed with an IfCondNode.");
    this.conditionNode = (IfCondNode) conditionNode;
  }

  @Override
  public Optional<SoyNode> getSoyNode() {
    return Optional.of(conditionNode);
  }

  @Override
  public Optional<HtmlMatcherGraphNode> getNodeForEdgeKind(EdgeKind edgeKind) {
    switch (edgeKind) {
      case TRUE_EDGE:
        return Optional.fromNullable(trueBranchNode);
      case FALSE_EDGE:
        return Optional.fromNullable(falseBranchNode);
    }
    return Optional.absent();
  }

  @Override
  public void setActiveEdgeKind(EdgeKind edgeKind) {
    activeEdgeKind = edgeKind;
  }

  @Override
  public void linkActiveEdgeToNode(HtmlMatcherGraphNode node) {
    checkState(!this.equals(node), "Cannot link to self.");
    switch (activeEdgeKind) {
      case TRUE_EDGE:
        trueBranchNode = node;
        break;
      case FALSE_EDGE:
        falseBranchNode = node;
        break;
    }
  }
}
