/*
 * Copyright 2022 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.types.TemplateType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A visitor that finds all mod template literals that eventually make their way into a call. */
public class DelcallAnnotationVisitor extends AbstractSoyNodeVisitor<ImmutableSet<String>> {

  /** Collects final output, a set of legacydeltemplatenamespace values of referenced templates. */
  private final ImmutableSet.Builder<String> legacyNamespaces;

  /**
   * Map of variable to legacydeltemplatenamespace values for all templates referenced in its
   * declaration.
   */
  private final Map<VarDefn, ImmutableSet<String>> varToLegacyNamespacesMap;

  /** Map of variable to other variables that are referenced in its declaration. */
  private final Map<VarDefn, ImmutableSet<VarDefn>> varToDepVarsMap;

  public DelcallAnnotationVisitor() {
    this.legacyNamespaces = ImmutableSet.builder();
    this.varToLegacyNamespacesMap = new HashMap<>();
    this.varToDepVarsMap = new HashMap<>();
  }

  @Override
  public ImmutableSet<String> exec(SoyNode node) {
    visit(node);
    return legacyNamespaces.build();
  }

  @Override
  protected void visitLetValueNode(LetValueNode node) {
    FindModTemplatesAndVars visitor = new FindModTemplatesAndVars();
    visitor.exec(node.getExpr());
    varToLegacyNamespacesMap.put(node.getVar(), visitor.getLegacyNamespaces());
    varToDepVarsMap.put(node.getVar(), visitor.getReferencedVars());
  }

  @Override
  protected void visitCallBasicNode(CallBasicNode node) {
    findReferencesLegacyNamespaces(node.getCalleeExpr());
    visitChildren(node);
  }

  @Override
  protected void visitCallParamValueNode(CallParamValueNode node) {
    findReferencesLegacyNamespaces(node.getExpr());
  }

  /** Add all mod templates transitively referenced in exprNode to the output. */
  private void findReferencesLegacyNamespaces(ExprNode exprNode) {
    FindModTemplatesAndVars visitor = new FindModTemplatesAndVars();
    visitor.exec(exprNode);
    legacyNamespaces.addAll(visitor.getLegacyNamespaces());
    visitor.getReferencedVars().forEach(this::collectVarTemplateRefs);
  }

  /**
   * For the given variable, search all transitive assignments and add referenced mod templates to
   * the output set.
   */
  private void collectVarTemplateRefs(VarDefn varName) {
    collectVarTemplateRefs(varName, new HashSet<>());
  }

  private void collectVarTemplateRefs(VarDefn varName, Set<VarDefn> visited) {
    if (!visited.add(varName)) {
      // Guard against circular references.
      return;
    }

    if (varToLegacyNamespacesMap.containsKey(varName)) {
      legacyNamespaces.addAll(varToLegacyNamespacesMap.get(varName));
    }
    if (varToDepVarsMap.containsKey(varName)) {
      varToDepVarsMap.get(varName).forEach(var -> collectVarTemplateRefs(var, visited));
    }
  }

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode) {
      visitChildren((ParentSoyNode) node);
    }
  }

  /** Finds all mod template literals and variables contained in an expression. */
  private static class FindModTemplatesAndVars extends AbstractExprNodeVisitor<Void> {

    // The legacydeltemplatenamespace values of all templates referenced.
    private final ImmutableSet.Builder<String> legacyNamespaces;
    // Defns of all variable references.
    private final ImmutableSet.Builder<VarDefn> referencedVars;

    FindModTemplatesAndVars() {
      this.legacyNamespaces = ImmutableSet.builder();
      this.referencedVars = ImmutableSet.builder();
    }

    ImmutableSet<String> getLegacyNamespaces() {
      return legacyNamespaces.build();
    }

    ImmutableSet<VarDefn> getReferencedVars() {
      return referencedVars.build();
    }

    @Override
    protected void visitTemplateLiteralNode(TemplateLiteralNode node) {
      if (node.getType() instanceof TemplateType
          && ((TemplateType) node.getType()).isModifiable()) {
        String legacyDeltemplateNamespace =
            ((TemplateType) node.getType()).getLegacyDeltemplateNamespace();
        if (!legacyDeltemplateNamespace.isEmpty()) {
          legacyNamespaces.add(legacyDeltemplateNamespace);
        }
      }
    }

    @Override
    protected void visitConditionalOpNode(ConditionalOpNode node) {
      // Skip first child, which is the boolean expression.
      visit(node.getChild(1));
      visit(node.getChild(2));
    }

    @Override
    protected void visitVarRefNode(VarRefNode node) {
      referencedVars.add(node.getDefnDecl());
    }

    @Override
    protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }
  }
}
