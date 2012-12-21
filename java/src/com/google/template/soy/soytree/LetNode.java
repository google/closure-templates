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

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.exprparse.ExprParseUtils;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.SoyNode.LocalVarInlineNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;


/**
 * Abstract node representing a 'let' statement.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public abstract class LetNode extends AbstractCommandNode
    implements StandaloneNode, StatementNode, LocalVarInlineNode {


  /**
   * Return value for {@code parseCommandTextHelper()}.
   */
  protected static class CommandTextParseResult {

    /** The parsed local var name (without '$'). */
    public final String localVarName;
    /** The parsed value expr, or null if none. */
    @Nullable public final ExprRootNode<?> valueExpr;
    /** The parsed param's content kind, or null if none. */
    @Nullable public final ContentKind contentKind;

    private CommandTextParseResult(
        String localVarName, @Nullable ExprRootNode<?> valueExpr,
        @Nullable ContentKind contentKind) {
      this.localVarName = localVarName;
      this.valueExpr = valueExpr;
      this.contentKind = contentKind;
    }
  }


  /** Pattern for a variable name plus optional value or attributes (but not both). */
  // Note: group 1 = local var name, group 2 = value expr (or null), group 3 = trailing attributes
  // (or null).
  private static final Pattern COMMAND_TEXT_PATTERN = Pattern.compile(
      "( [$] \\w+ ) (?: \\s* : \\s* (\\S .*) | \\s+ (\\S .*) )?",
      Pattern.COMMENTS | Pattern.DOTALL);


  /** Parser for optional attributes in the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser(
          "let",
          new Attribute("kind", NodeContentKinds.getAttributeValues(), null));


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
   * @return An info object containing the parse results.
   * @throws SoySyntaxException If a syntax error is found.
   */
  protected CommandTextParseResult parseCommandTextHelper(String commandText)
      throws SoySyntaxException {

    Matcher matcher = COMMAND_TEXT_PATTERN.matcher(commandText);
    if (!matcher.matches()) {
      throw SoySyntaxException.createWithoutMetaInfo(
          "Invalid 'let' command text \"" + commandText + "\".");
    }

    String localVarName;
    localVarName = ExprParseUtils.parseVarNameElseThrowSoySyntaxException(
        matcher.group(1), "Invalid variable name in 'let' command text \"" + commandText + "\".");

    ExprRootNode<?> valueExpr;
    if (matcher.group(2 /* value expression */) != null) {
      valueExpr = ExprParseUtils.parseExprElseThrowSoySyntaxException(
          matcher.group(2),
          "Invalid value expression in 'let' command text \"" + commandText + "\".");
    } else {
      valueExpr = null;
    }

    ContentKind contentKind;
    if (matcher.group(3 /* optional attributes */) != null) {
      Preconditions.checkState(matcher.group(2) == null,
          "Match groups for value expression and optional attributes should be mutually exclusive");
      // Parse optional attributes
      Map<String, String> attributes = ATTRIBUTES_PARSER.parse(matcher.group(3));
      contentKind = (attributes.get("kind") != null)
          ? NodeContentKinds.forAttributeValue(attributes.get("kind")) : null;
    } else {
      contentKind = null;
    }

    return new CommandTextParseResult(localVarName, valueExpr, contentKind);
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
