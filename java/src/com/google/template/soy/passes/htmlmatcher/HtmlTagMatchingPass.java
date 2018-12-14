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
import com.google.common.collect.ImmutableMultimap;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode.EdgeKind;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

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

  private static final SoyErrorKind UNEXPECTED_CLOSE_TAG =
      SoyErrorKind.of("Unexpected HTML close tag.");

  private static final SoyErrorKind UNEXPECTED_CLOSE_TAG_KNOWN =
      SoyErrorKind.of("Unexpected HTML close tag. Expected to match the ''<{0}>'' at {1}.");

  private static final SoyErrorKind UNEXPECTED_OPEN_TAG_ALWAYS =
      SoyErrorKind.of("This HTML open tag is never matched with a close tag.");

  private static final SoyErrorKind UNEXPECTED_OPEN_TAG_SOMETIMES =
      SoyErrorKind.of("This HTML open tag does not consistently match with a close tag.");

  /**
   * Additional metadata for close tags. During graph traversal it is possible for a close tag to be
   * paired with a tag that does not have the same tag name. In that case, we can generate a message
   * like "Expected div at file:line:col". expectedOpenTag is that message. However, the
   * expectedOpenTag is optional because there may not be one.
   */
  private static class ExpectedTagInfo {
    private final Optional<HtmlTagNode> node;
    private final Optional<HtmlOpenTagNode> expectedOpenTag;

    private ExpectedTagInfo(Optional<HtmlTagNode> node, Optional<HtmlOpenTagNode> expectedOpenTag) {
      this.node = node;
      this.expectedOpenTag = expectedOpenTag;
    }
  }

  /**
   * After traversing a graph, check if any of the paths are erroneous in terms of tag matching. The
   * general algorithm for this method works as follows: For a given state list (example: [OPEN,
   * OPEN, CLOSE, CLOSE])
   *
   * <pre>
   * 0. If the path is not valid, continue onto the next path.
   * 1.  Loop through the stack and push OPEN, OPEN onto the stack
   * 2.  When CLOSE is seen, check if the stack if it has any nodes available.
   * 2a.   If there are no nodes, register an error for the close tag
   * 3.  Pop a open tag off of the stack and check if the tags match. If they do, then register
   *     a close tag for the open tag and vice versa.
   * 3a.   If the tags do not match, register an error for both close/open tags
   * 4.  If there are still open tags on the stack at the end, register an error for
   *     remaining open tags.
   * </pre>
   */
  public static void checkForErrors(
      ArrayDeque<ArrayDeque<HtmlMatcherTagNode>> paths, ErrorReporter errorReporter) {
    ImmutableMultimap.Builder<HtmlTagNode, Optional<HtmlTagNode>> openToCloseMapBuilder =
        ImmutableMultimap.builder();
    ImmutableMultimap.Builder<HtmlTagNode, ExpectedTagInfo> closeToOpenMapBuilder =
        ImmutableMultimap.builder();
    for (ArrayDeque<HtmlMatcherTagNode> path : paths) {
      ArrayDeque<HtmlOpenTagNode> stack = new ArrayDeque<>();
      for (HtmlMatcherTagNode node : path) {
        switch (node.getTagKind()) {
          case OPEN_TAG:
            // TODO(b/118396161): The top of the stack may be an optional tag. Handle this by
            // inserting a synthetic node.
            stack.push((HtmlOpenTagNode) node.getSoyNode().get());
            break;
          case CLOSE_TAG:
            HtmlTagNode closeTag = (HtmlTagNode) node.getSoyNode().get();
            if (stack.isEmpty()) {
              closeToOpenMapBuilder.put(
                  closeTag, new ExpectedTagInfo(Optional.absent(), Optional.absent()));
              continue;
            }
            HtmlOpenTagNode openTag = stack.pop();
            if (openTag.getTagName().equals(closeTag.getTagName())) {
              closeToOpenMapBuilder.put(
                  closeTag, new ExpectedTagInfo(Optional.of(openTag), Optional.absent()));
              openToCloseMapBuilder.put(openTag, Optional.of(closeTag));
              // TODO(b/118396161): Handle optional tags and void tags. If the tags don't match
              // but the state is acceptable such as CLOSE_TAG = UL & OPEN_TAG=<li>,
              // then there should be an auto-insert of a closing tag.
            } else {
              openToCloseMapBuilder.put(openTag, Optional.absent());
              closeToOpenMapBuilder.put(
                  closeTag, new ExpectedTagInfo(Optional.absent(), Optional.of(openTag)));
            }
            break;
        }
      }
      while (!stack.isEmpty()) {
        openToCloseMapBuilder.put(stack.pop(), Optional.absent());
      }
    }
    // Now that the data structure has been created, we can walk through the data structure
    // and trigger error messages depending on the condition.
    ImmutableMultimap<HtmlTagNode, Optional<HtmlTagNode>> openMap = openToCloseMapBuilder.build();
    ImmutableMultimap<HtmlTagNode, ExpectedTagInfo> closeMap = closeToOpenMapBuilder.build();
    for (HtmlTagNode openTag : openMap.keySet()) {
      int counter = 0;
      // For open tags, we want to distinguish between tags that will never be closed and tags
      // that are sometimes closed. This helps the developer know if control flow is causing some
      // state that makes this open tag sometimes unclosable.
      for (Optional<HtmlTagNode> node : openMap.get(openTag)) {
        if (node.isPresent()) {
          counter++;
          openTag.addTagPair(node.get());
          node.get().addTagPair(openTag);
        }
      }
      if (counter == 0) {
        errorReporter.report(openTag.getSourceLocation(), UNEXPECTED_OPEN_TAG_ALWAYS);
      } else if (counter < openMap.get(openTag).size()) {
        errorReporter.report(openTag.getSourceLocation(), UNEXPECTED_OPEN_TAG_SOMETIMES);
      }
    }
    // For close tags, we sometimes can offer a hint to where a possible open tag might be.
    for (HtmlTagNode closeTag : closeMap.keySet()) {
      for (ExpectedTagInfo info : closeMap.get(closeTag)) {
        if (!info.node.isPresent()) {
          if (info.expectedOpenTag.isPresent()) {
            errorReporter.report(
                closeTag.getSourceLocation(),
                UNEXPECTED_CLOSE_TAG_KNOWN,
                info.expectedOpenTag.get().getTagName(),
                info.expectedOpenTag.get().getSourceLocation());
          } else {
            errorReporter.report(closeTag.getSourceLocation(), UNEXPECTED_CLOSE_TAG);
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
  private ArrayDeque<ArrayDeque<HtmlMatcherTagNode>> visit(
      HtmlMatcherTagNode tagNode, Map<Equivalence.Wrapper<ExprNode>, Boolean> exprValueMap) {
    Optional<HtmlMatcherGraphNode> nextNode = tagNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE);
    ArrayDeque<ArrayDeque<HtmlMatcherTagNode>> paths;
    if (nextNode.isPresent()) {
      paths = visit(nextNode.get(), exprValueMap);
    } else {
      paths = new ArrayDeque<>();
      paths.add(new ArrayDeque<HtmlMatcherTagNode>());
    }
    for (ArrayDeque<HtmlMatcherTagNode> path : paths) {
      path.addFirst(tagNode);
    }
    return paths;
  }

  /**
   * For a conditional node, we have up to two different paths. In this case, we basically add them
   * together and return the result.
   */
  private ArrayDeque<ArrayDeque<HtmlMatcherTagNode>> visit(
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

    ArrayDeque<ArrayDeque<HtmlMatcherTagNode>> truePath = new ArrayDeque<>();
    if (nextNode.isPresent() && !Boolean.FALSE.equals(originalState)) {
      exprValueMap.put(condition, true);
      truePath = visit(nextNode.get(), exprValueMap);
    }

    ArrayDeque<ArrayDeque<HtmlMatcherTagNode>> falsePath = new ArrayDeque<>();
    if (nextAltNode.isPresent() && !Boolean.TRUE.equals(originalState)) {
      exprValueMap.put(condition, false);
      falsePath = visit(nextAltNode.get(), exprValueMap);
    }
    truePath.addAll(falsePath);

    exprValueMap.remove(condition);
    return truePath;
  }

  /** Accumulator nodes mostly work like HTMLMatcherTagNodes, but don't add any elements. */
  private ArrayDeque<ArrayDeque<HtmlMatcherTagNode>> visit(
      HtmlMatcherAccumulatorNode accNode,
      Map<Equivalence.Wrapper<ExprNode>, Boolean> exprValueMap) {
    ArrayDeque<ArrayDeque<HtmlMatcherTagNode>> paths;
    Optional<HtmlMatcherGraphNode> nextNode = accNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE);
    if (nextNode.isPresent()) {
      paths = visit(nextNode.get(), exprValueMap);
    } else {
      paths = new ArrayDeque<>();
      paths.add(new ArrayDeque<>());
    }
    return paths;
  }

  public ArrayDeque<ArrayDeque<HtmlMatcherTagNode>> visit(HtmlMatcherGraphNode node) {
    return visit(node, new HashMap<Equivalence.Wrapper<ExprNode>, Boolean>());
  }

  private ArrayDeque<ArrayDeque<HtmlMatcherTagNode>> visit(
      HtmlMatcherGraphNode node, Map<Equivalence.Wrapper<ExprNode>, Boolean> exprValueMap) {
    if (node instanceof HtmlMatcherTagNode) {
      return visit((HtmlMatcherTagNode) node, exprValueMap);
    } else if (node instanceof HtmlMatcherConditionNode) {
      return visit((HtmlMatcherConditionNode) node, exprValueMap);
    } else if (node instanceof HtmlMatcherAccumulatorNode) {
      return visit((HtmlMatcherAccumulatorNode) node, exprValueMap);
    } else {
      throw new UnsupportedOperationException("No implementation for: " + node);
    }
  }
}
