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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.NamespaceDeclaration;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.StrictHtmlMode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateNode;
import java.util.ArrayDeque;

/**
 * A {@link CompilerFilePass} that checks strict html mode.
 *
 * <p>TODO(b/31771679): Add more validations as needed.
 */
final class StrictHtmlValidationPass extends CompilerFilePass {
  private static final SoyErrorKind STRICT_HTML_DISABLED =
      SoyErrorKind.of(
          "Strict HTML mode is disabled by default. In order to use stricthtml syntax in your Soy "
              + "template, explicitly pass --enabledExperimentalFeatures=stricthtml to compiler.");
  private static final SoyErrorKind STRICT_HTML_WITHOUT_AUTOESCAPE =
      SoyErrorKind.of("stricthtml=\"true\" must be used with autoescape=\"strict\".");
  private static final SoyErrorKind STRICT_HTML_WITH_NON_HTML =
      SoyErrorKind.of("stricthtml=\"true\" can only be used with kind=\"html\".");
  private static final SoyErrorKind UNEXPECTED_CLOSE_TAG =
      SoyErrorKind.of("Unexpected HTML close tag.");
  private static final SoyErrorKind OPEN_TAG_NOT_CLOSED =
      SoyErrorKind.of("Expected tag to be closed in this block.");
  private static final SoyErrorKind INVALID_SELF_CLOSING_TAG =
      SoyErrorKind.of("''{0}'' tag is not allowed to be self-closing.");
  private static final SoyErrorKind INVALID_CLOSE_TAG =
      SoyErrorKind.of("''{0}'' tag is a void element and must not specify a close tag.");

  private final boolean enabledStrictHtml;
  private final ErrorReporter errorReporter;

  StrictHtmlValidationPass(
      ImmutableList<String> experimentalFeatures, ErrorReporter errorReporter) {
    this.enabledStrictHtml = experimentalFeatures.contains("stricthtml");
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    // First check namespace declarations, and return if there is any violation.
    NamespaceDeclaration namespace = file.getNamespaceDeclaration();
    if (namespace.getStrictHtmlMode() != StrictHtmlMode.UNSET) {
      if (!enabledStrictHtml && namespace.getStrictHtmlMode() == StrictHtmlMode.YES) {
        errorReporter.report(namespace.getStrictHtmlModeLocation(), STRICT_HTML_DISABLED);
        return;
      }
      if (namespace.getDefaultAutoescapeMode() != AutoescapeMode.STRICT
          && namespace.getStrictHtmlMode() == StrictHtmlMode.YES) {
        errorReporter.report(namespace.getAutoescapeModeLocation(), STRICT_HTML_WITHOUT_AUTOESCAPE);
        return;
      }
    }
    // Then check each template node.
    for (TemplateNode node : file.getChildren()) {
      checkTemplateNode(node);
    }
  }

  private void checkTemplateNode(TemplateNode node) {
    if (!enabledStrictHtml && node.isStrictHtml()) {
      errorReporter.report(node.getSourceLocation(), STRICT_HTML_DISABLED);
      return;
    }
    AutoescapeMode autoescapeMode = node.getAutoescapeMode();
    if (autoescapeMode != AutoescapeMode.STRICT && node.isStrictHtml()) {
      errorReporter.report(node.getSourceLocation(), STRICT_HTML_WITHOUT_AUTOESCAPE);
      return;
    }
    // ContentKind is guaranteed to be non-null if AutoescapeMode is strict.
    ContentKind contentKind = node.getContentKind();
    if (contentKind != ContentKind.HTML && node.isStrictHtml()) {
      errorReporter.report(node.getSourceLocation(), STRICT_HTML_WITH_NON_HTML);
      return;
    }
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

    private final ErrorReporter errorReporter;

    HtmlTagVisitor(ErrorReporter errorReporter) {
      this.errorReporter = errorReporter;
    }

    @Override
    protected void visitHtmlOpenTagNode(HtmlOpenTagNode node) {
      // For static tag, check if it is a valid self-closing tag.
      if (node.getTagName().isStatic()) {
        // Report errors for non-void tags that are self-closing.
        // For void tags, we don't care if they are self-closing or not. But when we visit
        // a HtmlCloseTagNode we will throw an error if it is a void tag.
        if (!isDefinitelyVoid(node) && node.isSelfClosing()) {
          errorReporter.report(
              node.getSourceLocation(),
              INVALID_SELF_CLOSING_TAG,
              node.getTagName().getStaticTagName().getRawText());
          return;
        }
      }
      // Push the node into open tag stack.
      if (!node.isSelfClosing() && !isDefinitelyVoid(node)) {
        openTagStack.addFirst(new HtmlTagEntry(node.getTagName()));
      }
    }

    private void popOptionalTags(boolean popBranches) {
      while (!openTagStack.isEmpty()) {
        HtmlTagEntry entry = openTagStack.peekFirst();
        if (entry.isDefinitelyOptional()) {
          openTagStack.pollFirst();
          continue;
        } else if (!entry.hasTagName() && popBranches) {
          entry.getBranches().popOptionalTags();
          if (entry.getBranches().isEmpty()) {
            openTagStack.pollFirst();
            continue;
          }
        }
        return;
      }
    }

    @Override
    protected void visitHtmlCloseTagNode(HtmlCloseTagNode node) {
      // Report an error if this node is a void tag. Void tag should never be closed.
      if (isDefinitelyVoid(node)) {
        errorReporter.report(
            node.getSourceLocation(),
            INVALID_CLOSE_TAG,
            node.getTagName().getStaticTagName().getRawText());
        return;
      }
      TagName closeTag = node.getTagName();
      // If close tag is not optional, we pop all optional tags from the open tag stack.
      // Notably here we should NOT pop optional tags from the branches. Only pop optional tags in
      // the same block.
      if (!closeTag.isDefinitelyOptional()) {
        popOptionalTags(false);
      }
      // Check if we can find a matching open tag within the current block.
      if (!openTagStack.isEmpty() && openTagStack.peekFirst().hasTagName()) {
        TagName openTag = openTagStack.pollFirst().getTagName();
        HtmlTagEntry.matchOrError(openTag, closeTag, errorReporter);
      } else {
        // If we cannot find a matching open tag in current block, put the current tag into
        // closeTagQueue and compare everything after we visit the entire template node.
        closeTagQueue.addLast(new HtmlTagEntry(closeTag));
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
      if (!openTagBranches.isEmpty()) {
        openTagStack.addFirst(new HtmlTagEntry(openTagBranches));
        openTagBranches.clear();
      }
      if (!closeTagBranches.isEmpty()) {
        closeTagQueue.addLast(new HtmlTagEntry(closeTagBranches));
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
      currentCondition = Condition.createIfCondition(node.getExprUnion());
      visitBlockChildren(node, true);
      currentCondition = outerCondition.copy();
    }

    @Override
    protected void visitIfElseNode(IfElseNode node) {
      Condition outerCondition = currentCondition.copy();
      currentCondition = Condition.createIfCondition();
      visitBlockChildren(node, true);
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
      if (!openTagBranches.isEmpty()) {
        openTagStack.addFirst(new HtmlTagEntry(openTagBranches));
        openTagBranches.clear();
      }
      if (!closeTagBranches.isEmpty()) {
        closeTagQueue.addLast(new HtmlTagEntry(closeTagBranches));
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
      currentCondition =
          Condition.createSwitchCondition(new ExprUnion(parent.getExpr()), node.getAllExprUnions());
      visitBlockChildren(node, true);
      currentCondition = outerCondition.copy();
    }

    @Override
    protected void visitSwitchDefaultNode(SwitchDefaultNode node) {
      Condition outerCondition = currentCondition.copy();
      SwitchNode parent = (SwitchNode) node.getParent();
      currentCondition =
          Condition.createSwitchCondition(
              new ExprUnion(parent.getExpr()), ImmutableList.<ExprUnion>of());
      visitBlockChildren(node, true);
      currentCondition = outerCondition.copy();
    }

    private void removeEmptyEntries() {
      if (!openTagStack.isEmpty()) {
        HtmlTagEntry entry = openTagStack.peekFirst();
        while (entry != null && !entry.hasTagName() && entry.getBranches().isEmpty()) {
          entry = openTagStack.pollFirst();
        }
      }
      if (!closeTagQueue.isEmpty()) {
        HtmlTagEntry entry = closeTagQueue.peekFirst();
        while (entry != null && !entry.hasTagName() && entry.getBranches().isEmpty()) {
          entry = closeTagQueue.pollFirst();
        }
      }
    }

    /**
     * A help method that checks openTagStack and closeTagQueue. It recursively compare the top of
     * the stack and the front of the queue. Since it is recursive, we only report the "first" error
     * to avoid an explosion of errors (whenever we see an error we will return).
     */
    private void matchTagsInDeques() {
      while (!openTagStack.isEmpty() && !closeTagQueue.isEmpty()) {
        HtmlTagEntry openTag = openTagStack.peekFirst();
        HtmlTagEntry closeTag = closeTagQueue.peekFirst();
        // For an optional close tag, try to match it with the top of stack.
        // If matches, remove the close tag from the queue and continue.
        // Otherwise, report an error.
        if (closeTag != null && closeTag.isDefinitelyOptional()) {
          if (openTag != null
              && openTag.isDefinitelyOptional()
              && openTag.getTagName().equals(closeTag.getTagName())) {
            openTagStack.pollFirst();
            closeTagQueue.pollFirst();
            continue;
          } else {
            errorReporter.report(closeTag.getSourceLocation(), UNEXPECTED_CLOSE_TAG);
            return;
          }
        } else if (closeTag.hasTagName()) {
          // Pop optional tags from open stack if the current close tag is not an optional tag.
          popOptionalTags(true);
        }
        openTag = openTagStack.pollFirst();
        closeTag = closeTagQueue.pollFirst();
        if (!HtmlTagEntry.matchOrError(openTag, closeTag, errorReporter)) {
          return;
        }
      }
      removeEmptyEntries();
      // checks if both deques are empty at the end. If any of them is not empty,
      // report an error accordingly.
      if (openTagStack.isEmpty() && closeTagQueue.isEmpty()) {
        return;
      }
      if (openTagStack.isEmpty()) {
        HtmlTagEntry entry = closeTagQueue.pollFirst();
        errorReporter.report(entry.getSourceLocation(), UNEXPECTED_CLOSE_TAG);
      } else {
        HtmlTagEntry entry = openTagStack.pollFirst();
        errorReporter.report(entry.getSourceLocation(), OPEN_TAG_NOT_CLOSED);
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
      matchTagsInDeques();
    }

    private static boolean isDefinitelyVoid(HtmlOpenTagNode node) {
      return node.getTagName().isDefinitelyVoid();
    }

    private static boolean isDefinitelyVoid(HtmlCloseTagNode node) {
      return node.getTagName().isDefinitelyVoid();
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode) {
        if (node instanceof BlockNode) {
          visitBlockChildren((BlockNode) node, false);
        } else {
          visitChildren((ParentSoyNode<?>) node);
        }
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
      visitChildren(node);
      // After we visit all children, we check if deques are empty or not.
      if (inControlBlock) {
        // If we are in a control block, we add non-empty deques to the branches.
        if (!openTagStack.isEmpty()) {
          openTagBranches.add(currentCondition, openTagStack);
        }
        if (!closeTagQueue.isEmpty()) {
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
    }
  }
}
