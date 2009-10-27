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

import com.google.common.base.Preconditions;
import com.google.template.soy.basetree.AbstractNode;


/**
 * Abstract implementation of a SoyNode.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public abstract class AbstractSoyNode extends AbstractNode implements SoyNode {


  /** The id for this node. */
  private final String id;

  /** The syntax version of this node. Not final -- may be adjusted by subclass constructors. */
  private SyntaxVersion syntaxVersion;


  /**
   * @param id The id for this node.
   */
  protected AbstractSoyNode(String id) {
    Preconditions.checkNotNull(id);
    this.id = id;
    // Assumes this node follows V2 syntax. Subclass constructors can modify this value.
    syntaxVersion = SyntaxVersion.V2;
  }


  @Override public String getId() {
    return id;
  }

  @Override public SyntaxVersion getSyntaxVersion() {
    return syntaxVersion;
  }


  /**
   * If the given syntax version is lower than the current syntax version value, then lowers the
   * syntax version to the given value.
   * @param syntaxVersion The syntax version to drop down to, if it is lower.
   */
  protected void maybeSetSyntaxVersion(SyntaxVersion syntaxVersion) {
    if (this.syntaxVersion.compareTo(syntaxVersion) > 0) {
      this.syntaxVersion = syntaxVersion;
    }
  }


  @Override public ParentSoyNode<? extends SoyNode> getParent() {
    @SuppressWarnings("unchecked")  // cast involving type parameter
    ParentSoyNode<? extends SoyNode> parentCast =
        (ParentSoyNode<? extends SoyNode>) super.getParent();
    return parentCast;
  }


  @Override public String toString() {
    return super.toString() + "_" + id;
  }

}
