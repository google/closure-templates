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
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;


/**
 * Abstract node representing a 'param'.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public abstract class CallParamNode extends AbstractCommandNode {


  /**
   * Return value for {@code parseCommandTextHelper()}.
   */
  protected static class CommandTextParseResult {

    /** The parsed key. */
    public final String key;
    /** The parsed value expr, or null if none. */
    @Nullable public final ExprUnion valueExprUnion;

    private CommandTextParseResult(String key, ExprUnion valueExprUnion) {
      this.key = key;
      this.valueExprUnion = valueExprUnion;
    }
  }


  /** Pattern for a key and optional value not listed as attributes. */
  // Note: group 1 = key, group 2 = value (or null).
  private static final Pattern NONATTRIBUTE_COMMAND_TEXT =
      Pattern.compile(
          "^ (?! key=\") (\\w+) (?: \\s* : \\s* (\\S .*) )? $", Pattern.COMMENTS | Pattern.DOTALL);

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser(
          "param",
          new Attribute(
              "key", Attribute.ALLOW_ALL_VALUES, Attribute.NO_DEFAULT_VALUE_BECAUSE_REQUIRED),
          new Attribute("value", Attribute.ALLOW_ALL_VALUES, null));


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   */
  protected CallParamNode(int id, String commandText) {
    super(id, "param", commandText);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected CallParamNode(CallParamNode orig) {
    super(orig);
  }


  /**
   * Helper used by subclass constructors to parse the command text.
   * @param commandText The command text.
   * @return An info object containing the parse results.
   * @throws SoySyntaxException If a syntax error is found.
   */
  protected CommandTextParseResult parseCommandTextHelper(String commandText)
      throws SoySyntaxException {

    String key;
    String valueExprText;
    ExprUnion valueExprUnion;

    // Parse the command text into key and valueExprText.
    Matcher nctMatcher = NONATTRIBUTE_COMMAND_TEXT.matcher(commandText);
    if (nctMatcher.matches()) {
      key = nctMatcher.group(1);
      valueExprText = nctMatcher.group(2);
    } else {
      Map<String, String> attributes = ATTRIBUTES_PARSER.parse(commandText);
      key = attributes.get("key");
      valueExprText = attributes.get("value");
    }

    // Check the validity of the key name.
    try {
      DataRefNode dataRef = (new ExpressionParser("$" + key)).parseDataReference().getChild(0);
      if (dataRef.numChildren() > 1 || dataRef.isIjDataRef()) {
        throw new SoySyntaxException(
            "The key in a 'param' tag must be top level, i.e. not contain multiple keys" +
            " (invalid 'param' command text \"" + getCommandText() + "\").");
      }
    } catch (TokenMgrError tme) {
      throw createExceptionForInvalidKey(tme);
    } catch (ParseException pe) {
      throw createExceptionForInvalidKey(pe);
    }

    // If valueExprText exists, try to parse it.
    if (valueExprText != null) {
      ExprRootNode<?> valueExpr;
      try {
        valueExpr = (new ExpressionParser(valueExprText)).parseExpression();
      } catch (TokenMgrError tme) {
        valueExpr = null;
      } catch (ParseException pe) {
        valueExpr = null;
      }
      valueExprUnion =
          (valueExpr != null) ? new ExprUnion(valueExpr) : new ExprUnion(valueExprText);
    } else {
      valueExprUnion = null;
    }

    return new CommandTextParseResult(key, valueExprUnion);
  }


  /**
   * Private helper for parseCommandTextHelper().
   * @param cause The underlying exception.
   * @return The SoySyntaxException to be thrown.
   */
  private SoySyntaxException createExceptionForInvalidKey(Throwable cause) {
    //noinspection ThrowableInstanceNeverThrown
    return new SoySyntaxException(
        "Invalid key in 'param' command text \"" + getCommandText() + "\".", cause);
  }


  /**
   * Returns the param key.
   */
  public abstract String getKey();


  @Override public CallNode getParent() {
    return (CallNode) super.getParent();
  }

}
