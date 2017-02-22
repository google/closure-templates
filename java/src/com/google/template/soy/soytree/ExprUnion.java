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

package com.google.template.soy.soytree;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import java.util.List;

/**
 * Represents a Soy expression in either V2 or V1 syntax. Since Soy V1 expressions are going away,
 * this class will be removed from the Soy codebase soon.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class ExprUnion {

  // TODO(mknichel): Remove this class from Soy.

  /**
   * Utility to create a list of {@code ExprUnion}s from a list of expression trees.
   *
   * @param exprs The list of expression trees.
   * @return A new list of corresponding {@code ExprUnion}s.
   */
  public static List<ExprUnion> createList(List<? extends ExprRootNode> exprs) {
    List<ExprUnion> exprUnions = Lists.newArrayListWithCapacity(exprs.size());
    for (ExprRootNode expr : exprs) {
      exprUnions.add(new ExprUnion(expr));
    }
    return exprUnions;
  }

  private final ExprRootNode expr;

  /**
   * Constructor for an instance that represents a V2 expression.
   *
   * @param expr The expression tree.
   */
  public ExprUnion(ExprNode expr) {
    this(new ExprRootNode(Preconditions.checkNotNull(expr)));
  }

  /**
   * Constructor for an instance that represents a V2 expression.
   *
   * @param expr The expression tree.
   */
  public ExprUnion(ExprRootNode expr) {
    this.expr = Preconditions.checkNotNull(expr);
  }

  private ExprUnion(ExprUnion orig, CopyState copyState) {
    this.expr = orig.expr.copy(copyState);
  }

  /** Returns the expression tree if the expression is in V2 syntax, else null. */
  public ExprRootNode getExpr() {
    return expr;
  }

  /** Returns the expression text. This method works for both V2 and V1 expressions. */
  public String getExprText() {
    return expr.toSourceString();
  }

  /** Returns a (deep) clone of this object. */
  public ExprUnion copy(CopyState copyState) {
    return new ExprUnion(this, copyState);
  }
}
