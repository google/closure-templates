/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.soytree.defn;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.exprtree.AbstractLocalVarDefn;
import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.types.SoyType;

/**
 * A local variable declaration.
 *
 */
public class LocalVar extends AbstractLocalVarDefn<LocalVarNode> {

  /**
   * @param name The variable name.
   * @param nameLocation The location where the variable name is declared.
   * @param declaringNode The statement in which this variable is defined.
   * @param type The data type of the variable.
   */
  public LocalVar(
      String name, SourceLocation nameLocation, LocalVarNode declaringNode, SoyType type) {
    super(name, nameLocation, declaringNode, type);
  }

  /** Copy constructor for when the declaring node is being cloned. */
  public LocalVar(LocalVar localVar, LocalVarNode declaringNode) {
    super(localVar, declaringNode);
  }

  @Override
  public Kind kind() {
    return Kind.LOCAL_VAR;
  }
}
