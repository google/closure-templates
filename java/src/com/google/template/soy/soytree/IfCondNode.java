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
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;

import java.util.List;


/**
 * Node representing a block within an 'if' statement that has a conditional expression (i.e.
 * either the 'if' block or an 'elseif' block).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class IfCondNode extends AbstractBlockCommandNode
    implements ConditionalBlockNode, ExprHolderNode {


  /** The parsed expression. */
  private final ExprUnion exprUnion;


  /**
   * @param id The id for this node.
   * @param commandName The command name -- either 'if' or 'elseif'.
   * @param commandText The command text.
   */
  public IfCondNode(int id, String commandName, String commandText) {
    super(id, commandName, commandText);
    Preconditions.checkArgument(commandName.equals("if") || commandName.equals("elseif"));

    ExprRootNode<?> expr;
    try {
      expr = (new ExpressionParser(commandText)).parseExpression();
    } catch (TokenMgrError tme) {
      expr = null;
    } catch (ParseException pe) {
      expr = null;
    }

    if (expr != null) {
      exprUnion = new ExprUnion(expr);
    } else {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
      exprUnion = new ExprUnion(commandText);
    }
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected IfCondNode(IfCondNode orig) {
    super(orig);
    this.exprUnion = (orig.exprUnion != null) ? orig.exprUnion.clone() : null;
  }


  @Override public Kind getKind() {
    return Kind.IF_COND_NODE;
  }


  /** Returns the text of the conditional expression. */
  public String getExprText() {
    return exprUnion.getExprText();
  }


  /** Returns the parsed expression. */
  public ExprUnion getExprUnion() {
    return exprUnion;
  }


  @Override public String getCommandName() {
    return (getParent().getChild(0) == this) ? "if" : "elseif";
  }


  @Override public String getCommandText() {
    return exprUnion.getExprText();
  }


  @Override public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    // Note: No end tag.
    return sb.toString();
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    return ImmutableList.of(exprUnion);
  }


  @Override public IfCondNode clone() {
    return new IfCondNode(this);
  }

}
