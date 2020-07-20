/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.soytree;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import javax.annotation.Nullable;

/**
 * Node representing an 'if' statement.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class IfNode extends AbstractParentSoyNode<BlockNode>
    implements HtmlContext.HtmlContextHolder,
        StandaloneNode,
        SplitLevelTopNode<BlockNode>,
        StatementNode {

  @Nullable private HtmlContext htmlContext;
  private final SourceLocation closeTagLocation;

  /** @param id The id for this node. */
  public IfNode(int id, SourceLocation closeTagLocation, SourceLocation sourceLocation) {
    super(id, sourceLocation);
    this.closeTagLocation = closeTagLocation;
  }

  /** @param id The id for this node. */
  public IfNode(int id, SourceLocation sourceLocation) {
    super(id, sourceLocation);
    this.closeTagLocation = SourceLocation.UNKNOWN;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private IfNode(IfNode orig, CopyState copyState) {
    super(orig, copyState);
    this.htmlContext = orig.htmlContext;
    this.closeTagLocation = orig.closeTagLocation;
  }

  @Override
  public HtmlContext getHtmlContext() {
    return checkNotNull(
        htmlContext, "Cannot access HtmlContext before HtmlContextVisitor or InferenceEngine.");
  }

  public void setHtmlContext(HtmlContext value) {
    this.htmlContext = value;
  }

  public SourceLocation getCloseTagLocation() {
    return closeTagLocation;
  }

  @Override
  public Kind getKind() {
    return Kind.IF_NODE;
  }

  /** Returns true if this if statement has an {@code else} block. */
  public boolean hasElse() {
    return getChild(numChildren() - 1) instanceof IfElseNode;
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    // Note: The first IfCondNode takes care of generating the 'if' tag.
    appendSourceStringForChildren(sb);
    sb.append("{/if}");
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  @Override
  public IfNode copy(CopyState copyState) {
    return new IfNode(this, copyState);
  }
}
