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

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;

/**
 * A node representing a list to map conversion expr (e.g. "map(l)").
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class MapLiteralFromListNode extends AbstractParentExprNode {
  public static final String KEY_STRING = "key";
  public static final String VALUE_STRING = "value";
  public static final ImmutableSet<String> MAP_RECORD_FIELDS =
      ImmutableSet.of(KEY_STRING, VALUE_STRING);

  private final Identifier mapIdentifier;
  private int nodeId;

  public MapLiteralFromListNode(
      Identifier ident, ExprNode listExpr, SourceLocation sourceLocation, int nodeId) {
    super(sourceLocation);
    mapIdentifier = ident;
    this.nodeId = nodeId;
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
    this.nodeId = orig.nodeId;
  }

  @Override
  public ExprNode.Kind getKind() {
    return ExprNode.Kind.MAP_LITERAL_FROM_LIST_NODE;
  }

  /** Gets the listExpr in "map(listExpr)". */
  public ExprNode getListExpr() {
    return checkNotNull(getChild(0));
  }

  public void setNodeId(int nodeId) {
    this.nodeId = nodeId;
  }

  public int getNodeId() {
    return nodeId;
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
