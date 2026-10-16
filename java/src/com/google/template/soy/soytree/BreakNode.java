/*
 * Copyright 2025 Google Inc.
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
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import javax.annotation.Nullable;

/** Node representing a 'break' statement. */
public final class BreakNode extends AbstractCommandNode implements StatementNode {

  @Nullable private final Identifier label;

  public BreakNode(int id, SourceLocation location, @Nullable Identifier label) {
    super(id, location, "break");
    this.label = label;
  }

  public BreakNode(int id, SourceLocation location) {
    this(id, location, null);
  }

  private BreakNode(BreakNode orig, CopyState copyState) {
    super(orig, copyState);
    this.label = orig.label;
  }

  @Nullable
  public Identifier getLabel() {
    return label;
  }

  @Override
  public String getCommandText() {
    return label != null ? label.identifier() : "";
  }

  @Override
  public Kind getKind() {
    return Kind.BREAK_NODE;
  }

  @Override
  public String toSourceString() {
    return getTagString(true /* self-ending */);
  }

  @Override
  public BreakNode copy(CopyState copyState) {
    return new BreakNode(this, copyState);
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }
}
