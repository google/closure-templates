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
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.ParseException;
import com.google.template.soy.exprparse.TokenMgrError;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

import java.util.List;


/**
 * Node representing a 'switch' statement.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class SwitchNode extends AbstractParentCommandNode<SoyNode>
    implements StandaloneNode, SplitLevelTopNode<SoyNode>, StatementNode, ExprHolderNode {


  /** The parsed expression. */
  private final ExprRootNode<?> expr;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public SwitchNode(int id, String commandText) throws SoySyntaxException {
    super(id, "switch", commandText);

    ExprRootNode<?> tempExpr;
    try {
      tempExpr = (new ExpressionParser(commandText)).parseExpression();
    } catch (TokenMgrError tme) {
      throw createExceptionForInvalidExpr(tme, commandText);
    } catch (ParseException pe) {
      throw createExceptionForInvalidExpr(pe, commandText);
    }
    expr = tempExpr;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected SwitchNode(SwitchNode orig) {
    super(orig);
    this.expr = orig.expr.clone();
  }


  /**
   * Private helper for the constructor.
   * @param cause The underlying exception.
   * @param commandText The command text which contains the invalid expression.
   * @return The SoySyntaxException to be thrown.
   */
  private SoySyntaxException createExceptionForInvalidExpr(Throwable cause, String commandText) {
    //noinspection ThrowableInstanceNeverThrown
    return new SoySyntaxException(
        "Invalid expression in 'switch' command text \"" + commandText + "\".", cause);
  }


  @Override public Kind getKind() {
    return Kind.SWITCH_NODE;
  }


  /** Returns the text for the expression to switch on. */
  public String getExprText() {
    return expr.toSourceString();
  }


  /** Returns the parsed expression, or null if the expression is not in V2 syntax. */
  public ExprRootNode<?> getExpr() {
    return expr;
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    return ImmutableList.of(new ExprUnion(expr));
  }


  @Override public String getCommandText() {
    return expr.toSourceString();
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public SwitchNode clone() {
    return new SwitchNode(this);
  }

}
