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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.ParseException;
import com.google.template.soy.exprparse.TokenMgrError;
import com.google.template.soy.exprtree.DataRefKeyNode;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderNode;
import com.google.template.soy.soytree.SoyNode.ParentExprHolderNode;
import com.google.template.soy.soytree.SoyNode.SoyStatementNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;

import java.util.Collections;
import java.util.List;


/**
 * Node representing a 'print' statement.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class PrintNode extends AbstractParentSoyCommandNode<PrintDirectiveNode>
    implements SplitLevelTopNode<PrintDirectiveNode>, SoyStatementNode,
    ParentExprHolderNode<PrintDirectiveNode>, MsgPlaceholderNode {


  /** Whether the command 'print' is implicit. */
  private final boolean isImplicit;

  /** The text of the expression to print. */
  private final String exprText;

  /** The parsed expression (null if the expression is not in V2 syntax). */
  private final ExprRootNode<ExprNode> expr;

  /** The base placeholder name for this node (null if genBasePlaceholderName() is never called). */
  private String basePlaceholderName = null;


  /**
   * @param id The id for this node.
   * @param isImplicit Whether the command 'print' is implicit.
   * @param commandText The command text.
   * @param exprText The text of the expression to print.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public PrintNode(String id, boolean isImplicit, String commandText, String exprText)
      throws SoySyntaxException {

    super(id, "print", commandText);

    this.isImplicit = isImplicit;
    this.exprText = exprText;

    ExprRootNode<ExprNode> tempExpr = null;
    try {
      tempExpr = (new ExpressionParser(exprText)).parseExpression();
    } catch (TokenMgrError tme) {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
    } catch (ParseException pe) {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
    }
    expr = tempExpr;
  }


  /** Returns the text of the expression to print. */
  public String getExprText() {
    return exprText;
  }

  /** Returns the parsed expression, or null if the expression is not in V2 syntax. */
  public ExprRootNode<ExprNode> getExpr() {
    return expr;
  }


  /**
   * Given a value to be included in a generated string, create an appropriate
   * placeholder name to use in the generated code.
   *
   * We don't know what the object being referenced in the exprText is --
   * is it a local variable (starting with $) or a reference to a global
   * variable?
   *
   * Try parsing the expression as each potential kind of object - data
   * reference or global.  If the string parses as neither object,
   * then default to "XXX" for the placeholder, otherwise generate
   * an appropriate name based on the kind of reference and the provided name.
   * Placeholder names must stay constant between versions of Soy because
   * changing the placeholders requires re-translating the strings, and the
   * translators use the placeholder names as hints for context.
   *
   * @return Base name of placeholder.
   */
  @Override public String genBasePlaceholderName() {

    if (basePlaceholderName != null) {
      return basePlaceholderName;  // return previously generated name
    }

    // Attempt to parse the expression as a data ref for the purpose of using the last key as the
    // base placeholder name.
    DataRefNode exprAsDataRef = null;
    try {
      exprAsDataRef = ((new ExpressionParser(exprText)).parseDataReference()).getChild(0);
    } catch (TokenMgrError tme) {  // exprAsDataRef is still null
    } catch (ParseException pe) {  // exprAsDataRef is still null
    }
    if (exprAsDataRef != null) {
      ExprNode lastPart = exprAsDataRef.getChild(exprAsDataRef.numChildren() - 1);
      if (lastPart instanceof DataRefKeyNode) {
        String key = ((DataRefKeyNode) lastPart).getKey();
        basePlaceholderName = BaseUtils.convertToUpperUnderscore(key);
      }
    }

    // Didn't parse as a data reference?  Try parsing the string as a global
    // reference.  If it parses cleanly, then derive the appropriate placeholder
    // name.
    if (basePlaceholderName == null) {
      GlobalNode exprAsGlobal = null;
      try {
        exprAsGlobal = ((new ExpressionParser(exprText)).parseGlobal()).getChild(0);
      } catch (TokenMgrError tme) {  // exprAsGlobal is still null
      } catch (ParseException pe) {  // exprAsGlobal is still null
      }
      // If the name appears as a dotted list of components, only display
      // the last component.
      if (exprAsGlobal != null) {
        int lastDotIndex = exprAsGlobal.getName().lastIndexOf('.');
        String lastPart = exprAsGlobal.getName().substring(lastDotIndex + 1);
        basePlaceholderName = BaseUtils.convertToUpperUnderscore(lastPart);
      }
    }

    if (basePlaceholderName == null) {
      basePlaceholderName = "XXX";  // fallback value
    }

    return basePlaceholderName;
  }


  @Override public boolean isSamePlaceholderAs(MsgPlaceholderNode other) {
    return (other instanceof PrintNode) &&
           this.getCommandText().equals(((PrintNode) other).getCommandText());
  }


  @Override public List<? extends ExprRootNode<? extends ExprNode>> getAllExprs() {
    return (expr != null) ? ImmutableList.of(expr)
                          : Collections.<ExprRootNode<? extends ExprNode>>emptyList();
  }


  @Override public String getTagString() {
    return buildTagStringHelper(false, isImplicit);
  }


  @Override public String toSourceString() {
    return getTagString();
  }

}
