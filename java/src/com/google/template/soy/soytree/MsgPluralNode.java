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
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgSubstUnitNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Node representing a 'plural' block.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MsgPluralNode extends AbstractParentCommandNode<CaseOrDefaultNode>
    implements MsgSubstUnitNode, SplitLevelTopNode<CaseOrDefaultNode>, ExprHolderNode {

  private static final SoyErrorKind INVALID_PLURAL_COMMAND_TEXT =
      SoyErrorKind.of("Invalid ''plural'' command text \"{0}\".");
  private static final SoyErrorKind PLURAL_OFFSET_OUT_OF_BOUNDS =
      SoyErrorKind.of("The ''offset'' for plural must be a nonnegative integer.");
  private static final SoyErrorKind MALFORMED_PLURAL_OFFSET =
      SoyErrorKind.of("Invalid offset in ''plural'' command text \"{0}\".");

  /** An expression, and optional "offset" attribute. */
  private static final Pattern COMMAND_TEXT_PATTERN =
      Pattern.compile("(.+?) ( \\s+ offset= .+ )?", Pattern.COMMENTS);

  /** Parser for the attributes in the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser(
          "plural", new Attribute("offset", Attribute.ALLOW_ALL_VALUES, null));

  /** Fallback base plural var name. */
  public static final String FALLBACK_BASE_PLURAL_VAR_NAME = "NUM";

  /** The offset. */
  private final int offset;

  /** The parsed expression. */
  private final ExprRootNode pluralExpr;

  /** The base plural var name (what the translator sees). */
  private final String basePluralVarName;

  private MsgPluralNode(
      int id,
      SourceLocation sourceLocation,
      String commandText,
      int offset,
      ExprRootNode pluralExpr,
      String basePluralVarName) {
    super(id, sourceLocation, "plural", commandText);
    this.offset = offset;
    this.pluralExpr = pluralExpr;
    this.basePluralVarName = basePluralVarName;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private MsgPluralNode(MsgPluralNode orig, CopyState copyState) {
    super(orig, copyState);
    this.offset = orig.offset;
    this.pluralExpr = orig.pluralExpr.copy(copyState);
    this.basePluralVarName = orig.basePluralVarName;
  }

  @Override
  public Kind getKind() {
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
  public ExprRootNode getExpr() {
    return pluralExpr;
  }

  /** Returns the base plural var name (what the translator sees). */
  @Override
  public String getBaseVarName() {
    return basePluralVarName;
  }

  @Override
  public boolean shouldUseSameVarNameAs(MsgSubstUnitNode other) {
    return (other instanceof MsgPluralNode)
        && this.getCommandText().equals(((MsgPluralNode) other).getCommandText());
  }

  @Override
  public List<ExprUnion> getAllExprUnions() {
    return ImmutableList.of(new ExprUnion(pluralExpr));
  }

  @Override
  public MsgBlockNode getParent() {
    return (MsgBlockNode) super.getParent();
  }

  @Override
  public MsgPluralNode copy(CopyState copyState) {
    return new MsgPluralNode(this, copyState);
  }

  /** Builder for {@link MsgPluralNode}. */
  public static final class Builder {

    private static MsgPluralNode error() {
      return new Builder(-1, "plural", SourceLocation.UNKNOWN)
          .build(SoyParsingContext.exploding()); // guaranteed to be valid
    }

    private final int id;
    private final String commandText;
    private final SourceLocation sourceLocation;

    /**
     * @param id The node's id.
     * @param commandText The node's command text.
     * @param sourceLocation The node's source location.
     */
    public Builder(int id, String commandText, SourceLocation sourceLocation) {
      this.id = id;
      this.commandText = commandText;
      this.sourceLocation = sourceLocation;
    }

    /**
     * Returns a new {@link MsgPluralNode} built from the builder's state. If the builder's state is
     * invalid, errors are reported to {@code errorReporter} and {@link Builder#error} is returned.
     */
    public MsgPluralNode build(SoyParsingContext context) {
      Checkpoint checkpoint = context.errorReporter().checkpoint();

      Matcher matcher = COMMAND_TEXT_PATTERN.matcher(commandText);
      if (!matcher.matches()) {
        context.report(sourceLocation, INVALID_PLURAL_COMMAND_TEXT, commandText);
      }

      ExprNode pluralExpr =
          new ExpressionParser(matcher.group(1), sourceLocation, context).parseExpression();

      int offset = 0;

      // If attributes were given, parse them.
      if (matcher.group(2) != null) {
        try {
          Map<String, String> attributes =
              ATTRIBUTES_PARSER.parse(matcher.group(2).trim(), context, sourceLocation);
          String offsetAttribute = attributes.get("offset");
          offset = Integer.parseInt(offsetAttribute);
          if (offset < 0) {
            context.report(sourceLocation, PLURAL_OFFSET_OUT_OF_BOUNDS);
          }
        } catch (NumberFormatException nfe) {
          context.report(sourceLocation, MALFORMED_PLURAL_OFFSET, commandText);
        }
      }

      String basePluralVarName =
          MsgSubstUnitBaseVarNameUtils.genNaiveBaseNameForExpr(
              pluralExpr, FALLBACK_BASE_PLURAL_VAR_NAME);

      if (context.errorReporter().errorsSince(checkpoint)) {
        return error();
      }

      return new MsgPluralNode(
          id, sourceLocation, commandText, offset, new ExprRootNode(pluralExpr), basePluralVarName);
    }
  }
}
