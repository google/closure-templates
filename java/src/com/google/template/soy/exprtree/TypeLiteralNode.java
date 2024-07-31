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

package com.google.template.soy.exprtree;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.ast.TypeNode;

/** Node representing a type literal. */
public final class TypeLiteralNode extends AbstractPrimitiveNode {

  public static VarRefNode error(SourceLocation location) {
    return new VarRefNode("$error", location, null);
  }

  /** The boolean value. */
  private final TypeNode typeNode;

  public TypeLiteralNode(TypeNode typeNode) {
    super(typeNode.sourceLocation());
    this.typeNode = typeNode;
  }

  private TypeLiteralNode(TypeLiteralNode orig, CopyState copyState) {
    super(orig, copyState);
    this.typeNode = orig.typeNode.copy();
  }

  public TypeNode getTypeNode() {
    return typeNode;
  }

  @Override
  public Kind getKind() {
    return Kind.TYPE_LITERAL_NODE;
  }

  @Override
  public SoyType getType() {
    return typeNode.getResolvedType();
  }

  @Override
  public String toSourceString() {
    return typeNode.toString();
  }

  @Override
  public TypeLiteralNode copy(CopyState copyState) {
    return new TypeLiteralNode(this, copyState);
  }
}
