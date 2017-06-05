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

package com.google.template.soy.jssrc.internal;

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.coredirectives.CoreDirectiveUtils;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.passes.CombineConsecutiveRawTextNodesVisitor;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import javax.inject.Inject;

/**
 * Visitor for replacing any {@code PrintNode} whose expression is a single call to {@code
 * bidiMark()}, {@code bidiStartEdge()}, or {@code bidiEndEdge()} with an equivalent {@code
 * RawTextNode}. If these replacements cause the resulting tree to contain any sequences of
 * consecutive {@code RawTextNodes}, they will each be simplified to a single {@code RawTextNode}.
 *
 * <p>This pass does not replace {@code PrintNode}s containing directives other than {@code |id},
 * {@code |noAutoescape}, and {@code |escapeHtml}.
 *
 * <p>{@link #exec} must be called on a full parse tree.
 *
 */
public class OptimizeBidiCodeGenVisitor extends AbstractSoyNodeVisitor<Void> {

  // Names of the bidi functions for which we optimize code generation.
  private static final String BIDI_MARK_FN_NAME = "bidiMark";
  private static final String BIDI_START_EDGE_FN_NAME = "bidiStartEdge";
  private static final String BIDI_END_EDGE_FN_NAME = "bidiEndEdge";

  /** The bidi global directionality. */
  private BidiGlobalDir bidiGlobalDir;

  /** The node id generator for the parse tree. Retrieved from the root SoyFileSetNode. */
  private IdGenerator nodeIdGen;

  /** Whether a replacement was made. */
  boolean madeReplacement;

  /** @param bidiGlobalDir The bidi global directionality. */
  @Inject
  public OptimizeBidiCodeGenVisitor(BidiGlobalDir bidiGlobalDir) {
    this.bidiGlobalDir = bidiGlobalDir;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    // if don't have a static value, skip
    if (!bidiGlobalDir.isStaticValue()) {
      return;
    }

    // Retrieve the node id generator.
    nodeIdGen = node.getNodeIdGenerator();

    // Run the pass.
    madeReplacement = false;
    visitChildren(node);

    // If we made any replacements, we may have created consecutive RawTextNodes, so clean them up.
    if (madeReplacement) {
      new CombineConsecutiveRawTextNodesVisitor().exec(node);
    }
  }

  @Override
  protected void visitPrintNode(PrintNode node) {

    // We replace this node if and only if it:
    // (a) is not a child of a MsgBlockNode,
    // (b) has a single call to bidiMark(), bidiStartEdge(), or bidiEndEdge() as its expression and
    //     the global directionality is static,
    // (c) doesn't have directives other than "|id", "|noAutoescape", and "|escapeHtml".

    ParentSoyNode<StandaloneNode> parent = node.getParent();
    if (parent instanceof MsgBlockNode) {
      return; // don't replace this node
    }

    ExprNode expr = node.getExpr().getRoot();
    if (!(expr instanceof FunctionNode)) {
      return; // don't replace this node
    }

    SoyFunction fn = ((FunctionNode) expr).getSoyFunction();
    if (fn == null) {
      return;
    }
    String rawText;
    switch (fn.getName()) {
      case BIDI_MARK_FN_NAME:
        rawText = (bidiGlobalDir.getStaticValue() < 0) ? "\\u200F" /*RLM*/ : "\\u200E" /*LRM*/;
        break;
      case BIDI_START_EDGE_FN_NAME:
        rawText = (bidiGlobalDir.getStaticValue() < 0) ? "right" : "left";
        break;
      case BIDI_END_EDGE_FN_NAME:
        rawText = (bidiGlobalDir.getStaticValue() < 0) ? "left" : "right";
        break;
      default:
        return; // don't replace this node
    }

    for (PrintDirectiveNode directiveNode : node.getChildren()) {
      if (!CoreDirectiveUtils.isCoreDirective(directiveNode)) {
        return; // don't replace this node
      }
    }

    // Replace this node with a RawTextNode.
    parent.replaceChild(
        node, new RawTextNode(nodeIdGen.genId(), rawText, node.getSourceLocation()));
    madeReplacement = true;
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildrenAllowingConcurrentModification((ParentSoyNode<?>) node);
    }
  }
}
