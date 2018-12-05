/*
 * Copyright 2016 Google Inc.
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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.ForIfemptyNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPluralCaseNode;
import com.google.template.soy.soytree.MsgPluralDefaultNode;
import com.google.template.soy.soytree.MsgSelectCaseNode;
import com.google.template.soy.soytree.MsgSelectDefaultNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

/** A {@link CompilerFilePass} that checks strict html mode. See go/soy-html for usages. */
final class StrictHtmlValidationPass extends CompilerFilePass {
  private static final SoyErrorKind INVALID_SELF_CLOSING_TAG =
      SoyErrorKind.of("''{0}'' tag is not allowed to be self-closing.");
  private static final SoyErrorKind INVALID_CLOSE_TAG =
      SoyErrorKind.of("''{0}'' tag is a void element and must not specify a close tag.");
  private static final SoyErrorKind SWITCH_HTML_MODE_IN_BLOCK =
      SoyErrorKind.of("Foreign elements (svg) must be opened and closed within the same block.");
  private static final SoyErrorKind NESTED_SVG = SoyErrorKind.of("Nested SVG tags are disallowed.");
  private static final SoyErrorKind UNEXPECTED_CLOSE_TAG =
      SoyErrorKind.of("Unexpected HTML close tag.");
  private static final SoyErrorKind UNEXPECTED_CLOSE_TAG_IN_CONTROL =
      SoyErrorKind.of(
          "Unexpected HTML close tag. Within an if or switch block, "
              + "all branches must end with unmatched open tags or unmatched close tags.");

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

  StrictHtmlValidationPass(ErrorReporter errorReporter) {
    this.errorReporter = checkNotNull(errorReporter);
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode node : file.getChildren()) {
      checkTemplateNode(node);
    }
  }

  private void checkTemplateNode(TemplateNode node) {
    AutoescapeMode autoescapeMode = node.getAutoescapeMode();
    // The SoyConformance pass runs before this pass, which guarantees that any strict HTML node has
    // STRICT autoescaping mode. Note that you are allowed to set STRICT autoescaping mode on
    // a non-strict-HTML node.
    checkState(
        !(autoescapeMode != AutoescapeMode.STRICT && node.isStrictHtml()),
        "Strict HTML template without strict autoescaping.");
    // ContentKind is guaranteed to be non-null if AutoescapeMode is strict.
    SanitizedContentKind contentKind = node.getContentKind();
    // The SoyConformance pass runs before this pass, which guarantees that any strict HTML node has
    // STRICT HTML sanitize mode. Note that you are allowed to set STRICT sanitize mode on
    // a non-strict-HTML node.
    checkState(
        !(contentKind != SanitizedContentKind.HTML && node.isStrictHtml()),
        "Strict HTML in a non-HTML node.");
    if (node.isStrictHtml()) {
      new HtmlTagVisitor(errorReporter).exec(node);
    }
  }

  private static final class HtmlTagVisitor extends AbstractSoyNodeVisitor<Void> {

    /** Current condition that will be updated when we visit a control flow node. */
    private Condition currentCondition = Condition.getEmptyCondition();

    /**
     * A {@code ConditionalBranches} that stores all open tags in an {@code IfNode}. The branch will
     * be pushed to openTagStack once we visit all children of an {@code IfNode}.
     */
    private final ConditionalBranches openTagBranches = new ConditionalBranches();

    /**
     * A {@code ConditionalBranches} that stores all close tags in an {@code IfNode}. The branch
     * will be pushed to closeTagQueue once we visit all children of an {@code IfNode}.
     */
    private final ConditionalBranches closeTagBranches = new ConditionalBranches();

    /**
     * A stack of open tags. After we visit all children of a {@code BlockNode}, the stack will be
     * added to a {@code ConditionalBranches} (based on currentConditions).
     */
    private final ArrayDeque<HtmlTagEntry> openTagStack = new ArrayDeque<>();

    /**
     * A queue of close tags. After we visit all children of a {@code BlockNode}, the queue will be
     * added to a {@code ConditionalBranches} (based on currentConditions).
     */
    private final ArrayDeque<HtmlTagEntry> closeTagQueue = new ArrayDeque<>();

    /**
     * A boolean indicates that the current snippet is in a foreign content (in particular, svg). If
     * this is true, we treat later tags as xml tags (that can be either self-closing or explicitly
     * closed) until we leave foreign content.
     */
    private boolean inForeignContent = false;

    /**
     * A map that records the tag matching process. The keys are close tags, and the values are the
     * open tags that actually match the corresponding close tags.
     */
    // TODO(user): Change this to a multimap and use it for improving error messages.
    private final Map<HtmlCloseTagNode, HtmlOpenTagNode> tagMatches = new IdentityHashMap<>();

    private SourceLocation foreignContentStartLocation = SourceLocation.UNKNOWN;
    private SourceLocation foreignContentEndLocation = SourceLocation.UNKNOWN;

    private final ErrorReporter errorReporter;

    HtmlTagVisitor(ErrorReporter errorReporter) {
      this.errorReporter = errorReporter;
    }

    @Override
    protected void visitHtmlOpenTagNode(HtmlOpenTagNode node) {
      TagName openTag = node.getTagName();
      HtmlTagEntry entry = HtmlTagEntry.builder().setTagNode(node).build();
      // Switch to xml mode if we reach a <svg> tag.
      if (openTag.isForeignContent()) {
        if (inForeignContent) {
          errorReporter.report(node.getSourceLocation(), NESTED_SVG);
        }
        inForeignContent = true;
        foreignContentStartLocation = node.getSourceLocation();
      }
      // For static tag, check if it is a valid self-closing tag.
      if (openTag.isStatic()) {
        // Report errors for non-void tags that are self-closing.
        // For void tags, we don't care if they are self-closing or not. But when we visit
        // a HtmlCloseTagNode we will throw an error if it is a void tag.
        // Ignore this check if we are currently in a foreign content (svg).
        if (!inForeignContent && !openTag.isDefinitelyVoid() && node.isSelfClosing()) {
          errorReporter.report(
              node.getSourceLocation(), INVALID_SELF_CLOSING_TAG, openTag.getStaticTagName());
          return;
        }
      }
      // Push the node into open tag stack.
      if (!node.isSelfClosing() && !openTag.isDefinitelyVoid()) {
        openTagStack.addFirst(entry);
      }
    }

    @Override
    protected void visitHtmlCloseTagNode(HtmlCloseTagNode node) {
      TagName closeTag = node.getTagName();
      HtmlTagEntry entry = HtmlTagEntry.builder().setTagNode(node).build();
      // Report an error if this node is a void tag. Void tag should never be closed.
      if (closeTag.isDefinitelyVoid()) {
        errorReporter.report(
            closeTag.getTagLocation(), INVALID_CLOSE_TAG, closeTag.getStaticTagName());
        return;
      }
      // Switch back to html mode if we leave a svg tag.
      if (closeTag.isForeignContent()) {
        foreignContentEndLocation = node.getSourceLocation();
        inForeignContent = false;
      }
      // If we cannot find a matching open tag in current block, put the current tag into
      // closeTagQueue and compare everything after we visit the entire template node.
      if (!HtmlTagEntry.tryMatchCloseTag(openTagStack, entry, tagMatches, errorReporter)) {
        if (isInControlBlock(node)) {
          closeTagQueue.addLast(entry);
        } else {
          errorReporter.report(closeTag.getTagLocation(), UNEXPECTED_CLOSE_TAG);
        }
      }
    }

    /**
     * When we visit IfNode, we do the following steps:
     *
     * <ul>
     *   <li>Create new {@code ConditionalBranches} (and save old branches).
     *   <li>For each of its children, update the current conditions.
     *   <li>After visiting all children, check if branches are empty. If they are not empty (i.e.,
     *       we find some HTML tags within this {@code IfNode}), push the branches into
     *       corresponding stack or queue.
     *   <li>Restore the conditions and branches.
     * </ul>
     */
    @Override
    protected void visitIfNode(IfNode node) {
      ConditionalBranches outerOpenTagBranches = new ConditionalBranches(openTagBranches);
      ConditionalBranches outerCloseTagBranches = new ConditionalBranches(closeTagBranches);
      openTagBranches.clear();
      closeTagBranches.clear();
      visitChildren(node);
      if (!openTagBranches.isEmpty() && !closeTagBranches.isEmpty()) {
        errorReporter.report(closeTagBranches.getSourceLocation(), UNEXPECTED_CLOSE_TAG_IN_CONTROL);
        openTagBranches.clear();
        closeTagBranches.clear();
      }
      if (!openTagBranches.isEmpty()) {
        openTagStack.addFirst(HtmlTagEntry.builder().setBranches(openTagBranches).build());
        openTagBranches.clear();
      }
      if (!closeTagBranches.isEmpty()) {
        closeTagQueue.addLast(HtmlTagEntry.builder().setBranches(closeTagBranches).build());
        closeTagBranches.clear();
      }
      // At this point we should try to match openTagStack and closeTagQueue and remove anything
      // that matches.
      HtmlTagEntry.tryMatchOrError(openTagStack, closeTagQueue, errorReporter);
      openTagBranches.addAll(outerOpenTagBranches);
      closeTagBranches.addAll(outerCloseTagBranches);
    }

    @Override
    protected void visitIfCondNode(IfCondNode node) {
      Condition outerCondition = currentCondition.copy();
      currentCondition = Condition.createIfCondition(node.getExpr());
      visitBlockChildren(node, /* inControlBlock= */ true);
      currentCondition = outerCondition.copy();
    }

    @Override
    protected void visitIfElseNode(IfElseNode node) {
      Condition outerCondition = currentCondition.copy();
      currentCondition = Condition.createIfCondition();
      visitBlockChildren(node, /* inControlBlock= */ true);
      currentCondition = outerCondition.copy();
    }

    /**
     * {@code SwitchNode} is very similar with {@code IfNode}. The major difference is the way to
     * generate conditions.
     */
    @Override
    protected void visitSwitchNode(SwitchNode node) {
      ConditionalBranches outerOpenTagBranches = new ConditionalBranches(openTagBranches);
      ConditionalBranches outerCloseTagBranches = new ConditionalBranches(closeTagBranches);
      openTagBranches.clear();
      closeTagBranches.clear();
      visitChildren(node);
      if (!openTagBranches.isEmpty() && !closeTagBranches.isEmpty()) {
        errorReporter.report(closeTagBranches.getSourceLocation(), UNEXPECTED_CLOSE_TAG_IN_CONTROL);
        openTagBranches.clear();
        closeTagBranches.clear();
      }
      if (!openTagBranches.isEmpty()) {
        openTagStack.addFirst(HtmlTagEntry.builder().setBranches(openTagBranches).build());
        openTagBranches.clear();
      }
      if (!closeTagBranches.isEmpty()) {
        closeTagQueue.addLast(HtmlTagEntry.builder().setBranches(closeTagBranches).build());
        closeTagBranches.clear();
      }
      // At this point we should try to match openTagStack and closeTagQueue and remove anything
      // that matches.
      HtmlTagEntry.tryMatchOrError(openTagStack, closeTagQueue, errorReporter);
      openTagBranches.addAll(outerOpenTagBranches);
      closeTagBranches.addAll(outerCloseTagBranches);
    }

    @Override
    protected void visitSwitchCaseNode(SwitchCaseNode node) {
      Condition outerCondition = currentCondition.copy();
      SwitchNode parent = (SwitchNode) node.getParent();
      currentCondition = Condition.createSwitchCondition(parent.getExpr(), node.getExprList());
      visitBlockChildren(node, /* inControlBlock= */ true);
      currentCondition = outerCondition.copy();
    }

    @Override
    protected void visitSwitchDefaultNode(SwitchDefaultNode node) {
      Condition outerCondition = currentCondition.copy();
      SwitchNode parent = (SwitchNode) node.getParent();
      currentCondition =
          Condition.createSwitchCondition(parent.getExpr(), ImmutableList.<ExprRootNode>of());
      visitBlockChildren(node, /* inControlBlock= */ true);
      currentCondition = outerCondition.copy();
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
      visitBlockChildren(node, /* inControlBlock= */ false);
      // After visiting all the children, we should have already built the map.
      // At this point, we check the map and verify that the first child is actually popped by the
      // last child. Otherwise, report an error.
      if (lastTag != null) {
        // If the map does not contain the last tag, other part of this compiler pass should enforce
        // that there is an error thrown. Don't report another error here since it is a duplicate.
        // This check make sures that there is exactly one top-level element -- the last tag must
        // close the first tag within {velog} command.
        if (tagMatches.get(lastTag) != null && !tagMatches.get(lastTag).equals(firstTag)) {
          errorReporter.report(
              tagMatches.get(lastTag).getSourceLocation(), VELOG_NODE_EXACTLY_ONE_TAG);
        }
      }
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

      // Match the tags in the deques.
      HtmlTagEntry.matchOrError(openTagStack, closeTagQueue, errorReporter);

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
                  Kind.VE_LOG_NODE,
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
      SoyNode firstNode = node.firstChildThatMatches(new HtmlOrControlNode());
      SoyNode lastNode = node.lastChildThatMatches(new HtmlOrControlNode());
      if (maybeVelogNode != null && firstNode != null && lastNode != null) {
        errorReporter.report(node.getSourceLocation(), SOY_ELEMENT_EXACTLY_ONE_TAG);
        return;
      }
      // Get the first and last nodes that we want to validate are HTML tags that match each other.
      // Skip e.g. comment, let, and debugger nodes.
      if (maybeVelogNode != null) {
        firstNode = maybeVelogNode.firstChildThatMatches(new HtmlOrControlNode());
        lastNode = maybeVelogNode.lastChildThatMatches(new HtmlOrControlNode());
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

      if (tagMatches.get(lastNodeAsCloseTag) != null
          && !tagMatches.get(lastNodeAsCloseTag).equals(firstNodeAsOpenTag)) {
        // The last close tag does not match the first open tag, i.e. there are multiple root
        // HTML tag elements.
        errorReporter.report(
            tagMatches.get(lastNodeAsCloseTag).getSourceLocation(), SOY_ELEMENT_EXACTLY_ONE_TAG);
      }
    }

    @Override
    protected void visitMsgNode(MsgNode node) {
      visitBlockChildren(node, /* inControlBlock= */ false);
    }

    @Override
    protected void visitMsgPluralCaseNode(MsgPluralCaseNode node) {
      visitBlockChildren(node, /* inControlBlock= */ false);
    }

    @Override
    protected void visitMsgPluralDefaultNode(MsgPluralDefaultNode node) {
      visitBlockChildren(node, /* inControlBlock= */ false);
    }

    @Override
    protected void visitMsgSelectCaseNode(MsgSelectCaseNode node) {
      visitBlockChildren(node, /* inControlBlock= */ false);
    }

    @Override
    protected void visitMsgSelectDefaultNode(MsgSelectDefaultNode node) {
      visitBlockChildren(node, /* inControlBlock= */ false);
    }

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      visitBlockChildren(node, /* inControlBlock= */ false);
    }

    // TODO(user): We could do something special for ForIfemptyNode.
    @Override
    protected void visitForIfemptyNode(ForIfemptyNode node) {
      visitBlockChildren(node, /* inControlBlock= */ false);
    }

    @Override
    protected void visitForNonemptyNode(ForNonemptyNode node) {
      visitBlockChildren(node, /* inControlBlock= */ false);
    }

    @Override
    protected void visitCallParamContentNode(CallParamContentNode node) {
      visitBlockChildren(node, /* inControlBlock= */ false);
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }

    private void visitBlockChildren(BlockNode node, boolean inControlBlock) {
      // Whenever we visit a {@code BlockNode}, we create new deques for this block. Save the
      // contents that are not introduced by the current block.
      ArrayDeque<HtmlTagEntry> outerOpenTagStack = new ArrayDeque<>();
      ArrayDeque<HtmlTagEntry> outerCloseTagQueue = new ArrayDeque<>();
      outerOpenTagStack.addAll(openTagStack);
      outerCloseTagQueue.addAll(closeTagQueue);
      openTagStack.clear();
      closeTagQueue.clear();
      boolean inForeignContentBeforeBlock = inForeignContent;
      visitChildren(node);
      // After we visit all children, we check if deques are empty or not.
      if (inControlBlock) {
        boolean matched = HtmlTagEntry.tryMatchOrError(openTagStack, closeTagQueue, errorReporter);
        checkState(
            !(matched && !openTagStack.isEmpty() && !closeTagQueue.isEmpty()),
            "This should not happen. At least one of the stack/queue should be empty.");
        // If we are in a control block, we add non-empty deques to the branches.
        if (!openTagStack.isEmpty() && closeTagQueue.isEmpty()) {
          openTagBranches.add(currentCondition, openTagStack);
        }
        if (openTagStack.isEmpty() && !closeTagQueue.isEmpty()) {
          closeTagBranches.add(currentCondition, closeTagQueue);
        }
      } else {
        // If we are not in a control block, we try to match deques and report an error if we find
        // nodes that do not match.
        HtmlTagEntry.matchOrError(openTagStack, closeTagQueue, errorReporter);
      }
      // No matter what happened in this block, clear everything and continue.
      openTagStack.clear();
      closeTagQueue.clear();
      // Restore the deques.
      openTagStack.addAll(outerOpenTagStack);
      closeTagQueue.addAll(outerCloseTagQueue);
      // If inForeignContent has been changed after visiting a block, it means there is a svg tag
      // that has not been closed.
      if (inForeignContent != inForeignContentBeforeBlock) {
        errorReporter.report(
            inForeignContent ? foreignContentStartLocation : foreignContentEndLocation,
            SWITCH_HTML_MODE_IN_BLOCK);
      }
      // Switch back to the original html mode.
      inForeignContent = inForeignContentBeforeBlock;
    }

    /** Recursively check if a soy node is under a control block. */
    private static boolean isInControlBlock(SoyNode node) {
      SoyNode parent = node.getParent();
      if (parent instanceof TemplateNode
          || parent instanceof SoyFileNode
          || parent instanceof SoyFileSetNode) {
        return false;
      }
      if (parent instanceof IfCondNode
          || parent instanceof IfElseNode
          || parent instanceof SwitchCaseNode
          || parent instanceof SwitchDefaultNode) {
        return true;
      }
      return isInControlBlock(parent);
    }
  }
}
