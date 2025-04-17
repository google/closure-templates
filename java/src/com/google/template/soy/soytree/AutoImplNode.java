/*
 * Copyright 2021 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.StackContextNode;

/** Java implementation for an extern. */
public final class AutoImplNode extends AbstractBlockCommandNode
    implements ExternImplNode, StackContextNode {

  public AutoImplNode(int id, SourceLocation sourceLocation, SourceLocation openTagLocation) {
    super(id, sourceLocation, openTagLocation, "autoimpl");
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private AutoImplNode(AutoImplNode orig, CopyState copyState) {
    super(orig, copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.AUTO_IMPL_NODE;
  }

  @Override
  public AutoImplNode copy(CopyState copyState) {
    return new AutoImplNode(this, copyState);
  }

  @Override
  public ExternNode getParent() {
    return (ExternNode) super.getParent();
  }

  @Override
  public StackTraceElement createStackTraceElement(SourceLocation srcLocation) {
    SoyFileNode file = getNearestAncestor(SoyFileNode.class);
    return new StackTraceElement(
        /* declaringClass= */ file.getNamespace(),
        /* methodName= */ getParent().getIdentifier().identifier(),
        srcLocation.getFileName(),
        srcLocation.getBeginLine());
  }
}
