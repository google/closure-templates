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
 * A node that may have children in the parse tree.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> The parameter N represents the interface or class that is the superclass of all possible
 * children for this ParentNode. E.g. for a Soy parse tree node, N is usually SoyNode, but for
 * SoyFileSetNode N is SoyFileNode, for SoyFileNode N is TemplateNode, etc; for a Soy expression
 * parse tree, N is usually ExprNode.
 *
 */
public interface ParentNode<N extends Node> extends Node {


  /**
   * Gets the number of children.
   * @return The number of children.
   */
  public int numChildren();


  /**
   * Gets the child at the given index.
   * @param index The index of the child to get.
   * @return The child at the given index.
   */
  public N getChild(int index);


  /**
   * Finds the index of the given child.
   * @param child The child to find the index of.
   * @return The index of the given child, or -1 if the given child is not a child of this node.
   */
  public int getChildIndex(N child);


  /**
   * Gets the list of children.
   *
   * Note: The returned list may not be a copy. Please do not modify the list directly. Instead, use
   * the other methods in this class that are intended for modifying children. Also, if you're
   * iterating over the children list as you're modifying it, then you should first make a copy of
   * the children list to iterate over, in order to avoid ConcurrentModificationException.
   *
   * @return The list of children.
   */
  public List<N> getChildren();


  /**
   * Adds the given child.
   * @param child The child to add.
   */
  public void addChild(N child);


  /**
   * Adds the given child at the given index (shifting existing children if necessary).
   * @param index The index to add the child at.
   * @param child The child to add.
   */
  public void addChild(int index, N child);


  /**
   * Removes the child at the given index.
   * @param index The index of the child to remove.
   */
  public void removeChild(int index);


  /**
   * Removes the given child.
   * @param child The child to remove.
   */
  public void removeChild(N child);


  /**
   * Replaces the child at the given index with the given new child.
   * @param index The index of the child to replace.
   * @param newChild The new child.
   */
  public void replaceChild(int index, N newChild);


  /**
   * Replaces the given current child with the given new child.
   * @param currChild The current child to be replaced.
   * @param newChild The new child.
   */
  public void replaceChild(N currChild, N newChild);


  /**
   * Clears the list of children.
   */
  public void clearChildren();


  /**
   * Adds the given children.
   * @param children The children to add.
   */
  public void addChildren(List<? extends N> children);


  /**
   * Adds the given children at the given index (shifting existing children if necessary).
   * @param index The index to add the children at.
   * @param children The children to add.
   */
  public void addChildren(int index, List<? extends N> children);


  /**
   * Appends the source strings for all the children to the given StringBuilder.
   * @param sb The StringBuilder to which to append the children's source strings.
   */
  public void appendSourceStringForChildren(StringBuilder sb);


  /**
   * Appends the tree strings for all the children to the given StringBuilder, at one further
   * indentation level (3 spaces) than the given current indentation level.
   * @param sb The StringBuilder to which to append the children's tree strings.
   * @param indent The current indentation level of this parent node.
   */
  public void appendTreeStringForChildren(StringBuilder sb, int indent);

}
