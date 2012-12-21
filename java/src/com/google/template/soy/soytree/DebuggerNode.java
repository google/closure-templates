/*
 * Copyright 2012 Google Inc.
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

import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;


/**
 * Node representing the 'debugger' statement.
 *
 * @author Kai Huang
 */
public class DebuggerNode extends AbstractCommandNode implements StandaloneNode, StatementNode {


  public DebuggerNode(int id) {
    super(id, "debugger", "");
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected DebuggerNode(DebuggerNode orig) {
    super(orig);
  }


  @Override public Kind getKind() {
    return Kind.DEBUGGER_NODE;
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public SoyNode clone() {
    return new DebuggerNode(this);
  }

}
