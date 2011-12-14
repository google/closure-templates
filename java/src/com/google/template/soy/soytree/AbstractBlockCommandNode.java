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

import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;


/**
 * Abstract implementation of a BlockNode and CommandNode.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public abstract class AbstractBlockCommandNode extends AbstractParentCommandNode<StandaloneNode>
    implements BlockNode {


  /**
   * @param id The id for this node.
   * @param commandName The name of the Soy command.
   * @param commandText The command text, or empty string if none.
   */
  public AbstractBlockCommandNode(int id, String commandName, String commandText) {
    super(id, commandName, commandText);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected AbstractBlockCommandNode(AbstractBlockCommandNode orig) {
    super(orig);
  }

}
