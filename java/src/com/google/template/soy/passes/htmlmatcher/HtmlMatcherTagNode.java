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

import com.google.common.base.Optional;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.SoyNode;
import javax.annotation.Nullable;

/**
 * HTML tag nodes in the HTML tag matcher graph.
 *
 * <p>An HTML tag node represents either an open tag (such as {@code <div>} or {@code <span>}), or a
 * close tag (such as {@code </div>} or {@code </span>}).
 *
 * <p>Instances denote tag type by overriding {@link #getTagKind()}. Example usage:
 *
 * <pre>
 *    HtmlOpenTagNode soyNode = new HtmlOpenTagNode( ... );
 *    HtmlMatcherTagNode openTagNode = new HtmlMatcherTagNode(soyNode) {
 *       &#64;Override
 *       public TagKind getTagKind() {
 *         return TagKind.OPEN_TAG;
 *       }
 *     };
 * </pre>
 */
public abstract class HtmlMatcherTagNode extends HtmlMatcherGraphNode {

  /**
   * The kind of this HTML tag node.
   *
   * <p>All HTML tag nodes are either open tags (e.g, {@code <span>}) or close tags (e.g. {@code
   * </span>}). Void or self-closing tags (e.g. {@code <img>}) are represented as an {@link
   * TagKind#OPEN_TAG}.
   */
  public enum TagKind {
    OPEN_TAG,
    CLOSE_TAG
  }

  private final HtmlTagNode htmlTagNode;

  @Nullable private HtmlMatcherGraphNode nextNode = null;

  public HtmlMatcherTagNode(SoyNode htmlTagNode) {
    checkState(
        htmlTagNode instanceof HtmlTagNode,
        "HtmlMatcherCondition nodes must be constructed with an HtmlTagNode.");
    this.htmlTagNode = (HtmlTagNode) htmlTagNode;
  }

  /**
   * Returns the tag kind.
   *
   * <p>Override this when making concrete instances of open and close tags.
   */
  public abstract TagKind getTagKind();

  // ------ HtmlMatcherGraphNode implementation ------

  @Override
  public Optional<SoyNode> getSoyNode() {
    return Optional.of(htmlTagNode);
  }

  @Override
  public Optional<HtmlMatcherGraphNode> getNodeForEdgeKind(EdgeKind edgeKind) {
    checkState(edgeKind == EdgeKind.TRUE_EDGE, "HTML Tag nodes only have a true branch.");
    return Optional.fromNullable(nextNode);
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
