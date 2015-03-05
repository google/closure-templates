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
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprparse.ExprParseUtils;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soyparse.ErrorReporter;
import com.google.template.soy.soyparse.ErrorReporter.Checkpoint;
import com.google.template.soy.soyparse.TransitionalThrowingErrorReporter;
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
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MsgPluralNode extends AbstractParentCommandNode<CaseOrDefaultNode>
    implements MsgSubstUnitNode, SplitLevelTopNode<CaseOrDefaultNode>, ExprHolderNode {


  /** An expression, and optional "offset" attribute. */
  private static final Pattern COMMAND_TEXT_PATTERN =
      Pattern.compile("(.+?) ( \\s+ offset= .+ )?", Pattern.COMMENTS);

  /** Parser for the attributes in the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("plural",
          new Attribute("offset", Attribute.ALLOW_ALL_VALUES, null));

  /** Fallback base plural var name. */
  public static final String FALLBACK_BASE_PLURAL_VAR_NAME = "NUM";

  /** The offset. */
  private final int offset;

  /** The parsed expression. */
  private final ExprRootNode<?> pluralExpr;

  /** The base plural var name (what the translator sees). */
  private final String basePluralVarName;

  private MsgPluralNode(
      int id,
      String commandText,
      int offset,
      ExprRootNode<?> pluralExpr,
      String basePluralVarName) {
    super(id, "plural", commandText);
    this.offset = offset;
    this.pluralExpr = pluralExpr;
    this.basePluralVarName = basePluralVarName;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected MsgPluralNode(MsgPluralNode orig) {
    super(orig);
    this.offset = orig.offset;
    this.pluralExpr = orig.pluralExpr.clone();
    this.basePluralVarName = orig.basePluralVarName;
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


  /** Returns the base plural var name (what the translator sees). */
  @Override public String getBaseVarName() {
    return basePluralVarName;
  }


  @Override public boolean shouldUseSameVarNameAs(MsgSubstUnitNode other) {
    return (other instanceof MsgPluralNode) &&
        this.getCommandText().equals(((MsgPluralNode) other).getCommandText());
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    return ImmutableList.of(new ExprUnion(pluralExpr));
  }


  @Override public MsgBlockNode getParent() {
    return (MsgBlockNode) super.getParent();
  }


  @Override public MsgPluralNode clone() {
    return new MsgPluralNode(this);
  }

  /**
   * Builder for {@link MsgPluralNode}.
   */
  public static final class Builder {

    public static final MsgPluralNode ERROR = new Builder(-1, "plural", SourceLocation.UNKNOWN)
        .buildAndThrowIfInvalid(); // guaranteed to be valid

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
     * Returns a new {@link MsgPluralNode} built from the builder's state. If the builder's state
     * is invalid, errors are reported to {@code errorReporter} and {@link Builder#ERROR}
     * is returned.
     */
    public MsgPluralNode build(ErrorReporter errorReporter) {
      Checkpoint checkpoint = errorReporter.checkpoint();

      Matcher matcher = COMMAND_TEXT_PATTERN.matcher(commandText);
      if (!matcher.matches()) {
        errorReporter.report(SoySyntaxException.createWithMetaInfo(
            "Invalid 'plural' command text \"" + commandText + "\".", sourceLocation));
      }

      ExprRootNode<?> pluralExpr = ExprParseUtils.parseExprElseThrowSoySyntaxException(
          matcher.group(1),
          "Invalid expression in 'plural' command text \"" + commandText + "\".");

      int offset = 0;

      // If attributes were given, parse them.
      if (matcher.group(2) != null) {
        try {
          Map<String, String> attributes = ATTRIBUTES_PARSER.parse(matcher.group(2).trim());
          String offsetAttribute = attributes.get("offset");
          offset = Integer.parseInt(offsetAttribute);
          if (offset < 0) {
            errorReporter.report(SoySyntaxException.createWithMetaInfo(
                "The 'offset' for plural must be a nonnegative integer.", sourceLocation));
          }
        } catch (NumberFormatException nfe) {
          errorReporter.report(SoySyntaxException.createCausedWithMetaInfo(
              "Invalid offset in 'plural' command text \"" + commandText + "\".",
              nfe,
              sourceLocation,
              null /* filePath */,
              null /* templateName */));
        }
      }

      String basePluralVarName = MsgSubstUnitBaseVarNameUtils.genNaiveBaseNameForExpr(
              pluralExpr, FALLBACK_BASE_PLURAL_VAR_NAME);

      if (errorReporter.errorsSince(checkpoint)) {
        return ERROR;
      }

      MsgPluralNode node
          = new MsgPluralNode(id, commandText, offset, pluralExpr, basePluralVarName);
      node.setSourceLocation(sourceLocation);
      return node;
    }

    private MsgPluralNode buildAndThrowIfInvalid() {
      TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
      MsgPluralNode node = build(errorReporter);
      errorReporter.throwIfErrorsPresent();
      return node;
    }
  }
}
