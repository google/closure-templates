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

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;

/**
 * Node representing a 'case' block in a 'plural' block.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 * @author Mohamed Eldawy
 */
public class MsgPluralCaseNode extends CaseOrDefaultNode implements MsgBlockNode {

  // A plural 'case' can only have a number in the command text.
  /** The number for this case */
  private final int caseNumber;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public MsgPluralCaseNode(int id, String commandText) throws SoySyntaxException {
    super(id, "case", commandText);

    try {
      caseNumber = Integer.parseInt(commandText);
      if (caseNumber < 0) {
        throw SoySyntaxException.createWithoutMetaInfo(
            "Plural cases must be nonnegative integers.");
      }
    } catch (NumberFormatException nfe) {
      throw SoySyntaxException.createCausedWithoutMetaInfo(
          "Invalid number in 'plural case' command text \"" + getCommandText() + "\".", nfe);
    }
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected MsgPluralCaseNode(MsgPluralCaseNode orig) {
    super(orig);
    this.caseNumber = orig.caseNumber;
  }


  @Override public Kind getKind() {
    return Kind.MSG_PLURAL_CASE_NODE;
  }


  /** Returns the case number. */
  public int getCaseNumber() {
    return caseNumber;
  }


  @Override public MsgPluralCaseNode clone() {
    return new MsgPluralCaseNode(this);
  }

}
