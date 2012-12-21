/*
 * Copyright 2009 Google Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.internalutils.DataUtils;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoytreeUtils;

import java.util.Map;

import javax.annotation.Nullable;


/**
 * Visitor for substituting values of compile-time globals and/or for checking that all globals are
 * defined by the compile-time globals map.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> To do substitution only, set {@code shouldAssertNoUnboundGlobals} to false in the
 * constructor. To do substitution and checking, set  {@code shouldAssertNoUnboundGlobals} to true.
 *
 * @author Kai Huang
 */
public class SubstituteGlobalsVisitor {


  /** Map from compile-time global name to value. */
  private Map<String, PrimitiveData> compileTimeGlobals;

  /** Whether to throw an exception if we encounter an unbound global. */
  private final boolean shouldAssertNoUnboundGlobals;


  /**
   * @param compileTimeGlobals Map from compile-time global name to value.
   * @param shouldAssertNoUnboundGlobals Whether to throw an exception if we encounter an unbound
   *     global.
   */
  public SubstituteGlobalsVisitor(
      @Nullable Map<String, PrimitiveData> compileTimeGlobals,
      boolean shouldAssertNoUnboundGlobals) {
    this.compileTimeGlobals = compileTimeGlobals;
    this.shouldAssertNoUnboundGlobals = shouldAssertNoUnboundGlobals;
  }


  /**
   * Runs this pass on the given Soy tree.
   */
  public void exec(SoyFileSetNode soyTree) {

    SoytreeUtils.execOnAllV2Exprs(soyTree, new SubstituteGlobalsInExprVisitor());
  }


  /**
   * Private helper class for SubstituteGlobalsVisitor to visit expressions.
   * This class does the real work.
   */
  @VisibleForTesting
  class SubstituteGlobalsInExprVisitor extends AbstractExprNodeVisitor<Void> {

    @Override protected void visitGlobalNode(GlobalNode node) {

      PrimitiveData value =
          (compileTimeGlobals != null) ? compileTimeGlobals.get(node.getName()) : null;

      if (value == null) {
        if (shouldAssertNoUnboundGlobals) {
          throw SoySyntaxException.createWithoutMetaInfo(
              "Found unbound global '" + node.getName() + "'.");
        }
        return;
      }

      // Replace this node with a primitive literal.
      node.getParent().replaceChild(node, DataUtils.convertPrimitiveDataToExpr(value));
    }

    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildrenAllowingConcurrentModification((ParentExprNode) node);
      }
    }
  }

}
