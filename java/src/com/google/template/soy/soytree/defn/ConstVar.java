/*
 * Copyright 2021 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.exprtree.AbstractVarDefn;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.types.SoyType;

/** A file-level constant declaration. */
public final class ConstVar extends AbstractVarDefn {

  private final ExprHolderNode declaringNode;

  public ConstVar(String name, SourceLocation nameLocation, ExprHolderNode declaringNode) {
    super(name, nameLocation, /* type= */ null);
    this.declaringNode = checkNotNull(declaringNode);
  }

  public ConstVar(ConstVar localVar, ExprHolderNode declaringNode) {
    super(localVar);
    this.declaringNode = checkNotNull(declaringNode);
  }

  public ExprHolderNode getDeclaringNode() {
    return declaringNode;
  }

  @Override
  public Kind kind() {
    return Kind.CONST;
  }

  @Override
  public boolean isInjected() {
    return false;
  }

  public void setType(SoyType type) {
    this.type = type;
  }
}
