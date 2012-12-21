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
import com.google.template.soy.exprparse.ExprParseUtils;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;


/**
 * Node representing a 'case' block in a 'select' block.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 * @author Mohamed Eldawy
 */
public class MsgSelectCaseNode extends CaseOrDefaultNode implements MsgBlockNode {


  /** The value for this case. */
  private final String caseValue;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public MsgSelectCaseNode(int id, String commandText) throws SoySyntaxException {
    super(id, "case", commandText);

    ExprRootNode<?> strLit = ExprParseUtils.parseExprElseThrowSoySyntaxException(
        commandText, "Invalid expression in 'case' command text \"" + commandText + "\".");
    // Make sure the expression is a string.
    if (!(strLit.numChildren() == 1 && strLit.getChild(0) instanceof StringNode)) {
        throw SoySyntaxException.createWithoutMetaInfo("Invalid string for select 'case'.");
    }
    caseValue = ((StringNode) (strLit.getChild(0))).getValue();
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected MsgSelectCaseNode(MsgSelectCaseNode orig) {
    super(orig);
    this.caseValue = orig.caseValue;
  }


  @Override public Kind getKind() {
    return Kind.MSG_SELECT_CASE_NODE;
  }


  /** Returns the case value. */
  public String getCaseValue() {
    return caseValue;
  }


  @Override public MsgSelectCaseNode clone() {
    return new MsgSelectCaseNode(this);
  }

}
