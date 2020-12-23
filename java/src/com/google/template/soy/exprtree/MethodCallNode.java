/*
 * Copyright 2019 Google Inc.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.Point;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.shared.restricted.SoyMethod;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import java.util.List;
import java.util.Optional;

/** A node representing a method call. (e.g. {@code $myString.length()}) */
public final class MethodCallNode extends DataAccessNode implements ExprNode.CallableExpr {

  public static MethodCallNode newWithPositionalArgs(
      ExprNode base,
      List<ExprNode> params,
      Identifier methodName,
      SourceLocation location,
      boolean isNullSafe) {
    MethodCallNode call =
        new MethodCallNode(
            base, isNullSafe, location, methodName, ParamsStyle.POSITIONAL, ImmutableList.of());
    call.addChildren(params);
    return call;
  }

  private final Identifier methodName;
  private final ParamsStyle paramsStyle;
  /** When paramsStyle is NAMED this contains the list of named parameters. Otherwise empty. */
  private final ImmutableList<Identifier> paramNames;

  /**
   * The resolved SoyMethod that corresponds to this node. Resolution occurs in
   * ResolveExpressionTypesPass after the types of the method received and the method arguments are
   * known. This will be null before that pass and set after that pass, as long as the method
   * resolves to exactly one match.
   */
  private SoyMethod method;

  /**
   * @param base The base expression that the method is called on.
   * @param methodName The name of the method.
   * @param sourceLocation The location of the method call expression, i.e. the dot, method name and
   *     parameters.
   * @param isNullSafe If true, checks during evaluation whether the base expression is null and
   *     returns null instead of causing an invalid dereference.
   */
  MethodCallNode(
      ExprNode base,
      boolean isNullSafe,
      SourceLocation sourceLocation,
      Identifier methodName,
      ParamsStyle paramsStyle,
      ImmutableList<Identifier> paramNames) {
    super(base, sourceLocation, isNullSafe);
    this.methodName = Preconditions.checkNotNull(methodName);
    this.paramNames = paramNames;
    this.paramsStyle = paramsStyle;
  }

  /** @param orig The node to copy */
  private MethodCallNode(MethodCallNode orig, CopyState copyState) {
    super(orig, copyState);
    this.methodName = orig.methodName;
    this.paramsStyle = orig.paramsStyle;
    this.paramNames = orig.paramNames;
    this.method = orig.method;
  }

  /** Returns the name of the method */
  public Identifier getMethodName() {
    return methodName;
  }

  public void setSoyMethod(SoyMethod method) {
    this.method = method;
  }

  public SoyMethod getSoyMethod() {
    checkState(method != null, "setSoyMethod() hasn't been called yet at " + getSourceLocation());
    return method;
  }

  /**
   * Returns the type of the base expression child, with |null removed if `nullSafe` is `true`.
   * NullSafeAccessNode should probably handle this automatically.
   */
  public SoyType getBaseType(boolean nullSafe) {
    SoyType type = getBaseExprChild().getType();
    if (nullSafe) {
      type = SoyTypes.tryRemoveNull(type);
    }
    return type;
  }

  /** Returns the method's parameters. */
  @Override
  public List<ExprNode> getParams() {
    return getChildren().subList(1, numChildren()); // First child is the method's base expr.
  }

  @Override
  public int numParams() {
    return numChildren() - 1;
  }

  /** Returns true if the methods have been resolved to exactly one SoySourceFunction. */
  public boolean isMethodResolved() {
    return method != null;
  }

  /**
   * @return source string for the part of the expression that calls the method - in other words,
   *     not including the base expression. This is intended for use in reporting errors.
   */
  @Override
  public String getSourceStringSuffix() {
    StringBuilder sb = new StringBuilder();
    sb.append(isNullSafe ? "?." : ".").append(getMethodName().identifier()).append('(');

    for (int i = 1; i < numChildren(); i++) {
      if (i > 1) {
        sb.append(", ");
      }
      sb.append(getChild(i).toSourceString());
    }
    sb.append(")");
    return sb.toString();
  }

  @Override
  public Kind getKind() {
    return Kind.METHOD_CALL_NODE;
  }

  @Override
  public ExprNode copy(CopyState copyState) {
    return new MethodCallNode(this, copyState);
  }

  @Override
  public ParamsStyle getParamsStyle() {
    return paramsStyle;
  }

  @Override
  public ImmutableList<Identifier> getParamNames() {
    Preconditions.checkState(paramsStyle != ParamsStyle.POSITIONAL);
    return paramNames;
  }

  @Override
  public Optional<ImmutableList<Point>> getCommaLocations() {
    return Optional.empty();
  }

  @Override
  public Identifier getIdentifier() {
    return methodName;
  }
}
