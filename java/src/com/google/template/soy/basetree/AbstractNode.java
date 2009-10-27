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
 * Abstract implementation of a Node.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public abstract class AbstractNode implements Node {


  /** Just spaces. */
  protected static final String SPACES = "                                        ";


  /** The parent of this node. */
  private ParentNode<? extends Node> parent;


  protected AbstractNode() {
    parent = null;
  }


  @Override public void setParent(ParentNode<? extends Node> parent) {
    this.parent = parent;
  }

  @Override public ParentNode<? extends Node> getParent() {
    return parent;
  }


  @Override public boolean hasAncestor(Class<? extends Node> ancestorClass) {

    for (Node node = this; node != null; node = node.getParent()) {
      if (ancestorClass.isInstance(node)) {
        return true;
      }
    }
    return false;
  }


  @Override public <N extends Node> N getNearestAncestor(Class<N> ancestorClass) {

    for (Node node = this; node != null; node = node.getParent()) {
      if (ancestorClass.isInstance(node)) {
        return ancestorClass.cast(node);
      }
    }
    return null;
  }


  @Override public String toString() {
    return this.getClass().getSimpleName();
  }


  @Override public String toTreeString(int indent) {
    return SPACES.substring(0, indent) + "[" + toString() + "]\n";
  }

}
