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
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprparse.ExprParseUtils;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

import java.util.List;

import javax.annotation.Nullable;


/**
 * Node representing a 'print' statement.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class PrintNode extends AbstractParentCommandNode<PrintDirectiveNode>
    implements StandaloneNode, SplitLevelTopNode<PrintDirectiveNode>, StatementNode,
    ExprHolderNode, MsgPlaceholderInitialNode {


  /** Fallback base placeholder name. */
  public static final String FALLBACK_BASE_PLACEHOLDER_NAME = "XXX";


  /** Whether the command 'print' is implicit. */
  private final boolean isImplicit;

  /** The parsed expression. */
  private final ExprUnion exprUnion;

  /** The user-supplied placeholder name, or null if not supplied or not applicable. */
  @Nullable private final String userSuppliedPlaceholderName;


  /**
   * @param id The id for this node.
   * @param isImplicit Whether the command 'print' is implicit.
   * @param exprText The text of the expression to print.
   * @param userSuppliedPlaceholderName The user-supplied placeholder name, or null if not supplied
   *     or not applicable.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public PrintNode(
      int id, boolean isImplicit, String exprText, @Nullable String userSuppliedPlaceholderName)
      throws SoySyntaxException {

    super(id, "print", "");

    this.isImplicit = isImplicit;

    ExprRootNode<?> expr = ExprParseUtils.parseExprElseNull(exprText);
    if (expr != null) {
      this.exprUnion = new ExprUnion(expr);
    } else {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
      this.exprUnion = new ExprUnion(exprText);
    }

    this.userSuppliedPlaceholderName = userSuppliedPlaceholderName;
  }


  /**
   * Constructor for use by passes that want to create a PrintNode with already-parsed expression.
   *
   * @param id The id for this node.
   * @param isImplicit Whether the command 'print' is implicit.
   * @param exprUnion The parsed expression.
   * @param userSuppliedPlaceholderName The user-supplied placeholder name, or null if not supplied
   *     or not applicable.
   */
  public PrintNode(
      int id, boolean isImplicit, ExprUnion exprUnion,
      @Nullable String userSuppliedPlaceholderName) {
    super(id, "print", "");
    this.isImplicit = isImplicit;
    this.exprUnion = exprUnion;
    this.userSuppliedPlaceholderName = userSuppliedPlaceholderName;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected PrintNode(PrintNode orig) {
    super(orig);
    this.isImplicit = orig.isImplicit;
    this.exprUnion = (orig.exprUnion != null) ? orig.exprUnion.clone() : null;
    this.userSuppliedPlaceholderName = orig.userSuppliedPlaceholderName;
  }


  @Override public Kind getKind() {
    return Kind.PRINT_NODE;
  }


  /** Returns whether the 'print' command name was implicit. */
  public boolean isImplicit() {
    return isImplicit;
  }


  /** Returns the text of the expression to print. */
  public String getExprText() {
    return exprUnion.getExprText();
  }


  /** Returns the parsed expression, or null if the expression is not in V2 syntax. */
  public ExprUnion getExprUnion() {
    return exprUnion;
  }


  @Override public String getUserSuppliedPlaceholderName() {
    return userSuppliedPlaceholderName;
  }


  @Override public String genBasePlaceholderName() {

    if (userSuppliedPlaceholderName != null) {
      return BaseUtils.convertToUpperUnderscore(userSuppliedPlaceholderName);
    }

    ExprRootNode<?> exprRoot = exprUnion.getExpr();
    if (exprRoot == null) {
      return FALLBACK_BASE_PLACEHOLDER_NAME;
    }

    return AbstractMsgNode.genBaseNameFromExpr(exprRoot, FALLBACK_BASE_PLACEHOLDER_NAME);
  }


  @Override public Object genSamenessKey() {
    // PrintNodes are considered the same placeholder if they have the same command text.
    return getCommandText();
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    return ImmutableList.of(exprUnion);
  }


  @Override public String getCommandText() {
    StringBuilder sb = new StringBuilder();
    sb.append(exprUnion.getExprText());
    for (PrintDirectiveNode child : getChildren()) {
      sb.append(' ').append(child.toSourceString());
    }
    if (userSuppliedPlaceholderName != null) {
      sb.append(" phname=\"").append(userSuppliedPlaceholderName).append('"');
    }
    return sb.toString();
  }


  @Override public String getTagString() {
    return buildTagStringHelper(false, isImplicit);
  }


  @Override public String toSourceString() {
    return getTagString();
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public PrintNode clone() {
    return new PrintNode(this);
  }

}
