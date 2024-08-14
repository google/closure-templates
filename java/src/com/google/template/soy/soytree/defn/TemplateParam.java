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
import com.google.template.soy.exprtree.AbstractVarDefn;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.types.IndexedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.ast.TypeNode;
import javax.annotation.Nullable;

/**
 * An explicitly declared template parameter.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public class TemplateParam extends AbstractVarDefn implements TemplateHeaderVarDefn {
  private final TypeNode typeNode;
  private String desc;
  private final SourceLocation sourceLocation;

  /** Whether the param is an injected param. */
  private final boolean isInjected;

  /**
   * Whether the param is implicitly added by the Soy framework. These are omitted from the Java
   * invocation builders and the template JsDoc declarations.
   */
  private final boolean isImplicit;

  /** Whether the param is declared with ?. */
  private final boolean isExplicitlyOptional;

  @Nullable private ExprRootNode defaultValue;

  public TemplateParam(
      String name,
      SourceLocation nameLocation,
      SourceLocation sourceLocation,
      @Nullable TypeNode typeNode,
      boolean isInjected,
      boolean isImplicit,
      boolean optional,
      @Nullable String desc,
      @Nullable ExprNode defaultValue) {
    super(name, nameLocation, /* type= */ null);
    this.isInjected = isInjected;
    this.isImplicit = isImplicit;
    this.desc = desc;
    this.defaultValue = defaultValue == null ? null : new ExprRootNode(defaultValue);
    this.sourceLocation = sourceLocation;
    this.isExplicitlyOptional = optional;
    this.typeNode = typeNode;
  }

  protected TemplateParam(TemplateParam param, CopyState copyState) {
    super(param);
    this.typeNode = param.typeNode == null ? null : param.typeNode.copy();
    this.isInjected = param.isInjected;
    this.isImplicit = param.isImplicit;
    this.sourceLocation = param.sourceLocation;
    this.desc = param.desc;
    this.isExplicitlyOptional = param.isExplicitlyOptional;
    this.defaultValue = param.defaultValue == null ? null : param.defaultValue.copy(copyState);
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
  @Override
  public TypeNode getTypeNode() {
    return typeNode;
  }

  /** Returns whether the param is an injected (declared with {@code @inject}) or not. */
  @Override
  public boolean isInjected() {
    return isInjected;
  }

  /** Returns whether the param is implicit */
  public boolean isImplicit() {
    return this.isImplicit;
  }

  @Override
  public boolean isRequired() {
    return defaultValue == null
        && !isExplicitlyOptional
        // Special support for indexed types implying optional. Allows you to omit the '?' of
        // {param?} if the field of the original record type is optional.
        && !(type instanceof IndexedType && ((IndexedType) type).isOriginallyOptional());
  }

  @Override
  public boolean isExplicitlyOptional() {
    return isExplicitlyOptional;
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
  @Nullable
  public ExprRootNode defaultValue() {
    return defaultValue;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{name = " + name() + ", desc = " + desc + "}";
  }

  @Override
  public TemplateParam copy(CopyState copyState) {
    return new TemplateParam(this, copyState);
  }
}
