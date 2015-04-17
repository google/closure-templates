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
import com.google.template.soy.ErrorReporterImpl;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.VarRefNode;
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
 */
public abstract class CallParamNode extends AbstractCommandNode {

  private static final SoyError KEY_IS_NOT_TOP_LEVEL
      = SoyError.of("The key in a ''param'' tag must be top level, i.e. not contain multiple keys "
          + "(invalid ''param'' command text \"{0}\").");

  /**
   * Return value for {@code parseCommandTextHelper()}.
   */
  protected static class CommandTextParseResult {

    /** The parsed key. */
    final String key;
    /** The parsed value expr, or null if none. */
    @Nullable final ExprUnion valueExprUnion;
    /** The parsed param's content kind, or null if none. */
    @Nullable final ContentKind contentKind;

    private CommandTextParseResult(
        String key, @Nullable ExprUnion valueExprUnion, @Nullable ContentKind contentKind) {
      this.key = key;
      this.valueExprUnion = valueExprUnion;
      this.contentKind = contentKind;
    }
  }


  /** Pattern for a key plus optional value or attributes (but not both). */
  //Note: group 1 = key, group 2 = value (or null), group 3 = trailing attributes (or null).
  private static final Pattern NONATTRIBUTE_COMMAND_TEXT =
      Pattern.compile(
          "^ (?! key=\") (\\w+) (?: \\s* : \\s* (\\S .*) | (.*) )? $",
          Pattern.COMMENTS | Pattern.DOTALL);


  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser(
          "param",
          new Attribute(
              "key", Attribute.ALLOW_ALL_VALUES, Attribute.NO_DEFAULT_VALUE_BECAUSE_REQUIRED),
          new Attribute("value", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("kind", NodeContentKinds.getAttributeValues(), null));


  /**
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   * @param commandText The command text.
   */
  protected CallParamNode(int id, SourceLocation sourceLocation, String commandText) {
    super(id, sourceLocation, "param", commandText);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected CallParamNode(CallParamNode orig) {
    super(orig);
  }


  /**
   * Returns the param key.
   */
  public abstract String getKey();


  @Override public CallNode getParent() {
    return (CallNode) super.getParent();
  }

  /**
   * Base class for {@link CallParamContentNode.Builder} and {@link CallParamValueNode.Builder}.
   */
  static class Builder {

    protected final int id;
    protected final String commandText;
    protected final SourceLocation sourceLocation;

    protected Builder(int id, String commandText, SourceLocation sourceLocation) {
      this.id = id;
      this.commandText = commandText;
      this.sourceLocation = sourceLocation;
    }

    /**
     * Helper used by subclass builders to parse the command text.
     * @return An info object containing the parse results.
     */
    protected CommandTextParseResult parseCommandTextHelper(ErrorReporter errorReporter) {
      String commandText = this.commandText;

      // Parse the command text into key and optional valueExprText or extra attributes
      // TODO(user): instead of munging the command text, use a parser that understands
      // the actual content.
      Matcher nctMatcher = NONATTRIBUTE_COMMAND_TEXT.matcher(commandText);
      if (nctMatcher.matches()) {
        // Convert {param foo : $bar/} and {param foo kind="xyz"/} syntax into attributes.
        commandText = "key=\"" + nctMatcher.group(1) + "\"";

        if (nctMatcher.group(3) != null) {
          Preconditions.checkState(nctMatcher.group(2) == null);
          commandText += " " + nctMatcher.group(3);
        }

        // Note that we do not convert a group(2) match into a value= attribute, since the attribute
        // parser does not support a quoting syntax for double quotes within an attribute, which
        // would result in errors for e.g. {param foo : bar " baz/}
      }
      Map<String, String> attributes
          = ATTRIBUTES_PARSER.parse(commandText, errorReporter, sourceLocation);
      String key = attributes.get("key");
      String valueExprText;
      // If the command was of the form {param foo : <bar>}, obtain the value from match group 2.
      if (nctMatcher.matches() && (nctMatcher.group(2) != null)) {
        valueExprText = nctMatcher.group(2);
      } else {
        valueExprText = attributes.get("value");
      }
      ContentKind contentKind =
          (attributes.get("kind") != null) ?
              NodeContentKinds.forAttributeValue(attributes.get("kind")) :
              null;

      // Check the validity of the key name.
      ExprNode dataRef = new ExpressionParser("$" + key, sourceLocation, errorReporter)
          .parseDataReference();

      if (!(dataRef instanceof VarRefNode) || ((VarRefNode) dataRef).isInjected()) {
        errorReporter.report(sourceLocation, KEY_IS_NOT_TOP_LEVEL, commandText);
      }

      if (valueExprText == null) {
        return new CommandTextParseResult(key, null /* valueExprUnion */, contentKind);
      }

      // If valueExprText exists, try to parse it.
      // TODO(user): Remove throwaway ErrorReporter.
      // Explanation: In certain cases, Soy considers param nodes with clearly malformed command
      // texts not to be an error. (See TemplateParserTest#testRecognizeCommands, around line 359.)
      // To preserve that behavior, we create a throwaway error reporter here in order not to
      // report it back to the main error reporter.
      //
      // Use the real error manager once any currently broken templates are migrated.
      ErrorReporter throwaway = new ErrorReporterImpl();
      Checkpoint checkpoint = throwaway.checkpoint();
      ExprNode valueExpr
          = new ExpressionParser(valueExprText, sourceLocation, throwaway).parseExpression();
      ExprUnion valueExprUnion = throwaway.errorsSince(checkpoint)
          ? new ExprUnion(valueExprText)
          : new ExprUnion(valueExpr);
      return new CommandTextParseResult(key, valueExprUnion, contentKind);
    }
  }
}
