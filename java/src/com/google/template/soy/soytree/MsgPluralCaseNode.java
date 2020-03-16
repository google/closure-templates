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

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;

/**
 * Node representing a 'case' block in a 'plural' block.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MsgPluralCaseNode extends CaseOrDefaultNode implements MsgBlockNode {

  /** The number for this case. Plural 'case' nodes can only have numbers. */
  private final int caseNumber;

  public MsgPluralCaseNode(
      int id, SourceLocation location, SourceLocation openTagLocation, int caseNumber) {
    super(id, location, openTagLocation, "case");
    Preconditions.checkArgument(caseNumber >= 0);
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
  public String getCommandText() {
    return Integer.toString(caseNumber);
  }

  @Override
  public MsgPluralCaseNode copy(CopyState copyState) {
    return new MsgPluralCaseNode(this, copyState);
  }
}
