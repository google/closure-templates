/*
 * Copyright 2013 Google Inc.
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
import com.google.template.soy.types.SoyType;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Expression representing an unqualified variable name, e.g. $foo, $ij.foo.
 *
 */
public final class VarRefNode extends AbstractExprNode {

  /** The name of the variable. */
  private final String name;

  /** Whether this is an injected parameter reference. */
  private final boolean isInjected;

  /** Whether this a null-safe access to an injected parameter. */
  private final boolean isNullSafeInjected;

  /** Reference to the variable declaration. */
  private VarDefn defn;

  /**
   * For cases where we are able to infer a stronger type than the variable, this
   * will contain the stronger type which overrides the variable's type.
   */
  private SoyType subtituteType;

  /**
   * @param name The name of the variable.
   * @param injected Whether this is an injected variable.
   * @param nullSafeInjected Whether this a null-safe access to an injected parameter.
   * @param defn (optional) The variable declaration for this variable.
   */
  public VarRefNode(
      String name, boolean injected, boolean nullSafeInjected, @Nullable VarDefn defn) {
    Preconditions.checkArgument(name != null);
    this.name = name;
    this.isInjected = injected;
    this.isNullSafeInjected = nullSafeInjected;
    this.defn = defn;
  }

  @Override public Kind getKind() {
    return Kind.VAR_REF_NODE;
  }

  @Override public SoyType getType() {
    // We won't know the type until we know the variable declaration.
    Preconditions.checkState(defn != null);
    return subtituteType != null ? subtituteType : defn.type();
  }

  /** Returns the name of the variable. */
  public String getName() {
    return name;
  }

  /** Returns Whether this is an injected parameter reference. */
  public boolean isInjected() {
    return isInjected;
  }

  /** Returns whether this a null-safe access to an injected parameter. */
  public boolean isNullSafeInjected() {
    return isNullSafeInjected;
  }

  /**
   * @param defn the varDecl to set
   */
  public void setDefn(VarDefn defn) {
    this.defn = defn;
  }

  /**
   * @return the varDecl
   */
  public VarDefn getDefnDecl() {
    return defn;
  }

  /** Returns whether this is a local variable reference. */
  public Boolean isLocalVar() {
    return defn == null ? null : defn.kind() == VarDefn.Kind.LOCAL_VAR;
  }

  /**
   * Returns whether this might be a local variable reference. If the variable definition
   * is unknown, then it returns true.
   */
  public Boolean isPossibleParam() {
    // TODO: Get rid of the null check - needs to revise EvalVisitorTest to run
    // the resolve names pass in order for this to be true.
    return defn == null || defn.kind() == VarDefn.Kind.PARAM ||
        defn.kind() == VarDefn.Kind.UNDECLARED;
  }

  /**
   * Override the type of the variable when used in this context. This is set by
   * the flow analysis in the type resolution pass which can infer a stronger type.
   * @param type The overridden type value.
   */
  public void setSubstituteType(SoyType type) {
    subtituteType = type;
  }

  @Override public String toSourceString() {
    return "$" + (isInjected ? (isNullSafeInjected ? "ij?." : "ij.") : "") + name;
  }

  @Override public ExprNode clone() {
    return new VarRefNode(name, isInjected, isNullSafeInjected, defn);
  }

  @Override public boolean equals(Object other) {
    if (other == null || other.getClass() != this.getClass()) { return false; }
    VarRefNode otherVar = (VarRefNode) other;
    return name.equals(otherVar.name) &&
        isInjected == otherVar.isInjected &&
        isNullSafeInjected == otherVar.isNullSafeInjected;
  }

  @Override public int hashCode() {
    return Objects.hash(this.getClass(), name, isInjected, isNullSafeInjected);
  }
}
