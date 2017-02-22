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
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Abstract node representing a 'param'.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public abstract class CallParamNode extends AbstractCommandNode {

  private static final SoyErrorKind INVALID_COMMAND_TEXT =
      SoyErrorKind.of("Invalid param command text \"{0}\"");

  /** Return value for {@code parseCommandTextHelper()}. */
  public static final class CommandTextParseResult {
    final String originalCommantText;
    /** The parsed key. */
    final String key;
    /** The parsed value expr, or null if none. */
    @Nullable final ExprUnion valueExprUnion;
    /** The parsed param's content kind, or null if none. */
    @Nullable public final ContentKind contentKind;

    private CommandTextParseResult(
        String originalCommantText,
        String key,
        @Nullable ExprUnion valueExprUnion,
        @Nullable ContentKind contentKind) {
      this.originalCommantText = originalCommantText;
      this.key = key;
      this.valueExprUnion = valueExprUnion;
      this.contentKind = contentKind;
    }
  }

  /** Pattern for a key plus optional value or attributes (but not both). */
  //Note: group 1 = key, group 2 = value (or null), group 3 = trailing attributes (or null).
  private static final Pattern NONATTRIBUTE_COMMAND_TEXT =
      Pattern.compile(
          "^ \\s* (\\w+) (?: \\s* : \\s* (\\S .*) | \\s* (\\S .*) )? $",
          Pattern.COMMENTS | Pattern.DOTALL);

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser(
          "param", new Attribute("kind", NodeContentKinds.getAttributeValues(), null));

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
   *
   * @param orig The node to copy.
   */
  protected CallParamNode(CallParamNode orig, CopyState copyState) {
    super(orig, copyState);
  }

  /** Returns the param key. */
  public abstract String getKey();

  @Override
  public CallNode getParent() {
    return (CallNode) super.getParent();
  }

  /** Base class for {@link CallParamContentNode.Builder} and {@link CallParamValueNode.Builder}. */
  static class Builder {

    protected final int id;
    protected final CommandTextParseResult parseResult;
    protected final SourceLocation sourceLocation;

    protected Builder(int id, CommandTextParseResult parseResult, SourceLocation sourceLocation) {
      this.id = id;
      this.parseResult = parseResult;
      this.sourceLocation = sourceLocation;
    }
  }

  /**
   * Helper used by subclass builders to parse the command text.
   *
   * @return An info object containing the parse results.
   */
  public static CommandTextParseResult parseCommandTextHelper(
      String commandText, SoyParsingContext context, SourceLocation location) {

    // Parse the command text into key and optional valueExprText or extra attributes
    // TODO(user): instead of munging the command text, use a parser that understands
    // the actual content.
    Matcher nctMatcher = NONATTRIBUTE_COMMAND_TEXT.matcher(commandText);
    if (!nctMatcher.matches()) {
      context.report(location, INVALID_COMMAND_TEXT, commandText);
      return new CommandTextParseResult(
          commandText, "bad_key", null /* valueExprUnion */, null /* contentKind */);
    }
    // Convert {param foo : $bar/} and {param foo kind="xyz"/} syntax into attributes.
    String key = nctMatcher.group(1);

    // Check the validity of the key name, this will report appropriate errors to the
    // reporter if it fails.
    new ExpressionParser("$" + key, location, context).parseVariable();

    ContentKind contentKind;
    if (nctMatcher.group(3) != null) {
      Preconditions.checkState(nctMatcher.group(2) == null);
      Map<String, String> attributes =
          ATTRIBUTES_PARSER.parse(nctMatcher.group(3), context, location);
      contentKind = NodeContentKinds.forAttributeValue(attributes.get("kind"));
    } else {
      contentKind = null;
    }

    String valueExprText = nctMatcher.group(2);
    if (valueExprText == null) {
      return new CommandTextParseResult(commandText, key, null /* valueExprUnion */, contentKind);
    }
    ExprRootNode expr =
        new ExprRootNode(new ExpressionParser(valueExprText, location, context).parseExpression());
    return new CommandTextParseResult(commandText, key, new ExprUnion(expr), contentKind);
  }
}
