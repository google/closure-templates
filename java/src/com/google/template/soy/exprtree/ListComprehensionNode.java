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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.SoyType;
import javax.annotation.Nullable;

/**
 * A node representing a list comprehension expr (e.g. "$a+1 for $a in $myList if $a >= 0").
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class ListComprehensionNode extends AbstractParentExprNode {
  private final ComprehensionVarDefn listIterVar;
  private final boolean hasFilter;
  private int nodeId;

  public ListComprehensionNode(
      ExprNode listExpr,
      String listIterVarName,
      SourceLocation listIterVarNameLocation,
      ExprNode itemMapExpr,
      ExprNode filterExpr,
      SourceLocation sourceLocation,
      int nodeId) {
    super(sourceLocation);
    this.listIterVar = new ComprehensionVarDefn(listIterVarName, listIterVarNameLocation, this);
    this.nodeId = nodeId;

    addChild(listExpr);
    addChild(itemMapExpr);

    if (filterExpr != null) {
      hasFilter = true;
      addChild(filterExpr);
    } else {
      hasFilter = false;
    }
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private ListComprehensionNode(ListComprehensionNode orig, CopyState copyState) {
    super(orig, copyState);
    this.listIterVar = new ComprehensionVarDefn(orig.listIterVar, this);
    this.hasFilter = orig.hasFilter;
    this.nodeId = orig.nodeId;
    copyState.updateRefs(orig.listIterVar, this.listIterVar);
  }

  @Override
  public ExprNode.Kind getKind() {
    return ExprNode.Kind.LIST_COMPREHENSION_NODE;
  }

  public ComprehensionVarDefn getListIterVar() {
    return listIterVar;
  }

  /** Gets the listExpr in "[itemMapExpr for $var in listExpr]". */
  public ExprNode getListExpr() {
    return checkNotNull(getChild(0));
  }

  /** Gets the itemMapExpr in "[itemMapExpr for $var in listExpr]". */
  public ExprNode getListItemTransformExpr() {
    return checkNotNull(getChild(1));
  }

  /** Gets the filterExpr in "[itemMapExpr for $var in listExpr if filterExpr]". */
  @Nullable
  public ExprNode getFilterExpr() {
    if (hasFilter) {
      return checkNotNull(getChild(2));
    }
    return null;
  }

  public void setNodeId(int nodeId) {
    this.nodeId = nodeId;
  }

  public int getNodeId() {
    return nodeId;
  }

  @Override
  public String toSourceString() {
    if (hasFilter) {
      return String.format(
          "[%s for %s in %s if %s]",
          getListItemTransformExpr().toSourceString(),
          listIterVar.name(),
          getListExpr().toSourceString(),
          getFilterExpr().toSourceString());
    }
    return String.format(
        "[%s for %s in %s]",
        getListItemTransformExpr().toSourceString(),
        listIterVar.name(),
        getListExpr().toSourceString());
  }

  @Override
  public ListComprehensionNode copy(CopyState copyState) {
    return new ListComprehensionNode(this, copyState);
  }

  /**
   * List comprehension variable definition class.
   *
   * <p>NOTE: This does not use {@link com.google.template.soy.soytree.defn.LocalVar} because its
   * delcaring node is an expr node and not a {@link com.google.template.soy.SoyNode}.
   */
  public static final class ComprehensionVarDefn
      extends AbstractLocalVarDefn<ListComprehensionNode> {
    ComprehensionVarDefn(
        String name, SourceLocation nameLocation, ListComprehensionNode declaringNode) {
      this(name, nameLocation, declaringNode, null);
    }

    /**
     * @param name The variable name.
     * @param nameLocation The location where the variable name is declared.
     * @param declaringNode The comprehension node in which this variable is defined.
     * @param type The data type of the variable (i.e. the list element type).
     */
    ComprehensionVarDefn(
        String name,
        SourceLocation nameLocation,
        ListComprehensionNode declaringNode,
        SoyType type) {
      super(name, nameLocation, declaringNode, type);
    }

    /** Copy constructor. */
    ComprehensionVarDefn(ComprehensionVarDefn var, ListComprehensionNode declaringNode) {
      super(var, declaringNode);
    }

    @Override
    public VarDefn.Kind kind() {
      return VarDefn.Kind.COMPREHENSION_VAR;
    }
  }
}
