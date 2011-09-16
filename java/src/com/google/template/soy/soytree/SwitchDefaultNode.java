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
 * Node representing the 'default' block in a 'switch' statement.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class SwitchDefaultNode extends CaseOrDefaultNode implements ConditionalBlockNode {


  /**
   * @param id The id for this node.
   */
  public SwitchDefaultNode(int id) {
    super(id, "default", "");
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected SwitchDefaultNode(SwitchDefaultNode orig) {
    super(orig);
  }


  @Override public Kind getKind() {
    return Kind.SWITCH_DEFAULT_NODE;
  }


  @Override public SwitchDefaultNode clone() {
    return new SwitchDefaultNode(this);
  }

}
