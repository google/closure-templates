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

package com.google.template.soy.parsepasses;

import com.google.common.base.Preconditions;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;


/**
 * Visitor for prepending namespaces to the partial callee names in {@code CallBasicNode}s to make
 * full callee names.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} should be called on a full parse tree or a Soy file. This pass mutates
 * {@code CallBasicNode}s. There is no return value.
 *
 */
public class PrependNamespacesToCalleeNamesVisitor extends AbstractSoyNodeVisitor<Void> {


  /** The namespace of the current file that we're in (during a visit pass). */
  private String currNamespace;


  @Override public Void exec(SoyNode soyNode) {
    Preconditions.checkArgument(
        soyNode instanceof SoyFileSetNode || soyNode instanceof SoyFileNode);
    return super.exec(soyNode);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitSoyFileNode(SoyFileNode node) {
    currNamespace = node.getNamespace();
    if (currNamespace != null) {
      visitChildren(node);
    }
    currNamespace = null;
  }


  @Override protected void visitCallBasicNode(CallBasicNode node) {
    Preconditions.checkState(currNamespace != null);

    if (node.getCalleeName() == null) {
      node.setCalleeName(currNamespace + node.getPartialCalleeName());
    }

    visitChildren(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

}
