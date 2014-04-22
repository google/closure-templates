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

package com.google.template.soy.basetree;

import com.google.common.collect.Lists;

import java.util.List;


/**
 * Base class for {@code AbstractXxxNodeVisitor} classes.
 *
 * <p> Same as {@link AbstractNodeVisitor} except that in this class, internal {@code visit()}
 * calls return a value.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @param <N> A more specific subinterface of Node, or just Node if not applicable.
 * @param <R> The return type of this visitor.
 *
 * @see AbstractNodeVisitor
 */
public abstract class AbstractReturningNodeVisitor<N extends Node, R>
    implements NodeVisitor<N, R> {


  @Override public R exec(N node) {
    return visit(node);
  }


  /**
   * Visits the given node to execute the function defined by this visitor.
   * @param node The node to visit.
   */
  protected abstract R visit(N node);


  /**
   * Helper to visit all the children of a node, in order.
   * @param node The parent node whose children to visit.
   * @return The list of return values from visiting the children.
   * @see #visitChildrenAllowingConcurrentModification
   */
  protected List<R> visitChildren(ParentNode<? extends N> node) {
    List<R> results = Lists.newArrayListWithCapacity(node.numChildren());
    for (N child : node.getChildren()) {
      results.add(visit(child));
    }
    return results;
  }


  /**
   * Helper to visit all the children of a node, in order.
   *
   * This method differs from {@code visitChildren} in that we are iterating through a copy of the
   * children. Thus, concurrent modification of the list of children is allowed.
   *
   * @param node The parent node whose children to visit.
   * @return The list of return values from visiting the children.
   * @see #visitChildren
   */
  protected List<R> visitChildrenAllowingConcurrentModification(ParentNode<? extends N> node) {
    List<R> results = Lists.newArrayListWithCapacity(node.numChildren());
    for (N child : Lists.newArrayList(node.getChildren()) /*copy*/) {
      results.add(visit(child));
    }
    return results;
  }

}
