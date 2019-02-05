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

package com.google.template.soy.passes;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.InjectedParam;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.LoopVar;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.soytree.defn.UndeclaredVar;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Visitor which resolves all variable and parameter references to point to the corresponding
 * declaration object.
 *
 */
public final class ResolveNamesPass extends CompilerFilePass {
  private static final SoyErrorKind GLOBAL_MATCHES_VARIABLE =
      SoyErrorKind.of(
          "Found global reference aliasing a local variable ''{0}'', did you mean " + "''${0}''?");

  private static final SoyErrorKind VARIABLE_ALREADY_DEFINED =
      SoyErrorKind.of("Variable ''${0}'' already defined{1}.");

  /**
   * A data structure that assigns a unique (small) integer to all local variable definitions that
   * are active within a given lexical scope.
   *
   * <p>A 'slot' is a small integer that is assigned to a {@link VarDefn} such that at any given
   * point of execution while that variable could be referenced there is only one variable with that
   * index.
   */
  private final class LocalVariables {
    private final BitSet availableSlots = new BitSet();
    private final Deque<Map<String, VarDefn>> currentScope = new ArrayDeque<>();
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
     * Enters a new scope. Variables {@link #define defined} will have a lifetime that extends until
     * a matching call to {@link #exitScope()}.
     */
    void enterScope() {
      currentScope.push(new LinkedHashMap<String, VarDefn>());
    }

    /**
     * Enters a new scope.
     *
     * <p>Variables defined in a lazy scope have a lifetime that extends to the matching {@link
     * #exitLazyScope()} call, but the variable slots reserved have their lifetimes extended until
     * the parent scope closes.
     */
    void enterLazyScope() {
      delayReleaseClaims++;
      enterScope();
    }

    /** Exits the current scope. */
    void exitLazyScope() {
      checkState(delayReleaseClaims > 0, "Exiting a lazy scope when we aren't in one");
      exitScope();
      delayReleaseClaims--;
    }

    /**
     * Exits the current lazy scope.
     *
     * <p>This releases all the variable indices associated with the variables defined in this frame
     * so that they can be reused.
     */
    void exitScope() {
      Map<String, VarDefn> variablesGoingOutOfScope = currentScope.pop();
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
      for (Map<String, VarDefn> scope : currentScope) {
        VarDefn defn = scope.get(name);
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
    boolean define(LoopVar defn, SoyNode definingNode) {
      if (!define((VarDefn) defn, definingNode)) {
        return false;
      }
      // only allocate the extra slots if definition succeeded
      defn.setExtraLoopIndices(claimSlot(), claimSlot());
      return true;
    }

    /** Defines a variable. */
    boolean define(VarDefn defn, SoyNode definingNode) {
      // Search for the name to see if it is being redefined.
      VarDefn preexisting = lookup(defn.name());
      if (preexisting != null) {
        Optional<SourceLocation> sourceLocation = forVarDefn(preexisting);
        String location =
            sourceLocation.isPresent() ? " at line " + sourceLocation.get().getBeginLine() : "";
        errorReporter.report(
            definingNode.getSourceLocation(), VARIABLE_ALREADY_DEFINED, defn.name(), location);
        return false;
      }
      currentScope.peek().put(defn.name(), defn);
      defn.setLocalVariableIndex(claimSlot());
      return true;
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
      checkState(
          unavailableSlots.isEmpty(), "Expected all slots to be available: %s", unavailableSlots);
    }
  }

  /** Scope for injected params. */
  private LocalVariables localVariables;

  private Map<String, InjectedParam> ijParams;

  private final ErrorReporter errorReporter;

  public ResolveNamesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    new Visitor().exec(file);
  }

  private final class Visitor extends AbstractSoyNodeVisitor<Void> {
    @Override
    protected void visitTemplateNode(TemplateNode node) {
      // Create a scope for all parameters.
      localVariables = new LocalVariables();
      localVariables.enterScope();
      ijParams = new HashMap<>();

      // Add both injected and regular params to the param scope.
      for (TemplateParam param : node.getAllParams()) {
        localVariables.define(param, node);
      }
      if (node instanceof TemplateElementNode) {
        for (TemplateStateVar stateVar : ((TemplateElementNode) node).getStateVars()) {
          localVariables.define(stateVar, node);
        }
      }

      visitSoyNode(node);
      localVariables.exitScope();
      localVariables.verify();
      node.setMaxLocalVariableTableSize(localVariables.nextSlotToClaim);

      localVariables = null;
      ijParams = null;
    }

    @Override
    protected void visitPrintNode(PrintNode node) {
      visitSoyNode(node);
    }

    @Override
    protected void visitLetValueNode(LetValueNode node) {
      visitExpressions(node);
      // Now after the let-block is complete, define the new variable
      // in the current scope.
      localVariables.define(node.getVar(), node);
    }

    @Override
    protected void visitLetContentNode(LetContentNode node) {
      // LetContent nodes may reserve slots in their sub expressions, but due to lazy evaluation
      // will not use them immediately, so we can't release the slots until the parent scope is
      // gone. However the variable lifetime should be limited
      localVariables.enterLazyScope();
      visitChildren(node);
      localVariables.exitLazyScope();
      localVariables.define(node.getVar(), node);
    }

    @Override
    protected void visitForNonemptyNode(ForNonemptyNode node) {
      // Visit the foreach iterator expression
      visitExpressions(node.getParent());

      // Create a scope to hold the iteration variable
      localVariables.enterScope();
      localVariables.define(node.getVar(), node);

      // Visit the node body
      visitChildren(node);
      localVariables.exitScope();
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
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
      ResolveNamesExprVisitor exprVisitor = new ResolveNamesExprVisitor();
      for (ExprRootNode expr : node.getExprList()) {
        exprVisitor.exec(expr);
      }
    }
  }

  private static Optional<SourceLocation> forVarDefn(VarDefn varDefn) {
    switch (varDefn.kind()) {
      case PARAM:
        return Optional.of(((TemplateParam) varDefn).nameLocation());
      case LOCAL_VAR:
        return Optional.of(((LocalVar) varDefn).declaringNode().getSourceLocation());
      case STATE:
        return Optional.of(((TemplateStateVar) varDefn).nameLocation());
      case IJ_PARAM:
      case UNDECLARED:
        return Optional.absent();
    }
    throw new AssertionError(varDefn.kind());
  }

  // -----------------------------------------------------------------------------------------------
  // Expr visitor.

  /**
   * Visitor which resolves all variable and parameter references in expressions to point to the
   * corresponding declaration object.
   */
  private final class ResolveNamesExprVisitor extends AbstractExprNodeVisitor<Void> {

    @Override
    public Void exec(ExprNode node) {
      Preconditions.checkArgument(node instanceof ExprRootNode);
      visit(node);
      return null;
    }

    @Override
    protected void visitExprRootNode(ExprRootNode node) {
      visitChildren(node);
    }

    @Override
    protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }

    @Override
    protected void visitGlobalNode(GlobalNode node) {
      // Check for a typo involving a global reference.  If the author forgets the leading '$' on a
      // variable reference then it will get parsed as a global.  In some compiler configurations
      // unknown globals are not an error.  To ensure that typos are caught we check for this case
      // here.  Making 'unknown globals' an error consistently would be a better solution, though
      // even then we would probably want some typo checking like this.
      // Note.  This also makes it impossible for a global to share the same name as a local.  This
      // should be fine since global names are typically qualified strings.
      String globalName = node.getName();
      VarDefn varDefn = localVariables.lookup(globalName);
      if (varDefn != null) {
        node.suppressUnknownGlobalErrors();
        // This means that this global has the same name as an in-scope local or param.  It is
        // likely that they just forgot the leading '$'
        errorReporter.report(node.getSourceLocation(), GLOBAL_MATCHES_VARIABLE, globalName);
      }
    }

    @Override
    protected void visitVarRefNode(VarRefNode varRef) {
      if (varRef.getDefnDecl() != null) {
        // some passes (e.g. ContentSecurityPolicyNonceInjectionPass) add var refs with accurate
        // defns.
        return;
      }
      if (varRef.isDollarSignIjParameter()) {
        InjectedParam ijParam =
            ijParams.computeIfAbsent(
                varRef.getName(), k -> new InjectedParam(k, varRef.getSourceLocation()));
        varRef.setDefn(ijParam);
        return;
      }
      VarDefn varDefn = localVariables.lookup(varRef.getName());
      if (varDefn == null) {
        // this case is mostly about supporting v1 templates.  Undeclared vars for v2 templates are
        // flagged as errors in the CheckTemplateParamsPass
        varDefn = new UndeclaredVar(varRef.getName(), varRef.getSourceLocation());
      }
      varRef.setDefn(varDefn);
    }
  }
}
