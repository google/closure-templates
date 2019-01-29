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

import com.google.common.base.Equivalence;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode.EdgeKind;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.HtmlTagNode.TagExistence;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.TagName;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Pass for checking the balance of open tag nodes with possible close tags.
 * Because Soy contains control flow, it is possible for a open tag node to possible map to
 * different close tags. For example, consider the following:
 *
 * <pre>
 * @code {
 *   <div>
 *   {if $foo}</div><div>{/if}
 *   </div>
 * }
 * </pre>
 *
 * Because of this, we need to consider all possible paths statically inferable from the template
 * at hand (calls are not considered). In this example, we need to check if $foo==true
 * and $foo == false generate correct DOM. This visitor verifies that all possible paths are
 * legitimate and then annotates open tags and close tags with their possible pairs.
 */
public final class HtmlTagMatchingPass {
  private static final SoyErrorKind INVALID_CLOSE_TAG =
      SoyErrorKind.of("''{0}'' tag is a void element and must not specify a close tag.");
  private static final SoyErrorKind INVALID_SELF_CLOSING_TAG =
      SoyErrorKind.of("''{0}'' tag is not allowed to be self-closing.");
  private static final String UNEXPECTED_CLOSE_TAG = "Unexpected HTML close tag.";

  private static final String UNEXPECTED_CLOSE_TAG_KNOWN =
      "Unexpected HTML close tag. Expected to match the ''<{0}>'' at {1}.";

  private static final String NESTED_SVG = "Nested SVG tags are disallowed.";

  private static final String BLOCK_QUALIFIER = " Tags within a %s must be internally balanced.";

  private static final String UNEXPECTED_OPEN_TAG_ALWAYS =
      "This HTML open tag is never matched with a close tag.";
  private static final String UNEXPECTED_OPEN_TAG_SOMETIMES =
      "This HTML open tag does not consistently match with a close tag.";

  private static final Optional<HtmlTagNode> INVALID_NODE = Optional.absent();

  private final ErrorReporter errorReporter;
  /** Required in order to generate synthetic nodes. */
  private final IdGenerator idGenerator;
  /**
   * This pass runs itself recursively inside if condition. When doing so, it passes itself this
   * variable so that when there are errors, no annotations occur
   */
  private final boolean inCondition;

  /**
   * This pass runs itself recursively on block nodes. If inside foreign content, various rules
   * apply.
   */
  private final boolean inForeignContent;

  /** Used for error messages to detail what context an error is in. */
  @Nullable private final String parentBlockType;

  /**
   * Record of nodes and their related tag nodes. This is used to "save" a record of actions to be
   * taken. At the end of the graph traversal, if there are no errors, "commit" the changes.
   */
  HashMultimap<HtmlTagNode, Optional<HtmlTagNode>> annotationMap = HashMultimap.create();

  public HtmlTagMatchingPass(
      ErrorReporter errorReporter,
      IdGenerator idGenerator,
      boolean inCondition,
      boolean inForeignContent,
      String parentBlockType) {
    this.inForeignContent = inForeignContent;
    this.parentBlockType = parentBlockType;
    this.errorReporter = errorReporter;
    this.idGenerator = idGenerator;
    this.inCondition = inCondition;
  }

  private SoyErrorKind makeSoyErrorKind(String soyError) {
    return SoyErrorKind.of(
        soyError
            + (parentBlockType != null ? String.format(BLOCK_QUALIFIER, parentBlockType) : ""));
  }

  /**
   * Represents the state of the HTML graph traversal. Each tag has context on whether it is in
   * foreign content and a reference to the previous node. This allows pushing/popping to create a
   * traversal of the HTML Matcher Graph.
   */
  class HtmlStack {
    final HtmlOpenTagNode tagNode;
    final boolean inForeignContent;
    final HtmlStack prev;

    HtmlStack(HtmlOpenTagNode tagNode, boolean inForeignContent, HtmlStack prev) {
      this.tagNode = tagNode;
      this.inForeignContent = inForeignContent;
      this.prev = prev;
    }

    HtmlStack push(HtmlOpenTagNode tagNode, boolean inForeignContent) {
      return new HtmlStack(tagNode, inForeignContent, this);
    }

    HtmlStack pop() {
      return prev;
    }

    boolean isEmpty() {
      return tagNode == null;
    }

    @Override
    public String toString() {
      if (prev == null) {
        return "[START]";
      }
      return prev + "->" + tagNode.getTagName();
    }
  }

  /**
   * Runs the HtmlTagMatchingPass.
   *
   * <p>The pass does the following:
   *
   * <ol>
   *   <li>Traverse the HTML matcher graph and create a set of open -> close tag matches
   *   <li>Rebalance the code paths, injecting synthetic close tags to balance optional open tags.
   *       Optional open tags are defined here: <a
   *       href="https://www.w3.org/TR/html5/syntax.html#optional-tags">https://www.w3.org/TR/html5/syntax.html#optional-tags</a>.
   *       <p>&ndash; <em>Note:</em> Block nodes (such as {@code {msg} or {let}}) are discretely
   *       rebalanced, annotated and error-checked. By definition, a block node must internally
   *       balance HTML tags.
   *   <li>Check for tag mismatch errors in the fully balanced code paths.
   *       <p>&ndash; Afterwards, annotate the open with the list of possible close tags, and the
   *       close tags with the list of possible open tags.
   * </ol>
   */
  public void run(HtmlMatcherGraph htmlMatcherGraph) {
    if (!htmlMatcherGraph.getRootNode().isPresent()) {
      // Empty graph.
      return;
    }
    visit(htmlMatcherGraph.getRootNode().get());
    for (HtmlTagNode tag : annotationMap.keySet()) {
      if (tag instanceof HtmlOpenTagNode) {
        HtmlOpenTagNode openTag = (HtmlOpenTagNode) tag;
        if (annotationMap.containsEntry(openTag, INVALID_NODE)) {
          if (annotationMap.get(openTag).size() == 1) {
            errorReporter.report(
                openTag.getSourceLocation(), makeSoyErrorKind(UNEXPECTED_OPEN_TAG_ALWAYS));
          } else {
            errorReporter.report(
                openTag.getSourceLocation(), makeSoyErrorKind(UNEXPECTED_OPEN_TAG_SOMETIMES));
          }
        }
      }
    }
    // Do not annotate in inCondition because if there are errors, the nodes will be annotated
    // in the parent pass. The reason this happens is when the condition node is not balanced
    // internally but balanced globally.
    if (!errorReporter.getErrors().isEmpty() && inCondition) {
      return;
    }
    for (HtmlTagNode openTag : annotationMap.keySet()) {
      for (Optional<HtmlTagNode> closeTag : annotationMap.get(openTag)) {
        if (closeTag.isPresent()) {
          openTag.addTagPair(closeTag.get());
          closeTag.get().addTagPair(openTag);
        }
      }
    }
  }

  /**
   * Rebalances HTML tags when necessary.
   *
   * <p>If an optional tag is encountered, inject a synthetic close tag right before the tag that
   * performs the implicit close. For example, this HTML:
   *
   * <pre>{@code
   * <ul>
   *   <li>List 1
   *   <li>List 2
   * </ul>
   * }</pre>
   *
   * <p>Will be rewritten to look like this logical HTML (note the addition of the {@code </li>}
   * tags):
   *
   * <pre>{@code
   * <ul>
   *   <li>List 1</li>
   *   <li>List 2</li>
   * </ul>
   * }</pre>
   */
  private void injectCloseTag(
      HtmlOpenTagNode optionalOpenTag, HtmlTagNode destinationTag, IdGenerator idGenerator) {
    StandaloneNode openTagCopy = optionalOpenTag.getTagName().getNode().copy(new CopyState());
    HtmlCloseTagNode syntheticClose =
        new HtmlCloseTagNode(
            idGenerator.genId(),
            openTagCopy,
            optionalOpenTag.getSourceLocation(),
            TagExistence.SYNTHETIC);
    // If destination is null, then insert at the end of the template.
    if (destinationTag == null) {
      int i = optionalOpenTag.getParent().getChildren().size();
      optionalOpenTag.getParent().addChild(i, syntheticClose);
    } else {
      // This inserts the synthetic close tag right before the open tag.
      ParentSoyNode<StandaloneNode> openTagParent = destinationTag.getParent();
      int i = openTagParent.getChildIndex(destinationTag);
      openTagParent.addChild(i, syntheticClose);
    }

    annotationMap.put(optionalOpenTag, Optional.of(syntheticClose));
    annotationMap.put(syntheticClose, Optional.of(optionalOpenTag));
  }

  /** Perform tag matching/error reporting for invalid HTML. */
  private void visit(
      HtmlMatcherTagNode tagNode,
      Map<Equivalence.Wrapper<ExprNode>, Boolean> exprValueMap,
      HtmlStack stack) {
    HtmlTagNode tag = (HtmlTagNode) tagNode.getSoyNode().get();
    TagName openTagName = tag.getTagName();
    HtmlStack prev = stack;
    switch (tagNode.getTagKind()) {
      case VOID_TAG:
        HtmlOpenTagNode voidTag = (HtmlOpenTagNode) tag;
        // Report errors for non-void tags that are self-closing.
        // For void tags, we don't care if they are self-closing or not. But when we visit
        // a HtmlCloseTagNode we will throw an error if it is a void tag.
        // Ignore this check if we are currently in a foreign content (svg).
        if (!stack.inForeignContent
            && !openTagName.isDefinitelyVoid()
            && voidTag.isSelfClosing()
            && openTagName.isStatic()) {
          errorReporter.report(
              voidTag.getSourceLocation(),
              INVALID_SELF_CLOSING_TAG,
              openTagName.getStaticTagName());
        }
        break;
      case OPEN_TAG:
        HtmlOpenTagNode openTag = (HtmlOpenTagNode) tag;
        if (openTagName.isForeignContent() && stack.inForeignContent) {
          errorReporter.report(openTag.getSourceLocation(), makeSoyErrorKind(NESTED_SVG));
        }
        // In a case where an open tag can close another open tag (ie <p><p> or <li><li>),
        // check if this is possible by peeking the stack and inject a tag before the open tag.
        if (!prev.isEmpty()) {
          HtmlOpenTagNode optionalTag = stack.tagNode;
          if (optionalTag.getTagName().isDefinitelyOptional()) {
            if (TagName.checkOpenTagClosesOptional(
                openTag.getTagName(), optionalTag.getTagName())) {
              injectCloseTag(optionalTag, openTag, idGenerator);
              prev = prev.pop();
            }
          }
        }
        prev =
            prev.push(openTag, stack.inForeignContent || openTag.getTagName().isForeignContent());
        break;
      case CLOSE_TAG:
        HtmlCloseTagNode closeTag = (HtmlCloseTagNode) tag;
        // Report an error if this node is a void tag. Void tag should never be closed.
        if (closeTag.getTagName().isDefinitelyVoid()) {
          errorReporter.report(
              closeTag.getTagName().getTagLocation(),
              INVALID_CLOSE_TAG,
              closeTag.getTagName().getStaticTagName());
          break;
        }
        // This is for cases similar to {block}</p>{/block}
        if (stack.isEmpty()) {
          errorReporter.report(
              closeTag.getSourceLocation(), makeSoyErrorKind(UNEXPECTED_CLOSE_TAG));
          break;
        }
        prev = stack;
        while (!prev.isEmpty()) {
          HtmlOpenTagNode nextOpenTag = prev.tagNode;
          if (nextOpenTag.getTagName().equals(closeTag.getTagName())) {
            annotationMap.put(nextOpenTag, Optional.of(closeTag));
            annotationMap.put(closeTag, Optional.of(nextOpenTag));
            prev = prev.pop();
            break;
          } else if (nextOpenTag.getTagName().isDefinitelyOptional()
              && TagName.checkCloseTagClosesOptional(
                  closeTag.getTagName(), nextOpenTag.getTagName())) {
            // Close tag closes an optional open tag (e.g. <li> ... </ul>). Inject a synthetic
            // close tag that matches `openTag`.
            injectCloseTag(nextOpenTag, closeTag, idGenerator);
            prev = prev.pop();
          } else {
            annotationMap.put(nextOpenTag, INVALID_NODE);
            errorReporter.report(
                closeTag.getSourceLocation(),
                makeSoyErrorKind(UNEXPECTED_CLOSE_TAG_KNOWN),
                nextOpenTag.getTagName(),
                nextOpenTag.getSourceLocation());
            prev = prev.pop();
          }
        }
        break;
    }
    Optional<HtmlMatcherGraphNode> nextNode = tagNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE);
    if (nextNode.isPresent()) {
      visit(nextNode.get(), exprValueMap, prev);
    } else {
      checkUnusedTags(prev);
    }
  }

  /**
   * Blocks must be internally balanced, but require knowing if they are in foreign content or not.
   * Recursively run the tag matcher and throw away the result.
   */
  private void visit(
      HtmlMatcherBlockNode blockNode,
      Map<Equivalence.Wrapper<ExprNode>, Boolean> exprValueMap,
      HtmlStack stack) {
    if (blockNode.getGraph().getRootNode().isPresent()) {
      new HtmlTagMatchingPass(
              errorReporter,
              idGenerator,
              false,
              stack.inForeignContent,
              blockNode.getParentBlockType())
          .run(blockNode.getGraph());
    }
    Optional<HtmlMatcherGraphNode> nextNode = blockNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE);
    if (nextNode.isPresent()) {
      visit(nextNode.get(), exprValueMap, stack);
    } else {
      checkUnusedTags(stack);
    }
  }

  /**
   * For a conditional node, we have up to two different paths. In this case, traverse both.
   * However, if we have already visited a branch and concluded that it is internally balanced (in
   * foreign content or not), then don't revisit the branch.
   */
  private void visit(
      HtmlMatcherConditionNode condNode,
      Map<Equivalence.Wrapper<ExprNode>, Boolean> exprValueMap,
      HtmlStack stack) {
    Equivalence.Wrapper<ExprNode> condition = ExprEquivalence.get().wrap(condNode.getExpression());
    // In some cases we may encounter a condition we have already made a decision for. Consider
    // this case:
    // <pre>@code {
    //    {if $foo}<div>{/if}
    //    {if $foo}</div>{/if}
    // }</pre>
    // In this case, it is unnecessary once we have decided that $foo is TRUE to traverse the
    // branch where $foo is FALSE. We save the original state of the value and use it below
    // to decide if we should take a branch.
    Boolean originalState = exprValueMap.getOrDefault(condition, null);

    Optional<HtmlMatcherGraphNode> nextNode = condNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE);
    Optional<HtmlMatcherGraphNode> nextAltNode = condNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE);
    if (!condNode.isInternallyBalanced(stack.inForeignContent, idGenerator)
        && nextNode.isPresent()
        && !Boolean.FALSE.equals(originalState)) {
      Map<Equivalence.Wrapper<ExprNode>, Boolean> lMap = new HashMap<>(exprValueMap);
      lMap.put(condition, true);
      visit(nextNode.get(), lMap, stack);
    }

    if (nextAltNode.isPresent() && !Boolean.TRUE.equals(originalState)) {
      Map<Equivalence.Wrapper<ExprNode>, Boolean> rMap = new HashMap<>(exprValueMap);
      rMap.put(condition, false);
      visit(nextAltNode.get(), rMap, stack);
    }
  }

  /** Accumulator nodes mostly work like HTMLMatcherTagNodes, but don't add any elements. */
  private void visit(
      HtmlMatcherAccumulatorNode accNode,
      Map<Equivalence.Wrapper<ExprNode>, Boolean> exprValueMap,
      HtmlStack stack) {
    Optional<HtmlMatcherGraphNode> nextNode = accNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE);
    if (nextNode.isPresent()) {
      visit(nextNode.get(), exprValueMap, stack);
    } else {
      checkUnusedTags(stack);
    }
  }

  public void visit(HtmlMatcherGraphNode node) {
    visit(
        node,
        new HashMap<Equivalence.Wrapper<ExprNode>, Boolean>(),
        new HtmlStack(null, inForeignContent, null));
  }

  private void visit(
      HtmlMatcherGraphNode node,
      Map<Equivalence.Wrapper<ExprNode>, Boolean> exprValueMap,
      HtmlStack stack) {
    if (node instanceof HtmlMatcherTagNode) {
      visit((HtmlMatcherTagNode) node, exprValueMap, stack);
    } else if (node instanceof HtmlMatcherConditionNode) {
      visit((HtmlMatcherConditionNode) node, exprValueMap, stack);
    } else if (node instanceof HtmlMatcherAccumulatorNode) {
      visit((HtmlMatcherAccumulatorNode) node, exprValueMap, stack);
    } else if (node instanceof HtmlMatcherBlockNode) {
      visit((HtmlMatcherBlockNode) node, exprValueMap, stack);
    } else {
      throw new UnsupportedOperationException("No implementation for: " + node);
    }
  }

  private void checkUnusedTags(HtmlStack stack) {
    while (!stack.isEmpty()) {
      if (stack.tagNode.getTagName().isDefinitelyOptional() && !inCondition) {
        injectCloseTag(stack.tagNode, null, idGenerator);
      } else {
        annotationMap.put(stack.tagNode, INVALID_NODE);
      }
      stack = stack.pop();
    }
  }
}
