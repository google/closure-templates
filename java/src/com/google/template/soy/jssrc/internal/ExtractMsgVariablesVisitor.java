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
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.passes.BuildAllDependeesMapVisitor;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.LocalVarInlineNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Moves {@link MsgFallbackGroupNode}s to separate <code>{let}</code> and print nodes. We then move
 * the let node to the nearest block ancestor, so that the print node can be emitted as an
 * expression instead of a statement. After this pass, all MsgFallbackGroupNodes will only appear
 * inside {@link LetContentNodes}.
 *
 * <p>TODO(lukes,slaks): Rewrite any code relying on this pass to instead take advantage of the code
 * chunk api.
 *
 * <p>{@link #exec} must be called on a full parse tree.
 *
 */
public class ExtractMsgVariablesVisitor extends AbstractSoyNodeVisitor<Void> {

  /** The list of MsgFallbackGroupNodes found in the given node's subtree. */
  private List<MsgFallbackGroupNode> msgFbGrpNodes;

  private Map<SoyNode, List<SoyNode>> allDependeesMap;

  private int counter;

  @Override
  public Void exec(SoyNode node) {
    msgFbGrpNodes = new ArrayList<>();
    visit(node);
    return null;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    counter = 0;
    // We find all the MsgFallbackGroupNodes before replacing them because we don't want the
    // modifications to interfere with the traversal.

    // This pass simply finds all the MsgFallbackGroupNodes.
    visitChildren(node);

    // Help figure out where we can move each MsgFallbackGroupNode to.
    allDependeesMap = new BuildAllDependeesMapVisitor().exec(node);

    // Perform the replacements.
    IdGenerator nodeIdGen = node.getNearestAncestor(SoyFileSetNode.class).getNodeIdGenerator();
    for (MsgFallbackGroupNode msgFbGrpNode : msgFbGrpNodes) {
      wrapMsgFallbackGroupNodeHelper(msgFbGrpNode, nodeIdGen);
    }
  }

  @Override
  protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    msgFbGrpNodes.add(node);
    visitChildren(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  protected void wrapMsgFallbackGroupNodeHelper(
      MsgFallbackGroupNode msgFbGrpNode, IdGenerator nodeIdGen) {
    // try to generate a unique variable name
    // due to an optimization in GenJsCodeVisitor this variable is never actually used directly.
    // though it may be for incrementaldomsrc.
    String varName = "msg_" + ++counter;
    for (MsgNode msg : msgFbGrpNode.getChildren()) {
      varName += "_" + MsgUtils.computeMsgIdForDualFormat(msg);
    }

    // Find the actual content kind that this node prints in.
    RenderUnitNode container = msgFbGrpNode.getNearestAncestor(RenderUnitNode.class);
    SanitizedContentKind kind = container.getContentKind();
    HtmlAttributeNode containingAttribute =
        msgFbGrpNode.getNearestAncestor(HtmlAttributeNode.class);
    if (containingAttribute != null
        && SoyTreeUtils.isDescendantOf(containingAttribute, container)) {
      kind = SanitizedContentKind.TEXT;
    }

    // In traditional JS codegen, we will not infer a meaningful ContentKind (other than text).  But
    // that does not matter, since ContextAutoesc has already added appropriate escaping directives.
    // At this point, ContentKind only matters for idom, which uses it to figure out how to emit the
    // output (as itext, as parsed HTML or as attribute parameters).
    LetContentNode letNode =
        LetContentNode.forVariable(
            nodeIdGen.genId(), msgFbGrpNode.getSourceLocation(), varName, kind);

    msgFbGrpNode
        .getParent()
        .replaceChild(msgFbGrpNode, msgFbGrpNode.makePrintNode(nodeIdGen, letNode.getVar()));

    letNode.addChild(msgFbGrpNode);
    insertWrappingLetNode(letNode, allDependeesMap.get(msgFbGrpNode));
  }

  /** Inserts a wrapping {let} node in the nearest block ancestor. */
  private void insertWrappingLetNode(LetContentNode letNode, List<SoyNode> allDependees) {
    BlockNode newParent;
    int indexUnderNewParent;

    SoyNode nearestDependee = allDependees.get(0);
    if (nearestDependee instanceof LocalVarInlineNode) {
      newParent = (BlockNode) nearestDependee.getParent();
      indexUnderNewParent = newParent.getChildIndex(nearestDependee) + 1;
    } else if (nearestDependee instanceof BlockNode) {
      newParent = (BlockNode) nearestDependee;
      indexUnderNewParent = 0;
    } else {
      throw new AssertionError();
    }
    // TODO(slaks): Move after earlier inserted nodes in case {msg}s reference each-other.
    newParent.addChild(indexUnderNewParent, letNode);
  }
}
