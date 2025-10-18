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
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.NamedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.ast.TypeNode;
import com.google.template.soy.types.ast.TypesHolderNode;

/**
 * Node representing a 'type' statement. The type definition will be added to a new {@link
 * SoyTypeRegistry} for file typedefs.
 */
public final class TypeDefNode extends AbstractCommandNode implements TypesHolderNode {
  private final Identifier name;

  /** The value of the type def. */
  private final TypeNode typeNode;

  private final boolean exported;

  public TypeDefNode(
      int id, SourceLocation location, Identifier name, TypeNode typeNode, boolean exported) {
    super(id, location, "type");
    this.name = name;
    this.typeNode = typeNode;
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
    this.typeNode = orig.typeNode.copy(copyState);
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
  @Override
  public TypeNode getTypeNode() {
    return typeNode;
  }

  /** Returns the type literal as a SoyType. */
  public SoyType getType() {
    return typeNode.getResolvedType();
  }

  public String getName() {
    return name.identifier();
  }

  public SourceLocation getNameLocation() {
    return name.location();
  }

  @Override
  public Kind getKind() {
    return Kind.TYPEDEF_NODE;
  }

  @Override
  public String toSourceString() {
    return String.format("{%s %s = %s}", isExported() ? "export type" : "type", name, typeNode);
  }

  @Override
  public TypeDefNode copy(CopyState copyState) {
    return new TypeDefNode(this, copyState);
  }

  public NamedType asNamedType() {
    return NamedType.create(
        name.identifier(),
        getNearestAncestor(SoyFileNode.class).getNamespace(),
        getNearestAncestor(SoyFileNode.class).getFilePath(),
        typeNode.getResolvedType());
  }
}
