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

import com.google.common.collect.Lists;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import java.util.List;

/**
 * Node representing a 'case' block in a 'switch' block.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SwitchCaseNode extends CaseOrDefaultNode
    implements ConditionalBlockNode, ExprHolderNode {

  /** The text for this case's expression list. */
  private final String exprListText;

  /** The parsed expression list. */
  private final List<ExprRootNode> exprList;

  private SwitchCaseNode(
      int id, String commandText, List<ExprRootNode> exprList, SourceLocation sourceLocation) {
    super(id, sourceLocation, "case", commandText);
    this.exprList = exprList;
    this.exprListText = commandText;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private SwitchCaseNode(SwitchCaseNode orig, CopyState copyState) {
    super(orig, copyState);
    this.exprListText = orig.exprListText;
    this.exprList = Lists.newArrayListWithCapacity(orig.exprList.size());
    for (ExprRootNode origExpr : orig.exprList) {
      this.exprList.add(origExpr.copy(copyState));
    }
  }

  @Override
  public Kind getKind() {
    return Kind.SWITCH_CASE_NODE;
  }

  /** Returns the text for this case's expression list. */
  public String getExprListText() {
    return exprListText;
  }

  /** Returns the parsed expression list, or null if the expression list is not in V2 syntax. */
  public List<ExprRootNode> getExprList() {
    return exprList;
  }

  @Override
  public List<ExprUnion> getAllExprUnions() {
    return ExprUnion.createList(exprList);
  }

  @Override
  public SwitchCaseNode copy(CopyState copyState) {
    return new SwitchCaseNode(this, copyState);
  }

  /** Builder for {@link SwitchCaseNode}. */
  public static final class Builder {
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
     * Returns a new {@link SwitchCaseNode} from the state of this builder, reporting syntax errors
     * to the given {@link ErrorReporter}.
     */
    public SwitchCaseNode build(SoyParsingContext context) {
      List<ExprRootNode> exprList =
          ExprRootNode.wrap(
              new ExpressionParser(commandText, sourceLocation, context).parseExpressionList());
      return new SwitchCaseNode(id, commandText, exprList, sourceLocation);
    }
  }
}
