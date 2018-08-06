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
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.ast.TypeNode;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An explicitly declared template state variable.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
@Immutable
public final class TemplateStateVar extends AbstractVarDefn implements TemplateHeaderVarDefn {
  private final SourceLocation nameLocation;
  private final String desc;
  private final TypeNode typeNode;

  public TemplateStateVar(
      String name,
      SoyType type,
      TypeNode typeNode,
      @Nullable String desc,
      @Nullable SourceLocation nameLocation) {
    super(name, type);
    this.typeNode = typeNode;
    this.desc = desc;
    this.nameLocation = nameLocation;
  }

  TemplateStateVar(TemplateStateVar stateVar) {
    super(stateVar);
    this.typeNode = stateVar.typeNode;
    this.desc = stateVar.desc;
    this.nameLocation = stateVar.nameLocation;
  }

  public TypeNode typeNode() {
    return typeNode;
  }

  @Override
  public Kind kind() {
    return Kind.STATE;
  }

  @Override
  public boolean isInjected() {
    return false;
  }

  @Override
  public @Nullable SourceLocation nameLocation() {
    return nameLocation;
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
