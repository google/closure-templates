/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.basetree;

import java.util.List;

/**
 * Base class for {@code AbstractXxxNodeVisitor} classes.
 *
 * <p>Same as {@link AbstractReturningNodeVisitor} except that in this class, internal {@code
 * visit()} calls do not return a value.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @param <N> A more specific subinterface of Node, or just Node if not applicable.
 * @param <R> The return type of this visitor.
 * @see AbstractReturningNodeVisitor
 */
public abstract class AbstractNodeVisitor<N extends Node, R> implements NodeVisitor<N, R> {
  private static final Node[] EMPTY_NODE_ARRAY = new Node[0];

  @Override
  public R exec(N node) {
    visit(node);
    return null;
  }

  /**
   * Visits the given node to execute the function defined by this visitor.
   *
   * @param node The node to visit.
   */
  protected abstract void visit(N node);

  /**
   * Helper to visit all the children of a node, in order.
   *
   * @param node The parent node whose children to visit.
   * @see #visitChildrenAllowingConcurrentModification
   */
  protected void visitChildren(ParentNode<? extends N> node) {
    List<? extends N> children = node.getChildren();
    int size = children.size();
    for (int i = 0; i < size; i++) {
      visit(children.get(i));
    }
  }

  /**
   * Helper to visit all the children of a node, in order.
   *
   * <p>This method differs from {@code visitChildren} in that we are iterating through a copy of
   * the children. Thus, concurrent modification of the list of children is allowed.
   *
   * @param node The parent node whose children to visit.
   * @see #visitChildren
   */
  protected void visitChildrenAllowingConcurrentModification(ParentNode<? extends N> node) {
    // use toArray to create a copy to avoid concurrent modification exception
    for (Node child : node.getChildren().toArray(EMPTY_NODE_ARRAY)) {
      // safe since the parent only contains subtypes of N.
      @SuppressWarnings("unchecked")
      N typedChild = (N) child;
      visit(typedChild);
    }
  }
}
