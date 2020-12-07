/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.exprtree;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.types.SoyType;

/**
 * Abstract base class for a local variable declaration, parameterized for different "declaring
 * node" types.
 *
 * <p>NOTE: The reason for this base class is that the declaring node type can be either a {@link
 * com.google.template.soy.soytree.SoyNode} or an {@link com.google.template.soy.exprtree.ExprNode}:
 *
 * <p>Regular {@link com.google.template.soy.soytree.defn.LocalVar}s that are declared in a
 * statement have a pointer to a parent {@link
 * com.google.template.soy.soytree.SoyNode.LocalVarNode}, but this means that dependency cycles
 * prevent the comprehension expr nodes from using LocalVar then (Since LocalVar ->
 * SoyNode.LocalVarNode, and SoyNode -> ExprNode).
 *
 * @param <T> declaring node type.
 */
public abstract class AbstractLocalVarDefn<T extends Node> extends AbstractVarDefn {

  private final T declaringNode;
  private final String originalName;

  /**
   * @param name The variable name.
   * @param declaringNode The statement in which this variable is defined.
   * @param type The data type of the variable.
   */
  public AbstractLocalVarDefn(
      String name, SourceLocation nameLocation, T declaringNode, SoyType type) {
    super(name.startsWith("$") ? name.substring(1) : name, nameLocation, type);
    this.declaringNode = declaringNode;
    this.originalName = name;
  }

  /** Copy constructor for when the declaring node is being cloned. */
  public AbstractLocalVarDefn(AbstractLocalVarDefn<T> localVar, T declaringNode) {
    super(localVar);
    checkArgument(localVar.declaringNode != declaringNode);
    this.declaringNode = declaringNode;
    this.originalName = localVar.originalName;
  }

  @Override
  public String refName() {
    return "$" + name();
  }

  public String getOriginalName() {
    return originalName;
  }

  /**
   * Setter for the type - this is necessary because sometimes we don't know the variable type until
   * after analysis.
   *
   * @param type The data type of the variable.
   */
  public void setType(SoyType type) {
    this.type = type;
  }

  public T declaringNode() {
    return declaringNode;
  }

  @Override
  public boolean isInjected() {
    return false;
  }
}
