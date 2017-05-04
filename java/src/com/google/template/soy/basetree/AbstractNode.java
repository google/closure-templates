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

import javax.annotation.Nullable;

/**
 * Abstract implementation of a Node.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public abstract class AbstractNode implements Node {

  /** The lowest known upper bound (exclusive!) for the syntax version of this node. */
  @Nullable private SyntaxVersionUpperBound syntaxVersionBound;

  /** The parent of this node. */
  private ParentNode<?> parent;

  protected AbstractNode() {
    syntaxVersionBound = null;
    parent = null;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  protected AbstractNode(AbstractNode orig, CopyState copyState) {
    this.parent = null; // important: should not copy parent pointer
    this.syntaxVersionBound = orig.syntaxVersionBound;
  }

  @Override
  public void maybeSetSyntaxVersionUpperBound(SyntaxVersionUpperBound newSyntaxVersionBound) {
    syntaxVersionBound =
        SyntaxVersionUpperBound.selectLower(syntaxVersionBound, newSyntaxVersionBound);
  }

  @Override
  @Nullable
  public SyntaxVersionUpperBound getSyntaxVersionUpperBound() {
    return syntaxVersionBound;
  }

  @Override
  public boolean couldHaveSyntaxVersionAtLeast(SyntaxVersion syntaxVersionCutoff) {
    return syntaxVersionBound == null
        || syntaxVersionBound.syntaxVersion.num > syntaxVersionCutoff.num;
  }

  @Override
  public void setParent(ParentNode<?> parent) {
    this.parent = parent;
  }

  @Override
  public ParentNode<?> getParent() {
    return parent;
  }

  @Override
  public boolean hasAncestor(Class<? extends Node> ancestorClass) {

    for (Node node = this; node != null; node = node.getParent()) {
      if (ancestorClass.isInstance(node)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public <N extends Node> N getNearestAncestor(Class<N> ancestorClass) {

    for (Node node = this; node != null; node = node.getParent()) {
      if (ancestorClass.isInstance(node)) {
        return ancestorClass.cast(node);
      }
    }
    return null;
  }

  @Override
  public final int hashCode() {
    return super.hashCode();
  }

  @Override
  public final boolean equals(Object other) {
    return super.equals(other);
  }

  @Override
  public String toString() {
    String sourceString = toSourceString();
    sourceString =
        sourceString.length() > 30 ? sourceString.substring(0, 30) + "..." : sourceString;
    return this.getClass().getSimpleName() + "<" + sourceString + ">";
  }
}
