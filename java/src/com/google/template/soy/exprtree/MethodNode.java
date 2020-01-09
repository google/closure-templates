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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import java.util.List;

/** A node representing a method call. (e.g. {@code $myString.length()}) */
public final class MethodNode extends DataAccessNode {

  private final Identifier methodName;

  /**
   * The SoySourceFunctions that correspond to this node. There could be multiple methods
   * corresponding to the node when there are multiple method SoySourceFunctions defined with the
   * same name but different base type. The method resolution occurs before we know the type of the
   * base expression, so the different implementations are stored until the base type is determined.
   * If the type of the base expression can be determined at compile time, the list will be left
   * with one SoySourceFunction. Otherwise, the list will remain to have multiple methods.
   */
  private List<SoySourceFunction> methods;

  /**
   * @param base The base expression that the method is called on.
   * @param methodName The name of the method.
   * @param location The location of the method call expression.
   * @param isNullSafe If true, checks during evaluation whether the base expression is null and
   *     returns null instead of causing an invalid dereference.
   */
  public MethodNode(
      ExprNode base,
      List<ExprNode> params,
      Identifier methodName,
      SourceLocation location,
      boolean isNullSafe) {
    super(base, location, isNullSafe);
    Preconditions.checkArgument(methodName != null);
    this.methodName = methodName;
    addChildren(params);
  }

  /** @param orig The node to copy */
  private MethodNode(MethodNode orig, CopyState copyState) {
    super(orig, copyState);
    this.methodName = orig.methodName;
  }

  /** Returns the name of the method */
  public Identifier getMethodName() {
    return methodName;
  }

  public void setSoyMethods(List<SoySourceFunction> methods) {
    checkNotNull(methods);
    this.methods = methods;
  }

  public List<SoySourceFunction> getSoyMethods() {
    checkState(this.methods != null, "setSoyMethods() hasn't been called yet");
    return this.methods;
  }

  /** Returns true if the methods have been resolved to exactly one SoySourceFunction. */
  public boolean isMethodResolved() {
    return getSoyMethods().size() == 1;
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
    return Kind.METHOD_NODE;
  }

  @Override
  public ExprNode copy(CopyState copyState) {
    return new MethodNode(this, copyState);
  }
}
