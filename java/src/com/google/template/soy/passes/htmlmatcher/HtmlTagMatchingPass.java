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

import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.template.soy.base.internal.IdGenerator;
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
import java.util.ArrayDeque;
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

  private static final String UNEXPECTED_OPEN_TAG_ALWAYS =
      "This HTML open tag is never matched with a close tag.";

  private static final String UNEXPECTED_OPEN_TAG_SOMETIMES =
      "This HTML open tag does not consistently match with a close tag.";

  private static final SoyErrorKind NESTED_SVG = SoyErrorKind.of("Nested SVG tags are disallowed.");

  private static final String BLOCK_QUALIFIER = " Tags within a %s must be internally balanced.";

  private final boolean inForeignContent;
  @Nullable private final String parentBlockType;

  public HtmlTagMatchingPass(boolean inForeignContent, String parentBlockType) {
    this.inForeignContent = inForeignContent;
    this.parentBlockType = parentBlockType;
  }

  public HtmlTagMatchingPass(String parentBlockType) {
    this.inForeignContent = false;
    this.parentBlockType = parentBlockType;
  }

  public HtmlTagMatchingPass() {
    this.inForeignContent = false;
    this.parentBlockType = null;
  }

  private SoyErrorKind makeSoyErrorKind(String soyError) {
    return SoyErrorKind.of(
        soyError
            + (parentBlockType != null ? String.format(BLOCK_QUALIFIER, parentBlockType) : ""));
  }

  /**
   * Additional metadata for close tags. During graph traversal it is possible for a close tag to be
   * paired with a tag that does not have the same tag name. In that case, we can generate a message
   * like "Expected div at file:line:col". expectedOpenTag is that message. However, the
   * expectedOpenTag is optional because there may not be one.
   */
  @AutoValue
  abstract static class ExpectedTagInfo {
    abstract Optional<HtmlTagNode> getNode();

    abstract Optional<HtmlOpenTagNode> getExpectedOpenTag();

    static ExpectedTagInfo create(
        Optional<HtmlTagNode> node, Optional<HtmlOpenTagNode> expectedOpenTag) {
      return new AutoValue_HtmlTagMatchingPass_ExpectedTagInfo(node, expectedOpenTag);
    }
  }

  /**
   * The mappings from open tags to their possible closing tags, and from closing tags to their
   * possible open tags.
   *
   * <p>This state is created in {@link #rebalanceCodePaths(ArrayDeque, IdGenerator)}. It's used to
   * generate errors in {@link #annotateTagsAndCheckForErrors(TagAnnotationState, ErrorReporter)}
   */
  @AutoValue
  abstract static class TagAnnotationState {
    abstract ImmutableMultimap<HtmlTagNode, Optional<HtmlTagNode>> getOpenToCloseMap();

    abstract ImmutableMultimap<HtmlTagNode, ExpectedTagInfo> getCloseToOpenMap();

    static TagAnnotationState create(
        ImmutableMultimap<HtmlTagNode, Optional<HtmlTagNode>> openToCloseMap,
        ImmutableMultimap<HtmlTagNode, ExpectedTagInfo> closeToOpenMap) {
      return new AutoValue_HtmlTagMatchingPass_TagAnnotationState(openToCloseMap, closeToOpenMap);
    }
  }

  /**
   * Runs the HtmlTagMatchingPass.
   *
   * <p>The pass runs in three phases:
   *
   * <ol>
   *   <li>Traverse the HTML matcher graph and generate a list of all the possible code paths.
   *   <li>Rebalance the code paths, injecting synthetic close tags to balance optional open tags.
   *       Optional open tags are defined here: <a
   *       href="https://www.w3.org/TR/html5/syntax.html#optional-tags">https://www.w3.org/TR/html5/syntax.html#optional-tags</a>.
   *       <p>&ndash; <em>Note:</em> Block nodes (such as {@code {msg} or {let}}) are discretely
   *       rebalanced, annotated and error-checked. By definition, a block node must internally
   *       balance HTML tags.
   *   <li>Check for tag mismatch errors in the fully balanced code paths.
   *       <p>&ndash; At the same time, annotate the open with the list of possible close tags, and
   *       the close tags with the list of possible open tags.
   * </ol>
   */
  public void run(
      HtmlMatcherGraph htmlMatcherGraph, IdGenerator idGenerator, ErrorReporter errorReporter) {
    if (!htmlMatcherGraph.getRootNode().isPresent()) {
      // Empty graph.
      return;
    }
    ArrayDeque<ArrayDeque<HtmlMatcherGraphNode>> codePaths =
        visit(htmlMatcherGraph.getRootNode().get());
    TagAnnotationState tagAnnotationState =
        rebalanceCodePaths(codePaths, idGenerator, errorReporter);
    annotateTagsAndCheckForErrors(tagAnnotationState, errorReporter);
  }

  /**
   * Rebalances HTML tags in all the code paths.
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
   *
   * @param paths the code paths generated in {@link #visit(HtmlMatcherGraphNode)}
   * @param idGenerator used to generate IDs for the synthetic nodes
   * @param errorReporter the error reporter
   * @return fully rebalanced code paths ready for annotation and error detection by {@link
   *     #annotateTagsAndCheckForErrors(TagAnnotationState, ErrorReporter)}
   */
  private TagAnnotationState rebalanceCodePaths(
      ArrayDeque<ArrayDeque<HtmlMatcherGraphNode>> paths,
      IdGenerator idGenerator,
      ErrorReporter errorReporter) {
    ImmutableMultimap.Builder<HtmlTagNode, Optional<HtmlTagNode>> openToCloseMapBuilder =
        ImmutableMultimap.builder();
    ImmutableMultimap.Builder<HtmlTagNode, ExpectedTagInfo> closeToOpenMapBuilder =
        ImmutableMultimap.builder();

    for (ArrayDeque<HtmlMatcherGraphNode> path : paths) {
      ArrayDeque<HtmlOpenTagNode> stack = new ArrayDeque<>();
      boolean inForeignContent = this.inForeignContent;
      for (HtmlMatcherGraphNode graphNode : path) {
        if (graphNode instanceof HtmlMatcherBlockNode) {
          HtmlMatcherBlockNode block = (HtmlMatcherBlockNode) graphNode;
          if (block.getGraph().getRootNode().isPresent()) {
            HtmlTagMatchingPass pass =
                new HtmlTagMatchingPass(inForeignContent, block.getParentBlockType());
            TagAnnotationState blockAnnotationState =
                pass.rebalanceCodePaths(
                    pass.visit(block.getGraph().getRootNode().get()), idGenerator, errorReporter);
            pass.annotateTagsAndCheckForErrors(blockAnnotationState, errorReporter);
          }
          continue;
        }

        HtmlMatcherTagNode node = (HtmlMatcherTagNode) graphNode;
        HtmlOpenTagNode openTag;
        switch (node.getTagKind()) {
          case VOID_TAG:
            openTag = (HtmlOpenTagNode) node.getSoyNode().get();
            // For static tag, check if it is a valid self-closing tag.
            if (openTag.getTagName().isStatic()) {
              TagName openTagName = openTag.getTagName();
              // Report errors for non-void tags that are self-closing.
              // For void tags, we don't care if they are self-closing or not. But when we visit
              // a HtmlCloseTagNode we will throw an error if it is a void tag.
              // Ignore this check if we are currently in a foreign content (svg).
              if (!inForeignContent && !openTagName.isDefinitelyVoid() && openTag.isSelfClosing()) {
                errorReporter.report(
                    openTag.getSourceLocation(),
                    INVALID_SELF_CLOSING_TAG,
                    openTagName.getStaticTagName());
              }
            }
            break;
          case OPEN_TAG:
            HtmlOpenTagNode soyNode = (HtmlOpenTagNode) node.getSoyNode().get();
            if (!stack.isEmpty()) {
              HtmlOpenTagNode optionalTag = stack.peek();
              if (optionalTag.getTagName().isDefinitelyOptional()) {
                if (TagName.checkOpenTagClosesOptional(
                    soyNode.getTagName(), optionalTag.getTagName())) {
                  injectCloseTag(
                      optionalTag, idGenerator, openToCloseMapBuilder, closeToOpenMapBuilder);
                }
              }
            }
            openTag = (HtmlOpenTagNode) node.getSoyNode().get();
            TagName openTagName = openTag.getTagName();
            if (openTagName.isStatic() && openTagName.isForeignContent()) {
              if (inForeignContent) {
                errorReporter.report(openTag.getSourceLocation(), NESTED_SVG);
              }
              inForeignContent = true;
            }
            openTag = (HtmlOpenTagNode) node.getSoyNode().get();
            stack.push(openTag);
            break;
          case CLOSE_TAG:
            HtmlTagNode closeTag = (HtmlTagNode) node.getSoyNode().get();
            // Report an error if this node is a void tag. Void tag should never be closed.
            if (closeTag.getTagName().isDefinitelyVoid()) {
              errorReporter.report(
                  closeTag.getTagName().getTagLocation(),
                  INVALID_CLOSE_TAG,
                  closeTag.getTagName().getStaticTagName());
              continue;
            }
            if (stack.isEmpty()) {
              closeToOpenMapBuilder.put(
                  closeTag, ExpectedTagInfo.create(Optional.absent(), Optional.absent()));
              continue;
            }
            while (!stack.isEmpty()) {
              openTag = stack.pop();
              if (openTag.getTagName().equals(closeTag.getTagName())) {
                // Potentially balanced tags.
                closeToOpenMapBuilder.put(
                    closeTag, ExpectedTagInfo.create(Optional.of(openTag), Optional.absent()));
                openToCloseMapBuilder.put(openTag, Optional.of(closeTag));
                if (openTag.getTagName().isStatic() && openTag.getTagName().isForeignContent()) {
                  inForeignContent = false;
                }
                break;
              } else if (openTag.getTagName().isDefinitelyOptional()
                  && TagName.checkCloseTagClosesOptional(
                      closeTag.getTagName(), openTag.getTagName())) {
                // Close tag closes an optional open tag (e.g. <li> ... </ul>). Inject a synthetic
                // close tag that matches `openTag`.
                injectCloseTag(openTag, idGenerator, openToCloseMapBuilder, closeToOpenMapBuilder);
              } else {
              // Potentially unbalanced tags. These will be flagged as errors later.
              openToCloseMapBuilder.put(openTag, Optional.absent());
              closeToOpenMapBuilder.put(
                  closeTag, ExpectedTagInfo.create(Optional.absent(), Optional.of(openTag)));
              }
            }
            break;
        }
      }
      while (!stack.isEmpty()) {
        openToCloseMapBuilder.put(stack.pop(), Optional.absent());
      }
    }
    return TagAnnotationState.create(openToCloseMapBuilder.build(), closeToOpenMapBuilder.build());
  }

  /** Injects a synthetic close tag that corresponds to the given open tag into the AST. */
  private static void injectCloseTag(
      HtmlOpenTagNode optionalOpenTag,
      IdGenerator idGenerator,
      ImmutableMultimap.Builder<HtmlTagNode, Optional<HtmlTagNode>> openToCloseMapBuilder,
      ImmutableMultimap.Builder<HtmlTagNode, ExpectedTagInfo> closeToOpenMapBuilder) {
    ParentSoyNode<StandaloneNode> openTagParent = optionalOpenTag.getParent();
    int i = openTagParent.getChildIndex(optionalOpenTag);
    HtmlCloseTagNode syntheticClose =
        new HtmlCloseTagNode(
            idGenerator.genId(),
            optionalOpenTag.getTagName(),
            optionalOpenTag.getSourceLocation(),
            TagExistence.SYNTHETIC);
    // This inserts the synthetic close tag right before the open tag.
    openTagParent.addChild(i, syntheticClose);
    // Add the synthetic node to the tag maps.
    closeToOpenMapBuilder.put(
        syntheticClose, ExpectedTagInfo.create(Optional.of(optionalOpenTag), Optional.absent()));
    openToCloseMapBuilder.put(optionalOpenTag, Optional.of(syntheticClose));
  }

  /**
   * Annotates open and close tags with links to their matching close and open tags.
   *
   * <p>Checks if any of the paths are erroneous in terms of tag matching. The general algorithm for
   * this method works as follows: For a given state list (example: [OPEN, OPEN, CLOSE, CLOSE])
   *
   * <ol>
   *   <li>If the path is not valid, continue onto the next path.
   *   <li>Loop through the stack and push OPEN, OPEN onto the stack
   *   <li>When CLOSE is seen, check if the stack if it has any nodes available.
   *       <p>&ndash; If there are no nodes, register an error for the close tag
   *   <li>Pop a open tag off of the stack and check if the tags match. If they do, then register a
   *       close tag for the open tag and vice versa.
   *       <p>&ndash; If the tags do not match, register an error for both close/open tags.
   *   <li>If there are still open tags on the stack at the end, register an error for remaining
   *       open tags.
   * </ol>
   */
  private void annotateTagsAndCheckForErrors(
      TagAnnotationState tagAnnotationState, ErrorReporter errorReporter) {
    // Now that the data structure has been created, we can walk through the data structure
    // and trigger error messages depending on the condition.
    for (HtmlTagNode openTag : tagAnnotationState.getOpenToCloseMap().keySet()) {
      ImmutableCollection<Optional<HtmlTagNode>> closeTagList =
          tagAnnotationState.getOpenToCloseMap().get(openTag);
      int counter = 0;
      // For open tags, we want to distinguish between tags that will never be closed and tags
      // that are sometimes closed. This helps the developer know if control flow is causing some
      // state that makes this open tag sometimes unclosable.
      for (Optional<HtmlTagNode> node : closeTagList) {
        if (node.isPresent()) {
          counter++;
          openTag.addTagPair(node.get());
          node.get().addTagPair(openTag);
        }
      }
      if (counter == 0) {
        errorReporter.report(
            openTag.getSourceLocation(), makeSoyErrorKind(UNEXPECTED_OPEN_TAG_ALWAYS));
      } else if (counter < closeTagList.size()) {
        errorReporter.report(
            openTag.getSourceLocation(), makeSoyErrorKind(UNEXPECTED_OPEN_TAG_SOMETIMES));
      }
    }
    // For close tags, we sometimes can offer a hint to where a possible open tag might be.
    for (HtmlTagNode closeTag : tagAnnotationState.getCloseToOpenMap().keySet()) {
      for (ExpectedTagInfo info : tagAnnotationState.getCloseToOpenMap().get(closeTag)) {
        if (!info.getNode().isPresent()) {
          if (info.getExpectedOpenTag().isPresent()) {
            errorReporter.report(
                closeTag.getSourceLocation(),
                makeSoyErrorKind(UNEXPECTED_CLOSE_TAG_KNOWN),
                info.getExpectedOpenTag().get().getTagName(),
                info.getExpectedOpenTag().get().getSourceLocation());
          } else {
            errorReporter.report(
                closeTag.getSourceLocation(), makeSoyErrorKind(UNEXPECTED_CLOSE_TAG));
          }
          break;
        }
      }
    }
  }

  /**
   * Aggregate the state graph starting from a tag node. In general, this algorithm assumes that we
   * can visit the next node and contain a list of paths. If the next node does not exist (we are at
   * the end of the template), then we create a list of paths. From then, we append the current
   * element and move on.
   */
  private ArrayDeque<ArrayDeque<HtmlMatcherGraphNode>> visit(
      HtmlMatcherTagNode tagNode, Map<Equivalence.Wrapper<ExprNode>, Boolean> exprValueMap) {
    Optional<HtmlMatcherGraphNode> nextNode = tagNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE);
    ArrayDeque<ArrayDeque<HtmlMatcherGraphNode>> paths;
    if (nextNode.isPresent()) {
      paths = visit(nextNode.get(), exprValueMap);
    } else {
      paths = new ArrayDeque<>();
      paths.add(new ArrayDeque<HtmlMatcherGraphNode>());
    }
    for (ArrayDeque<HtmlMatcherGraphNode> path : paths) {
      path.addFirst(tagNode);
    }
    return paths;
  }

  /**
   * Aggregate the state graph starting from a tag node. In general, this algorithm assumes that we
   * can visit the next node and contain a list of paths. If the next node does not exist (we are at
   * the end of the template), then we create a list of paths. From then, we append the current
   * element and move on.
   */
  private ArrayDeque<ArrayDeque<HtmlMatcherGraphNode>> visit(
      HtmlMatcherBlockNode tagNode, Map<Equivalence.Wrapper<ExprNode>, Boolean> exprValueMap) {
    Optional<HtmlMatcherGraphNode> nextNode = tagNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE);
    ArrayDeque<ArrayDeque<HtmlMatcherGraphNode>> paths;
    if (nextNode.isPresent()) {
      paths = visit(nextNode.get(), exprValueMap);
    } else {
      paths = new ArrayDeque<>();
      paths.add(new ArrayDeque<HtmlMatcherGraphNode>());
    }
    for (ArrayDeque<HtmlMatcherGraphNode> path : paths) {
      path.addFirst(tagNode);
    }
    return paths;
  }

  /**
   * For a conditional node, we have up to two different paths. In this case, we basically add them
   * together and return the result.
   */
  private ArrayDeque<ArrayDeque<HtmlMatcherGraphNode>> visit(
      HtmlMatcherConditionNode condNode, Map<Equivalence.Wrapper<ExprNode>, Boolean> exprValueMap) {
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
    Boolean originalState = null;
    if (exprValueMap.containsKey(condition)) {
      originalState = exprValueMap.get(condition);
    }

    Optional<HtmlMatcherGraphNode> nextNode = condNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE);
    Optional<HtmlMatcherGraphNode> nextAltNode = condNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE);

    ArrayDeque<ArrayDeque<HtmlMatcherGraphNode>> truePath = new ArrayDeque<>();
    if (nextNode.isPresent() && !Boolean.FALSE.equals(originalState)) {
      Map<Equivalence.Wrapper<ExprNode>, Boolean> lMap = new HashMap<>(exprValueMap);
      lMap.put(condition, true);
      truePath = visit(nextNode.get(), lMap);
    }

    ArrayDeque<ArrayDeque<HtmlMatcherGraphNode>> falsePath = new ArrayDeque<>();
    if (nextAltNode.isPresent() && !Boolean.TRUE.equals(originalState)) {
      Map<Equivalence.Wrapper<ExprNode>, Boolean> rMap = new HashMap<>(exprValueMap);
      rMap.put(condition, false);
      falsePath = visit(nextAltNode.get(), rMap);
    }
    truePath.addAll(falsePath);

    return truePath;
  }

  /** Accumulator nodes mostly work like HTMLMatcherTagNodes, but don't add any elements. */
  private ArrayDeque<ArrayDeque<HtmlMatcherGraphNode>> visit(
      HtmlMatcherAccumulatorNode accNode,
      Map<Equivalence.Wrapper<ExprNode>, Boolean> exprValueMap) {
    ArrayDeque<ArrayDeque<HtmlMatcherGraphNode>> paths;
    Optional<HtmlMatcherGraphNode> nextNode = accNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE);
    if (nextNode.isPresent()) {
      paths = visit(nextNode.get(), exprValueMap);
    } else {
      paths = new ArrayDeque<>();
      paths.add(new ArrayDeque<>());
    }
    return paths;
  }

  public ArrayDeque<ArrayDeque<HtmlMatcherGraphNode>> visit(HtmlMatcherGraphNode node) {
    return visit(node, new HashMap<Equivalence.Wrapper<ExprNode>, Boolean>());
  }

  private ArrayDeque<ArrayDeque<HtmlMatcherGraphNode>> visit(
      HtmlMatcherGraphNode node, Map<Equivalence.Wrapper<ExprNode>, Boolean> exprValueMap) {
    if (node instanceof HtmlMatcherTagNode) {
      return visit((HtmlMatcherTagNode) node, exprValueMap);
    } else if (node instanceof HtmlMatcherConditionNode) {
      return visit((HtmlMatcherConditionNode) node, exprValueMap);
    } else if (node instanceof HtmlMatcherAccumulatorNode) {
      return visit((HtmlMatcherAccumulatorNode) node, exprValueMap);
    } else if (node instanceof HtmlMatcherBlockNode) {
      return visit((HtmlMatcherBlockNode) node, exprValueMap);
    } else {
      throw new UnsupportedOperationException("No implementation for: " + node);
    }
  }
}
