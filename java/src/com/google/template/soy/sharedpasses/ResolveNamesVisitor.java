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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
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
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.UndeclaredVar;

import java.util.Map;

/**
 * Visitor which resolves all variable and parameter references to point to
 * the corresponding declaration object.
 *
 * @author Talin
 */
public final class ResolveNamesVisitor extends AbstractSoyNodeVisitor<Void> {


  /**
   * Represents a naming scope in which variables are defined.
   */
  public static class Scope {
    private final Scope enclosingScope;
    private final Map<String, VarDefn> varDefnMap = Maps.newHashMap();

    /**
     * Construct a Scope.
     * @param enclosingScope The outer scope which encloses this one.
     */
    public Scope(Scope enclosingScope) {
      this.enclosingScope = enclosingScope;
    }

    /**
     * Construct a Scope and initialize it with one variable.
     * @param enclosingScope The outer scope which encloses this one.
     */
    public Scope(Scope enclosingScope, VarDefn varDefn) {
      this.enclosingScope = enclosingScope;
      define(varDefn);
    }

    /**
     * Look up a variable name in this scope and all enclosing scopes.
     * @param name The name of the variable to look for.
     * @return The variable, or {@code null} if no such variable exists.
     */
    public VarDefn lookup(String name) {
      // Yes we could do this with recursion but why waste stack?
      for (Scope searchScope = this; searchScope != null;
          searchScope = searchScope.enclosingScope) {
        VarDefn var = searchScope.varDefnMap.get(name);
        if (var != null) {
          return var;
        }
      }
      return null;
    }

    /**
     * Define a variable within this scope.
     * @param varDefn The variable to define.
     */
    public void define(VarDefn varDefn) {
      varDefnMap.put(varDefn.name(), varDefn);
    }
  }


  /** The current innermost scope. */
  private Scope currentScope = null;

  /** Implicit parameter scope (V1 templates only). */
  private Scope paramScope;

  /** Scope for injected params. */
  private Scope injectedParamScope;

  /** User-declared syntax version. */
  private final SyntaxVersion declaredSyntaxVersion;


  public ResolveNamesVisitor(SyntaxVersion declaredSyntaxVersion) {
    this.paramScope = null;
    this.injectedParamScope = null;
    this.declaredSyntaxVersion = declaredSyntaxVersion;
  }


  @Override protected void visitTemplateNode(TemplateNode node) {
    Scope savedScope = currentScope;
    // Create a scope for all parameters.
    currentScope = new Scope(savedScope);
    paramScope = currentScope;
    if (node.getParams() != null) {
      for (TemplateParam param : node.getParams()) {
        currentScope.define(param);
      }
    }

    visitSoyNode(node);
  }


  @Override protected void visitPrintNode(PrintNode node) {
    visitSoyNode(node);
  }


  @Override protected void visitLetValueNode(LetValueNode node) {
    visitSoyNode(node);

    // Now after the let-block is complete, define the new variable
    // in the current scope.
    currentScope.define(node.getVar());
  }


  @Override protected void visitLetContentNode(LetContentNode node) {
    visitSoyNode(node);

    // Now after the let-block is complete, define the new variable in the current scope.
    currentScope.define(node.getVar());
  }


  @Override protected void visitForNode(ForNode node) {
    // Visit the range expressions.
    visitExpressions(node);

    // Create a scope to hold the iteration variable
    Scope savedScope = currentScope;
    currentScope = new Scope(savedScope, node.getVar());

    // Visit the node body
    visitChildren(node);
    currentScope = savedScope;
  }


  @Override protected void visitForeachNonemptyNode(ForeachNonemptyNode node) {
    // Visit the foreach iterator expression
    visitExpressions(node.getParent());

    // Create a scope to hold the iteration variable
    Scope savedScope = currentScope;
    currentScope = new Scope(savedScope, node.getVar());

    // Visit the node body
    visitChildren(node);
    currentScope = savedScope;
  }


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ExprHolderNode) {
      visitExpressions((ExprHolderNode) node);
    }

    if (node instanceof ParentSoyNode<?>) {
      if (node instanceof BlockNode) {
        Scope savedScope = currentScope;
        currentScope = new Scope(savedScope);
        visitChildren((BlockNode) node);
        currentScope = savedScope;
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
        if (injectedParamScope == null) {
          injectedParamScope = new Scope(null);
        }
        VarDefn varDefn = injectedParamScope.lookup(varRef.getName());
        if (varDefn == null) {
          // Manufacture an injected variable.
          varDefn = new InjectedParam(varRef.getName());
          injectedParamScope.define(varDefn);
        }
        varRef.setDefn(varDefn);
        return;
      }
      VarDefn varDefn = currentScope.lookup(varRef.getName());
      if (varDefn == null) {
        // TODO: Disallow untyped variables starting with some future syntax version.
        if (declaredSyntaxVersion.num >= SyntaxVersion.V9_9.num) {
          throw createExceptionForInvalidExpr("Undefined variable: " + varRef.getName());
        } else {
          // If this is a legacy template, and we can't find a definition for the variable,
          // then create an undefined variable as a placeholder.
          varDefn = new UndeclaredVar(varRef.getName());
          paramScope.define(varDefn);
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
