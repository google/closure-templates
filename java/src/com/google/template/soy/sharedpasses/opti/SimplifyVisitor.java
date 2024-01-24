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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Comparator.comparing;

import com.google.common.base.Objects;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.ListComprehensionNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.OperatorNodes.AmpAmpOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.internal.BuiltinMethod;
import com.google.template.soy.sharedpasses.render.RenderException;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SkipNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.StringType;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * Visitor for simplifying subtrees based on constant values known at compile time.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class SimplifyVisitor {

  /** Creates a new simplify visitor. */
  public static SimplifyVisitor create(
      IdGenerator idGenerator,
      ImmutableList<SoyFileNode> sourceFiles,
      ErrorReporter errorReporter) {
    return new SimplifyVisitor(
        idGenerator,
        sourceFiles,
        new SimplifyExprVisitor(errorReporter),
        new PreevalVisitorFactory());
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

  private static final class RefAndHolder {
    static final RefAndHolder NULL = new RefAndHolder();
    final VarRefNode ref;
    final ExprHolderNode holder;

    RefAndHolder() {
      this.ref = null;
      this.holder = null;
    }

    RefAndHolder(VarRefNode ref, ExprHolderNode holder) {
      this.ref = checkNotNull(ref);
      this.holder = checkNotNull(holder);
    }
  }

  /** Returns true if we can place a let node as a child of the given node. */
  private static boolean isValidBlockNodeForLet(SoyNode node) {
    switch (node.getKind()) {
      case LET_CONTENT_NODE:
      case CALL_PARAM_CONTENT_NODE:
      case IF_COND_NODE:
      case IF_ELSE_NODE:
      case SWITCH_CASE_NODE:
      case SWITCH_DEFAULT_NODE:
      case FOR_NONEMPTY_NODE:
      case TEMPLATE_BASIC_NODE:
      case TEMPLATE_DELEGATE_NODE:
      case TEMPLATE_ELEMENT_NODE:
        return true;
      case VE_LOG_NODE:
        // `velog` nodes can contain lets unless they are in a msg
        return !(node.getParent() instanceof MsgBlockNode);

      case LOG_NODE:
        // msg nodes cannot (directly) hold lets
      case MSG_FALLBACK_GROUP_NODE:
      case MSG_NODE:
      case MSG_PLURAL_NODE:
      case MSG_SELECT_NODE:
      case MSG_SELECT_CASE_NODE:
      case MSG_SELECT_DEFAULT_NODE:
      case MSG_PLURAL_CASE_NODE:
      case MSG_PLURAL_DEFAULT_NODE:
      case MSG_HTML_TAG_NODE:
      case MSG_PLACEHOLDER_NODE:
        return false;
      default:
        if (node instanceof BlockNode) {
          throw new AssertionError(
              "unhandled block node: "
                  + node.getKind()
                  + "  "
                  + node
                  + " @"
                  + node.getSourceLocation());
        }
        return false;
    }
  }

  private final class Impl extends AbstractSoyNodeVisitor<Void> {
    final ImmutableMap<String, TemplateNode> basicTemplates;
    final IdGenerator nodeIdGen;
    final IdentityHashMap<LocalVar, LocalVar> varDefnReplacements = new IdentityHashMap<>();

    Impl(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
      this.nodeIdGen = idGenerator;
      ImmutableMap.Builder<String, TemplateNode> basicTemplates = ImmutableMap.builder();
      for (SoyFileNode fileNode : sourceFiles) {
        for (TemplateNode template : fileNode.getTemplates()) {
          // we can't simplify deltemplates
          if (!(template instanceof TemplateDelegateNode)) {
            basicTemplates.put(template.getTemplateName(), template);
          }
        }
      }
      this.basicTemplates = basicTemplates.buildOrThrow();
    }

    // --------------------------------------------------------------------------------------------
    // Implementations for specific nodes.

    @Override
    protected void visitTemplateNode(TemplateNode node) {
      boolean simplifiedAnyLet;
      List<RefAndHolder> allRefs;
      ImmutableSet<VarDefn> referencedVars;
      do {
        // reset datastructures
        simplifiedAnyLet = false;
        varDefnReplacements.clear();
        referencedVars = ImmutableSet.of();

        // First simplify all expressions in the subtree.
        SoyTreeUtils.execOnAllV2Exprs(node, simplifyExprVisitor);
        // Simplify the node
        super.visitTemplateNode(node);
        // Find all variables
        allRefs = getAllRefs(node);

        // Update definitions if we modified let nodes
        if (!varDefnReplacements.isEmpty()) {
          for (RefAndHolder refAndHolder : allRefs) {
            LocalVar newDefn = varDefnReplacements.get(refAndHolder.ref.getDefnDecl());
            if (newDefn != null) {
              refAndHolder.ref.setDefn(newDefn);
            }
          }
        }
        Map<LetValueNode, RefAndHolder> possiblyInlinableRefs =
            new TreeMap<>(
                comparing(LetValueNode::getSourceLocation).thenComparing(LetValueNode::getId));
        for (RefAndHolder refAndHolder : allRefs) {
          VarDefn defn = refAndHolder.ref.getDefnDecl();
          if (defn.kind() != VarDefn.Kind.LOCAL_VAR) {
            continue;
          }
          LocalVar local = (LocalVar) defn;
          if (local.declaringNode().getKind() != SoyNode.Kind.LET_VALUE_NODE) {
            continue;
          }
          LetValueNode letNode = (LetValueNode) local.declaringNode();
          // If we already had a reference, replace with the special NULL value, a LetValueNode is
          // only inlinable if there is exactly 1 reference to it.
          possiblyInlinableRefs.compute(
              letNode, (key, oldValue) -> oldValue == null ? refAndHolder : RefAndHolder.NULL);
        }
        // after visiting we can analyze references for inlining local variables.
        // We can only do this at the end after finding al VarRefNodes.  In theory we could do it at
        // the end of each scope, but that would require us to track scopes.  This would probably
        // only be useful if the data we are tracking was really expensive.
        //
        // A local variable is 'inlinable' if:
        // * It is referenced exactly once, and either that reference is not within a loop or list
        //   comprehension, or the definition is a literal primitive (a string or numeric literal).
        // * It is defined by a LetValueNode.  LetContentNodes are not possiblle due to the
        //   autoescaping semantics.  IF we had a concept of html literals, we could revisit this.
        //
        // We could consider inlining variables referenced more than once if they were sufficiently
        // trivial (e.g. numeric literals), but this isn't a clear tradeoff.
        //
        // Also, we want to iterate in order of source location of the declaration of the node.
        // This will ensure that if variabes inline into each other they can cascade appropriately.
        for (Map.Entry<LetValueNode, RefAndHolder> entry : possiblyInlinableRefs.entrySet()) {
          if (entry.getValue() == RefAndHolder.NULL) {
            continue;
          }
          RefAndHolder refAndHolder = entry.getValue();
          simplifiedAnyLet =
              maybeInline(entry.getKey(), refAndHolder.ref, refAndHolder.holder)
                  || simplifiedAnyLet;
        }

        // Finally delete unreferenced variable definitions.  The linter warns about these but
        // they do persist especially due to code generators.
        // Only do this if we didn't inline any, so that our allRefs set is accurate
        if (!simplifiedAnyLet) {
          referencedVars =
              allRefs.stream().map(ref -> ref.ref.getDefnDecl()).collect(toImmutableSet());
          for (LocalVarNode varNode :
              SoyTreeUtils.allNodesOfType(node, LocalVarNode.class).collect(toImmutableList())) {

            if (varNode instanceof ForNonemptyNode) {
              var forNode = (ForNonemptyNode) varNode;
              // we cannot delete unreferenced loop variables but we can delete unreferenced
              // index vars
              if (forNode.getIndexVar() != null
                  && !referencedVars.contains(forNode.getIndexVar())) {
                forNode.deleteIndexVar();
                simplifiedAnyLet = true;
              }
            } else if (varNode instanceof LetNode) {
              if (!referencedVars.contains(varNode.getVar())) {
                var parent = varNode.getParent();
                parent.removeChild(parent.getChildIndex(varNode));
                simplifiedAnyLet = true;
              }
            } else {
              throw new AssertionError(
                  "unknown var type: " + varNode + " @" + varNode.getSourceLocation());
            }
          }
        }

        // If we inlined or deleted any variables, then we have created new expressions that are
        // possible to simplify and that simplification may invalidate our variable use analysis.
        // So we will reanalyze the whole template.
        // Consider:
        // {$let bar: .../}{$let foo: true/} {if $foo}{$bar}{else}{$bar}{/if}
        // on the first iteration we will inline $foo, then on the second iteration we will delete
        // the else branch and then we will inline $bar.

        // This isn't very efficient, but doing this fully incrementally will be far more complex.
      } while (simplifiedAnyLet);

      // If we are done inlining and simplifying then now we need to move the lets.
      // This is useful for a few use cases
      // 1. in jbcsrc this will shorten variable lifetimes which can have an impact on code size.
      // 2. in jbcsrc we will be able to 'inline evaluate' a let variable as long as between
      //    definition and first use there are no useful streaming operations.  By moving the let
      //    we can help that optimization trigger.
      // 3. in jssrc and idom we can aid in JSCompiler optimizations since the JSCompiler is
      //    hesitant to move variables but we need not be.
      //
      // The tricky part is telling where to place the variable.  We want to move lets into the
      // smallest possible block scope and then within that scope past all possible printing
      // operations (print/call/raw text).  It is not considered useful to move a let past another
      // let unless it is necessary to move it past a printing node. (this ensures stability).
      //
      // Of course the constraint on this is simply that we cannot move lets past their first
      // reference.  So below we find the latest node that we can move past by pseudo evaluating
      // paths to references.

      SetMultimap<LetNode, ExprHolderNode> letToReferences =
          MultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
      for (RefAndHolder refAndHolder : allRefs) {
        VarDefn defn = refAndHolder.ref.getDefnDecl();
        if (defn.kind() != VarDefn.Kind.LOCAL_VAR) {
          continue;
        }
        LocalVar local = (LocalVar) defn;
        if (local.declaringNode() instanceof LetNode) {
          LetNode letNode = (LetNode) local.declaringNode();
          letToReferences.put(letNode, refAndHolder.holder);
        }
      }
      for (LetNode letNode :
          letToReferences.keySet().stream()
              // We will iterate in source order.
              // This preserves source order stability which makes the tests easier to write.
              // Also for weird plugins like uniqueId it preserves authorial intent
              .sorted(comparing(LetNode::getSourceLocation))
              .collect(toImmutableList())) {
        List<SoyNode> commonPath = null;
        for (ExprHolderNode reference : letToReferences.get(letNode)) {
          var pathToReference = evaluationOrder(letNode, reference);
          pathToReference = trimToMovablePrefix(letNode, reference, pathToReference);
          if (commonPath == null) {
            commonPath = pathToReference;
          } else {
            commonPath = commonPrefix(commonPath, pathToReference);
          }
        }

        if (commonPath.isEmpty()) {
          continue;
        }
        // Remove first so that our index calculations below are accurate
        letNode.getParent().removeChild(letNode);
        SoyNode insertAfter = Iterables.getLast(commonPath);
        @SuppressWarnings("unchecked")
        ParentSoyNode<SoyNode> insertAfterParent = (ParentSoyNode<SoyNode>) insertAfter.getParent();
        int index = insertAfterParent.getChildIndex(insertAfter);
        insertAfterParent.addChild(index + 1, letNode);
      }
    }

    /**
     * Returns a collection of all VarRefs and the ExprHolderNodes that own them in the given
     * Template
     */
    private List<RefAndHolder> getAllRefs(TemplateNode template) {
      List<RefAndHolder> refs = new ArrayList<>();
      SoyTreeUtils.allNodesOfType(template, ExprHolderNode.class)
          .forEach(
              holder ->
                  holder.getExprList().stream()
                      .flatMap(root -> SoyTreeUtils.allNodesOfType(root, VarRefNode.class))
                      .forEach(ref -> refs.add(new RefAndHolder(ref, holder))));
      return refs;
    }

    /**
     * Conditionally inlines the definition into the reference and removes the definition from the
     * AST
     */
    private boolean maybeInline(LetValueNode definition, VarRefNode ref, ExprHolderNode holder) {
      if (!isTrivialDefinition(definition.getExpr().getRoot())
          && isInLoop(definition, ref, holder)) {
        return false;
      }
      // perform the inlining
      ref.getParent().replaceChild(ref, definition.getExpr().getRoot());
      definition.getParent().removeChild(definition);
      return true;
    }

    /**
     * Returns true if the expression is so trivial that we can move it inside a loop with no
     * expected performance consequences. This generally means the value should be trivial to
     * construct.
     */
    private boolean isTrivialDefinition(ExprNode expr) {
      if (expr instanceof PrimitiveNode) {
        // number, string, boolean, null
        return true;
      }
      // css and xid are common special cases.  They are compiled to trivial hash
      if (expr instanceof FunctionNode) {
        FunctionNode functionNode = (FunctionNode) expr;
        if (functionNode.getSoyFunction() instanceof BuiltinFunction) {
          switch ((BuiltinFunction) functionNode.getSoyFunction()) {
              // These 2 are glorified strings
            case XID:
            case CSS:
              return true;
            default:
              // fall-through
          }
        }
      }
      return false;
    }

    /**
     * Returns true if the var ref node is inside a loop construct that the definition isn't also
     * in.
     */
    private boolean isInLoop(LetValueNode definition, VarRefNode var, ExprHolderNode holder) {
      checkNotNull(definition);
      // if the reference is in a list comprehension.
      if (var.getNearestAncestor(ListComprehensionNode.class) != null) {
        return true;
      }

      // if the expression is inside a loop and the definition isn't also inside the loop.
      // so as long as their nearest ancestor loops are different, we know ref must be in an inner
      // loop
      return !Objects.equal(
          holder.getNearestAncestor(ForNonemptyNode.class),
          definition.getNearestAncestor(ForNonemptyNode.class));
    }

    @Override
    protected void visitCallBasicNode(CallBasicNode node) {
      super.visitCallBasicNode(node);
      mergeBindAndCall(node, nodeIdGen);
    }

    @Override
    protected void visitPrintNode(PrintNode node) {

      super.visitPrintNode(node);
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
        if (parent instanceof HtmlAttributeNode) {
          // We assume HtmlAttributeNodes has at least one child node in many places. A RawTextNode
          // with empty content will also get removed by CombineConsecutiveRawTextNodesPass.
          return;
        }
        parent.removeChild(node);
      } else {
        parent.replaceChild(
            node, new RawTextNode(nodeIdGen.genId(), string, node.getSourceLocation()));
      }
    }

    @Override
    protected void visitIfNode(IfNode node) {

      // Recurse.
      super.visitIfNode(node);

      // For each IfCondNode child:
      // (a) If the condition is constant true: Replace the child with an IfElseNode and remove all
      //     children after it, if any. Can stop processing after doing this, because the new
      //     IfElseNode is now the last child.
      // (b) If the condition is constant false: Remove the child.
      var children = Lists.newArrayList(node.getChildren()); // make a copy to enable mutations
      for (int condIndex = 0; condIndex < children.size(); condIndex++) {
        SoyNode child = children.get(condIndex);
        if (child instanceof IfCondNode) {
          IfCondNode condNode = (IfCondNode) child;

          @Nullable SoyValue condExpr = getConstantOrNull(condNode.getExpr());
          if (condExpr != null) {
            // can statically evaluate the condition

            if (condExpr.coerceToBoolean()) {
              // ------ Constant true. ------
              // Remove all children after this child.
              for (int i = node.numChildren() - 1; i > condIndex; i--) {
                node.removeChild(i);
              }
              // Replace this child with a new IfElseNode.
              IfElseNode newElseNode =
                  new IfElseNode(
                      nodeIdGen.genId(),
                      condNode.getSourceLocation(),
                      condNode.getOpenTagLocation());
              newElseNode.addChildren(condNode.getChildren());
              node.replaceChild(condIndex, newElseNode);
              // Stop processing.
              break;

            } else {
              // ------ Constant false. ------
              node.removeChild(condNode);
              children = Lists.newArrayList(node.getChildren());
              condIndex--;
            }
          } else {
            if (condNode.numChildren() == 0) {
              // If we have no children, then we can just modify the _next_ nodes condition
              // expression to include the negation of ours.
              node.removeChild(condNode);
              children = Lists.newArrayList(node.getChildren());
              int nextNodeIndex = condIndex;
              condIndex--;
              if (nextNodeIndex < children.size()) {
                var negation =
                    new NotOpNode(SourceLocation.UNKNOWN, SourceLocation.UNKNOWN, Operator.NOT);
                negation.addChild(condNode.getExpr().getRoot());
                negation.setType(BoolType.getInstance());
                if (nextNodeIndex == children.size() - 1) {
                  // If we are the second to last case we can simplify by inlining our condition in
                  // the next one
                  SoyNode next = children.get(nextNodeIndex);

                  if (next instanceof IfCondNode) {
                    IfCondNode nextCondNode = (IfCondNode) next;
                    var rootCondition = nextCondNode.getExpr();
                    var andNode = new AmpAmpOpNode(SourceLocation.UNKNOWN, SourceLocation.UNKNOWN);
                    andNode.addChild(negation);
                    andNode.addChild(rootCondition.getRoot());
                    andNode.setType(BoolType.getInstance());
                    rootCondition.addChild(andNode);
                    rootCondition.setType(BoolType.getInstance());
                  } else {
                    IfElseNode nextElseNode = (IfElseNode) next;
                    IfCondNode newCondNode =
                        new IfCondNode(
                            nextElseNode.getId(),
                            nextElseNode.getSourceLocation(),
                            nextElseNode.getOpenTagLocation(),
                            "elseif",
                            negation);
                    newCondNode.getExpr().setType(BoolType.getInstance());
                    newCondNode.addChildren(nextElseNode.getChildren());
                    node.replaceChild(nextElseNode, newCondNode);
                    children.set(nextNodeIndex, newCondNode);
                  }
                } else {
                  // if there are multiple subsequent nodes then we need to factor out the condition
                  // {if not cond}{if remaining conditions}...{/if}{/if}
                  IfNode newParentIf = new IfNode(nodeIdGen.genId(), condNode.getSourceLocation());
                  newParentIf.setHtmlContext(node.getHtmlContext());
                  if (nextNodeIndex == 0) {
                    IfCondNode newParentCondNode =
                        new IfCondNode(
                            condNode.getId(),
                            condNode.getSourceLocation(),
                            condNode.getOpenTagLocation(),
                            "if",
                            negation);
                    newParentCondNode.getExpr().setType(BoolType.getInstance());
                    newParentIf.addChild(newParentCondNode);
                    // surround the current node with a new if
                    node.getParent().replaceChild(node, newParentIf);
                    newParentCondNode.addChild(node);
                  } else {
                    // If we weren't the first one then we need to add an elseif
                    IfCondNode elseIf =
                        new IfCondNode(
                            nodeIdGen.genId(),
                            condNode.getSourceLocation(),
                            condNode.getOpenTagLocation(),
                            "elseif",
                            negation);
                    elseIf.getExpr().setType(BoolType.getInstance());
                    elseIf.addChild(newParentIf);
                    node.addChild(nextNodeIndex, elseIf);
                    newParentIf.addChildren(children.subList(nextNodeIndex, children.size()));
                    // recurse to process the remaining children
                    visitIfNode(newParentIf);
                    // break out of the loop... we're done
                    break;
                  }
                }
              } else {
                // If we are the last case, then we can do nothing
              }
            }
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
        replaceNodeWithList(node, node.getChild(0).getChildren());
      }
    }

    @Override
    protected void visitSwitchNode(SwitchNode node) {

      // Recurse.
      super.visitSwitchNode(node);

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
                new SwitchDefaultNode(
                    nodeIdGen.genId(), caseNode.getSourceLocation(), caseNode.getOpenTagLocation());
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
        replaceNodeWithList(node, node.getChild(0).getChildren());
      }
    }

    @Override
    protected void visitCallParamContentNode(CallParamContentNode node) {
      // Recurse.
      super.visitCallParamContentNode(node);

      ExprNode asExpression = rewriteContentNodeAsExpression(node);
      if (asExpression != null) {
        CallParamValueNode valueNode =
            new CallParamValueNode(
                node.getId(), node.getSourceLocation(), node.getKey(), asExpression);
        node.getParent().replaceChild(node, valueNode);
      }
    }

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      // Recurse.
      super.visitLetContentNode(node);

      ExprNode asExpression = rewriteContentNodeAsExpression(node);
      if (asExpression != null) {
        LetValueNode valueNode =
            new LetValueNode(
                node.getId(),
                node.getSourceLocation(),
                node.getVarRefName(),
                node.getVar().nameLocation(),
                asExpression);
        valueNode.getVar().setType(node.getVar().type());
        node.getParent().replaceChild(node, valueNode);
        varDefnReplacements.put(node.getVar(), valueNode.getVar());
      }
    }

    private boolean containsLoggingFunction(RenderUnitNode node) {
      return SoyTreeUtils.allNodesOfType(node, FunctionNode.class)
          .anyMatch(n -> n.getSoyFunction() instanceof LoggingFunction);
    }

    @Nullable
    private ExprNode rewriteContentNodeAsExpression(RenderUnitNode renderUnitNode) {
      if (renderUnitNode.getContentKind() != SanitizedContentKind.TEXT) {
        return null;
      }
      // Logging functions don't work properly unless they are a direct child of a PrintNode. So,
      // any content node containing a logging function cannot be rewritten to an expression.
      if (containsLoggingFunction(renderUnitNode)) {
        return null;
      }
      // collect as list and then concat at the end.  Adding a node as a child of the PlusOpNode
      // will remove it from its old parent, we don't want to do that if we aren't going to replace
      // everything.
      List<ExprNode> newExprs = new ArrayList<>(renderUnitNode.numChildren());
      for (SoyNode child : renderUnitNode.getChildren()) {
        if (child.getKind() == SoyNode.Kind.PRINT_NODE) {
          PrintNode print = (PrintNode) child;
          // we can't handle print directives, except |text
          // all |text does is ensure that we coerce to a string, which is equivallent to "" + expr
          // which is exactly the kind of expression we are building here.
          if (print.numChildren() == 0
              || (print.numChildren() == 1
                  && print.getChild(0).getPrintDirective().getName().equals("|text"))) {
            newExprs.add(print.getExpr().getRoot());
          } else {
            return null;
          }
        } else if (child.getKind() == SoyNode.Kind.RAW_TEXT_NODE) {
          newExprs.add(
              new StringNode(
                  ((RawTextNode) child).getRawText(),
                  QuoteStyle.SINGLE,
                  child.getSourceLocation()));

        } else {
          return null;
        }
      }
      // Start with an empty string, to ensure this always evaluates to a string.
      ExprNode result;
      // tiny optimization, if the first node is a string literal, we don't need to concat with the
      // empty string.
      if (newExprs.size() >= 1 && newExprs.get(0) instanceof StringNode) {
        result = newExprs.get(0);
        newExprs = newExprs.subList(1, newExprs.size());
      } else {
        result = new StringNode("", QuoteStyle.SINGLE, renderUnitNode.getSourceLocation());
      }
      for (ExprNode expr : newExprs) {
        PlusOpNode op =
            new PlusOpNode(
                result.getSourceLocation().extend(expr.getSourceLocation()),
                /* operatorLocation= */ SourceLocation.UNKNOWN);
        op.addChild(result);
        op.addChild(expr);
        op.setType(StringType.getInstance());
        result = op;
      }
      return result;
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
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  private static boolean isConstant(@Nullable ExprRootNode exprRoot) {
    return exprRoot != null && SimplifyExprVisitor.isConstant(exprRoot.getRoot());
  }

  @Nullable
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

  /**
   * If {@code node} is a call on a bound template, merges the bound params and the call params and
   * modifies the node to be a single call.
   */
  public static void mergeBindAndCall(CallBasicNode node, IdGenerator nodeIdGen) {
    ExprNode calleeRoot = node.getCalleeExpr().getRoot();

    // Simplify call(bind(args1), args2) to call(args1+args2).
    if (calleeRoot.getKind() == Kind.METHOD_CALL_NODE
        && ((MethodCallNode) calleeRoot).getSoyMethod() == BuiltinMethod.BIND) {
      MethodCallNode methodCallNode = (MethodCallNode) calleeRoot;
      if (methodCallNode.numParams() != 1) {
        return;
      }
      RecordLiteralNode record = (RecordLiteralNode) methodCallNode.getParam(0);
      ExprNode bindCallee = methodCallNode.getBaseExprChild();
      node.getCalleeExpr().replaceChild(calleeRoot, bindCallee);
      node.getCalleeExpr().setType(bindCallee.getType());

      List<ExprNode> children = new ArrayList<>(record.getChildren());
      for (int i = 0; i < children.size(); i++) {
        Identifier key = record.getKey(i);
        ExprNode value = children.get(i);
        SourceLocation loc = key.location();
        if (loc.isBefore(value.getSourceLocation())) {
          loc = loc.extend(value.getSourceLocation());
        }
        CallParamValueNode paramNode = new CallParamValueNode(nodeIdGen.genId(), loc, key, value);
        node.addChild(i, paramNode);
      }
    }
  }

  /**
   * Trims an evaluation order path to only the nodes we can move past.
   *
   * <ul>
   *   <li>We cannot move into a loop
   *   <li>We cannot move past the HtmlOpenTagNode of a node with a skip child. Idom will generate a
   *       lambda for these that breaks block scoping. See b/299620882
   *   <li>We cannot move past a node that is not in a valid let position
   *   <li>We cannot move past a node that is a parent of the `ref`
   * </ul>
   */
  static ImmutableList<SoyNode> trimToMovablePrefix(
      LetNode from, ExprHolderNode ref, List<SoyNode> path) {

    path = new ArrayList<>(path); // ensure mutable
    for (int i = 0; i < path.size(); i++) {
      var node = path.get(i);
      if (node instanceof ForNode) {
        return ImmutableList.copyOf(path.subList(0, i));
      }
      if (node instanceof HtmlOpenTagNode
          && ((HtmlOpenTagNode) node).getChildren().stream().anyMatch(n -> n instanceof SkipNode)) {
        return ImmutableList.copyOf(path.subList(0, i));
      }
      if (node instanceof SkipNode) {
        HtmlOpenTagNode skipParent = (HtmlOpenTagNode) node.getParent();
        int index = path.indexOf(skipParent);
        index = index == -1 ? 0 : index;
        return ImmutableList.copyOf(path.subList(0, index));
      }
      // Filter to only the nodes where the let can be a sibling and exclude nodes that are a direct
      // ancestor of the reference, these show up in the path since evaluation order is a prefix.
      if (!isValidBlockNodeForLet(node.getParent()) || SoyTreeUtils.isDescendantOf(ref, node)) {
        path.remove(i);
        i--;
      }
    }
    return ImmutableList.copyOf(path);
  }

  /** Return a list of the nodes that will execute between `from` and `ref`, not including `ref`. */
  static List<SoyNode> evaluationOrder(LetNode from, ExprHolderNode ref) {
    int start = from.getParent().getChildIndex(from);
    List<SoyNode> children = new ArrayList<>();
    try {
      doEvaluationOrder(from.getParent(), start + 1, ref, children);
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "error while looking for: "
              + ref
              + " a reference to: "
              + from
              + ":\npath: "
              + children
              + "\n\n:"
              + from.getParent().toSourceString(),
          e);
    }
    return children;
  }

  private static void doEvaluationOrder(
      ParentSoyNode<?> from, int index, ExprHolderNode ref, List<SoyNode> children) {
    for (; index < from.numChildren(); index++) {
      var next = from.getChild(index);
      if (next == ref) {
        return;
      }
      children.add(next);
      if (SoyTreeUtils.isDescendantOf(ref, next)) {
        doEvaluationOrder((ParentSoyNode<?>) next, 0, ref, children);
        return;
      }
    }
    // If we didn't find a parent of the ref or the ref, we need to go up
    var parent = from.getParent();
    if (parent instanceof SoyFileNode) {
      throw new VerifyException("too far");
    }
    doEvaluationOrder(parent, parent.getChildIndex(from) + 1, ref, children);
  }

  /**
   * Returns the common prefix of the two lists.
   *
   * <p>The returned list will be a `sublist` of `a` of the two inputs.
   */
  private static <T> List<T> commonPrefix(List<T> a, List<T> b) {
    // trim common path to the shared prefix
    if (a.size() > b.size()) {
      a = a.subList(0, b.size());
    }
    for (int i = 0; i < a.size(); i++) {
      if (!a.get(i).equals(b.get(i))) {
        a = a.subList(0, i);
        break;
      }
    }
    return a;
  }
}
