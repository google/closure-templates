/*
 * Copyright 2021 Google Inc.
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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basicfunctions.LegacyObjectMapToMapFunction;
import com.google.template.soy.basicfunctions.MapToLegacyObjectMapFunction;
import com.google.template.soy.basicfunctions.RangeFunction;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ListComprehensionNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralFromListNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.NullSafeAccessNode;
import com.google.template.soy.exprtree.OperatorNodes.AmpAmpOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AsOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BarBarOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.SpreadOpNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.jbcsrc.runtime.JbcSrcRuntime;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPart.Case;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralRemainderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.RangeArgs;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.internal.BuiltinMethod;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CaseOrDefaultNode;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.KeyNode;
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
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.soytree.defn.ConstVar;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

final class TemplateAnalysisImpl implements TemplateAnalysis {

  static TemplateAnalysisImpl analyze(TemplateNode node) {
    AccessGraph templateGraph = new PseudoEvaluatorVisitor().evaluate(node);
    return new TemplateAnalysisImpl(templateGraph);
  }

  private final AccessGraph templateGraph;
  private final ImmutableSet<ExprNode> resolvedExpressions;

  private TemplateAnalysisImpl(AccessGraph templateGraph) {
    this.templateGraph = templateGraph;
    this.resolvedExpressions =
        templateGraph != null
            ? ImmutableSet.copyOf(templateGraph.getResolvedExpressions())
            : ImmutableSet.of();
  }

  /** Prints the access graph in .dot format */
  @VisibleForTesting
  String dumpGraph() {
    return templateGraph.toString();
  }

  @Override
  public boolean isResolved(VarRefNode ref) {
    return resolvedExpressions.contains(ref);
  }

  @Override
  public boolean isResolved(DataAccessNode ref) {
    return resolvedExpressions.contains(ref);
  }

  /**
   * This visitor (and the {@link PseudoEvaluatorExprVisitor}) visits every Soy node in the order
   * that the code generated from those node would execute and constructs an {@link AccessGraph}.
   */
  private static final class PseudoEvaluatorVisitor extends AbstractSoyNodeVisitor<Void> {
    final Map<VarDefn, AccessGraph> letNodes = new HashMap<>();
    final PseudoEvaluatorExprVisitor exprVisitor = new PseudoEvaluatorExprVisitor(letNodes);
    final ExprEquivalence exprEquivalence = new ExprEquivalence();
    Block current;

    AccessGraph evaluate(TemplateNode node) {
      Block start = new Block();
      Block end = exec(start, node);
      return AccessGraph.create(start, end, exprEquivalence);
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
    protected void visitKeyNode(KeyNode node) {
      // do nothing
      // KeyNode is a no-op in jbcsrc, see SoyNodeCompiler.visitKeyNode
    }

    @Override
    protected void visitVeLogNode(VeLogNode node) {
      visitChildren(node);

      // TODO(b/172970101): we always evaluate the logOnly expression and conditionally evaluate
      // the vedata expression, so we should probably treat it like a dead end branch.
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
    protected void visitForNode(ForNode node) {
      // the list is always evaluated first.
      evalInline(node.getExpr());

      Block loopBegin = this.current;
      // create a branch for the loop body
      Block loopBody = loopBegin.addBranch();
      Block loopEnd = exec(loopBody, node.getChild(0));
      // If we can statically prove the list empty, use that information.
      StaticAnalysisResult isLoopEmpty = isCollectionEmpty(node.getExpr());
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
      }
    }

    @Override
    protected void visitForNonemptyNode(ForNonemptyNode node) {
      var indexVar = node.getIndexVar();
      if (indexVar != null) {
        // Add a synthetic access to the index variable prior to execution.  This ensures that all
        // 'real' access are considered resolved.
        this.current.addReference(
            new VarRefNode(indexVar.getOriginalName(), SourceLocation.UNKNOWN, indexVar));
      }
      // Some list expressions are guaranteed to only contain resolved values.
      var listExpression = node.getParent().getExpr();
      var result = isResolvedCollection(listExpression);
      if (result == ResolutionResult.RESOLVED) {
        this.current.addReference(
            new VarRefNode(node.getVar().getOriginalName(), SourceLocation.UNKNOWN, node.getVar()));
      }
      visitChildren(node);
    }

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      // Add a branch to the content of the let node here so that variables already resolved at this
      // point will be available when analyzing the let body, then continue on a separate branch.
      // See visitVarRefNode().
      Block startBlock = new Block();
      Block block = startBlock;
      for (StandaloneNode child : node.getChildren()) {
        block = exec(block, child);
      }
      AccessGraph letStatement = AccessGraph.create(startBlock, block, exprEquivalence);
      letNodes.put(node.getVar(), letStatement);
      this.current.successors.add(letStatement.start);
      this.current = this.current.addBranch();
    }

    @Override
    protected void visitLetValueNode(LetValueNode node) {
      // see visitLetContentNode
      Block start = new Block();
      Block end = exprVisitor.eval(start, node.getExpr());
      AccessGraph letStatement = AccessGraph.create(start, end, exprEquivalence);
      letNodes.put(node.getVar(), letStatement);
      this.current.successors.add(letStatement.start);
      this.current = this.current.addBranch();
    }

    @Override
    protected void visitSwitchNode(SwitchNode node) {
      // A few special cases.  See SoyNodeCompiler.visitSwitchNode
      // * no children => don't evaluate the switch expression
      // * no cases => don't evaluate the switch expression

      List<CaseOrDefaultNode> children = node.getChildren();
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
    protected void visitCallNode(CallNode node) {
      if (node instanceof CallBasicNode) {
        // Always evaluate the callee expression.
        evalInline(((CallBasicNode) node).getCalleeExpr());
      }
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
     *
     * <p>The logic here is similar to a call. Each placeholder may be evaluated in an arbitrary
     * order or not at all, much like call params. There is one exception. All plural or select
     * variables will be evaluated. However, it is not safe to assume that a case will be evaluated
     * definitely after a plural variable because this induces some circular logic in the compiler.
     * For example, consider:
     *
     * <pre><code>
     * {plural $num}
     *   {default} {$num} widgets
     * {/plural}
     * </code></pre>
     *
     * <p>If we were to assume that {@code $num} is evaluated for the plural prior to the case, then
     * we might determine that the placeholder {@code {$num}} could be evaluated inline, but inline
     * placeholder evaluation happens <em>before</em> the message is evaluated. So we can't assume
     * that placeholders in plural cases are evaluated after their controlling plural. Similar logic
     * happens with lets and var refs.
     */
    private void evaluateMsgParts(MsgNode msgNode, ImmutableList<? extends SoyMsgPart> parts) {
      Map<SoyNode, Block> placeholderBlocks = new LinkedHashMap<>();
      Block placeholderBranch = this.current.addBranch();
      this.current = current.addBranch();
      // Individual placeholders within a message are only conditionally evaluated and the
      // evaluation order is undefined, since a very reasonable thing to do is to reorder
      // placeholders.  A less common, but reasonable thing is to remove a placeholder.  For
      // example, some inflected languages like polish will inflect proper names, so a simple
      // message like `Hello {$name}!` is actually not grammatically translatable, so a translator
      // may provide a translation that drops the placeholder.
      // Of course there are some ordering constraints, for example placeholders derived from html
      // tags should remained ordered according to open and close and the runtime for messages
      // enforces this.  However, this ordering constraint isn't interesting since placeholders
      // derived from html close tags are always trivial and contain no interesting subexpressions.
      //
      // To model this, we evaluate every placeholder in a dead end branch.  This is very similar to
      // how Lets are defined.
      evaluateMsgParts(msgNode, parts, placeholderBlocks);
      // treat all the placeholders as dead end branches from the beginning  of the message.
      placeholderBranch.successors.addAll(placeholderBlocks.values());
    }

    /** Evaluate placeholders. */
    private void evaluateMsgParts(
        MsgNode msgNode,
        ImmutableList<? extends SoyMsgPart> parts,
        Map<SoyNode, Block> placeholderBlocks) {
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
          checkState(parts.size() == 1); // sanity test
          SoyMsgPluralPart plural = (SoyMsgPluralPart) part;
          // plural variables are always evaluated
          evalInline(msgNode.getRepPluralNode(plural.getPluralVarName()).getExpr());
          // Recursively visit plural cases
          // cases act like control flow, so we need to branch to and from each branch, like a
          // switch, this is confusing because the conditions are actually translator controlled,
          // but the cases are not, so it is still like some control flow is jumping to/from each
          // case.
          evalPlrSelCases(msgNode, plural.getCases(), placeholderBlocks);
        } else if (part instanceof SoyMsgSelectPart) {
          checkState(parts.size() == 1); // sanity test
          SoyMsgSelectPart select = (SoyMsgSelectPart) part;
          // select variables are always evaluated
          evalInline(msgNode.getRepSelectNode(select.getSelectVarName()).getExpr());
          // Recursively visit select cases
          evalPlrSelCases(msgNode, select.getCases(), placeholderBlocks);
        } else if (part instanceof SoyMsgPlaceholderPart) {
          SoyMsgPlaceholderPart placeholder = (SoyMsgPlaceholderPart) part;
          SoyNode placeholderNode = msgNode.getRepPlaceholderNode(placeholder.getPlaceholderName());
          placeholderBlocks.computeIfAbsent(
              placeholderNode,
              node -> {
                Block placeholderBlock = new Block();
                exec(placeholderBlock, node);
                return placeholderBlock;
              });
        } else {
          throw new AssertionError("unexpected part: " + part);
        }
      }
    }

    private void evalPlrSelCases(
        MsgNode msgNode,
        ImmutableList<? extends Case<?>> cases,
        Map<SoyNode, Block> placeholderBlocks) {
      List<Block> branchEnds = new ArrayList<>();
      Block previous = this.current;
      for (Case<?> caseOrDefault : cases) {
        this.current = previous.addBranch();
        evaluateMsgParts(msgNode, caseOrDefault.parts(), placeholderBlocks);
        branchEnds.add(this.current);
      }
      this.current = Block.merge(branchEnds);
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
      current = evalInBlock(current, expr);
    }

    /** Evaluates the given expression in the given block. */
    Block evalInBlock(Block begin, ExprNode expr) {
      return exprVisitor.eval(begin, expr);
    }
  }

  private enum StaticAnalysisResult {
    TRUE,
    FALSE,
    UNKNOWN;

    static StaticAnalysisResult merge(StaticAnalysisResult a, StaticAnalysisResult b) {
      if (a == b) {
        return a;
      }
      // All other combinations are irreconcilable.
      return StaticAnalysisResult.UNKNOWN;
    }
  }

  private static final class ResolutionResult {
    static final ResolutionResult UNRESOLVED = new ResolutionResult(null);
    static final ResolutionResult RESOLVED = new ResolutionResult(ImmutableList.of());

    static ResolutionResult resolvedIfExprIsResolved(ExprNode conditionallyResolvedSource) {
      return new ResolutionResult(ImmutableList.of(conditionallyResolvedSource));
    }

    private final ImmutableList<ExprNode> onlyIfAllAreResolved;

    ResolutionResult(ImmutableList<ExprNode> onlyIfAllAreResolved) {
      this.onlyIfAllAreResolved = onlyIfAllAreResolved;
    }

    static ResolutionResult merge(ResolutionResult a, ResolutionResult b) {
      if (a == UNRESOLVED || b == UNRESOLVED) {
        return UNRESOLVED;
      }
      if (a == RESOLVED) {
        return b;
      }
      if (b == RESOLVED) {
        return a;
      }
      return new ResolutionResult(
          ImmutableList.<ExprNode>builder()
              .addAll(a.onlyIfAllAreResolved)
              .addAll(b.onlyIfAllAreResolved)
              .build());
    }

    ResolutionResult alsoDependsOn(ExprNode expr) {
      return merge(this, isResolvedValue(expr));
    }
  }

  private static Optional<ExprNode> getVarRefSource(VarRefNode varRefNode) {
    var defn = varRefNode.getDefnDecl();
    switch (defn.kind()) {
      case LOCAL_VAR:
        {
          var local = (LocalVar) defn;
          if (local.declaringNode() instanceof LetValueNode) {
            var letNode = (LetValueNode) local.declaringNode();
            return Optional.of(letNode.getExpr());
          }
        }
        break;
      case CONST:
        {
          var constVar = (ConstVar) defn;
          var constNode = (ConstNode) constVar.getDeclaringNode();
          return Optional.of(constNode.getExpr());
        }
      case COMPREHENSION_VAR:
      case PARAM:
      case STATE:
      case EXTERN:
      case IMPORT_VAR:
      case TEMPLATE:
        // there is nothing to do for these types of variables their values are always unknow.
        break;
    }

    return Optional.empty();
  }

  /**
   * Queries against all possible sources of the value.
   *
   * <p>Specifically follows variable references, and control flow conditions.
   */
  private static <T> T inspectValueSources(
      Function<ExprNode, T> query, BiFunction<T, T, T> merge, ExprNode expr) {
    if (expr instanceof ExprRootNode) {
      expr = ((ExprRootNode) expr).getRoot();
    }
    // We want to follow variables and conditional control flow.
    switch (expr.getKind()) {
      case CONDITIONAL_OP_NODE:
        {
          var node = ((ConditionalOpNode) expr);
          return merge.apply(
              inspectValueSources(query, merge, node.getChild(1)),
              inspectValueSources(query, merge, node.getChild(2)));
        }
      case NULL_COALESCING_OP_NODE:
      case AMP_AMP_OP_NODE:
      case BAR_BAR_OP_NODE:
        {
          // All these nodes are conditional operators that return one of the two subexpressions.
          var node = (ParentExprNode) expr;
          return merge.apply(
              inspectValueSources(query, merge, node.getChild(0)),
              inspectValueSources(query, merge, node.getChild(1)));
        }
      case ASSERT_NON_NULL_OP_NODE:
      case GROUP_NODE:
        {
          var node = ((ParentExprNode) expr);
          return inspectValueSources(query, merge, node.getChild(0));
        }
      case AS_OP_NODE:
        {
          var node = ((AsOpNode) expr);
          return inspectValueSources(query, merge, node.getChild(0));
        }

      default:
        break;
    }
    return query.apply(expr);
  }

  private static StaticAnalysisResult isCollectionEmpty(ExprNode node) {
    return inspectValueSources(
        expr -> {
          Optional<RangeArgs> rangeArgs = RangeArgs.createFromExpr(expr);
          if (rangeArgs.isPresent()) {
            return isRangeExpressionEmpty(rangeArgs.get());
          }
          if (expr instanceof VarRefNode) {
            return getVarRefSource((VarRefNode) expr)
                .map(TemplateAnalysisImpl::isCollectionEmpty)
                .orElse(StaticAnalysisResult.UNKNOWN);
          }
          if (expr instanceof ListLiteralNode) {
            var children = ((ListLiteralNode) expr).getChildren();
            if (children.isEmpty()) {
              return StaticAnalysisResult.TRUE;
            }
            if (children.stream().anyMatch(c -> !(c instanceof SpreadOpNode))) {
              return StaticAnalysisResult.FALSE;
            }
            ImmutableList<StaticAnalysisResult> results =
                children.stream()
                    .map(
                        c -> {
                          var spreadOpNode = (SpreadOpNode) c;
                          return isCollectionEmpty(spreadOpNode.getChild(0));
                        })
                    .collect(toImmutableList());
            if (results.stream().allMatch(r -> r == StaticAnalysisResult.TRUE)) {
              return StaticAnalysisResult.TRUE;
            }
            if (results.stream().anyMatch(r -> r == StaticAnalysisResult.FALSE)) {
              return StaticAnalysisResult.FALSE;
            }
          }
          // TODO(lukes): could handle things like various method calls and function calls, but
          // this is probably not worth it.
          return StaticAnalysisResult.UNKNOWN;
        },
        StaticAnalysisResult::merge,
        node);
  }

  private static final ImmutableSet<SoyType.Kind> COLLECTION_TYPES =
      ImmutableSet.of(
          SoyType.Kind.LIST, SoyType.Kind.MAP, SoyType.Kind.RECORD, SoyType.Kind.LEGACY_OBJECT_MAP);

  private static boolean isCollectionType(SoyType type) {
    type = SoyTypes.tryRemoveNullish(type);
    return SoyTypes.expandUnions(type).stream()
        .allMatch(member -> COLLECTION_TYPES.contains(member.getKind()));
  }

  /**
   * Returns a resolution result for whether the node evaluates to a collection that is always
   * resolved or under what conditions it is resolved.
   */
  private static ResolutionResult isResolvedCollection(ExprNode rootNode) {
    return inspectValueSources(
        expr -> {
          SoyType type = SoyTypes.tryRemoveNullish(expr.getType());
          if (!isCollectionType(type)) {
            return ResolutionResult.UNRESOLVED;
          }
          if (expr instanceof VarRefNode) {
            var varRef = (VarRefNode) expr;
            if (varRef.getDefnDecl().kind() == VarDefn.Kind.CONST) {
              return ResolutionResult.RESOLVED;
            }
            return getVarRefSource(varRef)
                .map(TemplateAnalysisImpl::isResolvedCollection)
                .orElse(ResolutionResult.UNRESOLVED);
          }
          if (expr instanceof MethodCallNode) {
            // Proto methods that return collections always return resolved values.
            var methodCall = (MethodCallNode) expr;
            if (SoyTypes.isKindOrUnionOfKind(methodCall.getBaseType(true), SoyType.Kind.PROTO)) {
              return ResolutionResult.RESOLVED;
            }

            switch (methodCall.getMethodName().identifier()) {
              case "sort":
              case "asciiSort":
              case "uniq":
              case "keys":
                // These force resolution of the base collection, so all returned collections are
                // resolved.
                return ResolutionResult.RESOLVED;
              case "values":
              case "entries":
              case "flat":
              case "slice":
              case "reverse":
                {
                  // The core collection types have methods like `Map.keys()` or `list.flat()` that
                  // return derived collections.  Those collections always share the same resolution
                  // status as the base collection.
                  var baseResult = isResolvedCollection(methodCall.getBaseExprChild());
                  if (baseResult == ResolutionResult.UNRESOLVED) {
                    return ResolutionResult.resolvedIfExprIsResolved(methodCall.getBaseExprChild());
                  }
                  return baseResult;
                }
              case "concat":
                {
                  ResolutionResult result = ResolutionResult.RESOLVED;
                  for (ExprNode child : methodCall.getChildren()) {
                    result = ResolutionResult.merge(result, isResolvedCollection(child));
                  }
                  return result;
                }

              case "split":
                // string.split() returns a list of strings.
                return ResolutionResult.RESOLVED;

              default:
                return ResolutionResult.UNRESOLVED;
            }
          }
          if (expr instanceof MapLiteralFromListNode) {
            return isResolvedCollection(((MapLiteralFromListNode) expr).getChild(0));
          }
          if (expr instanceof FunctionNode) {
            var fnNode = (FunctionNode) expr;
            var function = fnNode.getSoyFunction();
            if (function instanceof RangeFunction) {
              return ResolutionResult.RESOLVED;
            }
            if (function instanceof MapToLegacyObjectMapFunction
                || function instanceof LegacyObjectMapToMapFunction) {
              return isResolvedCollection(fnNode.getChild(0));
            }
          }
          if (expr instanceof ListComprehensionNode
              || expr instanceof ListLiteralNode
              || expr instanceof RecordLiteralNode
              || expr instanceof MapLiteralNode) {
            // For better or worse we resolve all elements when constructing literals.
            return ResolutionResult.RESOLVED;
          }

          return ResolutionResult.UNRESOLVED;
        },
        ResolutionResult::merge,
        rootNode);
  }

  private static ResolutionResult isResolvedValue(ExprNode rootNode) {
    return inspectValueSources(
        expr -> {
          if (expr instanceof VarRefNode) {
            var varRef = (VarRefNode) expr;
            // Const variables are always resolved.
            if (varRef.getDefnDecl().kind() == VarDefn.Kind.CONST) {
              return ResolutionResult.RESOLVED;
            }
            return getVarRefSource(varRef)
                .map(TemplateAnalysisImpl::isResolvedValue)
                .orElseGet(() -> ResolutionResult.resolvedIfExprIsResolved(varRef));
          }
          if (expr instanceof PrimitiveNode
              || expr instanceof ListLiteralNode
              || expr instanceof RecordLiteralNode
              || expr instanceof MapLiteralNode) {
            return ResolutionResult.RESOLVED;
          }
          if (expr instanceof FunctionNode) {
            var fnNode = (FunctionNode) expr;
            if (fnNode.getSoyFunction() instanceof SoyJavaSourceFunction
                && JavaSourceFunctionCompiler.doesPluginReturnFuture(
                    (SoyJavaSourceFunction) fnNode.getSoyFunction())) {
              return ResolutionResult.UNRESOLVED;
            }
            return ResolutionResult.RESOLVED;
          }
          return ResolutionResult.UNRESOLVED;
        },
        ResolutionResult::merge,
        rootNode);
  }

  private static StaticAnalysisResult isRangeExpressionEmpty(RangeArgs range) {
    int start = 0;
    if (range.start().isPresent()) {
      if (range.start().get() instanceof IntegerNode) {
        long startAsLong = ((IntegerNode) range.start().get()).getValue();
        if (startAsLong != (int) startAsLong) {
          return StaticAnalysisResult.UNKNOWN;
        }
        start = (int) startAsLong;
      } else {
        // if the start is not a constant then we don't know anything
        return StaticAnalysisResult.UNKNOWN;
      }
    }

    int limit;
    if (range.limit() instanceof IntegerNode) {
      long limitAsLong = ((IntegerNode) range.limit()).getValue();
      if (limitAsLong != (int) limitAsLong) {
        return StaticAnalysisResult.UNKNOWN;
      }
      limit = (int) limitAsLong;
    } else {
      return StaticAnalysisResult.UNKNOWN;
    }

    int step = 1;
    if (range.increment().isPresent()) {
      if (range.increment().get() instanceof IntegerNode) {
        long stepAsLong = ((IntegerNode) range.increment().get()).getValue();
        if (stepAsLong != (int) stepAsLong) {
          return StaticAnalysisResult.UNKNOWN;
        }
        step = (int) stepAsLong;
      } else {
        return StaticAnalysisResult.UNKNOWN;
      }
    }
    return JbcSrcRuntime.rangeLoopLength(start, limit, step) > 0
        ? StaticAnalysisResult.FALSE
        : StaticAnalysisResult.TRUE;
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
    protected void visitGlobalNode(GlobalNode node) {
      // do nothing
    }

    @Override
    protected void visitNullSafeAccessNode(NullSafeAccessNode node) {
      // The NullSafeAccessNode wraps a base node and DataAccessNode nodes. {x?.field} becomes:
      //
      // NullSafeAccessNode
      //   +--- VarRefNode(x)
      //   +--- FieldAccessNode("field")
      //          +--- GroupNode(NullNode())
      //
      // Since the DataAccessNode has a global placeholder for its own base expression, all field
      // accesses with the same name will match each other, regardless of the actual base
      // expression. Don't traverse it to avoid marking nodes as incorrectly resolved.
      visit(node.getBase());
    }

    @Override
    protected void visitMapLiteralNode(MapLiteralNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitMapLiteralFromListNode(MapLiteralFromListNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitListComprehensionNode(ListComprehensionNode node) {
      visit(node.getListExpr());
      Block loopBegin = this.current;
      // create a branch for the loop body
      Block loopBody = loopBegin.addBranch();
      // filters execute before the list item transform when they exist
      if (node.getFilterExpr() != null) {
        loopBody = eval(loopBody, node.getFilterExpr());
      }
      Block loopEnd = eval(loopBody, node.getListItemTransformExpr());
      // If we can statically prove the list empty, use that information.
      StaticAnalysisResult isLoopEmpty = isCollectionEmpty(node.getListExpr());
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
      }
    }

    @Override
    protected void visitAssertNonNullOpNode(AssertNonNullOpNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitTemplateLiteralNode(TemplateLiteralNode node) {
      // do nothing
    }

    @Override
    protected void visitExprRootNode(ExprRootNode node) {
      visit(node.getRoot());
    }

    @Override
    protected void visitVarRefNode(VarRefNode node) {
      AccessGraph letContent = letNodes.get(node.getDefnDecl());
      if (letContent != null) {
        // Due to lazy evaluation we don't evaluate the body of lets at the declaration site. The
        // set of variables that are in scope at the {let} call are passed into the method generated
        // for the {let}. However we defensively only treat values that have been referenced by then
        // as guaranteed to be resolved. To do this, in visitLet*Node() we add the original let
        // expression in a dead-end branch at the point of the {let}, which will allow the nodes
        // inside of the let node to be analyzed in this way.
        //
        // When the variable is referenced, a copy of the let block will be inserted just
        // before the reference, so that the variables referenced by the let will be marked as
        // referenced in the outer scope, without affecting the nodes inside the let body.
        // For example:
        //
        // {$param p}
        // {$foo}
        // {let $bar: $foo + $p + $qux /}
        // {$qux}
        // {$bar}
        // {$p}
        //
        // The graph structure would look like this (nodes that are definitely referenced are marked
        // with an asterisk):
        //
        // [ $foo*, $p, $qux ]
        //     ^
        //     |                     (copy of let block)
        // [ $foo ] => [ $qux ] => [ $foo'*, $p', $qux'* ] => [ $bar, $p* ]
        //
        // Of the exprs inside the let block, only foo is marked as definitely referenced. The
        // copies $foo' and $qux' will also be marked as referenced, but they aren't in the AST and
        // so won't affect any code generating expressions. The final reference to $p will also be
        // marked as referenced, since it has $p' as predecessors that are references to the same
        // variable.
        //
        // Note that in the example above, when $bar is referenced, $qux has already been
        // referenced. We could try to model this by emiting the original dead-end branch right
        // before the $bar reference, which would cause the $qux node inside the let to be marked as
        // definitely resolved. However this could cause incorrect gencode to be created, because
        // when evaluating the let, the LazyCompiler checks to see if the {let} can be compiled
        // without detaches, or if it needs to create a class for the detach. If it can be
        // compiled without detaches, it inlines the let at that point, which could then invalidate
        // the original assumption that $qux inside of the {let} can be resolved immediately. See
        //
        // There are some ways to fix this that would be a little complicated. When analyzing the
        // let block, we could have separate logic based on if we know it will be inlined or lazy
        // and calculate both. We can know this by checking if all references in the entire block
        // are definitely resolved, or not (although that in turn depends on if the block will be
        // inlined, or lazy evaluated).
        AccessGraph copy = letContent.copy();
        this.current.successors.add(copy.start);
        this.current = copy.end.addBranch();
      }
      current.addReference(node);
    }

    @Override
    protected void visitDataAccessNode(DataAccessNode node) {
      visitChildren(node); // visit subexpressions first

      switch (node.getKind()) {
        case FIELD_ACCESS_NODE:
          {
            FieldAccessNode fieldAccessNode = (FieldAccessNode) node;
            if (fieldAccessNode.getSoyMethod() != null) {
              // All the field accesses implemented as methods always produce resolved values.
              current.addResolved(node, ResolutionResult.RESOLVED);
            } else {
              // We can access fields on collections that are resolved.
              current.addResolved(node, isResolvedCollection(node.getBaseExprChild()));
            }
          }
          break;
        case ITEM_ACCESS_NODE:
          {
            // Item access is always ok if we are indexing into something like a proto repeated
            // field.
            current.addResolved(
                node,
                isResolvedCollection(node.getBaseExprChild()).alsoDependsOn(node.getChild(1)));
          }
          break;
        case METHOD_CALL_NODE:
          {
            var methodCallNode = (MethodCallNode) node;
            if (methodCallNode.getSoyMethod() instanceof BuiltinMethod) {
              switch ((BuiltinMethod) methodCallNode.getSoyMethod()) {
                case MAP_GET:
                  // Map.get return values are resolved if their key expression is resolved and so
                  // is the full collections
                  current.addResolved(
                      node,
                      isResolvedCollection(node.getBaseExprChild())
                          .alsoDependsOn(node.getChild(1)));
                  break;
                case HAS_PROTO_FIELD:
                case GET_PROTO_FIELD:
                case GET_READONLY_EXTENSION:
                case GET_EXTENSION:
                case GET_PROTO_FIELD_AS_STRING:
                case GET_PROTO_FIELD_AS_LEGACY_NUMBER_OR_STRING:
                case GET_READONLY_PROTO_FIELD:
                case GET_PROTO_FIELD_OR_UNDEFINED_AS_STRING:
                case GET_PROTO_FIELD_OR_UNDEFINED:
                case HAS_EXTENSION:
                case GET_PROTO_FIELD_OR_UNDEFINED_AS_LEGACY_NUMBER_OR_STRING:
                  // These are always resolved.
                  current.addResolved(node, ResolutionResult.RESOLVED);
                  break;
                case BIND:
                  current.addReference(node);
                  break;
              }
            }
          }
          break;

        default:
          throw new AssertionError();
      }
    }

    @Override
    protected void visitFunctionNode(FunctionNode node) {
      if (node.getSoyFunction() instanceof BuiltinFunction) {
        BuiltinFunction builtinFunction = (BuiltinFunction) node.getSoyFunction();
        switch (builtinFunction) {
          case IS_PRIMARY_MSG_IN_USE:
            // early return for these.  the AST looks like we are evaluating a var, but in fact we
            // generate alternate code to reference a synthetic variable.
            // See ExpressionCompiler
            return;
          case CHECK_NOT_NULL:
          case CSS:
          case EVAL_TOGGLE:
          case DEBUG_SOY_TEMPLATE_INFO:
          case PROTO_INIT:
          case SOY_SERVER_KEY:
          case TO_FLOAT:
          case VE_DATA:
          case XID:
          case EMPTY_TO_NULL:
          case UNDEFINED_TO_NULL:
          case UNDEFINED_TO_NULL_SSR:
          case BOOLEAN:
          case HAS_CONTENT:
          case IS_TRUTHY_NON_EMPTY:
            // visit children normally
            break;
          case UNKNOWN_JS_GLOBAL:
            throw new UnsupportedOperationException(
                "the "
                    + builtinFunction.getName()
                    + " function can't be used in templates compiled to Java");
          default:
            throw new AssertionError("unexpected builtin function: " + builtinFunction.getName());
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
    protected void visitRecordLiteralNode(RecordLiteralNode node) {
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
    protected void visitBarBarOpNode(BarBarOpNode node) {
      visit(node.getChild(0));
      executeInBranch(node.getChild(1));
    }

    @Override
    protected void visitAndOpNode(AndOpNode node) {
      visit(node.getChild(0));
      executeInBranch(node.getChild(1));
    }

    @Override
    protected void visitAmpAmpOpNode(AmpAmpOpNode node) {
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
    dfsPreOrder(
        start,
        current -> {
          for (Block successor : current.successors) {
            successor.predecessors.add(current);
          }
        });
  }

  private static void eliminateUnconditionalBranches(Block start, Block end) {
    dfsPreOrder(
        start,
        current -> {
          if (current == end) {
            return;
          }
          if (current.predecessors.size() == 1) {
            Block predecessor = Iterables.getOnlyElement(current.predecessors);
            if (predecessor.successors.size() == 1) {
              // in this case the node is a single unconditional link, merge its successor into it
              predecessor.encounters.addAll(current.encounters);
              predecessor.successors.clear();
              predecessor.successors.addAll(current.successors);
              for (Block successor : current.successors) {
                successor.predecessors.remove(current);
                successor.predecessors.add(predecessor);
              }
            }
          }
        });
  }

  /** Removes all empty nodes (nodes with no exprs) preserving both the start and end nodes */
  private static void eliminateEmptyNodes(Block start, Block end) {
    dfsPreOrder(
        start,
        current -> {
          // preserve the beginning and end nodes
          if (current == end || current == start) {
            return;
          }
          if (current.encounters.isEmpty()) {
            // This node has no exprs and it isn't the start node, so we can just merge all of its
            // successors into its predecessors and cut it out of the graph.
            for (Block pred : current.predecessors) {
              pred.successors.remove(current);
              pred.successors.addAll(current.successors);
            }
            for (Block succ : current.successors) {
              succ.predecessors.remove(current);
              succ.predecessors.addAll(current.predecessors);
            }
          }
        });
  }

  /**
   * A helper function that performs a DFS traversal through the access graph (only using {@code
   * succcessors} links) and invokes the consumer on every node in pre-order (i.e. {@code
   * succcessors} first).
   */
  private static void dfsPreOrder(Block node, Consumer<Block> fn) {
    Set<Block> visited = Sets.newIdentityHashSet();

    ArrayDeque<Block> stack = new ArrayDeque<>();
    stack.push(node);
    while (!stack.isEmpty()) {
      Block current = stack.pop();
      if (visited.add(current)) {
        for (Block successor : current.successors) {
          stack.push(successor);
        }
        fn.accept(current);
      }
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
    final ExprEquivalence exprEquivalence;

    static AccessGraph create(Block start, Block end, ExprEquivalence exprEquivalence) {
      // annotate the graph with predecessor links
      addPredecessors(start);
      // check basic constraints.
      checkState(start.predecessors.isEmpty());
      checkState(end.successors.isEmpty());
      // Due to the way we build the graph we may have lots of either empty nodes or
      // nodes with unconditional branches.  Here we reduce the size of the graph by trying to
      // eliminate such nodes.  These are O(N) passes and the analysis performed by AccessGraph is
      // O(N+M) so this may or may not be a performance optimization, but because there are also
      // scenarios where whole AccessGraph objects are copied, this should be beneficial.
      // Additionally, it makes visualizing the graph simpler.

      // ensure our simplification doesn't modify the sink
      eliminateEmptyNodes(start, end);
      eliminateUnconditionalBranches(start, end);
      // check basic constraints.
      checkState(start.predecessors.isEmpty());
      checkState(end.successors.isEmpty());
      return new AccessGraph(start, end, exprEquivalence);
    }

    AccessGraph(Block start, Block end, ExprEquivalence exprEquivalence) {
      this.start = start;
      this.end = end;
      this.exprEquivalence = exprEquivalence;
    }

    /** Creates a deep copy of the block, with all expressions also copied. */
    AccessGraph copy() {
      IdentityHashMap<Block, Block> originalToCopy = new IdentityHashMap<>();
      Block newStart = deepCopyBlock(start, originalToCopy);
      Block newEnd = originalToCopy.get(end);
      return new AccessGraph(newStart, newEnd, exprEquivalence);
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
      IdentityHashMap<Block, Set<ExprEquivalence.Wrapper>> blockToAccessedExprs =
          new IdentityHashMap<>();
      for (Block current : getTopologicalOrdering()) {
        // First calculate the set of exprs that were definitely accessed prior to this node
        Set<ExprEquivalence.Wrapper> currentBlockSet =
            mergePredecessors(blockToAccessedExprs, current);
        // Then figure out which nodes in this block were _already_ accessed.
        for (Block.Encounter encounter : current.encounters) {
          ExprNode expr = encounter.expr;
          // All Encounters are references.
          if (!currentBlockSet.add(exprEquivalence.wrap(expr))) {
            resolvedExprs.add(expr);
          } else {
            switch (encounter.type) {
              case REFERENCE:
                // handled above
                break;
              case CONDITIONALLY_RESOLVED:
              case RESOLVED:
                // If all the conditions are already resolved, then we can consider this resolved.
                if (encounter.conditionallyResolved.stream().allMatch(resolvedExprs::contains)) {
                  resolvedExprs.add(expr);
                }
                break;
            }
          }
        }
        // no need to store the result if we are in a dead end branch.
        if (!current.successors.isEmpty()) {
          blockToAccessedExprs.put(current, currentBlockSet);
        }
      }
      return resolvedExprs;
    }

    static <T> Set<T> mergePredecessors(Map<Block, Set<T>> blockToAccessedExprs, Block current) {
      Set<T> currentBlockSet = null;
      for (Block predecessor : current.predecessors) {
        Set<T> predecessorBlockSet = blockToAccessedExprs.get(predecessor);
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

    private Set<Block> getTopologicalOrdering() {
      Set<Block> ordering = new LinkedHashSet<>();
      Set<Block> discoveredButNotVisited = new LinkedHashSet<>();
      discoveredButNotVisited.add(start);
      while (!discoveredButNotVisited.isEmpty()) {
        Optional<Block> firstVisitedAllPredecessors =
            discoveredButNotVisited.stream()
                // If we have already visited all predecessors
                .filter(block -> ordering.containsAll(block.predecessors))
                .findFirst();
        if (firstVisitedAllPredecessors.isPresent()) {
          Block notVisited = firstVisitedAllPredecessors.get();
          discoveredButNotVisited.remove(notVisited);
          discoveredButNotVisited.addAll(notVisited.successors);
          ordering.add(notVisited);
        } else {
          throw new AssertionError("failed to make progress");
        }
      }
      return ordering;
    }

    private static Block deepCopyBlock(
        Block original, IdentityHashMap<Block, Block> originalToCopy) {
      if (originalToCopy.containsKey(original)) {
        return originalToCopy.get(original);
      }
      Block copy = new Block();
      // A copy of the let body is inserted before a variable is referenced. This lets us correctly
      // calculate variables used inside the let body that are later referenced as definitely
      // referenced, without marking the original expression inside the body as referenced. See
      // notes re: $qux' in visitVarRefNode.
      for (Block.Encounter encounter : original.encounters) {
        copy.encounters.add(encounter.copy());
      }
      // update the map before recursing to avoid infinite loops
      originalToCopy.put(original, copy);
      for (Block successor : original.successors) {
        copy.successors.add(deepCopyBlock(successor, originalToCopy));
      }
      for (Block predecessor : original.predecessors) {
        copy.predecessors.add(deepCopyBlock(predecessor, originalToCopy));
      }
      return copy;
    }

    @Override
    public String toString() {
      SetMultimap<Block, Block> adjacencyMatrix =
          MultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
      ArrayDeque<Block> toVisit = new ArrayDeque<>();
      Set<Block> visited = new LinkedHashSet<>();
      toVisit.add(start);
      Block current;
      while ((current = toVisit.poll()) != null) {
        if (visited.add(current)) {
          adjacencyMatrix.putAll(current, current.successors);
          toVisit.addAll(current.successors);
        }
      }
      StringBuilder graph = new StringBuilder().append("digraph AccessGraph {");
      // Draw nodes
      int id = 0;
      IdentityHashMap<Block, Integer> nodeIds = new IdentityHashMap<>();
      for (Block node : visited) {
        graph.append("\n ").append(id).append(" [label=\"");
        if (node == start) {
          graph.append("START ");
        }
        if (node == end) {
          graph.append("END ");
        }
        graph
            .append(
                node.encounters.stream()
                    .map(
                        e -> {
                          var expr = e.expr;
                          var location = expr.getSourceLocation();
                          return e.type
                              + ":"
                              + expr.toSourceString()
                              + "@"
                              + location.getBeginLine()
                              + ":"
                              + location.getBeginColumn();
                        })
                    .collect(joining(" ,", "[", "]")))
            .append("\"];");
        nodeIds.put(node, id);
        id++;
      }
      // Draw edges id->id
      for (Map.Entry<Block, Block> entry : adjacencyMatrix.entries()) {
        graph
            .append("\n  ")
            .append(nodeIds.get(entry.getKey()))
            .append(" -> ")
            .append(nodeIds.get(entry.getValue()))
            .append(";");
      }
      return graph.append("\n}").toString();
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

    private enum EncounterType {
      REFERENCE,
      RESOLVED,
      CONDITIONALLY_RESOLVED
    }

    private static final class Encounter {
      final ExprNode expr;
      final EncounterType type;
      final ImmutableList<ExprNode> conditionallyResolved;

      Encounter(ExprNode expr, EncounterType type) {
        this.expr = expr;
        this.type = type;
        this.conditionallyResolved = ImmutableList.of();
      }

      Encounter(ExprNode expr, ImmutableList<ExprNode> conditionallyResolved) {
        this.expr = expr;
        this.conditionallyResolved = conditionallyResolved;
        this.type = EncounterType.CONDITIONALLY_RESOLVED;
      }

      Encounter(ExprNode expr, EncounterType type, ImmutableList<ExprNode> conditionallyResolved) {
        this.expr = expr;
        this.conditionallyResolved = conditionallyResolved;
        this.type = type;
      }

      Encounter copy() {
        return new Encounter(
            type == EncounterType.REFERENCE ? expr.copy(new CopyState()) : expr,
            type,
            conditionallyResolved);
      }
    }

    // This list will contain either DataAccessNode or VarRefNodes, eventually we may want to add
    // all 'leaf' nodes.
    final List<Encounter> encounters = new ArrayList<>();
    final Set<Block> successors = new LinkedHashSet<>();
    final Set<Block> predecessors = new LinkedHashSet<>();

    void addReference(VarRefNode var) {
      encounters.add(new Encounter(var, EncounterType.REFERENCE));
    }

    void addReference(DataAccessNode dataAccess) {
      encounters.add(new Encounter(dataAccess, EncounterType.REFERENCE));
    }

    /** Adds an expression that may depend on a resolution result. */
    void addResolved(DataAccessNode dataAccess, ResolutionResult resolutionResult) {
      if (resolutionResult == ResolutionResult.RESOLVED) {
        encounters.add(new Encounter(dataAccess, EncounterType.RESOLVED));
      } else if (resolutionResult == ResolutionResult.UNRESOLVED) {
        addReference(dataAccess);
      } else {
        encounters.add(new Encounter(dataAccess, resolutionResult.onlyIfAllAreResolved));
      }
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
              "id", this.hashCode(),
              "exprs", encounters,
              "in_edges",
                  predecessors.stream()
                      .map(p -> String.valueOf(p.hashCode()))
                      .collect(joining(", ")),
              "out_edges",
                  successors.stream()
                      .map(p -> String.valueOf(p.hashCode()))
                      .collect(joining(", ")));
    }
  }
}
