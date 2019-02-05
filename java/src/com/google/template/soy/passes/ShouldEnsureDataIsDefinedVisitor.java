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

package com.google.template.soy.passes;

import com.google.template.soy.basetree.AbstractNodeVisitor;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;

/**
 * Visitor for determining whether a template needs to ensure that its data is defined.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class ShouldEnsureDataIsDefinedVisitor {

  /** Runs this pass on the given template. */
  public boolean exec(TemplateNode template) {

    boolean hasOptional = false;
    for (TemplateParam param : template.getParams()) {
      if (param.isRequired()) {
        // If there exists a required param, then data should already be defined (no need to
        // ensure).
        return false;
      } else {
        hasOptional = true;
      }
    }
    if (hasOptional) {
      // If all params are optional (and there is at least one), then we need to ensure data is
      // defined.  This is because the only legal way to have an optional param is if you reference
      // it somewhere in the template, so there is no need to check.
      return true;
    }
    // If we get here then the template has no declared params and we are observing a v1 compatible
    // template.  Search for things that could be data references:
    // * possibleParams
    // * data=All calls
    // others?
    return new AbstractNodeVisitor<Node, Boolean>() {
      boolean shouldEnsureDataIsDefined;

      @Override
      public Boolean exec(Node node) {
        visit(node);
        return shouldEnsureDataIsDefined;
      }

      @Override
      public void visit(Node node) {
        if (node instanceof VarRefNode) {
          VarRefNode varRefNode = (VarRefNode) node;
          VarDefn var = varRefNode.getDefnDecl();
          // Don't include injected params in this analysis
          if (varRefNode.isPossibleHeaderVar()
              && var.kind() != VarDefn.Kind.STATE
              && (var.kind() != VarDefn.Kind.PARAM // a soydoc param -> not ij
                  || !((TemplateParam) var).isInjected())) { // an {@param but not {@inject
            shouldEnsureDataIsDefined = true;
            return;
          }
        }
        if (node instanceof CallNode) {
          if (((CallNode) node).isPassingAllData()) {
            shouldEnsureDataIsDefined = true;
            return;
          }
        }
        if (node instanceof ParentNode) {
          for (Node child : ((ParentNode<?>) node).getChildren()) {
            visit(child);
            if (shouldEnsureDataIsDefined) {
              return;
            }
          }
        }
        if (node instanceof ExprHolderNode) {
          for (ExprRootNode expr : ((ExprHolderNode) node).getExprList()) {
            visit(expr);
            if (shouldEnsureDataIsDefined) {
              return;
            }
          }
        }
      }
    }.exec(template);
  }
}
