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
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;

/**
 * Node representing a 'case' block in a 'plural' block.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MsgPluralCaseNode extends CaseOrDefaultNode implements MsgBlockNode {

  private static final SoyErrorKind PLURAL_CASE_OUT_OF_BOUNDS =
      SoyErrorKind.of("Plural cases must be nonnegative integers.");
  private static final SoyErrorKind MALFORMED_PLURAL_CASE =
      SoyErrorKind.of("Invalid number in ''plural case'' command text");

  // A plural 'case' can only have a number in the command text.
  /** The number for this case */
  private final int caseNumber;

  private MsgPluralCaseNode(
      int id, SourceLocation sourceLocation, String commandText, int caseNumber) {
    super(id, sourceLocation, "case", commandText);
    this.caseNumber = caseNumber;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private MsgPluralCaseNode(MsgPluralCaseNode orig, CopyState copyState) {
    super(orig, copyState);
    this.caseNumber = orig.caseNumber;
  }

  @Override
  public Kind getKind() {
    return Kind.MSG_PLURAL_CASE_NODE;
  }

  /** Returns the case number. */
  public int getCaseNumber() {
    return caseNumber;
  }

  @Override
  public MsgPluralCaseNode copy(CopyState copyState) {
    return new MsgPluralCaseNode(this, copyState);
  }

  /** Builder for {@link MsgPluralCaseNode}. */
  public static final class Builder {

    public static final MsgPluralCaseNode ERROR =
        new MsgPluralCaseNode(-1, SourceLocation.UNKNOWN, "error", 1);

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
     * Builds a new {@link MsgPluralCaseNode} from this builder's state. If the builder's state is
     * invalid, errors are reported to the {@code errorReporter} and {@link Builder#ERROR} is
     * returned.
     */
    public MsgPluralCaseNode build(ErrorReporter errorReporter) {
      Checkpoint checkpoint = errorReporter.checkpoint();

      int caseNumber = 0;
      try {
        caseNumber = Integer.parseInt(commandText);
        if (caseNumber < 0) {
          errorReporter.report(sourceLocation, PLURAL_CASE_OUT_OF_BOUNDS);
        }
      } catch (NumberFormatException nfe) {
        errorReporter.report(sourceLocation, MALFORMED_PLURAL_CASE);
      }

      if (errorReporter.errorsSince(checkpoint)) {
        return ERROR;
      }

      MsgPluralCaseNode node = new MsgPluralCaseNode(id, sourceLocation, commandText, caseNumber);
      return node;
    }
  }
}
