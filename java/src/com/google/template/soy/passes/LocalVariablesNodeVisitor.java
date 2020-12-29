/*
 * Copyright 2020 Google Inc.
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

import static com.google.template.soy.soytree.TemplateDelegateNodeBuilder.isDeltemplateTemplateName;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.DelegatingVarDefn;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.ListComprehensionNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarDefn.Kind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Combined soy node and expression node visitor that always has an up-to-date {@link
 * LocalVariables} available at every node visit.
 */
final class LocalVariablesNodeVisitor {

  private final NodeVisitor nodeVisitor;

  public LocalVariablesNodeVisitor(NodeVisitor nodeVisitor) {
    this.nodeVisitor = nodeVisitor;
  }

  public void exec(SoyFileNode file) {
    nodeVisitor.exec(file);
  }

  /**
   * Manages the set of active variable names.
   *
   * <p>Ensures that no two active variabes have the same name
   */
  static final class LocalVariables {

    private static final SoyErrorKind VARIABLE_ALREADY_DEFINED =
        SoyErrorKind.of("{0} ''{1}'' conflicts with symbol defined at {2}.");

    private ErrorReporter errorReporter;
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
      return lookupInternal(name, defn -> defn != null && !(defn instanceof ReservedVarDefn));
    }

    private VarDefn lookupWithReserved(String name) {
      return lookupInternal(name, Objects::nonNull);
    }

    private VarDefn lookupInternal(String name, Predicate<VarDefn> filter) {
      return currentScope.stream()
          .map(scope -> scope.get(name))
          .filter(filter)
          .findFirst()
          .orElse(null);
    }

    private boolean check(VarDefn defn, Node definingNode) {
      String refName = defn.refName();
      // Search for the name to see if it is being redefined.
      VarDefn preexisting = lookupWithReserved(refName);
      if (preexisting != null) {
        if (errorReporter != null && !shouldSkipError(defn, preexisting)) {
          SourceLocation defnSourceLocation =
              defn.nameLocation() == null ? definingNode.getSourceLocation() : defn.nameLocation();
          errorReporter.report(
              defnSourceLocation,
              VARIABLE_ALREADY_DEFINED,
              englishName(defn),
              refName,
              preexisting.nameLocation().toLineColumnString());
        }
        return false;
      }
      return true;
    }

    /** Defines a variable. */
    void define(VarDefn defn, Node definingNode) {
      if (check(defn, definingNode)) {
        currentScope.peek().put(defn.refName(), defn);
      }
    }

    /**
     * Reserves the name of a variable without affecting what is returned by {@link #lookup}. For
     * use while fixing b/175405629.
     */
    void reserve(VarDefn defn, Node definingNode) {
      define(new ReservedVarDefn(defn), definingNode);
    }

    List<String> allVariablesInScope() {
      return currentScope.stream().flatMap(map -> map.keySet().stream()).collect(toList());
    }
  }

  /** Marked type for use while fixing b/175405629. */
  private static final class ReservedVarDefn extends DelegatingVarDefn {
    public ReservedVarDefn(VarDefn delegate) {
      super(delegate);
    }
  }

  abstract static class NodeVisitor extends AbstractSoyNodeVisitor<Void> {

    /** Scope for injected params. */
    private LocalVariables localVariables;

    protected abstract ExprVisitor getExprVisitor();

    @Nullable
    protected ErrorReporter getErrorReporter() {
      return null;
    }

    protected LocalVariables getLocalVariables() {
      return Preconditions.checkNotNull(localVariables);
    }

    @Override
    protected void visitSoyFileNode(SoyFileNode node) {
      // Create a scope for all parameters.
      localVariables = new LocalVariables();
      localVariables.errorReporter = getErrorReporter();
      localVariables.enterScope();
      for (ImportNode imp : node.getImports()) {
        if (imp.getImportType() == ImportType.TEMPLATE) {
          // TODO(b/175405629): As a step towards supporting templates, we verify that there are no
          // collisions here.
          for (ImportedVar var : imp.getIdentifiers()) {
            localVariables.reserve(var, imp);
          }
        } else {
          for (ImportedVar var : imp.getIdentifiers()) {
            localVariables.define(var, imp);
          }
        }
      }
      for (TemplateNode template : node.getTemplates()) {
        localVariables.reserve(template.asVarDefn(), template);
      }

      super.visitSoyFileNode(node);
      localVariables.exitScope();

      localVariables = null;
    }

    @Override
    protected void visitTemplateNode(TemplateNode node) {
      // Create a scope for all parameters.
      localVariables.enterScope();
      // Add all header params to the param scope.
      for (TemplateHeaderVarDefn param : node.getHeaderParams()) {
        if (param.defaultValue() != null) {
          getExprVisitor().exec(param.defaultValue(), localVariables);
        }
        localVariables.define(param, node);
      }

      super.visitTemplateNode(node);
      localVariables.exitScope();
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
      if (node.getIndexVar() != null) {
        localVariables.define(node.getIndexVar(), node);
      }

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
      for (ExprRootNode expr : node.getExprList()) {
        getExprVisitor().exec(expr, localVariables);
      }
    }
  }

  /** Better error messages exist for deltemplate duplicates. */
  private static boolean shouldSkipError(VarDefn defn, VarDefn preexisting) {
    return defn.kind() == Kind.TEMPLATE
        && preexisting.kind() == Kind.TEMPLATE
        && isDeltemplateTemplateName(defn.name())
        && isDeltemplateTemplateName(preexisting.name());
  }

  private static String englishName(VarDefn varDefn) {
    switch (varDefn.kind()) {
      case PARAM:
        return "Parameter";
      case STATE:
        return "State parameter";
      case IMPORT_VAR:
        return "Imported symbol";
      case LOCAL_VAR:
      case COMPREHENSION_VAR:
        return "Local variable";
      case TEMPLATE:
        return "Template name";
      case UNDECLARED:
        return "Symbol";
    }
    throw new AssertionError(varDefn.kind());
  }

  // -----------------------------------------------------------------------------------------------
  // Expr visitor.

  /**
   * Visitor which resolves all variable and parameter references in expressions to point to the
   * corresponding declaration object.
   */
  abstract static class ExprVisitor extends AbstractExprNodeVisitor<Void> {

    private LocalVariables localVariables;

    public final Void exec(ExprNode node, LocalVariables localVariables) {
      this.localVariables = localVariables;
      exec(node);
      this.localVariables = null;
      return null;
    }

    protected LocalVariables getLocalVariables() {
      return Preconditions.checkNotNull(localVariables);
    }

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

      // Define the optional index variable.
      if (node.getIndexVar() != null) {
        localVariables.define(node.getIndexVar(), node);
      }

      // Now we can visit the list item map and filter expressions.
      if (node.getFilterExpr() != null) {
        visit(node.getFilterExpr());
      }
      visit(node.getListItemTransformExpr());
      localVariables.exitScope();
    }
  }
}
