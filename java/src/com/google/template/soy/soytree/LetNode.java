/*
 * Copyright 2011 Google Inc.
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
import com.google.template.soy.soytree.SoyNode.LocalVarInlineNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import com.google.template.soy.soytree.defn.LocalVar;

/**
 * Abstract node representing a 'let' statement.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public abstract class LetNode extends AbstractCommandNode
    implements StandaloneNode, StatementNode, LocalVarInlineNode {

  /** The local variable defined by this node. */
  protected final LocalVar var;

  /**
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   * @param localVarName The let variable name.
   */
  protected LetNode(int id, SourceLocation sourceLocation, String localVarName) {
    super(id, sourceLocation, "let");
    this.var = new LocalVar(localVarName, this, null /* type */);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  protected LetNode(LetNode orig, CopyState copyState) {
    super(orig, copyState);
    this.var = new LocalVar(orig.var, this);
  }

  /** Return The local variable name (without the preceding '$'). */
  @Override
  public final String getVarName() {
    return var.name();
  }

  /** Gets a unique version of the local var name (e.g. appending "__soy##" if necessary). */
  public String getUniqueVarName() {
    return getVarName() + "__soy" + getId();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  /** Get the local variable defined by this node. */
  @Override
  public final LocalVar getVar() {
    return var;
  }
}
