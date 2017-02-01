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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;

/**
 * Node representing a 'case' block in a 'select' block.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MsgSelectCaseNode extends CaseOrDefaultNode implements MsgBlockNode {

  private static final SoyErrorKind INVALID_STRING_FOR_SELECT_CASE =
      SoyErrorKind.of("Invalid string for select ''case''.");

  /** The value for this case. */
  private final String caseValue;

  private MsgSelectCaseNode(
      int id, SourceLocation sourceLocation, String commandText, String caseValue) {
    super(id, sourceLocation, "case", commandText);
    this.caseValue = caseValue;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private MsgSelectCaseNode(MsgSelectCaseNode orig, CopyState copyState) {
    super(orig, copyState);
    this.caseValue = orig.caseValue;
  }

  @Override
  public Kind getKind() {
    return Kind.MSG_SELECT_CASE_NODE;
  }

  /** Returns the case value. */
  public String getCaseValue() {
    return caseValue;
  }

  @Override
  public MsgSelectCaseNode copy(CopyState copyState) {
    return new MsgSelectCaseNode(this, copyState);
  }

  /** Builder for {@link MsgSelectCaseNode}. */
  public static final class Builder {
    private static MsgSelectCaseNode error() {
      return new MsgSelectCaseNode(-1, SourceLocation.UNKNOWN, "error", "error");
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
     * Returns a new {@link MsgSelectCaseNode} built from the builder's state. If the builder's
     * state is invalid, errors are reported to the {@code errorReporter} and {@link Builder#error}
     * is returned.
     */
    public MsgSelectCaseNode build(SoyParsingContext context) {
      Checkpoint checkpoint = context.errorReporter().checkpoint();

      ExprRootNode strLit =
          new ExprRootNode(
              new ExpressionParser(commandText, sourceLocation, context).parseExpression());

      // Make sure the expression is a string.
      if (!(strLit.numChildren() == 1 && strLit.getRoot() instanceof StringNode)) {
        context.report(sourceLocation, INVALID_STRING_FOR_SELECT_CASE);
      }

      if (context.errorReporter().errorsSince(checkpoint)) {
        return error();
      }

      String caseValue = ((StringNode) (strLit.getRoot())).getValue();
      return new MsgSelectCaseNode(id, sourceLocation, commandText, caseValue);
    }
  }
}
