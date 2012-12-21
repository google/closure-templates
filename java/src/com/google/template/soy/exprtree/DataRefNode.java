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

package com.google.template.soy.exprtree;


/**
 * Node representing a data reference.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> The first key is stored as a field in the node. The remaining accesses, if any, are children
 * of the node. Each child must be a DataRefAccessNode (of which there are 3 subtypes).
 *
 * @author Kai Huang
 */
public class DataRefNode extends AbstractParentExprNode {


  /** Whether this node is a reference to injected data. */
  private final boolean isIjDataRef;

  /** Whether this node is a null-safe reference to injected data. */
  private final boolean isNullSafeIjDataRef;

  /** Whether this node is a reference to local var data, or null if unknown. */
  private Boolean isLocalVarDataRef;

  /** The first key. */
  private final String firstKey;


  /**
   * @param isIjDataRef Whether this node is a reference to injected data.
   * @param isNullSafeIjDataRef Whether this node is a null-safe reference to injected data.
   * @param firstKey The first key.
   */
  public DataRefNode(boolean isIjDataRef, boolean isNullSafeIjDataRef, String firstKey) {
    this.isIjDataRef = isIjDataRef;
    this.isNullSafeIjDataRef = isNullSafeIjDataRef;
    this.isLocalVarDataRef = null;
    this.firstKey = firstKey;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected DataRefNode(DataRefNode orig) {
    super(orig);
    this.isIjDataRef = orig.isIjDataRef;
    this.isNullSafeIjDataRef = orig.isNullSafeIjDataRef;
    this.isLocalVarDataRef = orig.isLocalVarDataRef;
    this.firstKey = orig.firstKey;
  }


  @Override public Kind getKind() {
    return Kind.DATA_REF_NODE;
  }


  /**
   * Returns whether this node is a reference to injected data.
   */
  public boolean isIjDataRef() {
    return isIjDataRef;
  }


  /**
   * Returns whether this node is a null-safe reference to injected data.
   */
  public boolean isNullSafeIjDataRef() {
    return isNullSafeIjDataRef;
  }


  /**
   * Sets whether this node is a reference to local var data, or null if unknown.
   */
  public void setIsLocalVarDataRef(Boolean isLocalVarDataRef) {
    this.isLocalVarDataRef = isLocalVarDataRef;
  }


  /**
   * Returns whether this node is a reference to local var data, or null if unknown.
   */
  public Boolean isLocalVarDataRef() {
    return isLocalVarDataRef;
  }


  /**
   * Returns the first key.
   */
  public String getFirstKey() {
    return firstKey;
  }


  @Override public String toSourceString() {

    StringBuilder sourceSb = new StringBuilder();
    sourceSb.append(isNullSafeIjDataRef ? "$ij?." : isIjDataRef ? "$ij." : "$");
    sourceSb.append(firstKey);
    for (ExprNode child : getChildren()) {
      sourceSb.append(child.toSourceString());
    }
    return sourceSb.toString();
  }


  @Override public DataRefNode clone() {
    return new DataRefNode(this);
  }

}
