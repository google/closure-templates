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

import com.google.template.soy.exprtree.DataRefNode;
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
public class ForeachNonemptyNode extends AbstractParentSoyNode<SoyNode>
    implements ConditionalBlockNode<SoyNode>, LoopNode<SoyNode>, LocalVarBlockNode<SoyNode> {


  /** The ForeachNode that is or will become this node's parent. */
  private final ForeachNode parentForeachNode;


  /**
   * @param id The id for this node.
   * @param parentForeachNode The ForeachNode that will become this node's parent.
   */
  public ForeachNonemptyNode(String id, ForeachNode parentForeachNode) {
    super(id);
    this.parentForeachNode = parentForeachNode;
  }


  public String getForeachNodeId() {
    return parentForeachNode.getId();
  }

  /** Returns the foreach-loop variable name. */
  public String getVarName() {
    return parentForeachNode.getVarName();
  }

  /** Returns the text of the data reference we're iterating over. */
  public String getDataRefText() {
    return parentForeachNode.getDataRefText();
  }

  /** Returns the parsed data reference. */
  public ExprRootNode<DataRefNode> getDataRef() {
    return parentForeachNode.getDataRef();
  }


  @Override public String getLocalVarName() {
    return parentForeachNode.getVarName();
  }


  @Override public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    appendSourceStringForChildren(sb);
    return sb.toString();
  }

}
