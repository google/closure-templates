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

package com.google.template.soy.sharedpasses;

import com.google.template.soy.base.IdGenerator;
import com.google.template.soy.coredirectives.CoreDirectiveUtils;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.internal.base.CharEscapers;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.SyntaxVersion;


/**
 * Visitor for replacing any {@code PrintNode} whose expression is a literal string with an
 * equivalent {@code RawTextNode}. If these replacements cause the resulting tree to contain any
 * sequences of consecutive {@code RawTextNodes}, they will each be simplified to a single
 * {@code RawTextNode}.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> This pass does not replace {@code PrintNode}s containing directives other than {@code |id},
 * {@code |noAutoescape}, and {@code |escapeHtml}.
 *
 * @author Kai Huang
 */
public class ReplaceStringPrintNodesVisitor extends AbstractSoyNodeVisitor<Void> {


  /** The node id generator for the parse tree. Retrieved from the root SoyFileSetNode. */
  private IdGenerator nodeIdGen;


  @Override public Void exec(SoyNode node) {

    // Retrieve the node id generator from the root of the parse tree.
    nodeIdGen = node.getNearestAncestor(SoyFileSetNode.class).getNodeIdGen();

    // Execute the pass.
    super.exec(node);

    // The pass may have created consecutive RawTextNodes, so clean them up.
    (new CombineConsecutiveRawTextNodesVisitor()).exec(node);

    return null;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(PrintNode node) {

    // We replace this node if and only if it:
    // (a) is in V2 syntax,
    // (b) is not a child of a MsgNode,
    // (c) has a string as its expression,
    // (d) doesn't have directives other than "|id", "|noAutoescape", and "|escapeHtml".

    if (node.getSyntaxVersion() != SyntaxVersion.V2) {
      return;
    }

    @SuppressWarnings("unchecked")  // cast with generics
    ParentSoyNode<SoyNode> parent = (ParentSoyNode<SoyNode>) node.getParent();
    if (parent instanceof MsgNode) {
      return;  // don't replace this node
    }

    ExprNode expr = node.getExpr().getChild(0);
    if (!(expr instanceof StringNode)) {
      return;  // don't replace this node
    }
    String value = ((StringNode) expr).getValue();

    for (PrintDirectiveNode directiveNode : node.getChildren()) {
      if (CoreDirectiveUtils.isEscapeHtmlDirective(directiveNode)) {
        value = CharEscapers.asciiHtmlEscaper().escape(value);
      } else if (! CoreDirectiveUtils.isNoAutoescapeOrIdDirective(directiveNode)) {
        return;  // don't replace this node
      }
    }

    // Replace this node with a RawTextNode.
    parent.setChild(parent.getChildIndex(node), new RawTextNode(nodeIdGen.genStringId(), value));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(SoyNode node) {
    // Nothing to do for other nodes.
  }


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
    visitChildren(node);
  }

}
