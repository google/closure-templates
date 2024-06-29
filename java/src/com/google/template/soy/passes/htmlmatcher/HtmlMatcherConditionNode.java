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

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Represents an {@link IfCondNode} or a {@link SwitchCaseNode}.
 *
 * <p>Note that there is no explicit representation for {@link IfElseNode} or {@link
 * SwitchDefaultNode}. When an {@code {else}} command is encountered, it is represented by a link
 * along the false edge of a {@link HtmlMatcherConditionNode}
 *
 */
public final class HtmlMatcherConditionNode extends HtmlMatcherGraphNode {

  private final SoyNode soyNode;

  private EdgeKind activeEdge = EdgeKind.TRUE_EDGE;

  private final ExprNode expression;

  private final HtmlMatcherGraph graph;

  @Nullable private HtmlMatcherGraphNode trueBranchNode = null;

  @Nullable private HtmlMatcherGraphNode falseBranchNode = null;

  private Optional<Boolean> isInternallyBalancedForForeignContent = Optional.empty();
  private Optional<Boolean> isInternallyBalanced = Optional.empty();

  public HtmlMatcherConditionNode(SoyNode soyNode, ExprNode expression, HtmlMatcherGraph graph) {
    this.soyNode = soyNode;
    this.expression = expression;
    this.graph = graph;
  }

  public HtmlMatcherConditionNode(SoyNode soyNode, ExprNode expression) {
    this.soyNode = soyNode;
    this.expression = expression;
    this.graph = null;
  }

  public ExprNode getExpression() {
    return this.expression;
  }

  public boolean isInternallyBalanced(int foreignContentTagDepth, IdGenerator idGenerator) {
    ErrorReporter errorReporter = ErrorReporter.create();
    HtmlTagMatchingPass pass =
        new HtmlTagMatchingPass(
            errorReporter,
            idGenerator,

            /* inCondition= */ true,
            foreignContentTagDepth,
            "condition");
    if (foreignContentTagDepth > 0) {
      if (!isInternallyBalancedForForeignContent.isPresent()) {
        pass.run(graph);
        isInternallyBalancedForForeignContent = Optional.of(errorReporter.getErrors().isEmpty());
      }
      return isInternallyBalancedForForeignContent.get();
    }
    if (!isInternallyBalanced.isPresent()) {
      pass.run(graph);
      isInternallyBalanced = Optional.of(errorReporter.getErrors().isEmpty());
    }
    return isInternallyBalanced.get();
  }

  @Override
  public Optional<SoyNode> getSoyNode() {
    return Optional.of(soyNode);
  }

  @Override
  public EdgeKind getActiveEdgeKind() {
    return activeEdge;
  }

  @Override
  public void setActiveEdgeKind(EdgeKind edgeKind) {
    activeEdge = edgeKind;
  }

  @Override
  public void linkEdgeToNode(EdgeKind edgeKind, HtmlMatcherGraphNode node) {
    checkState(!this.equals(node), "Cannot link a node to itself.");
    switch (edgeKind) {
      case TRUE_EDGE:
        trueBranchNode = node;
        break;
      case FALSE_EDGE:
        falseBranchNode = node;
        break;
    }
  }

  @Override
  public Optional<HtmlMatcherGraphNode> getNodeForEdgeKind(EdgeKind edgeKind) {
    switch (edgeKind) {
      case TRUE_EDGE:
        return Optional.ofNullable(trueBranchNode);
      case FALSE_EDGE:
        return Optional.ofNullable(falseBranchNode);
    }
    return Optional.empty();
  }
}
