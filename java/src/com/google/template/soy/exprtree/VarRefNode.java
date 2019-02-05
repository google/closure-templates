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
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.SoyType;
import javax.annotation.Nullable;

/**
 * Expression representing an unqualified variable name, e.g. $foo, $ij.foo.
 *
 */
public final class VarRefNode extends AbstractExprNode {

  public static VarRefNode error(SourceLocation location) {
    return new VarRefNode("error", location, false, null);
  }

  /** The name of the variable, without the preceding dollar sign. */
  private final String name;

  /** Whether this is an injected parameter reference. */
  private final boolean isDollarSignIjParameter;

  /** Reference to the variable declaration. */
  private VarDefn defn;

  /**
   * For cases where we are able to infer a stronger type than the variable, this will contain the
   * stronger type which overrides the variable's type.
   */
  private SoyType subtituteType;

  /**
   * @param name The name of the variable.
   * @param sourceLocation The node's source location.
   * @param isDollarSignIjParameter Whether this is an {@code $ij} variable.
   * @param defn (optional) The variable declaration for this variable.
   */
  public VarRefNode(
      String name,
      SourceLocation sourceLocation,
      boolean isDollarSignIjParameter,
      @Nullable VarDefn defn) {
    super(sourceLocation);
    this.name = Preconditions.checkNotNull(name);
    this.isDollarSignIjParameter = isDollarSignIjParameter;
    this.defn = defn;
  }

  private VarRefNode(VarRefNode orig, CopyState copyState) {
    super(orig, copyState);
    this.name = orig.name;
    this.isDollarSignIjParameter = orig.isDollarSignIjParameter;
    this.subtituteType = orig.subtituteType;
    // Maintain the original def in case only a subtree is getting cloned, but also register a
    // listener so that if the defn is replaced we will get updated also. Unless this is an
    // $ij param which doesn't get cloned because they are implicitly defined by their var refs.
    this.defn = orig.defn;
    if (orig.defn != null && orig.defn.kind() != VarDefn.Kind.IJ_PARAM) {
      copyState.registerRefListener(
          orig.defn,
          new CopyState.Listener<VarDefn>() {
            @Override
            public void newVersion(VarDefn newObject) {
              setDefn(newObject);
            }
          });
    }
  }

  @Override
  public Kind getKind() {
    return Kind.VAR_REF_NODE;
  }

  @Override
  public SoyType getType() {
    // We won't know the type until we know the variable declaration.
    Preconditions.checkState(defn != null);
    return subtituteType != null ? subtituteType : defn.type();
  }

  /** Returns the name of the variable. */
  public String getName() {
    return name;
  }

  /**
   * Returns Whether this is an {@code $ij} parameter reference.
   *
   * <p>You almost certainly don't want to use this method and instead want {@link #isInjected()}.
   */
  public boolean isDollarSignIjParameter() {
    return isDollarSignIjParameter;
  }

  /** Returns Whether this is an injected parameter reference. */
  public boolean isInjected() {
    return defn.isInjected();
  }

  /** @param defn the varDecl to set */
  public void setDefn(VarDefn defn) {
    this.defn = defn;
  }

  /** @return the varDecl */
  public VarDefn getDefnDecl() {
    return defn;
  }

  /** Returns whether this is a local variable reference. */
  public boolean isLocalVar() {
    return defn.kind() == VarDefn.Kind.LOCAL_VAR;
  }

  /**
   * Returns whether this might be header variable reference. A header variable is declared in Soy
   * with the @param or @state annotation.
   */
  public Boolean isPossibleHeaderVar() {
    if (defn == null) {
      throw new NullPointerException(getSourceLocation().toString());
    }
    return defn.kind() == VarDefn.Kind.PARAM
        || defn.kind() == VarDefn.Kind.STATE
        || defn.kind() == VarDefn.Kind.UNDECLARED;
  }

  /**
   * Override the type of the variable when used in this context. This is set by the flow analysis
   * in the type resolution pass which can infer a stronger type.
   *
   * @param type The overridden type value.
   */
  public void setSubstituteType(SoyType type) {
    subtituteType = type;
  }

  @Override
  public String toSourceString() {
    return "$" + (isDollarSignIjParameter ? "ij." : "") + name;
  }

  @Override
  public VarRefNode copy(CopyState copyState) {
    return new VarRefNode(this, copyState);
  }
}
