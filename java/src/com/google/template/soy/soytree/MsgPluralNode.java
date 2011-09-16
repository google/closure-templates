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
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Node representing a 'plural' block.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 * @author Mohamed Eldawy
 */
public class MsgPluralNode extends AbstractParentCommandNode<CaseOrDefaultNode>
    implements StandaloneNode, SplitLevelTopNode<CaseOrDefaultNode>, ExprHolderNode {


  /** An expression, and optional "offset" attribute. */
  private static final Pattern COMMAND_TEXT_PATTERN =
      Pattern.compile("(.+?) ( \\s+ offset= .+ )?", Pattern.COMMENTS);

  /** Parser for the attributes in the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("plural",
          new Attribute("offset", Attribute.ALLOW_ALL_VALUES, null));


  /** The offset. */
  private final int offset;

  /** The parsed expression. */
  private final ExprRootNode<?> pluralExpr;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public MsgPluralNode(int id, String commandText) throws SoySyntaxException {
    super(id, "plural", commandText);

    Matcher matcher = COMMAND_TEXT_PATTERN.matcher(commandText);

    if (!matcher.matches()) {
      throw new SoySyntaxException("Invalid 'plural' command text \"" + commandText + "\".");
    }

    try {
      pluralExpr = (new ExpressionParser(matcher.group(1))).parseExpression();
    } catch (TokenMgrError tme) {
      throw createExceptionForInvalidCommandText("expression", tme);
    } catch (ParseException pe) {
      throw createExceptionForInvalidCommandText("expression", pe);
    }

    // If attributes were given, parse them.
    if (matcher.group(2) != null) {
      try {
        Map<String, String> attributes = ATTRIBUTES_PARSER.parse(matcher.group(2).trim());
        String offsetAttribute = attributes.get("offset");
        offset = Integer.parseInt(offsetAttribute);
        if (offset < 0) {
          throw new SoySyntaxException("The 'offset' for plural must be a nonnegative integer.");
        }
      } catch (NumberFormatException nfe) {
        throw createExceptionForInvalidCommandText("offset", nfe);
      }
    } else {
      offset = 0;
    }
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected MsgPluralNode(MsgPluralNode orig) {
    super(orig);
    this.offset = orig.offset;
    this.pluralExpr = orig.pluralExpr.clone();
  }


  /**
   * Private helper for the constructor.
   * @param desc Description of the invalid item.
   * @param cause The underlying exception.
   * @return The SoySyntaxException to be thrown.
   */
  private SoySyntaxException createExceptionForInvalidCommandText(String desc, Throwable cause) {
    //noinspection ThrowableInstanceNeverThrown
    return new SoySyntaxException(
        "Invalid " + desc + " in 'plural' command text \"" + getCommandText() + "\".", cause);
  }


  @Override public Kind getKind() {
    return Kind.MSG_PLURAL_NODE;
  }


  /** Returns the offset. */
  public int getOffset() {
    return offset;
  }


  /** Returns the expression text. */
  public String getExprText() {
    return pluralExpr.toSourceString();
  }


  /** Returns the parsed expression. */
  public ExprRootNode<?> getExpr() {
    return pluralExpr;
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    return ImmutableList.of(new ExprUnion(pluralExpr));
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public MsgPluralNode clone() {
    return new MsgPluralNode(this);
  }

}
