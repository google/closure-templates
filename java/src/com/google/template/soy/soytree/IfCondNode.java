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
import com.google.common.collect.ImmutableList;
import com.google.template.soy.ErrorReporterImpl;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;

import java.util.List;

/**
 * Node representing a block within an 'if' statement that has a conditional expression (i.e.
 * either the 'if' block or an 'elseif' block).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class IfCondNode extends AbstractBlockCommandNode
    implements ConditionalBlockNode, ExprHolderNode {


  /** The parsed expression. */
  private final ExprUnion exprUnion;

  /**
   * @param id The node's id.
   * @param commandText The node's command text.
   * @param sourceLocation The node's source location.
   */
  public static Builder ifBuilder(int id, String commandText, SourceLocation sourceLocation) {
    return new Builder(id, "if", commandText, sourceLocation);
  }

  /**
   * @param id The node's id.
   * @param commandText The node's command text.
   * @param sourceLocation The node's source location.
   */
  public static Builder elseifBuilder(int id, String commandText, SourceLocation sourceLocation) {
    return new Builder(id, "elseif", commandText, sourceLocation);
  }

  /**
   * @param id The id for this node.
   * @param commandName The command name -- either 'if' or 'elseif'.
   * @param condition Determines when the body is performed.
   */
  public IfCondNode(
      int id, SourceLocation sourceLocation, String commandName, ExprUnion condition) {
    super(id, sourceLocation, commandName, condition.getExprText());
    Preconditions.checkArgument(commandName.equals("if") || commandName.equals("elseif"));
    this.exprUnion = condition;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  private IfCondNode(IfCondNode orig, CopyState copyState) {
    super(orig, copyState);
    this.exprUnion = (orig.exprUnion != null) ? orig.exprUnion.copy(copyState) : null;
  }


  @Override public Kind getKind() {
    return Kind.IF_COND_NODE;
  }


  /** Returns the text of the conditional expression. */
  public String getExprText() {
    return exprUnion.getExprText();
  }


  /** Returns the parsed expression. */
  public ExprUnion getExprUnion() {
    return exprUnion;
  }


  @Override public String getCommandName() {
    return (getParent().getChild(0) == this) ? "if" : "elseif";
  }


  @Override public String getCommandText() {
    return exprUnion.getExprText();
  }


  @Override public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    // Note: No end tag.
    return sb.toString();
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    return ImmutableList.of(exprUnion);
  }


  @Override public IfCondNode copy(CopyState copyState) {
    return new IfCondNode(this, copyState);
  }

  /**
   * Builder for {@link IfCondNode}.
   */
  public static final class Builder {
    private final int id;
    private final String commandName;
    private final String commandText;
    private final SourceLocation sourceLocation;

    private Builder(int id, String commandName, String commandText, SourceLocation sourceLocation) {
      this.id = id;
      this.commandName = commandName;
      this.commandText = commandText;
      this.sourceLocation = sourceLocation;
    }

    /**
     * Returns a new {@link IfCondNode} built from this builder's state.
     * TODO(user): Most node builders report syntax errors to the {@link ErrorReporter}
     * argument. This builder ignores the error reporter argument because if nodes have
     * special fallback logic for when parsing of the command text fails.
     * Such parsing failures should thus not currently be reported as "errors".
     * It seems possible and desirable to change Soy to consider these to be errors,
     * but it's not trivial, because it could break templates that currently compile.
     */
    public IfCondNode build(ErrorReporter unusedForNow) {
      ExprUnion condition = buildExprUnion();
      return new IfCondNode(id, sourceLocation, commandName, condition);
    }

    private ExprUnion buildExprUnion() {
      ErrorReporter errorReporter = new ErrorReporterImpl();
      Checkpoint checkpoint = errorReporter.checkpoint();
      ExprNode expr = new ExpressionParser(commandText, sourceLocation, errorReporter)
          .parseExpression();
      return errorReporter.errorsSince(checkpoint)
          ? new ExprUnion(commandText)
          : new ExprUnion(expr);
    }

  }

}
