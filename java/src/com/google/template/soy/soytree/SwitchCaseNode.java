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

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.ParseException;
import com.google.template.soy.exprparse.TokenMgrError;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.ParentExprHolderNode;

import java.util.List;


/**
 * Node representing a 'case' block in a 'switch' statement.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class SwitchCaseNode extends AbstractParentSoyCommandNode<SoyNode>
    implements ConditionalBlockNode<SoyNode>, ParentExprHolderNode<SoyNode> {


  /** The text for this case's expression list. */
  private final String exprListText;

  /** The parsed expression list. */
  private final List<ExprRootNode<ExprNode>> exprList;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public SwitchCaseNode(String id, String commandText) throws SoySyntaxException {
    super(id, "case", commandText);

    exprListText = commandText;
    List<ExprRootNode<ExprNode>> tempExprList = null;
    try {
      tempExprList = (new ExpressionParser(exprListText)).parseExpressionList();
    } catch (TokenMgrError tme) {
      throw createExceptionForInvalidExprList(tme);
    } catch (ParseException pe) {
      throw createExceptionForInvalidExprList(pe);
    }
    exprList = tempExprList;
  }


  /**
   * Private helper for the constructor.
   * @param cause The underlying exception.
   * @return The SoySyntaxException to be thrown.
   */
  private SoySyntaxException createExceptionForInvalidExprList(Throwable cause) {
    //noinspection ThrowableInstanceNeverThrown
    return new SoySyntaxException(
        "Invalid expression list in 'case' command text \"" + getCommandText() + "\".", cause);
  }


  /** Returns the text for this case's expression list. */
  public String getExprListText() {
    return exprListText;
  }

  /** Returns the parsed expression list, or null if the expression list is not in V2 syntax. */
  public List<ExprRootNode<ExprNode>> getExprList() {
    return exprList;
  }


  @Override public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    // Note: No end tag.
    return sb.toString();
  }


  @Override public List<? extends ExprRootNode<? extends ExprNode>> getAllExprs() {
    return exprList;
  }

}
