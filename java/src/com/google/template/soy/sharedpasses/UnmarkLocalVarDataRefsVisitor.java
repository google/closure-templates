/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.sharedpasses;

import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoytreeUtils;


/**
 * Visitor to unmark all DataRefNodes regarding whether they are actually local var data refs.
 * Unmarking is done by calling {@link DataRefNode#setIsLocalVarDataRef} with a value of null.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @see MarkLocalVarDataRefsVisitor
 */
public class UnmarkLocalVarDataRefsVisitor extends AbstractSoyNodeVisitor<Void> {


  @Override public Void exec(SoyNode node) {
    SoytreeUtils.execOnAllV2Exprs(node, new UnmarkLocalVarDataRefsInExprVisitor());
    return null;
  }


  /**
   * Helper visitor to unmark all DataRefNodes within an expression.
   */
  private static class UnmarkLocalVarDataRefsInExprVisitor extends AbstractExprNodeVisitor<Void> {

    // ------ Implementations for specific nodes. ------

    @Override protected void visitDataRefNode(DataRefNode node) {

      // Unmark whether the node references local var data.
      node.setIsLocalVarDataRef(null);

      // Important: Must visit children since children may be expressions that contain data refs.
      visitChildren(node);
    }

    // ------ Fallback implementation. ------

    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }
  }

}
