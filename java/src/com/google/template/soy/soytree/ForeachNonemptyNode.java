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

import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.LocalVarBlockNode;
import com.google.template.soy.soytree.SoyNode.LoopNode;


/**
 * Node representing the loop portion of a 'foreach' statement.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class ForeachNonemptyNode extends AbstractBlockNode
    implements ConditionalBlockNode, LoopNode, LocalVarBlockNode {


  /**
   * @param id The id for this node.
   */
  public ForeachNonemptyNode(int id) {
    super(id);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected ForeachNonemptyNode(ForeachNonemptyNode orig) {
    super(orig);
  }


  @Override public Kind getKind() {
    return Kind.FOREACH_NONEMPTY_NODE;
  }


  public int getForeachNodeId() {
    return getParent().getId();
  }


  @Override public String getVarName() {
    return getParent().getVarName();
  }


  /** Returns the text of the expression we're iterating over. */
  public String getExprText() {
    return getParent().getExprText();
  }


  /** Returns the expression we're iterating over. */
  public ExprRootNode<?> getExpr() {
    return getParent().getExpr();
  }


  @Override public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    appendSourceStringForChildren(sb);
    return sb.toString();
  }


  @Override public ForeachNode getParent() {
    return (ForeachNode) super.getParent();
  }


  @Override public ForeachNonemptyNode clone() {
    return new ForeachNonemptyNode(this);
  }

}
