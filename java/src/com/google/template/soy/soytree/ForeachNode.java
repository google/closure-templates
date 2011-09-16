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
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Node representing a 'foreach' statement. Should always contain a ForeachNonemptyNode as the
 * first child. May contain a second child, which should be a ForeachIfemptyNode.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class ForeachNode extends AbstractParentCommandNode<SoyNode>
    implements StandaloneNode, SplitLevelTopNode<SoyNode>, StatementNode, ExprHolderNode {


  /** Regex pattern for the command text. */
  // 2 capturing groups: local var name, expression
  private static final Pattern COMMAND_TEXT_PATTERN =
      Pattern.compile("( [$] \\w+ ) \\s+ in \\s+ (\\S .*)", Pattern.COMMENTS | Pattern.DOTALL);


  /** The loop variable name. */
  private final String varName;

  /** The parsed expression for the list that we're iterating over. */
  private final ExprRootNode<?> expr;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public ForeachNode(int id, String commandText) throws SoySyntaxException {
    super(id, "foreach", commandText);

    Matcher matcher = COMMAND_TEXT_PATTERN.matcher(commandText);
    if (!matcher.matches()) {
      throw new SoySyntaxException("Invalid 'foreach' command text \"" + commandText + "\".");
    }

    try {
      varName = (new ExpressionParser(matcher.group(1))).parseVariable().getChild(0).getName();
    } catch (TokenMgrError tme) {
      throw createExceptionForInvalidCommandText("variable name", tme);
    } catch (ParseException pe) {
      throw createExceptionForInvalidCommandText("variable name", pe);
    }

    try {
      expr = (new ExpressionParser(matcher.group(2))).parseExpression();
    } catch (TokenMgrError tme) {
      throw createExceptionForInvalidCommandText("expression", tme);
    } catch (ParseException pe) {
      throw createExceptionForInvalidCommandText("expression", pe);
    }
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected ForeachNode(ForeachNode orig) {
    super(orig);
    this.varName = orig.varName;
    this.expr = orig.expr.clone();
  }


  /**
   * Private helper for the constructor.
   * @param desc Description of the invalid item.
   * @param cause The underlying exception.
   * @return The SoySyntaxException to be thrown.
   */
  private SoySyntaxException createExceptionForInvalidCommandText(
      String desc, Throwable cause) {
    //noinspection ThrowableInstanceNeverThrown
    return new SoySyntaxException(
        "Invalid " + desc + " in 'foreach' command text \"" + getCommandText() + "\".", cause);
  }


  @Override public Kind getKind() {
    return Kind.FOREACH_NODE;
  }


  /** Returns the foreach-loop variable name. */
  public String getVarName() {
    return varName;
  }


  /** Returns the text of the expression we're iterating over. */
  public String getExprText() {
    return expr.toSourceString();
  }


  /** Returns the parsed expression. */
  public ExprRootNode<?> getExpr() {
    return expr;
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    return ImmutableList.of(new ExprUnion(expr));
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public ForeachNode clone() {
    return new ForeachNode(this);
  }

}
