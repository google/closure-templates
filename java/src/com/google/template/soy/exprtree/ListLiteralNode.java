/*
 * Copyright 2011 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.OperatorNodes.SpreadOpNode;
import java.util.List;
import java.util.Optional;

/**
 * A node representing a list literal (with items as children).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class ListLiteralNode extends AbstractParentExprNode {

  private final Optional<ImmutableList<SourceLocation.Point>> commaLocations;

  /** @param items The expressions for the items in this list. */
  public ListLiteralNode(
      List<ExprNode> items,
      SourceLocation sourceLocation,
      List<SourceLocation.Point> commaLocations) {
    super(sourceLocation);
    addChildren(items);
    this.commaLocations = Optional.of(ImmutableList.copyOf(commaLocations));
  }

  /** @param items The expressions for the items in this list. */
  public ListLiteralNode(List<ExprNode> items, SourceLocation sourceLocation) {
    super(sourceLocation);
    addChildren(items);
    this.commaLocations = Optional.empty();
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private ListLiteralNode(ListLiteralNode orig, CopyState copyState) {
    super(orig, copyState);
    this.commaLocations = orig.commaLocations;
  }

  public Optional<ImmutableList<SourceLocation.Point>> getCommaLocations() {
    return commaLocations;
  }

  @Override
  public Kind getKind() {
    return Kind.LIST_LITERAL_NODE;
  }

  @Override
  public String toSourceString() {

    StringBuilder sourceSb = new StringBuilder();
    sourceSb.append('[');

    boolean isFirst = true;
    for (ExprNode child : getChildren()) {
      if (isFirst) {
        isFirst = false;
      } else {
        sourceSb.append(", ");
      }
      sourceSb.append(child.toSourceString());
    }

    sourceSb.append(']');
    return sourceSb.toString();
  }

  @Override
  public ListLiteralNode copy(CopyState copyState) {
    return new ListLiteralNode(this, copyState);
  }

  public boolean containsSpreads() {
    return getChildren().stream().anyMatch(SpreadOpNode.class::isInstance);
  }
}
