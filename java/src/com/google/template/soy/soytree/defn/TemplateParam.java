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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.ast.TypeNode;
import javax.annotation.Nullable;

/**
 * An explicitly declared template parameter.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class TemplateParam extends AbstractVarDefn implements TemplateHeaderVarDefn {
  private final TypeNode typeNode;
  private final String desc;

  /** Whether the param is required. */
  private final boolean isRequired;

  /** Whether the param is an injected param. */
  private final boolean isInjected;

  @Nullable private final ExprRootNode defaultValue;

  public TemplateParam(
      String name,
      SourceLocation nameLocation,
      @Nullable TypeNode typeNode,
      boolean isRequired,
      boolean isInjected,
      @Nullable String desc,
      @Nullable ExprNode defaultValue) {
    super(name, nameLocation, /* type= */ null);
    this.typeNode = typeNode;
    this.isRequired = isRequired;
    this.isInjected = isInjected;
    this.desc = desc;
    this.defaultValue = defaultValue == null ? null : new ExprRootNode(defaultValue);
  }

  protected TemplateParam(TemplateParam param) {
    super(param);
    this.typeNode = param.typeNode == null ? null : param.typeNode.copy();
    this.isRequired = param.isRequired;
    this.isInjected = param.isInjected;
    this.desc = param.desc;
    this.defaultValue =
        param.defaultValue == null ? null : param.defaultValue.copy(new CopyState());
  }

  @Override
  public void setType(SoyType type) {
    checkState(this.type == null, "type has already been assigned");
    this.type = checkNotNull(type);
  }

  @Override
  public Kind kind() {
    return Kind.PARAM;
  }

  /**
   * Returns the TypeNode.
   *
   * <p>May be null if type parsing failed.
   */
  @Nullable
  public TypeNode getTypeNode() {
    return typeNode;
  }

  /** Returns whether the param is an injected (declared with {@code @inject}) or not. */
  @Override
  public boolean isInjected() {
    return isInjected;
  }

  @Override
  public boolean isRequired() {
    return isRequired;
  }

  @Override
  public @Nullable String desc() {
    return desc;
  }

  @Override
  @Nullable
  public ExprRootNode defaultValue() {
    return defaultValue;
  }

  public boolean hasDefault() {
    return defaultValue != null;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{name = " + name() + ", desc = " + desc + "}";
  }

  @Override
  public TemplateParam copy(CopyState copyState) {
    return new TemplateParam(this);
  }
}
