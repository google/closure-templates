/*
 * Copyright 2010 Google Inc.
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

import java.util.List;


/**
 * Node representing a 'select' block.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class MsgSelectNode extends AbstractParentCommandNode<CaseOrDefaultNode>
    implements StandaloneNode, SplitLevelTopNode<CaseOrDefaultNode>, ExprHolderNode {


  /** The parsed expression. */
  private final ExprRootNode<?> selectExpr;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public MsgSelectNode(int id, String commandText) throws SoySyntaxException {
    super(id, "select", commandText);

    try {
      selectExpr = (new ExpressionParser(commandText)).parseExpression();
    } catch (TokenMgrError tme) {
      throw createExceptionForInvalidCommandText(tme);
    } catch (ParseException pe) {
      throw createExceptionForInvalidCommandText(pe);
    }
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected MsgSelectNode(MsgSelectNode orig) {
    super(orig);
    this.selectExpr = orig.selectExpr.clone();
  }


  /**
   * Private helper for the constructor.
   * @param cause The underlying exception.
   * @return The SoySyntaxException to be thrown.
   */
  private SoySyntaxException createExceptionForInvalidCommandText(Throwable cause) {
    //noinspection ThrowableInstanceNeverThrown
    return new SoySyntaxException(
        "Invalid data reference in 'select' command text \"" + getCommandText() + "\".", cause);
  }


  @Override public Kind getKind() {
    return Kind.MSG_SELECT_NODE;
  }


  /** Returns the expression text. */
  public String getExprText() {
    return selectExpr.toSourceString();
  }


  /** Returns the parsed expression. */
  public ExprRootNode<?> getExpr() {
    return selectExpr;
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    return ImmutableList.of(new ExprUnion(selectExpr));
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public MsgSelectNode clone() {
    return new MsgSelectNode(this);
  }

}
