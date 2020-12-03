/*
 * Copyright 2018 Google Inc.
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
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.AbstractVarDefn;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.ast.TypeNode;
import javax.annotation.Nullable;

/**
 * An explicitly declared template state variable.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class TemplateStateVar extends AbstractVarDefn implements TemplateHeaderVarDefn {
  private String desc;
  private final SourceLocation sourceLocation;
  @Nullable private final TypeNode typeNode;
  private final ExprRootNode initialValue;

  public TemplateStateVar(
      String name,
      @Nullable TypeNode typeNode,
      ExprNode initialValue,
      @Nullable String desc,
      @Nullable SourceLocation nameLocation,
      SourceLocation sourceLocation) {
    super(name, nameLocation, /*type=*/ null);
    this.typeNode = typeNode;
    this.desc = desc;
    this.initialValue = new ExprRootNode(initialValue);
    this.sourceLocation = sourceLocation;
  }

  private TemplateStateVar(TemplateStateVar old, CopyState copyState) {
    super(old);
    this.typeNode = old.typeNode == null ? null : old.typeNode.copy();
    this.desc = old.desc;
    this.initialValue = old.initialValue.copy(copyState);
    this.sourceLocation = old.sourceLocation;
    copyState.updateRefs(old.initialValue, this.initialValue);
  }

  @Override
  public String refName() {
    return "$" + name();
  }

  @Override
  public SourceLocation getSourceLocation() {
    return sourceLocation;
  }

  @Override
  public TypeNode getTypeNode() {
    return typeNode;
  }

  @Override
  public TypeNode getOriginalTypeNode() {
    return typeNode;
  }

  @Override
  public ExprRootNode defaultValue() {
    return initialValue;
  }

  @Override
  public Kind kind() {
    return Kind.STATE;
  }

  @Override
  public boolean isInjected() {
    return false;
  }

  public void setType(SoyType type) {
    if (this.type == null) {
      this.type = type;
    } else {
      throw new IllegalStateException("type has already been set.");
    }
  }

  @Override
  public boolean isRequired() {
    return true;
  }

  @Override
  public boolean isExplicitlyOptional() {
    return false;
  }

  @Override
  @Nullable
  public String desc() {
    return desc;
  }

  @Override
  public void setDesc(String desc) {
    this.desc = desc;
  }

  @Override
  public String toString() {
    StringBuilder description = new StringBuilder();
    description.append(getClass().getSimpleName());
    description.append("{name = ").append(name());
    description.append(", desc = ").append(desc).append("}");
    return description.toString();
  }

  @Override
  public TemplateStateVar copy(CopyState copyState) {
    return new TemplateStateVar(this, copyState);
  }
}
