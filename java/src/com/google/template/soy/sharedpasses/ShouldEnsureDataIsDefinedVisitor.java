/*
 * Copyright 2012 Google Inc.
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
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.SoytreeUtils;
import com.google.template.soy.soytree.SoytreeUtils.Shortcircuiter;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;

import java.util.List;

/**
 * Visitor for determining whether a template needs to ensure that its data is defined.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class ShouldEnsureDataIsDefinedVisitor {

  /**
   * Runs this pass on the given template.
   */
  public boolean exec(TemplateNode template) {

    // If there exists a required param, then data should already be defined (no need to ensure).
    List<TemplateParam> params = template.getParams();
    for (TemplateParam param : params) {
      if (param.isRequired()) {
        return false;
      }
    }

    // Run the ExistsRegDataRefInExprVisitor on all expressions in the template, shortcircuiting as
    // soon as we find one regular data ref.
    ExistsRegDataRefInExprVisitor helperVisitor = new ExistsRegDataRefInExprVisitor();

    SoytreeUtils.execOnAllV2ExprsShortcircuitably(
        template,
        helperVisitor,
        new Shortcircuiter<Void>() {
          @Override
          public boolean shouldShortcircuit(AbstractExprNodeVisitor<Void> exprNodeVisitor) {
            return ((ExistsRegDataRefInExprVisitor) exprNodeVisitor).foundRegDataRef();
          }
        });

    return helperVisitor.foundRegDataRef();
  }

  /**
   * Private helper class for ShouldEnsureDataIsDefinedVisitor to determine whether there exists a
   * regular data ref in an expression, where regular in this case means not injected and not local
   * var.
   *
   * <p> Note: This visitor assumes VarRefNodes in the expression are correctly marked as being
   * local var data refs as appropriate (i.e. variable name resolution has been performed).
   */
  private static class ExistsRegDataRefInExprVisitor extends AbstractExprNodeVisitor<Void> {

    /** Whether this visitor has found a regular data ref in all the exec() calls so far. */
    private boolean foundRegDataRef = false;

    public boolean foundRegDataRef() {
      return foundRegDataRef;
    }

    @Override protected void visitVarRefNode(VarRefNode node) {
      if (node.isPossibleParam()) {
        VarDefn var = node.getDefnDecl();
        // Don't include injected params in this analysis
        if (var.kind() != VarDefn.Kind.PARAM || !((TemplateParam) var).isInjected()) {
          foundRegDataRef = true;
        }
      }
    }

    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }
  }
}
