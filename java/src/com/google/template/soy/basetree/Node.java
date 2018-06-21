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

import com.google.template.soy.base.SourceLocation;

/**
 * This class defines the base interface for a node in the parse tree, as well as a number of
 * subinterfaces that extend the base interface in various aspects. Every concrete node implements
 * some subset of these interfaces.
 *
 * <p>The top level definition is the base node interface.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public interface Node {

  /** Returns the source location (file path and line number) for this node. */
  SourceLocation getSourceLocation();

  /**
   * Sets this node's parent.
   *
   * @param parent The parent node to set.
   */
  void setParent(ParentNode<?> parent);

  /**
   * Gets this node's parent.
   *
   * @return This node's parent.
   */
  ParentNode<?> getParent();

  /**
   * Determines whether this node has an ancestor of the given type. The ancestor can be this node
   * (i.e. doesn't have to be a proper ancestor).
   *
   * @param ancestorClass The type of ancestor to look for.
   * @return True if this node has an ancestor of the given type.
   */
  boolean hasAncestor(Class<? extends Node> ancestorClass);

  /**
   * Finds and returns this node's nearest ancestor of the given type. The ancestor can be this node
   * (i.e. doesn't have to be a proper ancestor).
   *
   * @param <N> The type of ancestor to retrieve.
   * @param ancestorClass The class object for the type of ancestor to retrieve.
   * @return This node's nearest ancestor of the given type, or null if none.
   */
  <N extends Node> N getNearestAncestor(Class<N> ancestorClass);

  /**
   * Builds a Soy source string that could be the source for this node. Note that this is not the
   * actual original source string, but a (SORT OF, NOT QUITE) canonical equivalent.
   *
   * <p>Note: Some nodes do not have a direct mapping to Soy source (such as nodes created during
   * some optimization passes). Thus this method may not always be supported.
   *
   * @return A Soy string that could be the source for this node.
   * @throws UnsupportedOperationException If this node does not directly map to Soy source.
   */
  String toSourceString();

  /**
   * Copies this node. The copy's parent pointer is set to null.
   *
   * <p>All copy() overrides should follow this contract:
   *
   * <ul>
   *   <li>only leaf classes (in the class hierarchy) should have non-abstract clone methods
   *   <li>all leaf classes should be final
   *   <li>all leaf copy constructors should be private
   *   <li>all clone methods should look exactly like:
   *       <pre>{@code
   * {@literal @}Override public T copy(CopyState copyState) {
   *   return new T(this, copyState);
   * }
   * }</pre>
   *
   *   <li>all non-leaf copy constructors should be protected
   * </ul>
   *
   * <p>TODO(lukes): The usecases for a copy method are few and far between. Making the AST nodes
   * immutable (or at least unmodifiable) would be preferable to maintaining our copy() methods.
   *
   * <p>Don't clone nodes unless you know what you're doing. The Soy AST is not actually a tree (it
   * contains back edges from variables to their definitions), and naively copying nodes can result
   * in pointers into stale ASTs
   *
   * @return A clone of this code.
   */
  Node copy(CopyState copyState);
}
