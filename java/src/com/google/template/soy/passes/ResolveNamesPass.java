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


import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.ListComprehensionNode;
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
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.soytree.defn.UndeclaredVar;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Visitor which resolves all variable and parameter references to point to the corresponding
 * declaration object.
 *
 */
public final class ResolveNamesPass implements CompilerFilePass {
  private static final SoyErrorKind GLOBAL_MATCHES_VARIABLE =
      SoyErrorKind.of(
          "Found global reference aliasing a local variable ''{0}'', did you mean ''${0}''?");

  private static final SoyErrorKind VARIABLE_ALREADY_DEFINED =
      SoyErrorKind.of("Variable ''${0}'' already defined{1}.");

  /**
   * Manages the set of active variable names.
   *
   * <p>Ensures that no two active variabes have the same name
   */
  private final class LocalVariables {
    private final Deque<Map<String, VarDefn>> currentScope = new ArrayDeque<>();

    /**
     * Enters a new scope. Variables {@link #define defined} will have a lifetime that extends until
     * a matching call to {@link #exitScope()}.
     */
    void enterScope() {
      currentScope.push(new LinkedHashMap<>());
    }

    /** Exits the current scope. */
    void exitScope() {
      currentScope.pop();
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

    /** Defines a variable. */
    boolean define(VarDefn defn, Node definingNode) {
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
      return true;
    }
  }

  /** Scope for injected params. */
  private LocalVariables localVariables;

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
      ResolveNamesExprVisitor exprVisitor = new ResolveNamesExprVisitor();
      // Add all header params to the param scope.
      for (TemplateHeaderVarDefn param : node.getHeaderParams()) {
        if (param.defaultValue() != null) {
          exprVisitor.exec(param.defaultValue());
        }
        localVariables.define(param, node);
      }

      visitSoyNode(node);
      localVariables.exitScope();

      localVariables = null;
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
      localVariables.enterScope();
      visitChildren(node);
      localVariables.exitScope();
      // TODO(lukes): should local variables claim their name prior to the scope?  we wouldn't want
      // lookups to succeed but its weird that you could redefine this variable.  See
      // ResolveNamesPassTest.testLetContentNameLifetime() for a demonstration.
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
      case COMPREHENSION_VAR:
        return Optional.of(
            ((ListComprehensionNode.ComprehensionVarDefn) varDefn)
                .declaringNode()
                .getSourceLocation());
      case STATE:
        return Optional.of(((TemplateStateVar) varDefn).nameLocation());
      case UNDECLARED:
        return Optional.empty();
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
    protected void visitListComprehensionNode(ListComprehensionNode node) {
      // Visit the list expr.
      visit(node.getListExpr());

      // Define the list item variable.
      localVariables.enterScope();
      localVariables.define(node.getListIterVar(), node);

      // Now we can visit the list item map and filter expressions.
      if (node.getFilterExpr() != null) {
        visit(node.getFilterExpr());
      }
      visit(node.getListItemTransformExpr());
      localVariables.exitScope();
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
      VarDefn varDefn = localVariables.lookup(varRef.getName());
      if (varDefn == null) {
        // Undeclared vars are flagged as errors in the CheckTemplateHeaderVarsPass.
        varDefn = new UndeclaredVar(varRef.getName(), varRef.getSourceLocation());
      }
      varRef.setDefn(varDefn);
    }
  }
}
