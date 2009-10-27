/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.parsepasses;

import com.google.common.collect.Lists;
import com.google.template.soy.base.IdGenerator;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.shared.SoyGeneralOptions.CssHandlingScheme;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoytreeUtils;

import java.util.List;


/**
 * Visitor for handling 'css' commands.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} must be called on a full parse tree.
 *
 * <p> Each scheme is handled as follows:
 * (a) LITERAL: Turn each CssNode into a RawTextNode.
 * (b) REFERENCE: Turn each CssNode into a PrintNode.
 * (c) BACKEND_SPECIFIC: Don't change anything. Let backend handle 'css' commands.
 *
 * @author Kai Huang
 */
public class HandleCssCommandVisitor extends AbstractSoyNodeVisitor<Void> {


  /** Scheme for handling 'css' commands. */
  private final CssHandlingScheme cssHandlingScheme;

  /** The list of CssNodes found in the tree. */
  private List<CssNode> cssNodes;


  /**
   * @param cssHandlingScheme Scheme for handling 'css' commands.
   */
  public HandleCssCommandVisitor(CssHandlingScheme cssHandlingScheme) {
    this.cssHandlingScheme = cssHandlingScheme;
  }


  @Override protected void setup() {
    cssNodes = Lists.newArrayList();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(SoyFileSetNode node) {

    if (cssHandlingScheme == CssHandlingScheme.BACKEND_SPECIFIC) {
      return;  // leave CssNodes alone to be handled by backend
    }

    // We find all the CssNodes before replacing them because we don't want the modifications to
    // interfere with the traversal.

    // This pass simply finds all the CssNodes.
    visitChildren(node);

    // Perform the replacments.
    IdGenerator nodeIdGen = node.getNearestAncestor(SoyFileSetNode.class).getNodeIdGen();
    for (CssNode cssNode : cssNodes) {

      SoyNode newNode;
      if (cssHandlingScheme == CssHandlingScheme.LITERAL) {
        newNode = new RawTextNode(nodeIdGen.genStringId(), cssNode.getCommandText());
      } else if (cssHandlingScheme == CssHandlingScheme.REFERENCE) {
        PrintNode newPrintNode = new PrintNode(
            nodeIdGen.genStringId(), cssNode.getCommandText() + "|noAutoescape",
            cssNode.getCommandText());
        PrintDirectiveNode newPrintDirectiveNode =
            new PrintDirectiveNode(nodeIdGen.genStringId(), "|noAutoescape", "");
        newPrintNode.addChild(newPrintDirectiveNode);
        ExprNode exprNode = newPrintNode.getExpr().getChild(0);
        if (!(exprNode instanceof DataRefNode || exprNode instanceof GlobalNode)) {
          throw SoytreeUtils.createSoySyntaxExceptionWithMetaInfo(
              "The css-handling scheme is 'reference', but tag " + cssNode.getTagString() +
              " does not contain a valid reference.", null, node);
        }
        newNode = newPrintNode;
      } else {
        throw new AssertionError();
      }

      @SuppressWarnings("unchecked")  // cast with generics
      ParentSoyNode<SoyNode> parent = (ParentSoyNode<SoyNode>) cssNode.getParent();
      parent.setChild(parent.getChildIndex(cssNode), newNode);
    }
  }


  @Override protected void visitInternal(CssNode node) {
    cssNodes.add(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(SoyNode node) {
    // Nothing to do for non-parent node.
  }


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
    visitChildren(node);
  }

}
