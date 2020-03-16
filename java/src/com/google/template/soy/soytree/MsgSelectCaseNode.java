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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;

/**
 * Node representing a 'case' block in a 'select' block.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MsgSelectCaseNode extends CaseOrDefaultNode implements MsgBlockNode {

  /** The value for this case. */
  private final String caseValue;

  public MsgSelectCaseNode(
      int id, SourceLocation location, SourceLocation openTagLocation, String caseValue) {
    super(id, location, openTagLocation, "case");
    this.caseValue = checkNotNull(caseValue);
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
  public String getCommandText() {
    return String.format("'%s'", caseValue);
  }

  @Override
  public MsgSelectCaseNode copy(CopyState copyState) {
    return new MsgSelectCaseNode(this, copyState);
  }
}
