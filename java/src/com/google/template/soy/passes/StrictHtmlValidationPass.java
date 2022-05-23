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

package com.google.template.soy.passes;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.passes.htmlmatcher.ActiveEdge;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherAccumulatorNode;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherBlockNode;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherConditionNode;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraph;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode.EdgeKind;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherTagNode;
import com.google.template.soy.passes.htmlmatcher.HtmlTagMatchingPass;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.ForIfemptyNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPluralCaseNode;
import com.google.template.soy.soytree.MsgPluralDefaultNode;
import com.google.template.soy.soytree.MsgSelectCaseNode;
import com.google.template.soy.soytree.MsgSelectDefaultNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A {@link CompilerFilePass} that checks strict html mode. See go/soy-html for usages.
 */
@RunAfter(ResolveNamesPass.class)
public final class StrictHtmlValidationPass implements CompilerFilePass {

  private final ErrorReporter errorReporter;

  @Nullable private HtmlMatcherGraph htmlMatcherGraph = null;

  public StrictHtmlValidationPass(ErrorReporter errorReporter) {
    this.errorReporter = checkNotNull(errorReporter, "errorReporter must be non-null.");
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode node : file.getTemplates()) {
      checkTemplateNode(node, nodeIdGen);
    }
  }

  private void checkTemplateNode(TemplateNode node, IdGenerator idGenerator) {
    ErrorReporter reporter = ErrorReporter.create(ImmutableMap.of());
    htmlMatcherGraph = new HtmlTagVisitor(idGenerator, reporter).exec(node);
    new HtmlTagMatchingPass(
            reporter,
            idGenerator,
            /** inCondition */
            false,
            /** foreignContentTagDepth */
            0,
            /** parentBlockType */
            null)
        .run(htmlMatcherGraph);
    if (node.isStrictHtml()) {
      reporter.copyTo(this.errorReporter);
    }
  }

  @VisibleForTesting
  public Optional<HtmlMatcherGraph> getHtmlMatcherGraph() {
    return Optional.ofNullable(htmlMatcherGraph);
  }

  private static final class HtmlTagVisitor extends AbstractSoyNodeVisitor<HtmlMatcherGraph> {

    /**
     * The HTML tag matcher graph.
     *
     * <p>There are three stages concerning this graph:
     *
     * <ol>
     *   <li>Build the graph by walking the AST in-order. At each node, a corresponding HTML Matcher
     *       Graph node is created and linked to the HTML Matcher Graph. The HTML Matcher Graph is a
     *       shadow structure of the AST.
     *   <li>Traverse the graph, inserting implicit closing tags that match void or self-closing
     *       HTML tags.
     *   <li>Traverse the graph, matching HTML open and close tags through all possible code paths.
     *       This is done using a simple stack mechanism, where each open tag pushes on the stack,
     *       and each close tag pops the stack. Raise an "unm,atched HTML tag" error if there is a
     *       stack underflow or if the stack is not empty at the end of any graph traversal.
     * </ol>
     *
     * <p>See <a
     * for more info.
     */
    private final HtmlMatcherGraph htmlMatcherGraph = new HtmlMatcherGraph();

    /**
     * A stack of active edge lists.
     *
     * <p>The active edges belong to the syntactically last HTML tags in a condition block. Note
     * that the syntactically last node might be the condition node itself, if there are no HTML
     * tags in its block. For example {@code {if $cond1}Content{/if}}.
     *
     * <p>At the end of each {@link IfNode}, all active edges are accumulated into a synthetic
     * {@link HtmlMatcherAccumulatorNode}. These synthetic nodes act as a pass-through node when
     * traversing the {@link HtmlMatcherGraph} in order to match HTML tags.
     *
     * <p>The stack is pushed on entry to an {@link IfNode} and popped at the end.
     */
    private final ArrayDeque<List<ActiveEdge>> activeEdgeStack = new ArrayDeque<>();

    private final IdGenerator idGenerator;
    private final ErrorReporter errorReporter;

    HtmlTagVisitor(IdGenerator idGenerator, ErrorReporter errorReporter) {
      this.idGenerator = idGenerator;
      this.errorReporter = errorReporter;
    }

    @Override
    public HtmlMatcherGraph exec(SoyNode node) {
      visitChildren((BlockNode) node);
      return htmlMatcherGraph;
    }

    @Override
    protected void visitHtmlOpenTagNode(HtmlOpenTagNode node) {
      htmlMatcherGraph.addNode(new HtmlMatcherTagNode(node));
    }

    @Override
    protected void visitHtmlCloseTagNode(HtmlCloseTagNode node) {
      htmlMatcherGraph.addNode(new HtmlMatcherTagNode(node));
    }

    @Override
    protected void visitIfNode(IfNode node) {
      enterConditionalContext();
      visitChildren(node);
      exitConditionalContext();
    }

    /**
     * Starts a conditional branch in the {@link HtmlMatcherGraph}.
     *
     * <p>The basic logic flow is:
     *
     * <ol>
     *   <li>Start a new conditional node, with the active edge set to {@link EdgeKind#TRUE_EDGE}
     *   <li>Visit all the children.
     *   <li>The graph cursor now points to the syntactically last HTML tag in the {@code true}
     *       branch of the condition block. Add this node to the list of active nodes for later
     *       accumulation.
     *   <li>Set the active edge of this onditional node to {@link EdgeKind#FALSE_EDGE}
     *   <li>Continue building the HTML matcher graph from the {@code false} branch of this node.
     * </ol>
     *
     * <p>HTML Matcher graph building continues at the {@link HtmlMatcherAccumulatorNode}
     *
     * <p>See <a
     */
    @Override
    protected void visitIfCondNode(IfCondNode node) {
      HtmlMatcherConditionNode conditionNode = enterConditionBranch(node.getExpr(), node);
      visitChildren(node);
      exitConditionBranch(conditionNode);
    }

    @Override
    protected void visitSwitchNode(SwitchNode node) {
      enterConditionalContext();
      visitChildren(node);
      exitConditionalContext();
    }

    @Override
    protected void visitSwitchCaseNode(SwitchCaseNode node) {
      for (ExprNode expr : node.getExprList()) {
        HtmlMatcherConditionNode conditionNode = enterConditionBranch(expr, node);
        visitChildren(node);
        exitConditionBranch(conditionNode);
      }
    }

    // These are all the 'block' nodes.
    //
    // We require that every one of these blocks is internally balanced, to do that we recursively
    // call into ourselves to build a new independent graph.

    @Override
    protected void visitMsgNode(MsgNode node) {
      htmlMatcherGraph.addNode(
          new HtmlMatcherBlockNode(
              new HtmlTagVisitor(idGenerator, errorReporter).exec(node), "msg"));
    }

    @Override
    protected void visitMsgPluralCaseNode(MsgPluralCaseNode node) {
      htmlMatcherGraph.addNode(
          new HtmlMatcherBlockNode(
              new HtmlTagVisitor(idGenerator, errorReporter).exec(node), "msg"));
    }

    @Override
    protected void visitMsgPluralDefaultNode(MsgPluralDefaultNode node) {
      htmlMatcherGraph.addNode(
          new HtmlMatcherBlockNode(
              new HtmlTagVisitor(idGenerator, errorReporter).exec(node), "msg"));
    }

    @Override
    protected void visitMsgSelectCaseNode(MsgSelectCaseNode node) {
      htmlMatcherGraph.addNode(
          new HtmlMatcherBlockNode(
              new HtmlTagVisitor(idGenerator, errorReporter).exec(node), "msg"));
    }

    @Override
    protected void visitMsgSelectDefaultNode(MsgSelectDefaultNode node) {
      htmlMatcherGraph.addNode(
          new HtmlMatcherBlockNode(
              new HtmlTagVisitor(idGenerator, errorReporter).exec(node), "msg"));
    }

    @Override
    protected void visitForIfemptyNode(ForIfemptyNode node) {
      htmlMatcherGraph.addNode(
          new HtmlMatcherBlockNode(
              new HtmlTagVisitor(idGenerator, errorReporter).exec(node), "loop"));
    }

    @Override
    protected void visitForNonemptyNode(ForNonemptyNode node) {
      htmlMatcherGraph.addNode(
          new HtmlMatcherBlockNode(
              new HtmlTagVisitor(idGenerator, errorReporter).exec(node), "loop"));
    }

    @Override
    protected void visitVeLogNode(VeLogNode node) {
      htmlMatcherGraph.addNode(
          new HtmlMatcherBlockNode(
              new HtmlTagVisitor(idGenerator, errorReporter).exec(node), "velog"));
    }

    // These two blocks are explicitly not affected by foreign content, so just run the pass
    // over independently.

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      HtmlMatcherGraph htmlMatcherGraph = new HtmlTagVisitor(idGenerator, errorReporter).exec(node);
      new HtmlTagMatchingPass(
              errorReporter,
              idGenerator,
              /** inCondition */
              false,
              /** foreignContentTagDepth */
              0,
              "let content")
          .run(htmlMatcherGraph);
    }

    @Override
    protected void visitCallParamContentNode(CallParamContentNode node) {
      HtmlMatcherGraph htmlMatcherGraph = new HtmlTagVisitor(idGenerator, errorReporter).exec(node);
      new HtmlTagMatchingPass(
              errorReporter,
              idGenerator,
              /** inCondition */
              false,
              /** foreignContentTagDepth */
              0,
              "call param content")
          .run(htmlMatcherGraph);
    }

    /**
     * Establishes the start of a conditional control-flow in the {@link HtmlMatcherGraph}.
     *
     * <p>After this call, the graph builder starts making conditional branches, which terminate in
     * {@link HtmlMatcherAccumulatorNode}. While in a conditional builder context, the active edges
     * of certain nodes are recorded, and accumulated on exit of the condtional context. Condition
     * contexts can be nested.
     *
     * <p>See <a
     */
    private void enterConditionalContext() {
      activeEdgeStack.push(new ArrayList<>());
    }

    /**
     * Terminates the conditional graph-builder context in the {@link HtmlMatcherGraph} started in
     * the nearest call to {@link #enterConditionalContext()}. The basic logic flow is:
     *
     * <ol>
     *   <li>Start a new list of active edges that are candidates for an accumulator node. This is
     *       done in {@link #enterConditionalContext()}.
     *   <li>For each of its children, update the active node to the syntactically last node in the
     *       child block.
     *   <li>Capture all the active edges into an {@link HtmlMatcherAccumulatorNode}.
     * </ol>
     *
     * <p>HTML Matcher graph building continues at the {@link HtmlMatcherAccumulatorNode}
     *
     * <p>See <a
     */
    private void exitConditionalContext() {
      // Add the syntactically last AST node of the else branch. If there is no else branch, then
      // add the syntactically last if branch. Note that the active edge of the syntactically last
      // if branch is the FALSE edge.
      List<ActiveEdge> activeEdges = activeEdgeStack.pop();
      if (htmlMatcherGraph.getNodeAtCursor().isPresent()) {
        HtmlMatcherGraphNode activeNode = htmlMatcherGraph.getNodeAtCursor().get();
        activeEdges.add(ActiveEdge.create(activeNode, activeNode.getActiveEdgeKind()));
      }
      HtmlMatcherAccumulatorNode accNode = new HtmlMatcherAccumulatorNode();
      accNode.accumulateActiveEdges(ImmutableList.copyOf(activeEdges));
      htmlMatcherGraph.addNode(accNode);
    }

    /**
     * Starts a conditional branch in the {@link HtmlMatcherGraph}.
     *
     * <p>Adds a newly-created {@link HtmlMatcherConditionNode} to the matcher graph, and saves the
     * graph's cursor state and sets the active edge of the {@link HtmlMatcherConditionNode} to the
     * {@code true} edge.
     *
     * <p>This condition branch is terminated by calling {@link
     * #exitConditionBranch(HtmlMatcherConditionNode)}.
     *
     * <p>See <a
     */
    private HtmlMatcherConditionNode enterConditionBranch(ExprNode expr, SoyNode node) {
      HtmlMatcherConditionNode conditionNode =
          new HtmlMatcherConditionNode(
              node, expr, new HtmlTagVisitor(idGenerator, errorReporter).exec(node));
      htmlMatcherGraph.addNode(conditionNode);
      htmlMatcherGraph.saveCursor();
      conditionNode.setActiveEdgeKind(EdgeKind.TRUE_EDGE);
      return conditionNode;
    }

    /**
     * Terminates a conditional branch in the {@link HtmlMatcherGraph}.
     *
     * <p>The basic logic flow is:
     *
     * <ol>
     *   <li>The HTML matcher graph cursor now points to the syntactically last HTML tag in the
     *       {@code true} branch of the condition block. Add this node and its active edge to the
     *       list of active edges for later accumulation.
     *   <li>Set the active edge of this conditional node to {@link EdgeKind#FALSE_EDGE}
     *   <li>Continue building the HTML matcher graph from the {@code false} branch of this node.
     *   <li>The {@code false} condition branch gets terminated when the entire conditional
     *       graph-building context ends in {@link #exitConditionalContext()}.
     * </ol>
     *
     * <p>HTML Matcher graph building continues at the {@link HtmlMatcherAccumulatorNode}
     *
     * <p>See <a
     */
    private void exitConditionBranch(HtmlMatcherConditionNode ifConditionNode) {
      // The graph cursor points to the syntactically last HTML tag in the if block. Note that this
      // could be the originating HtmlMatcherConditionNode.
      if (htmlMatcherGraph.getNodeAtCursor().isPresent()) {
        HtmlMatcherGraphNode activeNode = htmlMatcherGraph.getNodeAtCursor().get();
        activeEdgeStack.peek().add(ActiveEdge.create(activeNode, activeNode.getActiveEdgeKind()));
      }
      ifConditionNode.setActiveEdgeKind(EdgeKind.FALSE_EDGE);
      htmlMatcherGraph.restoreCursor();
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }
}
