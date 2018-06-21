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

import com.google.common.base.Preconditions;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.types.SoyType;

/**
 * Implementation of common features of {@link VarDefn}.
 *
 */
abstract class AbstractVarDefn implements VarDefn {

  /** The name of the value. */
  private final String name;

  /** The data type of the value. */
  protected SoyType type;

  private int localVariableIndex = -1;

  /**
   * @param name The name of the value.
   * @param type The data type of the value.
   */
  public AbstractVarDefn(String name, SoyType type) {
    Preconditions.checkArgument(name != null);
    this.name = name;
    this.type = type;
  }

  protected AbstractVarDefn(AbstractVarDefn var) {
    this.name = var.name;
    this.type = var.type;
    this.localVariableIndex = var.localVariableIndex;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public SoyType type() {
    return type;
  }

  @Override
  public void setLocalVariableIndex(int i) {
    localVariableIndex = i;
  }

  @Override
  public int localVariableIndex() {
    return localVariableIndex;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{name = " + name() + "}";
  }

  // enforce identity semantics

  @Override
  public final boolean equals(Object obj) {
    return super.equals(obj);
  }

  @Override
  public final int hashCode() {
    return super.hashCode();
  }
}
