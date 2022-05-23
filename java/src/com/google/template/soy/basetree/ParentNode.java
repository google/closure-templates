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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;

/**
 * A node that may have children in the parse tree.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>The parameter N represents the interface or class that is the superclass of all possible
 * children for this ParentNode. E.g. for a Soy parse tree node, N is usually SoyNode, but for
 * SoyFileSetNode N is SoyFileNode, for SoyFileNode N is TemplateNode, etc; for a Soy expression
 * parse tree, N is usually ExprNode.
 */
public interface ParentNode<N extends Node> extends Node {

  /**
   * Gets the number of children.
   *
   * @return The number of children.
   */
  int numChildren();

  /**
   * Gets the child at the given index.
   *
   * @param index The index of the child to get.
   * @return The child at the given index.
   */
  N getChild(int index);

  /**
   * Finds the index of the given child.
   *
   * @param child The child to find the index of.
   * @return The index of the given child, or -1 if the given child is not a child of this node.
   */
  int getChildIndex(Node child);

  /**
   * Gets the list of children.
   *
   * <p>Note: The returned list may not be a copy. Please do not modify the list directly. Instead,
   * use the other methods in this class that are intended for modifying children. Also, if you're
   * iterating over the children list as you're modifying it, then you should first make a copy of
   * the children list to iterate over, in order to avoid ConcurrentModificationException.
   *
   * @return The list of children.
   */
  List<N> getChildren();

  default <T extends N> ImmutableList<T> getChildrenOfType(
      ParentNode<? super T> root, Class<T> type) {
    return root.getChildren().stream()
        .filter(type::isInstance)
        .map(type::cast)
        .collect(toImmutableList());
  }

  default <T extends N> Optional<T> getChildOfType(ParentNode<? super T> root, Class<T> type) {
    return root.getChildren().stream().filter(type::isInstance).map(type::cast).findFirst();
  }

  /**
   * Adds the given child.
   *
   * @param child The child to add.
   */
  void addChild(N child);

  /**
   * Adds the given child at the given index (shifting existing children if necessary).
   *
   * @param index The index to add the child at.
   * @param child The child to add.
   */
  void addChild(int index, N child);

  /**
   * Removes the child at the given index.
   *
   * @param index The index of the child to remove.
   */
  void removeChild(int index);

  /**
   * Removes the given child.
   *
   * @param child The child to remove.
   */
  void removeChild(N child);

  /**
   * Replaces the child at the given index with the given new child.
   *
   * @param index The index of the child to replace.
   * @param newChild The new child.
   */
  void replaceChild(int index, N newChild);

  /**
   * Replaces the given current child with the given new child.
   *
   * @param currChild The current child to be replaced.
   * @param newChild The new child.
   */
  void replaceChild(N currChild, N newChild);

  /** Clears the list of children. */
  void clearChildren();

  /**
   * Adds the given children.
   *
   * @param children The children to add.
   */
  void addChildren(List<? extends N> children);

  /**
   * Adds the given children at the given index (shifting existing children if necessary).
   *
   * @param index The index to add the children at.
   * @param children The children to add.
   */
  void addChildren(int index, List<? extends N> children);

  /**
   * Appends the source strings for all the children to the given StringBuilder.
   *
   * @param sb The StringBuilder to which to append the children's source strings.
   */
  void appendSourceStringForChildren(StringBuilder sb);
}
