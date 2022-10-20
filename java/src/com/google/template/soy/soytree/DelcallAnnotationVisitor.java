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
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.types.TemplateType;
import java.util.HashMap;
import java.util.Map;

/** A visitor that finds all mod template literals that eventually make their way into a call. */
public class DelcallAnnotationVisitor extends AbstractSoyNodeVisitor<ImmutableSet<String>> {

  /** Collects final output. */
  private final ImmutableSet.Builder<String> output;

  /** Map of variable to mod templates that are referenced in its delcaration. */
  private final Map<String, ImmutableSet<String>> modTemplateRefs;

  /** Map of variable to other variables that are referenced in its delcaration. */
  private final Map<String, ImmutableSet<String>> varRefs;

  public DelcallAnnotationVisitor() {
    this.output = ImmutableSet.builder();
    this.modTemplateRefs = new HashMap<>();
    this.varRefs = new HashMap<>();
  }

  @Override
  public ImmutableSet<String> exec(SoyNode node) {
    visit(node);
    return output.build();
  }

  @Override
  protected void visitLetValueNode(LetValueNode node) {
    FindModTemplatesAndVars visitor = new FindModTemplatesAndVars();
    visitor.exec(node.getExpr());
    modTemplateRefs.put(node.getVarName(), visitor.getModTemplates());
    varRefs.put(node.getVarName(), visitor.getVarRefs());
  }

  @Override
  protected void visitCallBasicNode(CallBasicNode node) {
    findReferencedModTemplates(node.getCalleeExpr());
    visitChildren(node);
  }

  @Override
  protected void visitCallParamValueNode(CallParamValueNode node) {
    findReferencedModTemplates(node.getExpr());
  }

  /** Add all mod templates transitively referenced in exprNode to the output. */
  private void findReferencedModTemplates(ExprNode exprNode) {
    FindModTemplatesAndVars visitor = new FindModTemplatesAndVars();
    visitor.exec(exprNode);
    output.addAll(visitor.getModTemplates());
    visitor.getVarRefs().forEach(this::collectVarTemplateRefs);
  }

  /**
   * For the given variable, search all transitive assignments and add referenced mod templates to
   * the output set.
   */
  private void collectVarTemplateRefs(String varName) {
    if (modTemplateRefs.containsKey(varName)) {
      output.addAll(modTemplateRefs.get(varName));
    }
    if (varRefs.containsKey(varName)) {
      varRefs.get(varName).forEach(this::collectVarTemplateRefs);
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

    private final ImmutableSet.Builder<String> modTemplates;
    private final ImmutableSet.Builder<String> varRefs;

    FindModTemplatesAndVars() {
      this.modTemplates = ImmutableSet.builder();
      this.varRefs = ImmutableSet.builder();
    }

    ImmutableSet<String> getModTemplates() {
      return modTemplates.build();
    }

    ImmutableSet<String> getVarRefs() {
      return varRefs.build();
    }

    @Override
    protected void visitTemplateLiteralNode(TemplateLiteralNode node) {
      if (node.getType() instanceof TemplateType
          && ((TemplateType) node.getType()).isModifiable()) {
        String legacyDeltemplateNamespace =
            ((TemplateType) node.getType()).getLegacyDeltemplateNamespace();
        if (!legacyDeltemplateNamespace.isEmpty()) {
          modTemplates.add(legacyDeltemplateNamespace);
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
      varRefs.add(node.getName());
    }

    @Override
    protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }
  }
}
