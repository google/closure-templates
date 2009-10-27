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
 * Abstract implementation of a ParentNode.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public abstract class AbstractParentNode<N extends Node>
    extends AbstractNode implements ParentNode<N> {


  /** The mixin object that implements the ParentNode functionality. */
  private final MixinParentNode<N> parentMixin;


  /** Constructor. */
  public AbstractParentNode() {
    parentMixin = new MixinParentNode<N>(this);
  }


  @Override public int numChildren() {
    return parentMixin.numChildren();
  }

  @Override public N getChild(int index) {
    return parentMixin.getChild(index);
  }

  @Override public int getChildIndex(N child) {
    return parentMixin.getChildIndex(child);
  }

  @Override public List<N> getChildren() {
    return parentMixin.getChildren();
  }

  @Override public void addChild(N child) {
    parentMixin.addChild(child);
  }

  @Override public void addChild(int index, N child) {
    parentMixin.addChild(index, child);
  }

  @Override public void removeChild(int index) {
    parentMixin.removeChild(index);
  }

  @Override public void removeChild(N child) {
    parentMixin.removeChild(child);
  }

  @Override public void setChild(int index, N newChild) {
    parentMixin.setChild(index, newChild);
  }

  @Override public void clearChildren() {
    parentMixin.clearChildren();
  }

  @Override public void addChildren(List<? extends N> children) {
    parentMixin.addChildren(children);
  }

  @Override public void appendSourceStringForChildren(StringBuilder sb) {
    parentMixin.appendSourceStringForChildren(sb);
  }

  @Override public void appendTreeStringForChildren(StringBuilder sb, int indent) {
    parentMixin.appendTreeStringForChildren(sb, indent);
  }

  @Override public String toTreeString(int indent) {
    return parentMixin.toTreeString(indent);
  }

}
