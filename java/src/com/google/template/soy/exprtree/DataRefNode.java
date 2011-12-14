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
 * The children of the node are the parts of the data reference. Each child may be a DataRefKeyNode,
 * DataRefIndexNode, or any type of ExprNode. The first child must be a DataRefKeyNode.
 *
 */
public class DataRefNode extends AbstractParentExprNode {


  /** Whether this node is a reference to injected data. */
  private final boolean isIjDataRef;

  /** Whether this node is a reference to local var data, or null if unknown. */
  private Boolean isLocalVarDataRef;


  /**
   * Constructor.
   * @param isIjDataRef Whether this node is a reference to injected data.
   */
  public DataRefNode(boolean isIjDataRef) {
    this.isIjDataRef = isIjDataRef;
    this.isLocalVarDataRef = null;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected DataRefNode(DataRefNode orig) {
    super(orig);
    this.isIjDataRef = orig.isIjDataRef;
    this.isLocalVarDataRef = orig.isLocalVarDataRef;
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
   * Returns the first key as a string.
   * <p> This method is for convenience. It is equivalent to
   * <pre>
   *     ((DataRefKeyNode) thisNode.getChild(0)).getKey()
   * </pre>
   */
  public String getFirstKey() {
    return ((DataRefKeyNode) getChild(0)).getKey();
  }


  @Override public String toSourceString() {

    StringBuilder sourceSb = new StringBuilder();

    boolean isFirst = true;
    for (ExprNode child : getChildren()) {

      if (isFirst) {
        sourceSb.append(isIjDataRef ? "$ij." : "$").append(child.toSourceString());
        isFirst = false;

      } else {
        if (child instanceof DataRefKeyNode || child instanceof DataRefIndexNode) {
          sourceSb.append('.').append(child.toSourceString());
        } else {
          sourceSb.append('[').append(child.toSourceString()).append(']');
        }
      }
    }

    return sourceSb.toString();
  }


  @Override public DataRefNode clone() {
    return new DataRefNode(this);
  }

}
