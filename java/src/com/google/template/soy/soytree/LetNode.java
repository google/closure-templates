/*
 * Copyright 2011 Google Inc.
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
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.SoyNode.LocalVarInlineNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Abstract node representing a 'let' statement.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public abstract class LetNode extends AbstractCommandNode
    implements StandaloneNode, StatementNode, LocalVarInlineNode {


  /** Regex pattern for the command text. */
  // Note: group 1 = local var name, group 2 = value expr (or null).
  private static final Pattern COMMAND_TEXT_PATTERN =
      Pattern.compile(
          "( [$] \\w+ ) (?: \\s* : \\s* (\\S .*) )?", Pattern.COMMENTS | Pattern.DOTALL);


  /** Whether the local var name is already unique (e.g. node id has already been appended). */
  private final boolean isVarNameUnique;


  /**
   * @param id The id for this node.
   * @param isVarNameUnique Whether the local var name is already unique (e.g. node id has already
   *     been appended).
   * @param commandText The command text.
   */
  protected LetNode(int id, boolean isVarNameUnique, String commandText) {
    super(id, "let", commandText);
    this.isVarNameUnique = isVarNameUnique;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected LetNode(LetNode orig) {
    super(orig);
    this.isVarNameUnique = orig.isVarNameUnique;
  }


  /**
   * Helper used by subclass constructors to parse the command text.
   * @param commandText The command text.
   * @return A pair containing the parsed local var name (without '$') and the value expression (or
   *     null if the command text doesn't include a value expression).
   * @throws SoySyntaxException If a syntax error is found.
   */
  protected Pair<String, ExprRootNode<?>> parseCommandTextHelper(String commandText)
      throws SoySyntaxException {

    Matcher matcher = COMMAND_TEXT_PATTERN.matcher(commandText);
    if (!matcher.matches()) {
      throw new SoySyntaxException("Invalid 'let' command text \"" + commandText + "\".");
    }

    String localVarName;
    try {
      localVarName = (new ExpressionParser(matcher.group(1))).parseVariable().getChild(0).getName();
    } catch (TokenMgrError tme) {
      throw createExceptionForInvalidCommandText("variable name", tme);
    } catch (ParseException pe) {
      throw createExceptionForInvalidCommandText("variable name", pe);
    }

    ExprRootNode<?> valueExpr;
    if (matcher.group(2) != null) {
      try {
        valueExpr = (new ExpressionParser(matcher.group(2))).parseExpression();
      } catch (TokenMgrError tme) {
        throw createExceptionForInvalidCommandText("value expression", tme);
      } catch (ParseException pe) {
        throw createExceptionForInvalidCommandText("value expression", pe);
      }
    } else {
      valueExpr = null;
    }

    return Pair.<String, ExprRootNode<?>>of(localVarName, valueExpr);
  }


  /**
   * Private helper for parseCommandTextHelper.
   * @param desc Description of the invalid item.
   * @param cause The underlying exception.
   * @return The SoySyntaxException to be thrown.
   */
  private SoySyntaxException createExceptionForInvalidCommandText(String desc, Throwable cause) {
    return new SoySyntaxException(
        "Invalid " + desc + " in 'let' command text \"" + getCommandText() + "\".", cause);
  }


  /**
   * Gets a unique version of the local var name (e.g. appending "__soy##" if necessary).
   */
  public String getUniqueVarName() {
    return isVarNameUnique ? getVarName() : getVarName() + "__soy" + getId();
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

}
