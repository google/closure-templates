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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.ParseException;
import com.google.template.soy.exprparse.TokenMgrError;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.ParentExprHolderNode;

import java.util.Collections;
import java.util.List;


/**
 * Node representing a block within an 'if' statement that has a conditional expression (i.e.
 * either the 'if' block or an 'elseif' block).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class IfCondNode extends AbstractParentSoyCommandNode<SoyNode>
    implements ConditionalBlockNode<SoyNode>, ParentExprHolderNode<SoyNode> {


  /** The text of the conditional expression. */
  private final String exprText;

  /** The parsed expression (null if the expression is not in V2 syntax). */
  private final ExprRootNode<ExprNode> expr;


  /**
   * @param id The id for this node.
   * @param commandName The command name -- either 'if' or 'elseif'.
   * @param commandText The command text.
   */
  public IfCondNode(String id, String commandName, String commandText) {
    super(id, commandName, commandText);
    Preconditions.checkArgument(commandName.equals("if") || commandName.equals("elseif"));

    exprText = commandText;
    ExprRootNode<ExprNode> tempExpr = null;
    try {
      tempExpr = (new ExpressionParser(exprText)).parseExpression();
    } catch (TokenMgrError tme) {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
    } catch (ParseException pe) {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
    }
    expr = tempExpr;
  }


  /** Returns the text of the conditional expression. */
  public String getExprText() {
    return exprText;
  }

  /** Returns the parsed expression, or null if the expression is not in V2 syntax. */
  public ExprRootNode<ExprNode> getExpr() {
    return expr;
  }


  @Override public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    // Note: No end tag.
    return sb.toString();
  }


  @Override public List<? extends ExprRootNode<? extends ExprNode>> getAllExprs() {
    return (expr != null) ? ImmutableList.of(expr)
                          : Collections.<ExprRootNode<? extends ExprNode>>emptyList();
  }

}
