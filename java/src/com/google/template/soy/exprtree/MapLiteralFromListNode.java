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

package com.google.template.soy.exprtree;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;

/**
 * A node representing a list to map conversion expr (e.g. "map(l)").
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class MapLiteralFromListNode extends AbstractParentExprNode {
  private final Identifier mapIdentifier;

  public MapLiteralFromListNode(
      Identifier ident, ExprNode listExpr, SourceLocation sourceLocation) {
    super(sourceLocation);
    mapIdentifier = ident;
    addChild(listExpr);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private MapLiteralFromListNode(MapLiteralFromListNode orig, CopyState copyState) {
    super(orig, copyState);
    this.mapIdentifier = orig.mapIdentifier;
  }

  @Override
  public ExprNode.Kind getKind() {
    return ExprNode.Kind.MAP_LITERAL_FROM_LIST_NODE;
  }

  /** Gets the listExpr in "map(listExpr)". */
  public ExprNode getListExpr() {
    return checkNotNull(getChild(0));
  }

  @Override
  public String toSourceString() {
    return String.format("map(%s)", getListExpr().toSourceString());
  }

  @Override
  public MapLiteralFromListNode copy(CopyState copyState) {
    return new MapLiteralFromListNode(this, copyState);
  }
}
