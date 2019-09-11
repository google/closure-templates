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
package com.google.template.soy.soytree;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

/**
 * Node representing a 'skip' statement, e.g. {@code <div {skip}></div>}. This configures DOM nodes
 * to be unconditionally skipped in incremental dom.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class SkipNode extends AbstractParentCommandNode<StandaloneNode>
    implements StatementNode {

  // Serializes to a soy-server-key which is used for Incremental DOM to find the element this skip
  // node is attached to.
  private String skipId = "";

  public SkipNode(int id, SourceLocation location) {
    super(id, location, "skip");
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private SkipNode(SkipNode orig, CopyState copyState) {
    super(orig, copyState);
    for (StandaloneNode node : this.getChildren()) {
      addChild(node.copy(copyState));
    }
  }

  public void setSkipId(String skipId) {
    this.skipId = skipId;
  }

  public String getSkipId() {
    return skipId;
  }

  @Override
  public Kind getKind() {
    return Kind.SKIP_NODE;
  }

  @Override
  public String getCommandText() {
    return "{skip}";
  }

  @Override
  public String toSourceString() {
    StringBuilder builder = new StringBuilder();
    builder.append("{skip}");
    for (StandaloneNode node : this.getChildren()) {
      builder.append(node.toSourceString());
    }
    return builder.append("{/skip}").toString();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    // Cast is necessary so this is typed as a parent with a StandaloneNode child (this KeyNode).
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  @Override
  public SkipNode copy(CopyState copyState) {
    return new SkipNode(this, copyState);
  }
}
