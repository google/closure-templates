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
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ParentExprHolderNode;
import com.google.template.soy.soytree.SoyNode.SoyStatementNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;

import java.util.List;


/**
 * Node representing a 'switch' statement.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class SwitchNode extends AbstractParentSoyCommandNode<SoyNode>
    implements SplitLevelTopNode<SoyNode>, SoyStatementNode, ParentExprHolderNode<SoyNode> {


  /** The text for the expression to switch on. */
  private final String exprText;

  /** The parsed expression. */
  private final ExprRootNode<ExprNode> expr;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public SwitchNode(String id, String commandText) throws SoySyntaxException {
    super(id, "switch", commandText);

    exprText = commandText;
    ExprRootNode<ExprNode> tempExpr = null;
    try {
      tempExpr = (new ExpressionParser(exprText)).parseExpression();
    } catch (TokenMgrError tme) {
      throw createExceptionForInvalidExpr(tme);
    } catch (ParseException pe) {
      throw createExceptionForInvalidExpr(pe);
    }
    expr = tempExpr;
  }


  /**
   * Private helper for the constructor.
   * @param cause The underlying exception.
   * @return The SoySyntaxException to be thrown.
   */
  private SoySyntaxException createExceptionForInvalidExpr(Throwable cause) {
    //noinspection ThrowableInstanceNeverThrown
    return new SoySyntaxException(
        "Invalid expression in 'switch' command text \"" + getCommandText() + "\".", cause);
  }


  /** Returns the text for the expression to switch on. */
  public String getExprText() {
    return exprText;
  }

  /** Returns the parsed expression, or null if the expression is not in V2 syntax. */
  public ExprRootNode<ExprNode> getExpr() {
    return expr;
  }


  @Override public List<? extends ExprRootNode<? extends ExprNode>> getAllExprs() {
    return ImmutableList.of(expr);
  }

}
