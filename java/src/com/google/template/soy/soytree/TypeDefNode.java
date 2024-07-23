/*
 * Copyright 2024 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.TypeLiteralNode;
import com.google.template.soy.types.SoyType;

/**
 * Node representing a 'type' statement. The type definition will be added to a new {@link
 * SoyTypeRegistry} for file typedefs.
 */
public final class TypeDefNode extends AbstractSoyNode {
  private final String name;

  /** Expression root node that holds the type literal. */
  private final ExprRootNode valueExpr;

  private final boolean exported;

  public TypeDefNode(
      int id,
      SourceLocation location,
      String varName,
      SourceLocation varNameLocation,
      TypeLiteralNode expr,
      boolean exported) {
    super(id, location);
    this.name = varName;
    this.valueExpr = new ExprRootNode(expr);
    this.exported = exported;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private TypeDefNode(TypeDefNode orig, CopyState copyState) {
    super(orig, copyState);
    this.name = orig.name;
    this.valueExpr = orig.valueExpr.copy(copyState);
    this.exported = orig.exported;
  }

  public boolean isExported() {
    return exported;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  /** Returns the type literal as a SoyType. */
  public ExprRootNode getExpr() {
    return valueExpr;
  }

  /** Returns the type literal as a SoyType. */
  public SoyType getType() {
    return valueExpr.getType();
  }

  public String getName() {
    return name;
  }

  @Override
  public Kind getKind() {
    return Kind.TYPEDEF_NODE;
  }

  @Override
  public String toSourceString() {
    return String.format("{type %s = %s}", name, valueExpr.toSourceString());
  }

  @Override
  public TypeDefNode copy(CopyState copyState) {
    return new TypeDefNode(this, copyState);
  }
}
