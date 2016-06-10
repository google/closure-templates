/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.incrementaldomsrc;

import com.google.template.soy.html.HtmlAttributeNode;
import com.google.template.soy.html.HtmlCloseTagNode;
import com.google.template.soy.html.HtmlOpenTagEndNode;
import com.google.template.soy.html.HtmlOpenTagNode;
import com.google.template.soy.html.HtmlOpenTagStartNode;
import com.google.template.soy.html.HtmlVoidTagNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoytreeUtils;

import java.util.List;


final class IncrementalDomOutputOptimizers {
  private IncrementalDomOutputOptimizers() {}

  /**
   * Finds nodes in the tree where an {@link HtmlOpenTagNode} is followed by a {@link
   * HtmlCloseTagNode} and collapses them into a {@link HtmlVoidTagNode}.
   * @param node The root node in which to collapse nodes.
   */
  static void collapseElements(SoyNode node) {
    Iterable<HtmlOpenTagNode> openTagNodes = SoytreeUtils.getAllNodesOfType(
        node,
        HtmlOpenTagNode.class);

    for (HtmlOpenTagNode openTagNode : openTagNodes) {
      BlockNode parent = openTagNode.getParent();
      int nextIndex = parent.getChildIndex(openTagNode) + 1;

      if (nextIndex >= parent.getChildren().size()) {
        continue;
      }

      StandaloneNode nextNode = parent.getChild(nextIndex);

      if (!(nextNode instanceof HtmlCloseTagNode)) {
        continue;
      }

      HtmlCloseTagNode closeTagNode = (HtmlCloseTagNode) nextNode;

      if (!closeTagNode.getTagName().equals(openTagNode.getTagName())) {
        continue;
      }

      HtmlVoidTagNode htmlVoidTagNode = new HtmlVoidTagNode(
          openTagNode.getId(),
          openTagNode.getTagName(),
          openTagNode.getSourceLocation());
      htmlVoidTagNode.addChildren(openTagNode.getChildren());

      parent.replaceChild(openTagNode, htmlVoidTagNode);
      parent.removeChild(closeTagNode);
    }
  }

  /**
   * Finds nodes in the tree where an {@link HtmlOpenTagStartNode} is followed by zero or more
   * {@link HtmlAttributeNode}s and finally an {@link HtmlOpenTagEndNode} and collapses them into a
   * {@link HtmlOpenTagNode}. This allows the code generation to output efficient way of specifying
   * attributes.
   * @param node The root node in which to collapse nodes.
   */
  static void collapseOpenTags(SoyNode node) {
    Iterable<HtmlOpenTagStartNode> openTagStartNodes = SoytreeUtils.getAllNodesOfType(
        node,
        HtmlOpenTagStartNode.class);

    for (HtmlOpenTagStartNode openTagStartNode : openTagStartNodes) {
      BlockNode parent = openTagStartNode.getParent();
      List<StandaloneNode> children = parent.getChildren();
      final int startIndex = children.indexOf(openTagStartNode);
      int currentIndex = startIndex + 1;
      StandaloneNode currentNode = null;

      // Keep going while HtmlAttributeNodes are encountered
      for (; currentIndex < children.size(); currentIndex++) {
        currentNode = children.get(currentIndex);
        if (!(currentNode instanceof HtmlAttributeNode)) {
          break;
        }
      }

      // If the currentNode is an HtmlOpenTagEndNode, then we encountered a start, zero or more
      // attributes, followed by an end.
      if (currentNode instanceof HtmlOpenTagEndNode) {
        // At this point, startIndex points to the HtmlOpenTagStartNode and currentIndex points to
        // the HtmlOpenTagEndNode, with everything in between being an HtmlAttributeNode.
        List<StandaloneNode> tagNodesRange = children.subList(startIndex, currentIndex + 1);

        HtmlOpenTagNode openTagNode = new HtmlOpenTagNode(
            openTagStartNode.getId(),
            openTagStartNode.getTagName(),
            openTagStartNode.getSourceLocation().extend(currentNode.getSourceLocation()));
        // Get all the attribute nodes (all of the nodes in the range, except the first and last)
        for (StandaloneNode standaloneNode : tagNodesRange.subList(1, tagNodesRange.size() - 1)) {
          openTagNode.addChild((HtmlAttributeNode) standaloneNode);
        }

        // Replace the range of nodes with the newly created HtmlOpenTagNode.
        tagNodesRange.clear();
        parent.addChild(startIndex, openTagNode);
      }
    }
  }
}
