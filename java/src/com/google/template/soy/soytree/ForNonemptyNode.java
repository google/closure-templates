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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.LocalVarBlockNode;
import com.google.template.soy.soytree.defn.LocalVar;

/**
 * Node representing the loop portion of a 'for' statement.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class ForNonemptyNode extends AbstractBlockNode
    implements ConditionalBlockNode, LocalVarBlockNode {

  private final LocalVar var;

  /**
   * @param id The id for this node.
   * @param location The node's source location.
   * @param varName The variable name of the loop index variable.
   */
  public ForNonemptyNode(int id, String varName, SourceLocation varNameLocation) {
    // TODO(lukes): this is a weird location for this node.  Not sure what would be better
    super(id, varNameLocation);
    this.var = new LocalVar(varName, varNameLocation, this, /* type= */ null);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private ForNonemptyNode(ForNonemptyNode orig, CopyState copyState) {
    super(orig, copyState);
    this.var = new LocalVar(orig.var, this);
    copyState.updateRefs(orig.var, this.var);
  }

  @Override
  public Kind getKind() {
    return Kind.FOR_NONEMPTY_NODE;
  }

  public int getForNodeId() {
    return getParent().getId();
  }

  @Override
  public final LocalVar getVar() {
    return var;
  }

  @Override
  public final String getVarName() {
    return var.name();
  }

  /** Returns the expression we're iterating over. */
  public ExprRootNode getExpr() {
    return getParent().getExpr();
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    appendSourceStringForChildren(sb);
    return sb.toString();
  }

  @Override
  public ForNode getParent() {
    return (ForNode) super.getParent();
  }

  @Override
  public ForNonemptyNode copy(CopyState copyState) {
    return new ForNonemptyNode(this, copyState);
  }
}
