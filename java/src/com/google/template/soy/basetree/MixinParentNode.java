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

import com.google.common.collect.Lists;

import java.util.List;


/**
 * Mixin implementation of the parent-specific aspect of the ParentNode interface.
 * Requires the master to be a ParentNode.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> The parameter N represents the interface or class that is the superclass of all possible
 * children for the master ParentNode. E.g. for a Soy parse tree node, N is usually SoyNode, but for
 * SoyFileSetNode N is SoyFileNode, for SoyFileNode N is TemplateNode, etc; for a Soy expression
 * parse tree, N is usually ExprNode.
 *
 * @author Kai Huang
 */
public final class MixinParentNode<N extends Node> {


  /** Just spaces. */
  protected static final String SPACES = "                                        ";


  /** The master node that delegates to this instance. */
  private final ParentNode<N> master;

  /** The children of the master node (accessed via this instance). */
  private final List<N> children;

  /** Whether the master node needs an env frame when being interpreted, or null if unknown. */
  private Boolean needsEnvFrameDuringInterp;


  /**
   * @param master The master node that delegates to this instance.
   */
  public MixinParentNode(ParentNode<N> master) {
    this.master = master;
    needsEnvFrameDuringInterp = null;
    children = Lists.newArrayList();
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   * @param newMaster The master node for the copy.
   */
  public MixinParentNode(MixinParentNode<N> orig, ParentNode<N> newMaster) {
    this.master = newMaster;

    this.needsEnvFrameDuringInterp = orig.needsEnvFrameDuringInterp;

    this.children = Lists.newArrayListWithCapacity(orig.children.size());
    for (N origChild : orig.children) {
      @SuppressWarnings("unchecked")
      N newChild = (N) origChild.clone();
      this.children.add(newChild);
      newChild.setParent(this.master);
    }
  }


  /**
   * Sets whether this node needs an env frame when the template is being interpreted.
   * @param needsEnvFrameDuringInterp Whether this node needs an env frame during interpretation,
   *     or null if unknown.
   */
  public void setNeedsEnvFrameDuringInterp(Boolean needsEnvFrameDuringInterp) {
    this.needsEnvFrameDuringInterp = needsEnvFrameDuringInterp;
  }


  /**
   * Returns whether this node needs an env frame during interpretation, or null if unknown.
   * @return Whether this node needs an env frame during interpretation, or null if unknown.
   */
  public Boolean needsEnvFrameDuringInterp() {
    return needsEnvFrameDuringInterp;
  }


  /**
   * Gets the number of children.
   * @return The number of children.
   */
  public int numChildren() {
    return children.size();
  }


  /**
   * Gets the child at the given index.
   * @param index The index of the child to get.
   * @return The child at the given index.
   */
  public N getChild(int index) {
    return children.get(index);
  }


  /**
   * Finds the index of the given child.
   * @param child The child to find the index of.
   * @return The index of the given child, or -1 if the given child is not a child of this node.
   */
  public int getChildIndex(N child) {
    return children.indexOf(child);
  }


  /**
   * Gets the list of children.
   *
   * Note: The returned list is not a copy. Please do not modify the list directly. Instead, use
   * the other methods in this class that are intended for modifying children. Also, if you're
   * iterating over the children list as you're modifying it, then you should first make a copy of
   * the children list to iterate over, in order to avoid ConcurrentModificationException.
   *
   * @return The list of children.
   */
  public List<N> getChildren() {
    return children;
  }


  /**
   * Adds the given child.
   * @param child The child to add.
   */
  public void addChild(N child) {
    children.add(child);
    child.setParent(master);
  }


  /**
   * Adds the given child at the given index (shifting existing children if necessary).
   * @param index The index to add the child at.
   * @param child The child to add.
   */
  public void addChild(int index, N child) {
    children.add(index, child);
    child.setParent(master);
  }


  /**
   * Removes the child at the given index.
   * @param index The index of the child to remove.
   */
  public void removeChild(int index) {
    N child = children.remove(index);
    child.setParent(null);
  }


  /**
   * Removes the given child.
   * @param child The child to remove.
   */
  public void removeChild(N child) {
    children.remove(child);
    child.setParent(null);
  }


  /**
   * Replaces the child at the given index with the given new child.
   * @param index The index of the child to replace.
   * @param newChild The new child.
   */
  public void replaceChild(int index, N newChild) {
    N oldChild = children.set(index, newChild);
    oldChild.setParent(null);
    newChild.setParent(master);
  }


  /**
   * Replaces the given current child with the given new child.
   * @param currChild The current child to be replaced.
   * @param newChild The new child.
   */
  public void replaceChild(N currChild, N newChild) {
    replaceChild(getChildIndex(currChild), newChild);
  }


  /**
   * Clears the list of children.
   */
  public void clearChildren() {
    children.clear();
  }


  /**
   * Adds the given children.
   * @param children The children to add.
   */
  public void addChildren(List<? extends N> children) {
    for (N child : children) {
      addChild(child);
    }
  }


  /**
   * Adds the given children at the given index (shifting existing children if necessary).
   * @param index The index to add the children at.
   * @param children The children to add.
   */
  public void addChildren(int index, List<? extends N> children) {
    List<N> origChildren = Lists.newArrayList(this.children);
    int origNumChildren = this.children.size();
    // Temporarily remove the original children from index onward (in reverse order).
    for (int i = origNumChildren - 1; i >= index; i--) {
      removeChild(i);
    }
    // Add the new children.
    addChildren(children);
    // Add back the original children that we temporarily removed (in correct order).
    addChildren(origChildren.subList(index, origNumChildren));
  }


  /**
   * Appends the source strings for all the children to the given StringBuilder.
   * @param sb The StringBuilder to which to append the children's source strings.
   */
  public void appendSourceStringForChildren(StringBuilder sb) {
    for (N child : children) {
      sb.append(child.toSourceString());
    }
  }


  /**
   * Appends the tree strings for all the children to the given StringBuilder, at one further
   * indentation level (3 spaces) than the given current indentation level.
   * @param sb The StringBuilder to which to append the children's tree strings.
   * @param indent The current indentation level of this parent node.
   */
  public void appendTreeStringForChildren(StringBuilder sb, int indent) {
    for (N child : children) {
      sb.append(child.toTreeString(indent + 3));
    }
  }


  /**
   * Builds a string that visually shows the subtree rooted at this node (for debugging).
   * Each line of the string will be indented by the given indentation amount. You should pass an
   * indentation of 0 unless this method is being called as part of building a larger tree string.
   * @param indent The indentation for each line of the tree string (usually pass 0).
   * @return A string that visually shows the subtree rooted at this node.
   */
  public String toTreeString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(SPACES.substring(0, indent)).append("[").append(master.toString()).append("]\n");
    appendTreeStringForChildren(sb, indent);
    return sb.toString();
  }

}
