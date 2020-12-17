/*
 * Copyright 2020 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.Point;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.exprtree.ExprNode.CallableExpr.ParamsStyle;
import java.util.List;

/**
 * Builds {@link MethodCallNode} and {@link FunctionNode} and converts between the two.
 *
 * <p>Note that calling {@link #buildFunction()} or {@link #buildMethod()} will mutate the nodes
 * passed to {@link #builder(FunctionNode)} or {@link #builder(MethodCallNode)}.
 */
public final class CallableExprBuilder {

  private Identifier identifier;
  private SourceLocation sourceLocation;
  private List<Identifier> paramNames;
  private List<ExprNode> paramValues;
  private List<Point> commaLocations;
  private boolean isNullSafe;
  private ExprNode target;
  private ExprNode functionExpr;

  public static CallableExprBuilder builder() {
    return new CallableExprBuilder();
  }

  public static CallableExprBuilder builder(MethodCallNode from) {
    return new CallableExprBuilder().setTarget(from.getBaseExprChild()).fillFrom(from);
  }

  public static CallableExprBuilder builder(FunctionNode from) {
    CallableExprBuilder builder = new CallableExprBuilder().fillFrom(from);
    if (!from.hasStaticName()) {
      builder.setFunctionExpr(from.getNameExpr());
    }
    return builder;
  }

  private CallableExprBuilder() {}

  private CallableExprBuilder fillFrom(ExprNode.CallableExpr from) {
    setSourceLocation(from.getSourceLocation());
    setIdentifier(from.getIdentifier());
    setParamValues(from.getParams());
    setCommaLocations(from.getCommaLocations().orElse(null));
    switch (from.getParamsStyle()) {
      case NAMED:
        setParamNames(from.getParamNames());
        break;
      default:
    }
    return this;
  }

  public CallableExprBuilder setIdentifier(Identifier identifier) {
    this.identifier = identifier;
    return this;
  }

  public CallableExprBuilder setFunctionExpr(ExprNode functionExpr) {
    this.functionExpr = functionExpr;
    return this;
  }

  public CallableExprBuilder setSourceLocation(SourceLocation sourceLocation) {
    this.sourceLocation = sourceLocation;
    return this;
  }

  public CallableExprBuilder setParamNames(List<Identifier> paramNames) {
    this.paramNames = paramNames;
    return this;
  }

  public CallableExprBuilder setParamValues(List<ExprNode> paramValues) {
    this.paramValues = paramValues;
    return this;
  }

  public CallableExprBuilder setCommaLocations(List<Point> commaLocations) {
    this.commaLocations = commaLocations;
    return this;
  }

  public CallableExprBuilder setNullSafe(boolean nullSafe) {
    isNullSafe = nullSafe;
    return this;
  }

  public CallableExprBuilder setTarget(ExprNode target) {
    this.target = target;
    return this;
  }

  private ParamsStyle buildParamsStyle() {
    if (paramValues.isEmpty()) {
      Preconditions.checkState(paramNames == null || paramNames.isEmpty());
      return ParamsStyle.NONE;
    } else if (paramNames != null) {
      Preconditions.checkState(paramValues.size() == paramNames.size());
      return ParamsStyle.NAMED;
    } else {
      return ParamsStyle.POSITIONAL;
    }
  }

  public MethodCallNode buildMethod() {
    Preconditions.checkState(target != null);

    MethodCallNode node =
        new MethodCallNode(
            target,
            isNullSafe,
            sourceLocation,
            identifier,
            buildParamsStyle(),
            paramNames != null ? ImmutableList.copyOf(paramNames) : ImmutableList.of());

    node.addChildren(paramValues);
    return node;
  }

  public FunctionNode buildFunction() {
    Preconditions.checkState(target == null);
    Preconditions.checkState(!isNullSafe);

    FunctionNode node =
        new FunctionNode(
            sourceLocation,
            identifier,
            functionExpr,
            buildParamsStyle(),
            paramNames != null ? ImmutableList.copyOf(paramNames) : ImmutableList.of(),
            commaLocations != null ? ImmutableList.copyOf(commaLocations) : null);

    node.addChildren(paramValues);
    return node;
  }
}
