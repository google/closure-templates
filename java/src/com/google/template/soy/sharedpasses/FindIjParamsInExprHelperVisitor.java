/*
 * Copyright 2010 Google Inc.
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

import com.google.common.collect.Sets;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;

import java.util.Set;


/**
 * Package-private helper for FindIjParamsVisitor and IsUsingIjDataVisitor to find the set of
 * injected params used in an expression.
 *
 * @author Kai Huang
 */
class FindIjParamsInExprHelperVisitor extends AbstractExprNodeVisitor<Set<String>> {


  /** The set of used injected params found so far. */
  private final Set<String> usedIjParamsInExpr;


  public FindIjParamsInExprHelperVisitor() {
    // Must initialize values here instead of in setup() since we call exec() multiple times on
    // one instance of this class, and we need to keep state across those calls to exec().
    usedIjParamsInExpr = Sets.newHashSet();
  }


  @Override public Set<String> exec(ExprNode node) {
    visit(node);
    return getResult();
  }


  /**
   * Gets the set of used injected params found so far.
   * @return The set of used injected params found so far.
   */
  public Set<String> getResult() {
    return usedIjParamsInExpr;
  }


  // ------ Implementations for specific nodes. ------


  @Override protected void visitDataRefNode(DataRefNode node) {

    if (node.isIjDataRef()) {
      usedIjParamsInExpr.add(node.getFirstKey());
    }

    visitChildren(node);
  }


  // ------ Fallback implementation. ------


  @Override protected void visitExprNode(ExprNode node) {
    if (node instanceof ParentExprNode) {
      visitChildren((ParentExprNode) node);
    }
  }

}
