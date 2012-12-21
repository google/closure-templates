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


/**
 * This class defines the base interface for a node in the parse tree, as well as a number of
 * subinterfaces that extend the base interface in various aspects. Every concrete node implements
 * some subset of these interfaces.
 *
 * The top level definition is the base node interface.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public interface Node {


  /**
   * Sets this node's parent.
   * @param parent The parent node to set.
   */
  public void setParent(ParentNode<?> parent);


  /**
   * Gets this node's parent.
   * @return This node's parent.
   */
  public ParentNode<?> getParent();


  /**
   * Determines whether this node has an ancestor of the given type. The ancestor can be this node
   * (i.e. doesn't have to be a proper ancestor).
   * @param ancestorClass The type of ancestor to look for.
   * @return True if this node has an ancestor of the given type.
   */
  public boolean hasAncestor(Class<? extends Node> ancestorClass);


  /**
   * Finds and returns this node's nearest ancestor of the given type. The ancestor can be this node
   * (i.e. doesn't have to be a proper ancestor).
   * @param <N> The type of ancestor to retrieve.
   * @param ancestorClass The class object for the type of ancestor to retrieve.
   * @return This node's nearest ancestor of the given type, or null if none.
   */
  public <N extends Node> N getNearestAncestor(Class<N> ancestorClass);


  /**
   * Builds a Soy source string that could be the source for this node. Note that this is not the
   * actual original source string, but a (sort of) canonical equivalent.
   *
   * Note: Some nodes do not have a direct mapping to Soy source (such as nodes created during
   * some optimization passes). Thus this method may not always be supported.
   *
   * @return A Soy string that could be the source for this node.
   * @throws UnsupportedOperationException If this node does not directly map to Soy source.
   */
  public String toSourceString();


  /**
   * Builds a string that visually shows the subtree rooted at this node (for debugging).
   * Each line of the string will be indented by the given indentation amount. You should pass an
   * indentation of 0 unless this method is being called as part of building a larger tree string.
   * @param indent The indentation for each line of the tree string (usually pass 0).
   * @return A string that visually shows the subtree rooted at this node.
   */
  public String toTreeString(int indent);


  /**
   * Clones this node. The clone's parent pointer is set to null.
   * @return A clone of this code.
   */
  public Node clone();

}
