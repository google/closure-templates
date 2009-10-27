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
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Node representing a 'param' with a 'value' attribute.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class CallParamValueNode extends CallParamNode implements ExprHolderNode {


  /** Pattern for a key and optional value not listed as attributes. */
  // Note: group 1 = key, group 2 = value (or null).
  private static final Pattern NONATTRIBUTE_COMMAND_TEXT =
      Pattern.compile("^ (?! key=\") ([\\w]+) (?: \\s* : \\s* (.+) )? $",
                      Pattern.COMMENTS);

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("param",
          new Attribute("key", Attribute.ALLOW_ALL_VALUES,
                        Attribute.NO_DEFAULT_VALUE_BECAUSE_REQUIRED),
          new Attribute("value", Attribute.ALLOW_ALL_VALUES,
                        Attribute.NO_DEFAULT_VALUE_BECAUSE_REQUIRED));


  /** The param key. */
  private final String key;

  /** The expression text for the param value. */
  private final String valueExprText;

  /** The parsed expression for the param value (null if the expression is not in V2 syntax). */
  private final ExprRootNode<ExprNode> valueExpr;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public CallParamValueNode(String id, String commandText) throws SoySyntaxException {
    super(id, commandText);

    Matcher nctMatcher = NONATTRIBUTE_COMMAND_TEXT.matcher(commandText);
    if (nctMatcher.matches()) {
      key = parseKeyHelper(nctMatcher.group(1));
      valueExprText = nctMatcher.group(2);
    } else {
      Map<String, String> attributes = ATTRIBUTES_PARSER.parse(commandText);
      key = parseKeyHelper(attributes.get("key"));
      valueExprText = attributes.get("value");
    }

    ExprRootNode<ExprNode> tempValueExpr = null;
    try {
      tempValueExpr = (new ExpressionParser(valueExprText)).parseExpression();
    } catch (TokenMgrError tme) {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
    } catch (ParseException pe) {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
    }
    valueExpr = tempValueExpr;
  }


  @Override public String getKey() {
    return key;
  }

  /** Returns the expression text for the param value. */
  public String getValueExprText() {
    return valueExprText;
  }

  /** Returns the parsed expression for the param value, or null if expr is not in V2 syntax. */
  public ExprRootNode<ExprNode> getValueExpr() {
    return valueExpr;
  }


  @Override public String getTagString() {
    return buildTagStringHelper(true);
  }


  @Override public List<? extends ExprRootNode<? extends ExprNode>> getAllExprs() {
    return (valueExpr != null) ? ImmutableList.of(valueExpr)
                               : Collections.<ExprRootNode<? extends ExprNode>>emptyList();
  }

}
