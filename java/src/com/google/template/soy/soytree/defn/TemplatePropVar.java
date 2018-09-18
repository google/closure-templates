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
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.ast.TypeNode;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An explicitly declared template prop variable.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
@Immutable
public final class TemplatePropVar extends AbstractVarDefn implements TemplateHeaderVarDefn {
  private final String desc;
  @Nullable private final TypeNode typeNode;
  private final ExprRootNode initialValue;

  public TemplatePropVar(
      String name,
      @Nullable SoyType type,
      @Nullable TypeNode typeNode,
      ExprNode initialValue,
      @Nullable String desc,
      @Nullable SourceLocation nameLocation) {
    super(name, nameLocation, type);
    this.typeNode = typeNode;
    this.desc = desc;
    this.initialValue = new ExprRootNode(initialValue);
  }

  TemplatePropVar(TemplatePropVar propVar) {
    super(propVar);
    this.typeNode = propVar.typeNode;
    this.desc = propVar.desc;
    this.initialValue = propVar.initialValue;
  }

  public TypeNode typeNode() {
    return typeNode;
  }

  public ExprRootNode initialValue() {
    return initialValue;
  }

  @Override
  public Kind kind() {
    return Kind.PROP;
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
  public @Nullable String desc() {
    return desc;
  }

  @Override
  public String toString() {
    StringBuilder description = new StringBuilder();
    description.append(getClass().getSimpleName());
    description.append("{name = ").append(name());
    description.append(", desc = ").append(desc).append("}");
    return description.toString();
  }
}
