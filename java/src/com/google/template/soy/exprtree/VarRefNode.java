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
import com.google.template.soy.exprtree.VarDefn.ImmutableVarDefn;
import com.google.template.soy.types.SoyType;
import javax.annotation.Nullable;

/**
 * Expression representing an unqualified variable name, e.g. $foo.
 *
 */
public final class VarRefNode extends AbstractExprNode {

  public static VarRefNode error(SourceLocation location) {
    return new VarRefNode("$error", location, null);
  }

  /** The name of the variable, without the preceding dollar sign. */
  private final String name;

  /** Reference to the variable declaration. */
  private VarDefn defn;

  /**
   * For cases where we are able to infer a stronger type than the variable, this will contain the
   * stronger type which overrides the variable's type.
   */
  private SoyType substituteType;

  /**
   * @param name The name of the variable.
   * @param sourceLocation The node's source location.
   * @param defn (optional) The variable declaration for this variable.
   */
  public VarRefNode(String name, SourceLocation sourceLocation, @Nullable VarDefn defn) {
    super(sourceLocation);
    this.name = name;
    this.defn = defn;
  }

  private VarRefNode(VarRefNode orig, CopyState copyState) {
    super(orig, copyState);
    this.name = orig.name;
    this.substituteType = orig.substituteType;
    if (orig.defn != null) {
      if (orig.defn instanceof ImmutableVarDefn) {
        this.defn = orig.defn;
      } else {
        // Maintain the original def in case only a subtree is getting cloned, but also register a
        // listener so that if the defn is replaced we will get updated also.
        this.defn = orig.defn;
        copyState.registerRefListener(orig.defn, this::setDefn);
      }
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
    return substituteType != null ? substituteType : defn.type();
  }

  public boolean hasType() {
    return defn != null && (substituteType != null || defn.hasType());
  }

  /** Returns the source of the variable reference, possibly with leading "$". */
  public String getName() {
    return name;
  }

  public String getNameWithoutLeadingDollar() {
    return name.startsWith("$") ? name.substring(1) : name;
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
    substituteType = type;
  }

  @Override
  public String toSourceString() {
    return name;
  }

  @Override
  public VarRefNode copy(CopyState copyState) {
    return new VarRefNode(this, copyState);
  }
}
