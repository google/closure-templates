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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.passes.htmlmatcher.ActiveEdge;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherAccumulatorNode;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraph;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode.EdgeKind;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherIfConditionNode;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherTagNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A {@link CompilerFilePass} that checks strict html mode. See go/soy-html for usages.
 *
 * <p>Note: This pass requires that the {@link SoyConformancePass} has already been run.
 *
 * <p>TODO(b/118396161): Implement this pass, then replace {@link StrictHtmlValidationPass} with
 * this one.
 */
public final class StrictHtmlValidationPassNewMatcher extends CompilerFilePass {
  private static final SoyErrorKind VELOG_NODE_FIRST_CHILD_NOT_TAG =
      SoyErrorKind.of("The first child of '{velog'} must be a HTML open tag.");
  private static final SoyErrorKind VELOG_NODE_LAST_CHILD_NOT_TAG =
      SoyErrorKind.of("The last child of '{velog'} must be a HTML close tag.");
  private static final SoyErrorKind VELOG_NODE_EXACTLY_ONE_TAG =
      SoyErrorKind.of("'{velog'} must contain exactly one top-level HTML element.");

  private static final SoyErrorKind SOY_ELEMENT_EXACTLY_ONE_TAG =
      SoyErrorKind.of(
          "Soy elements must contain exactly one top-level HTML element (e.g, span, div).");

  private final ErrorReporter errorReporter;

  @Nullable private HtmlMatcherGraph htmlMatcherGraph = null;

  public StrictHtmlValidationPassNewMatcher(ErrorReporter errorReporter) {
    this.errorReporter = checkNotNull(errorReporter, "errorReporter must be non-null.");
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode node : file.getChildren()) {
      checkTemplateNode(node);
    }
  }

  private void checkTemplateNode(TemplateNode node) {
    final Function<HtmlMatcherGraph, Void> saveHtmlMatcherGraph =
        new Function<HtmlMatcherGraph, Void>() {
          @Override
          public Void apply(HtmlMatcherGraph visitorHtmlMatchingGraph) {
            htmlMatcherGraph = visitorHtmlMatchingGraph;
            return null;
          }
        };

    AutoescapeMode autoescapeMode = node.getAutoescapeMode();
    // The SoyConformance pass runs before this pass, which guarantees that any strict HTML node has
    // STRICT autoescaping mode. Note that you are allowed to set STRICT autoescaping mode on
    // a non-strict-HTML node.
    checkState(
        autoescapeMode.equals(AutoescapeMode.STRICT) || !node.isStrictHtml(),
        "Strict HTML template without strict autoescaping.");
    // ContentKind is guaranteed to be non-null if AutoescapeMode is strict.
    SanitizedContentKind contentKind = node.getContentKind();
    // The SoyConformance pass runs before this pass, which guarantees that any strict HTML node has
    // STRICT HTML sanitize mode. Note that you are allowed to set STRICT sanitize mode on
    // a non-strict-HTML node.
    checkState(
        contentKind.equals(SanitizedContentKind.HTML) || !node.isStrictHtml(),
        "Strict HTML in a non-HTML node.");
    if (node.isStrictHtml()) {
      new HtmlTagVisitor(errorReporter, saveHtmlMatcherGraph).exec(node);
    }
  }

  @VisibleForTesting
  public Optional<HtmlMatcherGraph> getHtmlMatcherGraph() {
    return Optional.fromNullable(htmlMatcherGraph);
  }

  private static final class HtmlTagVisitor extends AbstractSoyNodeVisitor<Void> {

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

    private final ErrorReporter errorReporter;

    /** Callback executed when finished visiting the parse tree. */
    private final Function<HtmlMatcherGraph, Void> didVisitParseTree;

    HtmlTagVisitor(ErrorReporter errorReporter, Function<HtmlMatcherGraph, Void> didVisitSoyGraph) {
      this.errorReporter = errorReporter;
      this.didVisitParseTree = didVisitSoyGraph;
    }

    @Override
    public Void exec(SoyNode node) {
      super.exec(node);
      didVisitParseTree.apply(htmlMatcherGraph);
      return null;
    }

    @Override
    protected void visitHtmlOpenTagNode(HtmlOpenTagNode node) {
      htmlMatcherGraph.addNode(
          new HtmlMatcherTagNode(node) {
            @Override
            public TagKind getTagKind() {
              return TagKind.OPEN_TAG;
            }
          });
    }

    @Override
    protected void visitHtmlCloseTagNode(HtmlCloseTagNode node) {
      htmlMatcherGraph.addNode(
          new HtmlMatcherTagNode(node) {
            @Override
            public TagKind getTagKind() {
              return TagKind.CLOSE_TAG;
            }
          });
    }

    @Override
    protected void visitIfNode(IfNode node) {
      activeEdgeStack.push(new ArrayList<>());
      visitChildren(node);
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

    @Override
    protected void visitIfCondNode(IfCondNode node) {
      HtmlMatcherIfConditionNode ifCondNode = new HtmlMatcherIfConditionNode(node);
      htmlMatcherGraph.addNode(ifCondNode);
      htmlMatcherGraph.saveCursor();
      ifCondNode.setActiveEdgeKind(EdgeKind.TRUE_EDGE);
      visitChildren(node);
      // The graph cursor points to the syntactically last HTML tag in the if block.
      if (htmlMatcherGraph.getNodeAtCursor().isPresent()) {
        HtmlMatcherGraphNode activeNode = htmlMatcherGraph.getNodeAtCursor().get();
        activeEdgeStack.peek().add(ActiveEdge.create(activeNode, activeNode.getActiveEdgeKind()));
      }
      ifCondNode.setActiveEdgeKind(EdgeKind.FALSE_EDGE);
      htmlMatcherGraph.restoreCursor();
    }

    @Override
    protected void visitIfElseNode(IfElseNode node) {
      // TODO(b/120430802): Implement this.
      throw new UnsupportedOperationException("elseif nodes are not yet implemented.");
    }

    @Override
    protected void visitSwitchNode(SwitchNode node) {
      // TODO(b/120430802): Implement this.
      throw new UnsupportedOperationException("switch nodes are not yet implemented.");
    }

    @Override
    protected void visitSwitchCaseNode(SwitchCaseNode node) {
      // TODO(b/120430802): Implement this.
      throw new UnsupportedOperationException("switch case nodes are not yet implemented.");
    }

    @Override
    protected void visitSwitchDefaultNode(SwitchDefaultNode node) {
      // TODO(b/120430802): Implement this.
      throw new UnsupportedOperationException("switch default nodes are not yet implemented.");
    }

    @Override
    protected void visitVeLogNode(VeLogNode node) {
      // {velog} must contain at least one child.
      if (node.numChildren() == 0) {
        errorReporter.report(node.getSourceLocation(), VELOG_NODE_EXACTLY_ONE_TAG);
        return;
      }
      HtmlOpenTagNode firstTag = node.getOpenTagNode();
      // The first child of {velog} must be an open tag.
      if (firstTag == null) {
        errorReporter.report(node.getChild(0).getSourceLocation(), VELOG_NODE_FIRST_CHILD_NOT_TAG);
        return;
      }
      // If the first child is self-closing or is a void tag, reports an error if we see anything
      // after it.
      if (node.numChildren() > 1
          && (firstTag.isSelfClosing() || firstTag.getTagName().isDefinitelyVoid())) {
        errorReporter.report(node.getChild(1).getSourceLocation(), VELOG_NODE_EXACTLY_ONE_TAG);
        return;
      }
      SoyNode lastChild = node.getChild(node.numChildren() - 1);
      HtmlCloseTagNode lastTag = null;
      if (node.numChildren() > 1) {
        lastTag = node.getCloseTagNode();
        // The last child (if it is not the same with the first child) must be a close tag.
        if (lastTag == null) {
          errorReporter.report(lastChild.getSourceLocation(), VELOG_NODE_LAST_CHILD_NOT_TAG);
          return;
        }
      }
      visitChildren(node);
      // After visiting all the children, we should have already built the map.
      // At this point, we check the map and verify that the first child is actually popped by the
      // last child. Otherwise, report an error.
      // TODO(b/120437376): Implement this logic.
    }

    @Override
    protected void visitTemplateNode(TemplateNode node) {
      Checkpoint checkpoint = errorReporter.checkpoint();
      visitChildren(node);
      // Return if we have already seen some errors. This case we won't generate a whole cascade
      // of errors for things in the remaining stack/queue.
      if (errorReporter.errorsSince(checkpoint)) {
        return;
      }

      if (node instanceof TemplateElementNode) {
        validateSoyElementHasOneRootTagNode(node);
      }
    }

    private void validateSoyElementHasOneRootTagNode(TemplateNode node) {
      class HtmlOrControlNode implements Predicate<SoyNode> {
        @Override
        public boolean apply(SoyNode node) {
          ImmutableList<Kind> validKinds =
              ImmutableList.of(
                  Kind.HTML_COMMENT_NODE,
                  Kind.LET_CONTENT_NODE,
                  Kind.LET_VALUE_NODE,
                  Kind.DEBUGGER_NODE);
          return !validKinds.contains(node.getKind())
              // Skip empty raw text nodes. They will be later be stripped out as part
              // of {@link CombineConsecutiveRawTextNodesPass}.
              && !(node instanceof RawTextNode && ((RawTextNode) node).isEmpty());
        }
      }

      class VeLogMatcher implements Predicate<SoyNode> {
        @Override
        public boolean apply(SoyNode node) {
          return node instanceof VeLogNode;
        }
      }

      VeLogNode maybeVelogNode = (VeLogNode) node.firstChildThatMatches(new VeLogMatcher());
      SoyNode firstNode;
      SoyNode lastNode;
      // Get the first and last nodes that we want to validate are HTML tags that match each other.
      // Skip e.g. comment, let, and debugger nodes.
      if (maybeVelogNode != null) {
        firstNode = maybeVelogNode.firstChildThatMatches(new HtmlOrControlNode());
        lastNode = maybeVelogNode.lastChildThatMatches(new HtmlOrControlNode());
      } else {
        firstNode = node.firstChildThatMatches(new HtmlOrControlNode());
        lastNode = node.lastChildThatMatches(new HtmlOrControlNode());
      }

      if (firstNode == null || lastNode == null) {
        errorReporter.report(node.getSourceLocation(), SOY_ELEMENT_EXACTLY_ONE_TAG);
        return;
      }

      // Get the nodes now as open and close tags, or null if they are not.
      HtmlOpenTagNode firstNodeAsOpenTag =
          (HtmlOpenTagNode) SoyTreeUtils.getNodeAsHtmlTagNode(firstNode, /* openTag= */ true);
      HtmlCloseTagNode lastNodeAsCloseTag =
          (HtmlCloseTagNode) SoyTreeUtils.getNodeAsHtmlTagNode(lastNode, /* openTag= */ false);
      boolean firstTagIsSelfClosing =
          firstNodeAsOpenTag != null
              && firstNodeAsOpenTag.isSelfClosing()
              && firstNodeAsOpenTag.getTagName().isDefinitelyVoid();
      if (firstTagIsSelfClosing) {
        if (!firstNode.equals(lastNode)) {
          // First node is self-closing, but there is another element after the self-closing node.
          errorReporter.report(lastNode.getSourceLocation(), SOY_ELEMENT_EXACTLY_ONE_TAG);
        }
      } else if (firstNodeAsOpenTag == null || lastNodeAsCloseTag == null) {
        // Either the first or last node is not an HTML tag.
        SoyNode nodeToReport = firstNodeAsOpenTag == null ? firstNode : lastNode;
        errorReporter.report(nodeToReport.getSourceLocation(), SOY_ELEMENT_EXACTLY_ONE_TAG);
        return;
      }

      // TODO(b/118396161): check for multiple root HTML tags.
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }
}
