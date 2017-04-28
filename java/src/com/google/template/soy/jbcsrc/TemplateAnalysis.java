/*
 * Copyright 2016   Google Inc.
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

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPart.Case;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralRemainderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNode.RangeArgs;
import com.google.template.soy.soytree.ForeachIfemptyNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.XidNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A static analyzer for how templates will access variables.
 *
 * <p>This class contains a generic method for constructing a control flow graph through a given Soy
 * template. We then use this graph to answer questions about the template.
 *
 * <p>Supported queries
 *
 * <ul>
 *   <li>{@link #isResolved(DataAccessNode)} can tell us whether or not a particular variable or
 *       field reference has already been referenced at a given point and therefore {@link
 *       SoyValueProvider#status()} has already returned {@link RenderResult#done()}.
 * </ul>
 *
 * <p>TODO(lukes): consider adding the following
 *
 * <ul>
 *   <li>Identify the last use of a variable. Currently we use variable scopes to decide when to
 *       stop saving/restoring variables, but if we knew they were no longer in use we could save
 *       generating save/restore logic.
 *   <li>Identify the last render of a variable. We could use this to save temporary buffers.
 *   <li>Identify the first use of a variable. We could use this to move {let...} definitions closer
 *       to their uses.
 * </ul>
 */
final class TemplateAnalysis {
  static TemplateAnalysis analyze(TemplateNode node) {
    AccessGraph templateGraph = new PseudoEvaluatorVisitor().evaluate(node);
    return new TemplateAnalysis(templateGraph.getResolvedExpressions());
  }

  private final ImmutableSet<ExprNode> resolvedExpressions;

  private TemplateAnalysis(Set<ExprNode> definitelyAccessedRefs) {
    this.resolvedExpressions = ImmutableSet.copyOf(definitelyAccessedRefs);
  }

  /**
   * Returns true if this variable reference is definitely not the first reference to the variable
   * within a given template.
   */
  boolean isResolved(VarRefNode ref) {
    return resolvedExpressions.contains(ref);
  }

  /**
   * Returns true if this data access is definitely not the first reference to the field or item
   * within a given template.
   */
  boolean isResolved(DataAccessNode ref) {
    return resolvedExpressions.contains(ref);
  }

  /**
   * This visitor (and the {@link PseudoEvaluatorExprVisitor}) visits every Soy node in the order
   * that the code generated from those node would execute and contructs an {@link AccessGraph}.
   */
  private static final class PseudoEvaluatorVisitor extends AbstractSoyNodeVisitor<Void> {
    final Map<VarDefn, AccessGraph> letNodes = new HashMap<>();
    final PseudoEvaluatorExprVisitor exprVisitor = new PseudoEvaluatorExprVisitor(letNodes);
    Block current;

    AccessGraph evaluate(TemplateNode node) {
      Block start = new Block();
      Block finalNode = exec(start, node);
      checkState(finalNode.successors.isEmpty());
      // annotate the graph with predecessor links
      addPredecessors(start);
      // TODO(lukes): due to the way we build the graph we may have lots of either empty nodes or
      // nodes with unconditional branches.  We could reduce the size of the graph by trying to
      // eliminate such nodes.  This may be worth it if we end up doing many traversals instead of
      // the current 1.
      return new AccessGraph(start, finalNode);
    }

    /**
     * Visit the given node and append all accesses to the given block.
     *
     * <p>Returns the ending block.
     */
    Block exec(Block block, SoyNode node) {
      Block original = this.current;
      this.current = block;
      visit(node);
      Block rVal = current;
      this.current = original;
      return rVal;
    }

    @Override
    protected void visitPrintNode(PrintNode node) {
      evalInline(node.getExpr());
      for (PrintDirectiveNode directive : node.getChildren()) {
        for (ExprRootNode arg : directive.getArgs()) {
          evalInline(arg);
        }
      }
    }

    @Override
    protected void visitRawTextNode(RawTextNode node) {
      // do nothing
    }

    @Override
    protected void visitLogNode(LogNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitDebuggerNode(DebuggerNode node) {
      // do nothing
    }

    @Override
    protected void visitTemplateNode(TemplateNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitForeachNode(ForeachNode node) {
      // the list is always evaluated first.
      evalInline(node.getExpr());

      Block loopBegin = this.current;
      // create a branch for the loop body
      Block loopBody = loopBegin.addBranch();
      Block loopEnd = exec(loopBody, node.getChild(0));
      // If we can statically prove the list empty, use that information.
      StaticAnalysisResult isLoopEmpty = isListExpressionEmpty(node.getExpr());
      if (node.numChildren() == 2) { // there is an {ifempty} block
        Block ifEmptyBlock = loopBegin.addBranch();
        Block ifEmptyEnd = exec(ifEmptyBlock, node.getChild(1));
        switch (isLoopEmpty) {
          case FALSE:
            this.current = loopEnd.addBranch();
            break;
          case TRUE:
            this.current = ifEmptyEnd.addBranch();
            break;
          case UNKNOWN:
            this.current = Block.merge(loopEnd, ifEmptyEnd);
            break;
          default:
            throw new AssertionError();
        }
      } else {
        switch (isLoopEmpty) {
          case FALSE:
            this.current = loopEnd.addBranch();
            break;
          case TRUE:
            this.current = loopBegin.addBranch();
            break;
          case UNKNOWN:
            this.current = Block.merge(loopEnd, loopBegin);
            break;
          default:
            throw new AssertionError();
        }
      }
    }

    @Override
    protected void visitForeachIfemptyNode(ForeachIfemptyNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitForeachNonemptyNode(ForeachNonemptyNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitForNode(ForNode node) {
      // The range(...) args of a For node are always evaluated first, in this order.
      // (see SoyNodeCompiler)
      RangeArgs rangeArgs = node.getRangeArgs();
      evalInline(rangeArgs.start());
      evalInline(rangeArgs.increment());
      evalInline(rangeArgs.limit());
      Block loopBegin = this.current;
      // create a branch for the loop body
      Block loopBody = loopBegin.addBranch();
      Block loopEnd = loopBody;
      for (StandaloneNode child : node.getChildren()) {
        loopEnd = exec(loopEnd, child);
      }
      // If the loop definitely executed we could merge results back up.  There are actually a
      // surprising number of people using constants in their range args.
      if (rangeArgs.definitelyNotEmpty()) {
        this.current = loopEnd;
      } else {
        this.current = Block.merge(loopBegin, loopEnd);
      }
    }

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      // Due to lazy evaluation we don't evaluate these at the declaration site.  Instead we have
      // to fix them up after the fact.  So calculate and store their basic blocks here.  We will
      // wire them into the graph later.
      Block startBlock = new Block();
      Block block = startBlock;
      for (StandaloneNode child : node.getChildren()) {
        block = exec(block, child);
      }
      letNodes.put(node.getVar(), new AccessGraph(startBlock, block));
    }

    @Override
    protected void visitLetValueNode(LetValueNode node) {
      // see visitLetContentNode
      Block start = new Block();
      Block end = exprVisitor.eval(start, node.getExpr());
      letNodes.put(node.getVar(), new AccessGraph(start, end));
    }

    @Override
    protected void visitSwitchNode(SwitchNode node) {
      // A few special cases.  See SoyNodeCompiler.visitSwitchNode
      // * no children => don't evaluate the switch expression
      // * no cases => don't evaluate the switch expression

      List<BlockNode> children = node.getChildren();
      if (children.isEmpty()) {
        return;
      }
      if (children.size() == 1 && children.get(0) instanceof SwitchDefaultNode) {
        visitChildren(children.get(0));
        return;
      }
      // otherwise we are in the normal case and this is much like an if-elseif-else statement
      // First eval the expr
      evalInline(node.getExpr());
      // The one special case is that there are allowed to be multiple conditions per case.
      // For these, only the first one is necessarily evaluated.
      Block conditions = null;
      List<Block> branchEnds = new ArrayList<>();
      boolean hasDefault = false;
      for (SoyNode child : node.getChildren()) {
        if (child instanceof SwitchCaseNode) {
          SwitchCaseNode scn = (SwitchCaseNode) child;
          Block caseBlockStart = new Block();
          Block caseBlockEnd = exec(caseBlockStart, scn);
          branchEnds.add(caseBlockEnd);

          for (ExprRootNode expr : scn.getExprList()) {
            if (conditions == null) {
              evalInline(expr); // the very first condition is always evaluated
              conditions = this.current;
            } else {
              // otherwise we are only maybe evaluating this condition
              Block condition = conditions.addBranch();
              conditions = exprVisitor.eval(condition, expr);
            }
            conditions.successors.add(caseBlockStart);
          }

        } else {
          SwitchDefaultNode ien = (SwitchDefaultNode) child;
          Block defaultBlockStart = conditions.addBranch();
          Block defaultBlockEnd = exec(defaultBlockStart, ien);
          branchEnds.add(defaultBlockEnd);
          hasDefault = true;
        }
      }
      if (!hasDefault) {
        branchEnds.add(conditions);
      }
      this.current = Block.merge(branchEnds);
    }

    @Override
    protected void visitSwitchCaseNode(SwitchCaseNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitSwitchDefaultNode(SwitchDefaultNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitIfNode(IfNode node) {
      // For ifs we always evaluate the first condition and if there is an 'else' clause at least
      // one of the branches.  Things get trickier if we have multiple conditions.  for example,
      // {if $p1}W{elseif $p2}X{else}Y{/if}Z
      // at position W $p1 has been ref'd
      // at position X $p1 and $p2 have been ref'd
      // at position Y $p1 and $p2 have been ref'd
      // at position Z only $p1 has definitely been ref'd
      // To handle all these cases we need to manage a bunch of forks
      Block conditionFork = null;
      List<Block> branchEnds = new ArrayList<>();
      boolean hasElse = false;
      for (SoyNode child : node.getChildren()) {
        if (child instanceof IfCondNode) {
          IfCondNode icn = (IfCondNode) child;
          ExprRootNode conditionExpression = icn.getExpr();
          if (conditionFork == null) {
            // first condition is always evaluated
            evalInline(conditionExpression);
            conditionFork = this.current;
          } else {
            conditionFork = exprVisitor.eval(conditionFork.addBranch(), conditionExpression);
          }
          Block branch = conditionFork.addBranch();
          branch = exec(branch, icn);
          branchEnds.add(branch);
        } else {
          IfElseNode ien = (IfElseNode) child;
          Block branch = conditionFork.addBranch();
          branch = exec(branch, ien);
          branchEnds.add(branch);
          hasElse = true;
        }
      }
      if (!hasElse) {
        branchEnds.add(conditionFork);
      }
      this.current = Block.merge(branchEnds);
    }

    @Override
    protected void visitIfCondNode(IfCondNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitIfElseNode(IfElseNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitCssNode(CssNode node) {
      if (node.getComponentNameExpr() != null) {
        evalInline(node.getComponentNameExpr());
      }
    }

    @Override
    protected void visitXidNode(XidNode node) {
      // do nothing, xids only contain constants.
    }

    @Override
    protected void visitCallNode(CallNode node) {
      // If there is a data="<expr>" this is always evaluated first.
      ExprRootNode dataExpr = node.getDataExpr();
      if (dataExpr != null) {
        evalInline(dataExpr);
      }
      // template params are evaluated lazily in the callee, so we don't know whether or not any
      // of them will be evaluated.  So treat each param like an optional branch.
      // Also we don't know anything about the relative ordering of each param. technically all
      // orderings are possible, but instead of that we just assume they are all mutually exclusive.
      // TODO(lukes): if we were modeling 'back-edges' we could treat params like a loop by putting
      // an optional back edge at the bottom of each one.  this would achieve the result of
      // modelling all possible orderings.  For our current usecases this is unnecessary.
      Block begin = this.current;
      List<Block> branchEnds = new ArrayList<>();
      branchEnds.add(
          begin); // this ensures that there is a path that doesn't go through any of the params
      for (CallParamNode param : node.getChildren()) {
        Block paramBranch = begin.addBranch();
        Block paramBranchEnd = exec(paramBranch, param);
        branchEnds.add(paramBranchEnd);
      }
      this.current = Block.merge(branchEnds);
    }

    @Override
    protected void visitCallParamValueNode(CallParamValueNode node) {
      // params are evaluated in their own fork.
      evalInline(node.getExpr());
    }

    @Override
    protected void visitCallParamContentNode(CallParamContentNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
      if (node.numChildren() == 1) {
        // there is a single message, evaluate inline
        visit(node.getChild(0));
      } else {
        // there is a fallback. then we have normal a branching structure
        Block normalBranch = this.current.addBranch();
        Block fallbackBranch = this.current.addBranch();

        normalBranch = exec(normalBranch, node.getChild(0));
        fallbackBranch = exec(fallbackBranch, node.getChild(1));
        this.current = Block.merge(normalBranch, fallbackBranch);
      }
    }

    @Override
    protected void visitMsgNode(MsgNode node) {
      // for {msg} nodes we always build a placeholder map of all the placeholders in the order
      // defined by the parsed 'msg parts'.  See MsgCompiler.
      evaluateMsgParts(node, MsgUtils.buildMsgPartsAndComputeMsgIdForDualFormat(node).parts);
    }

    /**
     * Visits all the placeholders of a {msg} in the same order that the MsgCompiler generates code.
     */
    private void evaluateMsgParts(MsgNode msgNode, Iterable<? extends SoyMsgPart> parts) {
      // Note:  The same placeholder may show up multiple times.  In the MsgCompiler we use a
      // separate data structure to dedup so we don't generate the same code for the same
      // placeholder multiple times.  This isn't a concern here because our 'pseudo evaluation'
      // process is idempotent.
      for (SoyMsgPart part : parts) {
        if (part instanceof SoyMsgRawTextPart || part instanceof SoyMsgPluralRemainderPart) {
          // raw text and plural remainders don't have associated expressions
          continue;
        }
        if (part instanceof SoyMsgPluralPart) {
          SoyMsgPluralPart plural = (SoyMsgPluralPart) part;
          evalInline(msgNode.getRepPluralNode(plural.getPluralVarName()).getExpr());
          // Recursively visit plural cases
          for (Case<SoyMsgPluralCaseSpec> caseOrDefault : plural.getCases()) {
            evaluateMsgParts(msgNode, caseOrDefault.parts());
          }
        } else if (part instanceof SoyMsgSelectPart) {
          SoyMsgSelectPart select = (SoyMsgSelectPart) part;
          evalInline(msgNode.getRepSelectNode(select.getSelectVarName()).getExpr());
          // Recursively visit select cases
          for (Case<String> caseOrDefault : select.getCases()) {
            evaluateMsgParts(msgNode, caseOrDefault.parts());
          }
        } else if (part instanceof SoyMsgPlaceholderPart) {
          SoyMsgPlaceholderPart placeholder = (SoyMsgPlaceholderPart) part;
          visit(msgNode.getRepPlaceholderNode(placeholder.getPlaceholderName()).getChild(0));
        } else {
          throw new AssertionError("unexpected part: " + part);
        }
      }
    }

    @Override
    protected void visitMsgPlaceholderNode(MsgPlaceholderNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      throw new UnsupportedOperationException("unsupported node type: " + node.getKind());
    }

    // override to make it visible
    @Override
    protected void visitChildren(ParentSoyNode<?> node) {
      super.visitChildren(node);
    }

    /** Evaluates the given expression in the current block. */
    void evalInline(ExprNode expr) {
      Block begin = current;
      Block end = exprVisitor.eval(begin, expr);
      current = end;
    }
  }

  private enum StaticAnalysisResult {
    TRUE,
    FALSE,
    UNKNOWN;
  }

  private static StaticAnalysisResult isListExpressionEmpty(ExprNode node) {
    node = node instanceof ExprRootNode ? ((ExprRootNode) node).getRoot() : node;
    if (node instanceof ListLiteralNode) {
      return ((ListLiteralNode) node).numChildren() > 0
          ? StaticAnalysisResult.FALSE
          : StaticAnalysisResult.TRUE;
    }
    return StaticAnalysisResult.UNKNOWN;
  }

  private static final class PseudoEvaluatorExprVisitor extends AbstractExprNodeVisitor<Void> {
    final Map<VarDefn, AccessGraph> letNodes;
    Block current;

    PseudoEvaluatorExprVisitor(Map<VarDefn, AccessGraph> letNodes) {
      this.letNodes = letNodes;
    }

    /** Evaluates the given expression in the given block. Returns the ending or 'exit' block. */
    Block eval(Block block, ExprNode expr) {
      Block orig = this.current;
      this.current = block;
      visit(expr);
      Block rVal = this.current;
      this.current = orig;
      return rVal;
    }

    @Override
    protected void visitExprRootNode(ExprRootNode node) {
      visit(node.getRoot());
    }

    @Override
    protected void visitVarRefNode(VarRefNode node) {
      AccessGraph letContent = letNodes.get(node.getDefnDecl());
      if (letContent != null) {
        // because let nodes are lazily executed, we model this in the access graph as each let
        // varref branching to a copy of the implementation and back. This ensures that each
        // variable referenced by the {let} is referenced prior to the let variable.
        // Additionally we add a dead end branch from just prior the let to the canonical let
        // implementation block
        //
        // this means that the graph structure for this:
        // {let $foo : $p /}
        // {$foo}
        //
        // is
        //          -> <foo-original>
        // <begin>
        //          -> <foo copy> -> <$foo>  -> <end>
        // This ensures that the variable references within the let are analyzed as being executed
        // immediately prior to any of the references.
        AccessGraph copy = letContent.copy();
        // dead end branch to the canonical
        this.current.successors.add(letContent.start);
        // branch to and back from the copy
        this.current.successors.add(copy.start);
        this.current = copy.end.addBranch();
      }
      current.add(node);
    }

    @Override
    protected void visitDataAccessNode(DataAccessNode node) {
      visitChildren(node); // visit subexpressions first
      current.add(node);
    }

    @Override
    protected void visitFunctionNode(FunctionNode node) {
      if (node.getSoyFunction() instanceof BuiltinFunction) {
        switch ((BuiltinFunction) node.getSoyFunction()) {
          case INDEX:
          case IS_FIRST:
          case IS_LAST:
            // early return for these.  the AST looks like we are evaluating a var, but in fact we
            // generate alternate code to reference a synthetic variable.
            // See ExpressionCompiler
            return;
          case QUOTE_KEYS_IF_JS:
          case CHECK_NOT_NULL:
          case CSS:
          case XID:
            // fall through
            break;
          case V1_EXPRESSION:
            throw new UnsupportedOperationException(
                "the v1Expression function can't be used in templates compiled to Java");
          default:
            throw new AssertionError("unexpected builtin function");
        }
      }
      // eval all arguments in order
      visitChildren(node);
    }

    @Override
    protected void visitPrimitiveNode(PrimitiveNode node) {
      // do nothing.
    }

    @Override
    protected void visitListLiteralNode(ListLiteralNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitMapLiteralNode(MapLiteralNode node) {
      visitChildren(node);
    }

    // Most operators always evaluate their arguments, the control flow operators are handled
    // separately
    @Override
    protected void visitOperatorNode(OperatorNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitNullCoalescingOpNode(NullCoalescingOpNode node) {
      visit(node.getLeftChild());
      // The right side may or may not be evaluated
      executeInBranch(node.getRightChild());
    }

    @Override
    protected void visitOrOpNode(OrOpNode node) {
      visit(node.getChild(0));
      executeInBranch(node.getChild(1));
    }

    @Override
    protected void visitAndOpNode(AndOpNode node) {
      visit(node.getChild(0));
      executeInBranch(node.getChild(1));
    }

    /** Evaluates the given node in an optional branch. */
    private void executeInBranch(ExprNode expr) {
      Block prev = current;
      Block branch = prev.addBranch();
      branch = eval(branch, expr);
      // Add a successor node to merge the branches back together.
      current = Block.merge(prev, branch);
    }

    @Override
    protected void visitConditionalOpNode(ConditionalOpNode node) {
      visit(node.getChild(0));
      current =
          Block.merge(
              eval(current.addBranch(), node.getChild(1)),
              eval(current.addBranch(), node.getChild(2)));
    }
  }

  /**
   * Traverses the control flow graph reachable from {@code start} and adds all the predecessor
   * links.
   */
  private static void addPredecessors(Block start) {
    Set<Block> visited = Sets.newIdentityHashSet();
    addPredecessors(start, visited);
  }

  private static void addPredecessors(Block current, Set<Block> visited) {
    if (!visited.add(current)) {
      return;
    }
    for (Block successor : current.successors) {
      successor.predecessors.add(current);
      addPredecessors(successor, visited);
    }
  }

  /**
   * A graph of {@link Block blocks} for a given template showing how control flows through the
   * expressions of the template.
   *
   * <p>Note this is almost a classic Control Flow Graph
   * https://en.wikipedia.org/wiki/Control_flow_graph with the following exceptions
   *
   * <ul>
   *   <li>We aren't tracking 'back edges' (e.g. loops) accurately.
   *   <li>We are tracking a small subset of the operations performed while evaluating a template.
   *       Currently only {@link VarRefNode variable references} and {@link DataAccessNode data
   *       access} operations.
   * </ul>
   *
   * Both of these limitations exist simply because we don't have usecases for tracking this data
   * yet.
   */
  private static final class AccessGraph {
    final Block start;
    final Block end;

    AccessGraph(Block start, Block end) {
      this.start = start;
      this.end = end;
    }

    /** Creates a copy of the block. Note, this does not clone the {@code #exprs}. */
    AccessGraph copy() {
      Map<Block, Block> originalToCopy = new IdentityHashMap<Block, Block>();
      Block newStart = shallowCopyBlock(start, originalToCopy);
      Block newEnd = originalToCopy.get(end);
      return new AccessGraph(newStart, newEnd);
    }

    /** Returns a set of ExprNodes that have definitely already been resolved. */
    Set<ExprNode> getResolvedExpressions() {
      // To implement we need to walk through the access graph and if we find any path to an
      // expression that doesn't go through another reference to the 'same expression'
      // The easiest way to do this is to calculate 'all paths' through the graph and then do a
      // search. Unfortunately, 'all paths through a DAG' is technically an exponential time
      // algorithm
      // But fortunately we can do better.  We can do a DFS from every expression to the start node
      // and if we go through another reference to the same expression we can abort. If we find any
      // path to 'start' from the expression then we can remove it from the set.
      // Still, DFS from every node is technically O(N^2 + MN)).  This too can be resolved with
      // dynamic programming to make it O(N + M).  To do this we have to limit the number of paths
      // traversed by accumulating results.
      // If we do a Topological traversal from the start node then whenever we visit a node we will
      // have already visited all of its predecessors.  The set of variables definitely accessed
      // prior to a node is simply the intersection of all the accessed variables from its
      // predecessors.  So each node can be processed in time relative to the number of incoming
      // edges.

      Set<ExprNode> resolvedExprs = Sets.newIdentityHashSet();
      Map<Block, Set<Equivalence.Wrapper<ExprNode>>> blockToAccessedExprs = new IdentityHashMap<>();
      for (Block current : getTopologicalOrdering()) {
        // First calculate the set of exprs that were definitely accessed prior to this node
        Set<Equivalence.Wrapper<ExprNode>> currentBlockSet =
            mergePredecessors(blockToAccessedExprs, current);
        // Then figure out which nodes in this block were _already_ accessed.
        for (ExprNode expr : current.exprs) {
          Equivalence.Wrapper<ExprNode> wrapped = ExprEquivalence.get().wrap(expr);
          if (!currentBlockSet.add(wrapped)) {
            resolvedExprs.add(expr);
          }
        }
        blockToAccessedExprs.put(current, currentBlockSet);
      }
      return resolvedExprs;
    }

    static Set<Equivalence.Wrapper<ExprNode>> mergePredecessors(
        Map<Block, Set<Equivalence.Wrapper<ExprNode>>> blockToAccessedExprs, Block current) {
      Set<Equivalence.Wrapper<ExprNode>> currentBlockSet = null;
      for (Block predecessor : current.predecessors) {
        Set<Wrapper<ExprNode>> predecessorBlockSet = blockToAccessedExprs.get(predecessor);
        if (currentBlockSet == null) {
          currentBlockSet = new HashSet<>(predecessorBlockSet);
        } else {
          currentBlockSet.retainAll(predecessorBlockSet);
        }
      }
      if (currentBlockSet == null) {
        currentBlockSet = new HashSet<>();
      }
      return currentBlockSet;
    }

    private List<Block> getTopologicalOrdering() {
      List<Block> ordering = new ArrayList<>();
      Set<Block> visited = Sets.newIdentityHashSet();
      Set<Block> discoveredButNotVisited = Sets.newIdentityHashSet();
      discoveredButNotVisited.add(start);
      outer:
      while (!discoveredButNotVisited.isEmpty()) {
        for (Block notVisited : discoveredButNotVisited) {
          // If we have already visited all predecessors
          if (visited.containsAll(notVisited.predecessors)) {
            discoveredButNotVisited.remove(notVisited);
            visited.add(notVisited);
            discoveredButNotVisited.addAll(notVisited.successors);
            ordering.add(notVisited);
            continue outer;
          }
        }
        throw new AssertionError("failed to make progress");
      }
      return ordering;
    }

    private static Block shallowCopyBlock(Block original, Map<Block, Block> originalToCopy) {
      if (originalToCopy.containsKey(original)) {
        return originalToCopy.get(original);
      }
      Block copy = new Block();
      for (ExprNode expr : original.exprs) {
        copy.exprs.add(expr.copy(new CopyState()));
      }
      // update the map before recursing to avoid infinite loops
      originalToCopy.put(original, copy);
      for (Block successor : original.successors) {
        copy.successors.add(shallowCopyBlock(successor, originalToCopy));
      }
      for (Block predecessor : original.predecessors) {
        copy.predecessors.add(shallowCopyBlock(predecessor, originalToCopy));
      }
      return copy;
    }
  }

  /**
   * A block is a linear sequence of evaluations that happen with no branches.
   *
   * <p>Each block has an arbitrary number of {@link Block#predecessors} and {@link
   * Block#successors} representing all the blocks that come immediately prior to or after this
   * node.
   *
   * <p>This essentially a 'basic block' https://en.wikipedia.org/wiki/Basic_block with the caveat
   * that we are tracking expressions instead of instructions.
   */
  private static final class Block {
    static Block merge(Block... preds) {
      return merge(Arrays.asList(preds));
    }

    static Block merge(List<Block> preds) {
      Block end = new Block();
      for (Block pred : preds) {
        pred.successors.add(end);
      }
      return end;
    }

    // This list will contain either DataAccessNode or VarRefNodes, eventually we may want to add
    // all 'leaf' nodes.
    final List<ExprNode> exprs = new ArrayList<>();
    final Collection<Block> successors = new LinkedHashSet<>();
    final Collection<Block> predecessors = new LinkedHashSet<>();

    void add(VarRefNode var) {
      exprs.add(var);
    }

    void add(DataAccessNode dataAccess) {
      exprs.add(dataAccess);
    }

    // Returns a new block that is a successor to this one
    Block addBranch() {
      Block branch = new Block();
      successors.add(branch);
      return branch;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName()
          + ImmutableMap.of(
              "exprs", exprs,
              "in_edges", predecessors.size(),
              "out_edges", successors.size());
    }
  }
}
