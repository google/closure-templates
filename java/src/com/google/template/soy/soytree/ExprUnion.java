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

import com.google.common.collect.Lists;
import com.google.template.soy.exprtree.ExprRootNode;

import java.util.List;

import javax.annotation.Nullable;


/**
 * Represents a Soy expression in either V2 or V1 syntax.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * If this expression is in V2 syntax, then {@link #getExpr()} will return a nonnull expression
 * tree. If this expression is in V1 syntax, then {@code #getExpr()} will return null. In either
 * case, the expression text can be obtained from {@link #getExprText()}.
 *
 */
public class ExprUnion {


  /**
   * Utility to create a list of {@code ExprUnion}s from a list of expression trees.
   * @param exprs The list of expression trees.
   * @return A new list of corresponding {@code ExprUnion}s.
   */
  public static List<ExprUnion> createList(List<? extends ExprRootNode<?>> exprs) {
    List<ExprUnion> exprUnions = Lists.newArrayListWithCapacity(exprs.size());
    for (ExprRootNode<?> expr : exprs) {
      exprUnions.add(new ExprUnion(expr));
    }
    return exprUnions;
  }


  /** The expression tree, or null if the expression is in V1 syntax. */
  @Nullable private final ExprRootNode<?> expr;

  /** The V1 expression text, or null if the expression is in V2 syntax. */
  @Nullable private final String exprText;


  /**
   * Constructor for an instance that represents a V2 expression.
   * @param expr The expression tree.
   */
  public ExprUnion(ExprRootNode<?> expr) {
    this.expr = expr;
    this.exprText = null;
  }


  /**
   * Constructor for an instance that represents an expression in V1 syntax.
   * @param exprTextV1 The text of the V1 expression.
   */
  public ExprUnion(String exprTextV1) {
    this.expr = null;
    this.exprText = exprTextV1;
  }


  /**
   * Returns the expression tree if the expression is in V2 syntax, else null.
   */
  public ExprRootNode<?> getExpr() {
    return expr;
  }


  /**
   * Returns the expression text. This method works for both V2 and V1 expressions.
   */
  public String getExprText() {
    return (expr != null) ? expr.toSourceString() : exprText;
  }


  /**
   * Returns a (deep) clone of this object.
   */
  @Override public ExprUnion clone() {
    return (expr != null) ? new ExprUnion(expr.clone()) : new ExprUnion(exprText);
  }

}
