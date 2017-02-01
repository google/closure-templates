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

import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.types.SoyType;

/**
 * A local variable declaration.
 *
 */
public class LoopVar extends LocalVar {

  /** The index of the currentIndex variable. */
  private int currentIndexIndex;

  /** The index of the isLast variable. */
  private int isLastIndex;

  /**
   * @param name The variable name.
   * @param declaringNode The statement in which this variable is defined.
   * @param type The data type of the variable.
   */
  public LoopVar(String name, LocalVarNode declaringNode, SoyType type) {
    super(name, declaringNode, type);
  }

  /** Copy constructor for when the declaring node is being cloned. */
  public LoopVar(LoopVar loop, LocalVarNode declaringNode) {
    super(loop, declaringNode);
    this.currentIndexIndex = loop.currentIndexIndex;
    this.isLastIndex = loop.isLastIndex;
  }

  @Override
  public Kind kind() {
    return Kind.LOCAL_VAR;
  }

  public void setExtraLoopIndices(int currentIndexIndex, int isLastIndex) {
    this.currentIndexIndex = currentIndexIndex;
    this.isLastIndex = isLastIndex;
  }

  public int currentLoopIndexIndex() {
    return this.currentIndexIndex;
  }

  public int isLastIteratorIndex() {
    return this.isLastIndex;
  }
}
