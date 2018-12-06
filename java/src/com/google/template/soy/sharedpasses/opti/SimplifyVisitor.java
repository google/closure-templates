/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.sharedpasses.opti;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.sharedpasses.render.RenderException;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Visitor for simplifying subtrees based on constant values known at compile time.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SimplifyVisitor {

  /** Creates a new simplify visitor. */
  public static SimplifyVisitor create(
      IdGenerator idGenerator, ImmutableList<SoyFileNode> sourceFiles) {
    return new SimplifyVisitor(
        idGenerator, sourceFiles, new SimplifyExprVisitor(), new PreevalVisitorFactory());
  }

  private final Impl impl;
  private final SimplifyExprVisitor simplifyExprVisitor;
  private final PreevalVisitorFactory preevalVisitorFactory;

  private SimplifyVisitor(
      IdGenerator idGenerator,
      ImmutableList<SoyFileNode> sourceFiles,
      SimplifyExprVisitor simplifyExprVisitor,
      PreevalVisitorFactory preevalVisitorFactory) {
    this.impl = new Impl(sourceFiles, idGenerator);
    this.simplifyExprVisitor = simplifyExprVisitor;
    this.preevalVisitorFactory = preevalVisitorFactory;
  }

  /** Simplifies the given file set. */
  public void simplify(SoyFileNode file) {
    impl.exec(file);
  }

  private final class Impl extends AbstractSoyNodeVisitor<Void> {
    final ImmutableMap<String, TemplateNode> basicTemplates;
    final IdGenerator nodeIdGen;

    Impl(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
      this.nodeIdGen = idGenerator;
      ImmutableMap.Builder<String, TemplateNode> basicTemplates = ImmutableMap.builder();
      for (SoyFileNode fileNode : sourceFiles) {
        for (TemplateNode template : fileNode.getChildren()) {
          // we can't simplify deltemplates
          if (!(template instanceof TemplateDelegateNode)) {
            basicTemplates.put(template.getTemplateName(), template);
          }
        }
      }
      this.basicTemplates = basicTemplates.build();
    }

    @Override
    public Void exec(SoyNode node) {

      SoyFileNode file = (SoyFileNode) node;
      // First simplify all expressions in the subtree.
      SoyTreeUtils.execOnAllV2Exprs(file, simplifyExprVisitor);
      // Simpify the subtree.
      visit(file);

      return null;
    }

    // --------------------------------------------------------------------------------------------
    // Implementations for specific nodes.

    @Override
    protected void visitPrintNode(PrintNode node) {

      // We attempt to prerender this node if and only if it:
      // (a) is in V2 syntax,
      // (b) is not a child of a MsgBlockNode,
      // (c) has a constant expression,
      // (d) has constant expressions for all directive arguments (if any).
      // The prerender attempt may fail due to other reasons not checked above.

      ParentSoyNode<StandaloneNode> parent = node.getParent();
      if (parent instanceof MsgBlockNode) {
        return; // don't prerender
      }

      if (!isConstant(node.getExpr())) {
        return; // don't prerender
      }

      for (PrintDirectiveNode directive : node.getChildren()) {
        for (ExprRootNode arg : directive.getArgs()) {
          if (!isConstant(arg)) {
            return; // don't prerender
          }
        }
      }

      StringBuilder prerenderOutputSb = new StringBuilder();
      try {
        PrerenderVisitor prerenderer =
            new PrerenderVisitor(preevalVisitorFactory, prerenderOutputSb, basicTemplates);
        prerenderer.exec(node);
      } catch (RenderException pe) {
        return; // cannot prerender for some other reason not checked above
      }

      // Replace this node with a RawTextNode.
      String string = prerenderOutputSb.toString();
      if (string.isEmpty()) {
        parent.removeChild(node);
      } else {
        parent.replaceChild(
            node, new RawTextNode(nodeIdGen.genId(), string, node.getSourceLocation()));
      }
    }

    @Override
    protected void visitIfNode(IfNode node) {

      // Recurse.
      visitSoyNode(node);

      // For each IfCondNode child:
      // (a) If the condition is constant true: Replace the child with an IfElseNode and remove all
      //     children after it, if any. Can stop processing after doing this, because the new
      //     IfElseNode is now the last child.
      // (b) If the condition is constant false: Remove the child.
      for (SoyNode child : Lists.newArrayList(node.getChildren()) /*copy*/) {
        if (child instanceof IfCondNode) {
          IfCondNode condNode = (IfCondNode) child;

          ExprRootNode condExpr = condNode.getExpr();
          if (!isConstant(condExpr)) {
            continue; // cannot simplify this child
          }

          if (getConstantOrNull(condExpr).coerceToBoolean()) {
            // ------ Constant true. ------
            // Remove all children after this child.
            int condIndex = node.getChildIndex(condNode);
            for (int i = node.numChildren() - 1; i > condIndex; i--) {
              node.removeChild(i);
            }
            // Replace this child with a new IfElseNode.
            IfElseNode newElseNode =
                new IfElseNode(nodeIdGen.genId(), condNode.getSourceLocation());
            newElseNode.addChildren(condNode.getChildren());
            node.replaceChild(condIndex, newElseNode);
            // Stop processing.
            break;

          } else {
            // ------ Constant false. ------
            node.removeChild(condNode);
          }
        }
      }

      // If this IfNode:
      // (a) Has no children left: Remove it.
      // (b) Has only one child left, and it's an IfElseNode: Replace this IfNode with its
      //     grandchildren.
      if (node.numChildren() == 0) {
        node.getParent().removeChild(node);
      }
      if (node.numChildren() == 1 && node.getChild(0) instanceof IfElseNode) {
        replaceNodeWithList(node, ((IfElseNode) node.getChild(0)).getChildren());
      }
    }

    @Override
    protected void visitSwitchNode(SwitchNode node) {

      // Recurse.
      visitSoyNode(node);

      // If the SwitchNode's expr is not constant, we can't simplify.
      SoyValue switchExprValue = getConstantOrNull(node.getExpr());
      if (switchExprValue == null) {
        return; // cannot simplify this node
      }

      // For each SwitchCaseNode child:
      // (a) If the case has a constant expr that matches: Replace the child with a
      //     SwitchDefaultNode and remove all children after it, if any. Can stop processing after
      //     doing this, because the new SwitchDefaultNode is now the last child.
      // (b) If the case has all constant exprs and none match: Remove the child.
      for (SoyNode child : Lists.newArrayList(node.getChildren()) /*copy*/) {
        if (child instanceof SwitchCaseNode) {
          SwitchCaseNode caseNode = (SwitchCaseNode) child;

          boolean hasMatchingConstant = false;
          boolean hasAllNonmatchingConstants = true;
          for (ExprRootNode caseExpr : caseNode.getExprList()) {
            SoyValue caseExprValue = getConstantOrNull(caseExpr);
            if (caseExprValue == null) {
              hasAllNonmatchingConstants = false;
            } else if (caseExprValue.equals(switchExprValue)) {
              hasMatchingConstant = true;
              hasAllNonmatchingConstants = false;
              break;
            }
          }

          if (hasMatchingConstant) {
            // ------ Has a constant expr that matches. ------
            // Remove all children after this child.
            int caseIndex = node.getChildIndex(caseNode);
            for (int i = node.numChildren() - 1; i > caseIndex; i--) {
              node.removeChild(i);
            }
            // Replace this child with a new SwitchDefaultNode.
            SwitchDefaultNode newDefaultNode =
                new SwitchDefaultNode(nodeIdGen.genId(), caseNode.getSourceLocation());
            newDefaultNode.addChildren(caseNode.getChildren());
            node.replaceChild(caseIndex, newDefaultNode);
            // Stop processing.
            break;

          } else if (hasAllNonmatchingConstants) {
            // ------ Has all constant exprs and none match. ------
            node.removeChild(caseNode);
          }
        }
      }

      // If this SwitchNode:
      // (a) Has no children left: Remove it.
      // (b) Has only one child left, and it's a SwitchDefaultNode: Replace this SwitchNode with its
      //     grandchildren.
      if (node.numChildren() == 1 && node.getChild(0) instanceof SwitchDefaultNode) {
        replaceNodeWithList(node, ((SwitchDefaultNode) node.getChild(0)).getChildren());
      }
    }

    // Note (Sep-2012): We removed prerendering of calls (visitCallBasicNode) due to development
    // issues. We decided it was better to remove it than to add another rarely-used option to the
    // Soy compiler.

    // -----------------------------------------------------------------------------------------------
    // Fallback implementation.

    @Override
    protected void visitSoyNode(SoyNode node) {

      if (node instanceof ParentSoyNode<?>) {
        visitChildrenAllowingConcurrentModification((ParentSoyNode<?>) node);
      }

      if (!(node instanceof BlockNode)) {
        return;
      }
      BlockNode nodeAsBlock = (BlockNode) node;

      // Check whether there are any consecutive RawTextNode children.
      boolean hasConsecRawTextNodes = false;
      for (int i = 0; i <= nodeAsBlock.numChildren() - 2; i++) {
        if (nodeAsBlock.getChild(i) instanceof RawTextNode
            && nodeAsBlock.getChild(i + 1) instanceof RawTextNode) {
          hasConsecRawTextNodes = true;
          break;
        }
      }
      // If there aren't any consecutive RawTextNode children, we're done.
      if (!hasConsecRawTextNodes) {
        return;
      }

      // Rebuild the list of children, combining consecutive RawTextNodes into one.
      List<StandaloneNode> copyOfOrigChildren = Lists.newArrayList(nodeAsBlock.getChildren());
      nodeAsBlock.clearChildren();

      List<RawTextNode> consecutiveRawTextNodes = Lists.newArrayList();
      for (StandaloneNode origChild : copyOfOrigChildren) {

        if (origChild instanceof RawTextNode) {
          consecutiveRawTextNodes.add((RawTextNode) origChild);

        } else {
          // First add the preceding consecutive RawTextNodes, if any.
          addConsecutiveRawTextNodesAsOneNodeHelper(nodeAsBlock, consecutiveRawTextNodes);
          consecutiveRawTextNodes.clear();
          // Then add the current new child.
          nodeAsBlock.addChild(origChild);
        }
      }

      // Add the final group of consecutive RawTextNodes, if any.
      addConsecutiveRawTextNodesAsOneNodeHelper(nodeAsBlock, consecutiveRawTextNodes);
      consecutiveRawTextNodes.clear();
    }

    /**
     * Helper to add consecutive RawTextNodes as one child node (the raw text will be joined). If
     * the consecutive RawTextNodes list actually only has one item, then adds that node instead of
     * creating a new RawTextNode.
     *
     * <p>Note: This function works closely with the above code. In particular, it assumes we're
     * rebuilding the whole list (thus adding to the end of the parent) instead of fixing the old
     * list in-place.
     *
     * @param parent The parent to add the new child to.
     * @param consecutiveRawTextNodes The list of consecutive RawTextNodes.
     */
    private void addConsecutiveRawTextNodesAsOneNodeHelper(
        BlockNode parent, List<RawTextNode> consecutiveRawTextNodes) {
      if (consecutiveRawTextNodes.isEmpty()) {
        return;
      } else if (consecutiveRawTextNodes.size() == 1) {
        // Simply add the one RawTextNode.
        parent.addChild(consecutiveRawTextNodes.get(0));
      } else {
        // Create a new combined RawTextNode.
        StringBuilder rawText = new StringBuilder();
        for (RawTextNode rtn : consecutiveRawTextNodes) {
          rawText.append(rtn.getRawText());
        }
        parent.addChild(
            new RawTextNode(nodeIdGen.genId(), rawText.toString(), parent.getSourceLocation()));
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  private static boolean isConstant(@Nullable ExprRootNode exprRoot) {
    return exprRoot != null && SimplifyExprVisitor.isConstant(exprRoot.getRoot());
  }

  private static SoyValue getConstantOrNull(ExprRootNode exprRoot) {
    if (exprRoot == null) {
      return null;
    }
    ExprNode expr = exprRoot.getRoot();
    return SimplifyExprVisitor.getConstantOrNull(expr);
  }

  /**
   * @param origNode The original node to replace.
   * @param replacementNodes The list of nodes to put in place of the original node.
   */
  private static void replaceNodeWithList(
      StandaloneNode origNode, List<? extends StandaloneNode> replacementNodes) {

    ParentSoyNode<StandaloneNode> parent = origNode.getParent();
    int indexInParent = parent.getChildIndex(origNode);
    parent.removeChild(indexInParent);
    parent.addChildren(indexInParent, replacementNodes);
  }
}
