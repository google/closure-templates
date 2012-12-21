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

package com.google.template.soy.exprtree;


/**
 * Node representing an access using brackets containing an expression.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> For each instance of this node, the expression within the brackets should be its only child.
 *
 * @author Kai Huang
 */
public class DataRefAccessExprNode extends DataRefAccessNode {


  /**
   * @param isNullSafe Whether this access first checks that the left side is defined and nonnull.
   * @param expr The child expression.
   */
  public DataRefAccessExprNode(boolean isNullSafe, ExprNode expr) {
    super(isNullSafe);
    this.addChild(expr);
  }


  protected DataRefAccessExprNode(DataRefAccessExprNode orig) {
    super(orig);
  }


  @Override public Kind getKind() {
    return Kind.DATA_REF_ACCESS_EXPR_NODE;
  }


  @Override public String toSourceString() {
    return (isNullSafe() ? "?" : "") + "[" + getChild(0).toSourceString() + "]";
  }


  @Override public DataRefAccessExprNode clone() {
    return new DataRefAccessExprNode(this);
  }

}
