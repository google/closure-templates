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

package com.google.template.soy.soytree;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;

/**
 * Node representing a 'param' with a value expression.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class CallParamValueNode extends CallParamNode implements ExprHolderNode {

  /** The parsed expression for the param value. */
  private final ExprRootNode valueExpr;

  public CallParamValueNode(int id, SourceLocation location, Identifier key, ExprNode valueExpr) {
    super(id, location, key);
    this.valueExpr = new ExprRootNode(valueExpr);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private CallParamValueNode(CallParamValueNode orig, CopyState copyState) {
    super(orig, copyState);
    this.valueExpr = orig.valueExpr.copy(copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.CALL_PARAM_VALUE_NODE;
  }

  /** Returns the parsed expression for the param value. */
  public ExprRootNode getExpr() {
    return valueExpr;
  }

  @Override
  public String getCommandText() {
    return getKey().identifier() + " : " + valueExpr.toSourceString();
  }

  @Override
  public String getTagString() {
    return getTagString(true); // self-ending
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return ImmutableList.of(valueExpr);
  }

  @Override
  public CallParamValueNode copy(CopyState copyState) {
    return new CallParamValueNode(this, copyState);
  }
}
