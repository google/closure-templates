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

import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;


/**
 * Node representing an 'if' statement.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class IfNode extends AbstractParentSoyNode<SoyNode>
    implements StandaloneNode, SplitLevelTopNode<SoyNode>, StatementNode {


  /**
   * @param id The id for this node.
   */
  public IfNode(int id) {
    super(id);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected IfNode(IfNode orig) {
    super(orig);
  }


  @Override public Kind getKind() {
    return Kind.IF_NODE;
  }


  @Override public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    // Note: The first IfCondNode takes care of generating the 'if' tag.
    appendSourceStringForChildren(sb);
    sb.append("{/if}");
    return sb.toString();
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public IfNode clone() {
    return new IfNode(this);
  }

}
