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
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.base.SoySyntaxException;


/**
 * Visitor for prepending namespaces to the names for {@code TemplateNode}s and {@code CallNode}s
 * (so that the resulting names are all full names instead of partial names).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} should be called on a full parse tree. The name attributes of 'template' and
 * 'call' nodes may be modified. There is no return value.
 *
 * @author Kai Huang
 */
public class PrependNamespacesVisitor extends AbstractSoyNodeVisitor<Void> {


  /** The namespace of the current file that we're in (during a visit pass). */
  private String currNamespace = null;


  @Override protected void setup() {
    currNamespace = null;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(SoyFileNode node) {

    currNamespace = node.getNamespace();
    visitChildren(node);
    currNamespace = null;
  }


  @Override protected void visitInternal(TemplateNode node) {

    if (currNamespace == null) {
      // If there's no namespace, template name must not start with a dot.
      if (node.getTemplateName().charAt(0) == '.') {
        throw new SoySyntaxException(
            "No namespace found in file " + ((SoyFileNode) node.getParent()).getFileName() + ".");
      }
      return;
    }

    String origTemplateName = node.getTemplateName();
    if (origTemplateName.charAt(0) == '.') {
      node.setTemplateName(currNamespace + origTemplateName);
    }

    visitChildren(node);
  }


  @Override protected void visitInternal(CallNode node) {
    Preconditions.checkState(currNamespace != null);

    String origCalleeName = node.getCalleeName();
    if (origCalleeName.charAt(0) == '.') {
      node.setCalleeName(currNamespace + origCalleeName);
    }

    visitChildren(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(SoyNode node) {
    // Nothing to do for non-parent node.
  }


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
    visitChildren(node);
  }

}
