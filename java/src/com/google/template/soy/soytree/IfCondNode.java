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
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import java.util.List;

/**
 * Node representing a block within an 'if' statement that has a conditional expression (i.e. either
 * the 'if' block or an 'elseif' block).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
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
   * @param exprUnion Determines when the body is performed.
   */
  public IfCondNode(
      int id, SourceLocation sourceLocation, String commandName, ExprUnion exprUnion) {
    super(id, sourceLocation, commandName, exprUnion.getExprText());
    Preconditions.checkArgument(commandName.equals("if") || commandName.equals("elseif"));
    this.exprUnion = Preconditions.checkNotNull(exprUnion);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private IfCondNode(IfCondNode orig, CopyState copyState) {
    super(orig, copyState);
    this.exprUnion = orig.exprUnion.copy(copyState);
  }

  @Override
  public Kind getKind() {
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

  @Override
  public String getCommandName() {
    return (getParent().getChild(0) == this) ? "if" : "elseif";
  }

  @Override
  public String getCommandText() {
    return exprUnion.getExprText();
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    // Note: No end tag.
    return sb.toString();
  }

  @Override
  public List<ExprUnion> getAllExprUnions() {
    return ImmutableList.of(exprUnion);
  }

  @Override
  public IfCondNode copy(CopyState copyState) {
    return new IfCondNode(this, copyState);
  }

  /** Builder for {@link IfCondNode}. */
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

    /** Returns a new {@link IfCondNode} built from this builder's state. */
    public IfCondNode build(SoyParsingContext context) {
      ExprRootNode expr =
          new ExprRootNode(
              new ExpressionParser(commandText, sourceLocation, context).parseExpression());
      ExprUnion condition = new ExprUnion(expr);
      return new IfCondNode(id, sourceLocation, commandName, condition);
    }
  }
}
