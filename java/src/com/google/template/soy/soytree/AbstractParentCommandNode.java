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

package com.google.template.soy.soytree;

import com.google.common.base.Predicate;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.MixinParentNode;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Abstract implementation of a ParentNode and CommandNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
abstract class AbstractParentCommandNode<N extends SoyNode> extends AbstractCommandNode
    implements ParentSoyNode<N> {

  /** The mixin object that implements the ParentNode functionality. */
  private final MixinParentNode<N> parentMixin;

  /**
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   * @param commandName The name of the Soy command.
   */
  public AbstractParentCommandNode(int id, SourceLocation sourceLocation, String commandName) {
    super(id, sourceLocation, commandName);
    parentMixin = new MixinParentNode<>(this);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  protected AbstractParentCommandNode(AbstractParentCommandNode<N> orig, CopyState copyState) {
    super(orig, copyState);
    this.parentMixin = new MixinParentNode<>(orig.parentMixin, this, copyState);
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    sb.append("{/").append(getCommandName()).append('}');
    return sb.toString();
  }

  @Override
  public int numChildren() {
    return parentMixin.numChildren();
  }

  @Override
  public N getChild(int index) {
    return parentMixin.getChild(index);
  }

  @Override
  public int getChildIndex(Node child) {
    return parentMixin.getChildIndex(child);
  }

  @Override
  public List<N> getChildren() {
    return parentMixin.getChildren();
  }

  @Override
  public void addChild(N child) {
    parentMixin.addChild(child);
  }

  @Override
  public void addChild(int index, N child) {
    parentMixin.addChild(index, child);
  }

  @Override
  public void removeChild(int index) {
    parentMixin.removeChild(index);
  }

  @Override
  public void removeChild(N child) {
    parentMixin.removeChild(child);
  }

  @Override
  public void replaceChild(int index, N newChild) {
    parentMixin.replaceChild(index, newChild);
  }

  @Override
  public void replaceChild(N currChild, N newChild) {
    parentMixin.replaceChild(currChild, newChild);
  }

  @Override
  public void clearChildren() {
    parentMixin.clearChildren();
  }

  @Override
  public void addChildren(List<? extends N> children) {
    parentMixin.addChildren(children);
  }

  @Override
  public void addChildren(int index, List<? extends N> children) {
    parentMixin.addChildren(index, children);
  }

  @Override
  public void appendSourceStringForChildren(StringBuilder sb) {
    parentMixin.appendSourceStringForChildren(sb);
  }

  /** Returns the template's first child node that matches the given condition. */
  @Nullable
  public SoyNode firstChildThatMatches(Predicate<SoyNode> condition) {
    int firstChildIndex = 0;
    while (firstChildIndex < numChildren() && !condition.apply(getChild(firstChildIndex))) {
      firstChildIndex++;
    }
    if (firstChildIndex < numChildren()) {
      return getChild(firstChildIndex);
    }
    return null;
  }

  /** Returns the template's last child node that matches the given condition. */
  @Nullable
  public SoyNode lastChildThatMatches(Predicate<SoyNode> condition) {
    int lastChildIndex = numChildren() - 1;
    while (lastChildIndex >= 0 && !condition.apply(getChild(lastChildIndex))) {
      lastChildIndex--;
    }
    if (lastChildIndex >= 0) {
      return getChild(lastChildIndex);
    }
    return null;
  }
}
