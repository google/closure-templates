/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.pysrc.internal;

import com.google.common.base.Preconditions;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;

import java.util.ArrayList;
import java.util.List;


/**
 * Visitor for generating Python expressions for parse tree nodes.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class GenPyExprsVisitor extends AbstractSoyNodeVisitor<List<PyExpr>> {


  /**
   * Injectable factory for creating an instance of this class.
   */
  public static interface GenPyExprsVisitorFactory {

    public GenPyExprsVisitor create();
  }


  /** The IsComputableAsPyExprVisitor used by this instance (when needed). */
  private final IsComputableAsPyExprVisitor isComputableAsPyExprVisitor;

  private final GenPyExprsVisitorFactory genPyExprsVisitorFactory;

  /** List to collect the results. */
  private List<PyExpr> pyExprs;


  /**
   * @param isComputableAsPyExprVisitor The IsComputableAsPyExprVisitor used by this instance.
   */
  @AssistedInject
  GenPyExprsVisitor(IsComputableAsPyExprVisitor isComputableAsPyExprVisitor,
      GenPyExprsVisitorFactory genPyExprsVisitorFactory) {
    this.isComputableAsPyExprVisitor = isComputableAsPyExprVisitor;
    this.genPyExprsVisitorFactory = genPyExprsVisitorFactory;
  }

  @Override public List<PyExpr> exec(SoyNode node) {
    Preconditions.checkArgument(isComputableAsPyExprVisitor.exec(node));
    pyExprs = new ArrayList<>();
    visit(node);
    return pyExprs;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  /**
   * Example:
   * <pre>
   *   I'm feeling lucky!
   * </pre>
   * generates
   * <pre>
   *   'I\'m feeling lucky!'
   * </pre>
   */
  @Override protected void visitRawTextNode(RawTextNode node) {
    // Escape special characters in the text before writing as a string.
    String exprText = BaseUtils.escapeToSoyString(node.getRawText(), false);
    pyExprs.add(new PyStringExpr(exprText));
  }

  @Override protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    GenPyExprsVisitor genPyExprsVisitor = genPyExprsVisitorFactory.create();

    // MsgFallbackGroupNode could only have 1 or 2 child, see TemplateParseTest.java
    if (node.numChildren() == 1) {
      visitChildren(node);
    } else {
      StringBuilder pyExprTextSb = new StringBuilder();
      List<PyExpr> firstMsgPyExpr = genPyExprsVisitor.exec(node.getChild(0));
      List<PyExpr> fallbackMsgPyExpr = genPyExprsVisitor.exec(node.getChild(1));

      // Build Python ternary expression: a if cond else c
      pyExprTextSb.append(
          PyExprUtils.concatPyExprs(firstMsgPyExpr).toPyString().getText());
      pyExprTextSb.append(" if ");
      // TODO(steveyang): replace node.getId() with computed msgId once MsgNode is implemented
      pyExprTextSb.append("is_msg_available(" + node.getId() + ")");

      pyExprTextSb.append(" else ");
      pyExprTextSb.append(
          PyExprUtils.concatPyExprs(fallbackMsgPyExpr).toPyString().getText());
      pyExprs.add(new PyStringExpr(pyExprTextSb.toString(),
          Operator.CONDITIONAL.getPrecedence()));
    }
  }


  @Override protected void visitMsgNode(MsgNode node) {
    visitChildren(node);
  }
}
