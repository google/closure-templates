/*
 * Copyright 2013 Google Inc.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.InjectedParam;
import com.google.template.soy.soytree.defn.LoopVar;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.UndeclaredVar;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Visitor which resolves all variable and parameter references to point to
 * the corresponding declaration object.
 *
 */
public final class ResolveNamesVisitor extends AbstractSoyNodeVisitor<Void> {

  /**
   * A data structure that assigns a unique (small) integer to all local variable definitions that
   * are active within a given lexical scope.
   *
   * <p>A 'slot' is a small integer that is assigned to a {@link VarDefn} such that at any given
   * point of execution while that variable could be referenced there is only one variable with that
   * index.
   */
  private static final class LocalVariables {
    private final BitSet availableSlots = new BitSet();
    private final Deque<ListMultimap<String, VarDefn>> currentScope = new ArrayDeque<>();
    private final BitSet slotsToRelease = new BitSet();

    /** Tracks the next unused slot to claim. */
    private int nextSlotToClaim = 0;

    /**
     * A counter that tracks when to release the {@link #slotsToRelease} set.
     *
     * <p>We add {@link #slotsToRelease} to {@link #availableSlots} only when exiting a scope if
     * this value == 0.
     */
    private int delayReleaseClaims = 0;

    /**
     * Enters a new scope.  Variables {@link #define defined} will have a lifetime that extends
     * until a matching call to {@link #exitScope()}.
     */
    void enterScope() {
      currentScope.push(ArrayListMultimap.<String, VarDefn>create());
    }

    /**
     * Enters a new scope.
     *
     * <p>Variables defined in a lazy scope have a lifetime that extends to the matching
     * {@link #exitLazyScope()} call, but the variable slots reserved have their lifetimes extended
     * until the parent scope closes.
     */
    void enterLazyScope() {
      delayReleaseClaims++;
      enterScope();
    }

    /**
     * Exits the current scope.
     */
    void exitLazyScope() {
      checkState(delayReleaseClaims > 0, "Exiting a lazy scope when we aren't in one");
      exitScope();
      delayReleaseClaims--;
    }

    /**
     * Exits the current lazy scope.
     *
     * <p>This releases all the variable indices associated with the variables defined in this
     * frame so that they can be reused.
     */
    void exitScope() {
      ListMultimap<String, VarDefn> variablesGoingOutOfScope = currentScope.pop();
      for (VarDefn var : variablesGoingOutOfScope.values()) {
        if (var instanceof LoopVar) {
          LoopVar loopVar = (LoopVar) var;
          slotsToRelease.set(loopVar.currentLoopIndexIndex());
          slotsToRelease.set(loopVar.isLastIteratorIndex());
        }
        slotsToRelease.set(var.localVariableIndex());
      }
      if (delayReleaseClaims == 0) {
        availableSlots.or(slotsToRelease);
        slotsToRelease.clear();
      }
    }

    /**
     * Returns the {@link VarDefn} associated with the given name by searching through the current
     * scope and all parent scopes.
     */
    VarDefn lookup(String name) {
      for (ListMultimap<String, VarDefn> scope : currentScope) {
        // Get last to handle the case where multiple variables with the same name are active.
        // hopefully this will be a temporary situation.
        VarDefn defn = Iterables.getLast(scope.get(name), null);
        if (defn != null) {
          return defn;
        }
      }
      return null;
    }

    /**
     * Defines a {@link LoopVar}. Unlike normal local variables and params loop variables get 2
     * extra implicit local variables for tracking the current index and whether or not we are at
     * the last index.
     */
    void define(LoopVar defn) {
      defn.setExtraLoopIndices(claimSlot(), claimSlot());
      define((VarDefn) defn);
    }

    /** Defines a variable. */
    void define(VarDefn defn) {
      currentScope.peek().put(defn.name(), defn);
      defn.setLocalVariableIndex(claimSlot());
    }

    /**
     * Returns the smallest available local variable slot or claims a new one if there is none
     * available.
     */
    private int claimSlot() {
      int nextSetBit = availableSlots.nextSetBit(0);
      int slotToUse;
      if (nextSetBit != -1) {
        slotToUse = nextSetBit;
        availableSlots.clear(nextSetBit);
      } else {
        slotToUse = nextSlotToClaim;
        nextSlotToClaim++;
      }
      return slotToUse;
    }

    void verify() {
      checkState(delayReleaseClaims == 0, "%s lazy scope(s) are still active", delayReleaseClaims);
      checkState(slotsToRelease.isEmpty(), "%s slots are waiting to be released", slotsToRelease);
      BitSet unavailableSlots = new BitSet(nextSlotToClaim);
      unavailableSlots.set(0, nextSlotToClaim);
      // now the only bits on will be the ones where available slots has '0'.
      unavailableSlots.xor(availableSlots);
      checkState(unavailableSlots.isEmpty(),
          "Expected all slots to be available: %s", unavailableSlots);
    }
  }

  /** Scope for injected params. */
  private LocalVariables localVariables;
  private Map<String, InjectedParam> ijParams;

  /** User-declared syntax version. */
  private final SyntaxVersion declaredSyntaxVersion;

  public ResolveNamesVisitor(SyntaxVersion declaredSyntaxVersion) {
    this.declaredSyntaxVersion = declaredSyntaxVersion;
  }

  @Override protected void visitTemplateNode(TemplateNode node) {
    // Create a scope for all parameters.
    localVariables = new LocalVariables();
    localVariables.enterScope();
    ijParams = new HashMap<>();

    // Add both injected and regular params to the param scope.
    for (TemplateParam param : node.getAllParams()) {
      localVariables.define(param);
    }

    visitSoyNode(node);
    localVariables.exitScope();
    localVariables.verify();
    node.setMaxLocalVariableTableSize(localVariables.nextSlotToClaim);

    localVariables = null;
    ijParams = null;
  }

  @Override protected void visitPrintNode(PrintNode node) {
    visitSoyNode(node);
  }

  @Override protected void visitLetValueNode(LetValueNode node) {
    visitExpressions(node);
    // Now after the let-block is complete, define the new variable
    // in the current scope.
    localVariables.define(node.getVar());
  }

  @Override protected void visitLetContentNode(LetContentNode node) {
    // LetContent nodes may reserve slots in their sub expressions, but due to lazy evaluation will
    // not use them immediately, so we can't release the slots until the parent scope is gone.
    // however the variable lifetime should be limited
    localVariables.enterLazyScope();
    visitChildren(node);
    localVariables.exitLazyScope();
    localVariables.define(node.getVar());
  }

  @Override protected void visitForNode(ForNode node) {
    // Visit the range expressions.
    visitExpressions(node);

    localVariables.enterScope();
    localVariables.define(node.getVar());

    // Visit the node body
    visitChildren(node);
    localVariables.exitScope();
  }

  @Override protected void visitForeachNonemptyNode(ForeachNonemptyNode node) {
    // Visit the foreach iterator expression
    visitExpressions(node.getParent());

    // Create a scope to hold the iteration variable
    localVariables.enterScope();
    localVariables.define(node.getVar());

    // Visit the node body
    visitChildren(node);
    localVariables.exitScope();
  }

  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ExprHolderNode) {
      visitExpressions((ExprHolderNode) node);
    }

    if (node instanceof ParentSoyNode<?>) {
      if (node instanceof BlockNode) {
        localVariables.enterScope();
        visitChildren((BlockNode) node);
        localVariables.exitScope();
      } else {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }

  private void visitExpressions(ExprHolderNode node) {
    ResolveNamesExprVisitor exprVisitor = new ResolveNamesExprVisitor(node);
    for (ExprUnion exprUnion : node.getAllExprUnions()) {
      if (exprUnion.getExpr() != null) {
        exprVisitor.exec(exprUnion.getExpr());
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Expr visitor.

  /**
   * Visitor which resolves all variable and parameter references in expressions
   * to point to the corresponding declaration object.
   */
  private class ResolveNamesExprVisitor extends AbstractExprNodeVisitor<Void> {

    /** SoyNode owning the expression; Used for error reporting. */
    private final ExprHolderNode owningSoyNode;

    /** The root node of the current expression being visited (during an exec call). */
    private ExprRootNode<?> currExprRootNode;

    /**
     * Construct a new ResolveNamesExprVisitor.
     * @param owningSoyNode The current error context, in other words the SoyNode owning the
     *     expression being scanned.
     */
    public ResolveNamesExprVisitor(ExprHolderNode owningSoyNode) {
      this.owningSoyNode = owningSoyNode;
    }

    @Override public Void exec(ExprNode node) {
      Preconditions.checkArgument(node instanceof ExprRootNode<?>);
      this.currExprRootNode = (ExprRootNode<?>) node;
      visit(node);
      this.currExprRootNode = null;
      return null;
    }

    @Override protected void visit(ExprNode node) {
      super.visit(node);
    }

    @Override protected void visitExprRootNode(ExprRootNode<?> node) {
      visitChildren(node);
    }

    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }

    @Override protected void visitVarRefNode(VarRefNode varRef) {
      if (varRef.isInjected()) {
        InjectedParam ijParam = ijParams.get(varRef.getName());
        if (ijParam == null) {
          ijParam = new InjectedParam(varRef.getName());
          ijParams.put(varRef.getName(), ijParam);
        }
        varRef.setDefn(ijParam);
        return;
      }
      VarDefn varDefn = localVariables.lookup(varRef.getName());
      if (varDefn == null) {
        // TODO: Disallow untyped variables starting with some future syntax version.
        if (declaredSyntaxVersion.num >= SyntaxVersion.V9_9.num) {
          throw createExceptionForInvalidExpr("Undefined variable: " + varRef.getName());
        } else {
          // TODO(lukes): eliminate this case.  It is common in tests because the tests are too lazy
          // to declare params.  I'm not sure how common it is in general
          // If this is a legacy template, and we can't find a definition for the variable,
          // then create an undefined variable as a placeholder.
          // Undeclared vars behave more like ijs, and do not get registered in the local variable
          // table.
          varDefn = new UndeclaredVar(varRef.getName());
        }
      }
      varRef.setDefn(varDefn);
    }

    /**
     * Private helper to create a SoySyntaxException whose error message incorporates both the
     * owningSoyNode and the currExprRootNode.
     */
    private SoySyntaxException createExceptionForInvalidExpr(String errorMsg) {
      return SoySyntaxExceptionUtils.createWithNode(
          "Invalid expression \"" + currExprRootNode.toSourceString() + "\": " + errorMsg,
          owningSoyNode);
    }
  }
}
