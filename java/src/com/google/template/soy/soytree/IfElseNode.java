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

import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;


/**
 * Node representing the 'else' block within an 'if' statement.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class IfElseNode extends AbstractBlockCommandNode implements ConditionalBlockNode {


  /**
   * @param id The id for this node.
   */
  public IfElseNode(int id) {
    super(id, "else", "");
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected IfElseNode(IfElseNode orig) {
    super(orig);
  }


  @Override public Kind getKind() {
    return Kind.IF_ELSE_NODE;
  }


  @Override public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    // Note: No end tag.
    return sb.toString();
  }


  @Override public IfElseNode clone() {
    return new IfElseNode(this);
  }

}
