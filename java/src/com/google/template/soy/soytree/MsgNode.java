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

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.SoyNode.StatementNode;


/**
 * Node representing a 'msg' statement/block. Every child must be a RawTextNode, MsgPlaceholderNode,
 * MsgPluralNode, or MsgSelectNode.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class MsgNode extends AbstractMsgNode implements StatementNode {


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public MsgNode(int id, String commandText) throws SoySyntaxException {
    super(id, commandText);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected MsgNode(MsgNode orig) {
    super(orig);
  }


  @Override public Kind getKind() {
    return Kind.MSG_NODE;
  }


  @Override public MsgNode clone() {
    return new MsgNode(this);
  }

}
