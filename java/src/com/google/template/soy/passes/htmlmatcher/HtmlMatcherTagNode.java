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

import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode.EdgeKind;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.SoyNode;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * HTML tag nodes in the HTML tag matcher graph.
 *
 * <p>An HTML tag node represents either an open tag (such as {@code <div>} or {@code <span>}), or a
 * close tag (such as {@code </div>} or {@code </span>}).
 */
public class HtmlMatcherTagNode extends HtmlMatcherGraphNode {

  /**
   * The kind of this HTML tag node.
   *
   * <p>All HTML tag nodes are either open tags (e.g, {@code <span>}) or close tags (e.g. {@code
   * </span>}), or void/self-closing tags (e.g. {@code <img>}).
   */
  public enum TagKind {
    OPEN_TAG,
    CLOSE_TAG,
    VOID_TAG,
  }

  private final HtmlTagNode htmlTagNode;

  @Nullable private HtmlMatcherGraphNode nextNode = null;

  public HtmlMatcherTagNode(SoyNode htmlTagNode) {
    checkState(
        htmlTagNode instanceof HtmlTagNode,
        "HtmlMatcherCondition nodes must be constructed with an HtmlTagNode.");
    this.htmlTagNode = (HtmlTagNode) htmlTagNode;
  }

  /** Returns the tag kind. */
  public TagKind getTagKind() {
    if (htmlTagNode instanceof HtmlOpenTagNode) {
      HtmlOpenTagNode openTagNode = (HtmlOpenTagNode) htmlTagNode;
      if (openTagNode.isSelfClosing() || openTagNode.getTagName().isDefinitelyVoid()) {
        return TagKind.VOID_TAG;
      }
      return TagKind.OPEN_TAG;
    } else {
      return TagKind.CLOSE_TAG;
    }
  }

  // ------ HtmlMatcherGraphNode implementation ------

  @Override
  public Optional<SoyNode> getSoyNode() {
    return Optional.of(htmlTagNode);
  }

  @Override
  public Optional<HtmlMatcherGraphNode> getNodeForEdgeKind(EdgeKind edgeKind) {
    if (edgeKind == EdgeKind.TRUE_EDGE) {
      return Optional.ofNullable(nextNode);
    }
    return Optional.empty();
  }

  @Override
  public void setActiveEdgeKind(EdgeKind edgeKind) {
    throw new UnsupportedOperationException("Cannot set the edge kind of a Tag node.");
  }

  @Override
  public void linkEdgeToNode(EdgeKind edgeKind, HtmlMatcherGraphNode node) {
    checkState(edgeKind == EdgeKind.TRUE_EDGE, "HTML Tag nodes only have a true branch.");
    checkState(!this.equals(node), "Can't link a node to itsself.");
    nextNode = node;
  }
}
