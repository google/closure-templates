/*
 * Copyright 2008 Google Inc.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.sharedpasses.FindIndirectParamsVisitor.IndirectParamsInfo;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.TemplateParam;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Visitor for checking that in each template, the parameters declared in the SoyDoc match the data
 * keys referenced in the template.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> Precondition: All template and callee names should be full names (i.e. you must execute
 * {@code SetFullCalleeNamesVisitor} before executing this visitor).
 *
 * <p> Note this visitor only works for code in Soy V2 syntax.
 *
 * <p> {@link #exec} should be called on a full parse tree. There is no return value. However, a
 * {@code SoySyntaxException} is thrown if the parameters declared in some template's SoyDoc do not
 * match the data keys referenced in that template.
 *
 */
public class CheckSoyDocVisitor extends AbstractSoyNodeVisitor<Void> {

  /** User-declared syntax version. */
  private final SyntaxVersion declaredSyntaxVersion;

  /** Registry of all templates in the Soy tree. */
  private TemplateRegistry templateRegistry;

  /** The GetDataKeysInExprVisitor to use for expressions in the current template (during pass). */
  private GetDataKeysInExprVisitor getDataKeysInExprVisitor;

  /**
   * @param declaredSyntaxVersion User-declared syntax version,
   */
  public CheckSoyDocVisitor(SyntaxVersion declaredSyntaxVersion) {
    this.declaredSyntaxVersion = declaredSyntaxVersion;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  /**
   * {@inheritDoc}
   * @throws SoySyntaxException If the parameters declared in some template's SoyDoc do not match
   *     the data keys referenced in that template.
   */
  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {
    // Build templateRegistry.
    templateRegistry = new TemplateRegistry(node);

    // Run pass only on the Soy files that are all in V2 syntax.
    for (SoyFileNode soyFile : node.getChildren()) {
      // First determine if Soy file is all in V2 syntax.
      boolean doCheckSoyDocInFile;
      if (declaredSyntaxVersion.num >= SyntaxVersion.V2_0.num) {
        doCheckSoyDocInFile = true;
      } else {
        try {
          // TODO SOON: Use a simpler visitor that doesn't report errors and shortcircuits.
          (new ReportSyntaxVersionErrorsVisitor(SyntaxVersion.V2_0, true)).exec(soyFile);
          doCheckSoyDocInFile = true;
        } catch (SoySyntaxException sse) {
          doCheckSoyDocInFile = false;
        }
      }
      // Run pass on Soy file if it is all in V2 syntax.
      if (doCheckSoyDocInFile) {
        visit(soyFile);
      }
    }
  }

  /**
   * {@inheritDoc}
   * @throws SoySyntaxException If the parameters declared in some template's SoyDoc do not match
   *     the data keys referenced in that template.
   */
  @Override protected void visitTemplateNode(TemplateNode node) {
    Set<String> dataKeys = Sets.newHashSet();  // data keys referenced in this template
    getDataKeysInExprVisitor = new GetDataKeysInExprVisitor(dataKeys);

    visitChildren(node);

    IndirectParamsInfo ipi = (new FindIndirectParamsVisitor(templateRegistry)).exec(node);

    List<String> unusedParams = Lists.newArrayList();
    for (TemplateParam param : node.getAllParams()) {
      if (dataKeys.contains(param.name())) {
        // Good: Declared and referenced in template. We remove these from dataKeys so
        // that at the end of the for-loop, dataKeys will only contain the keys that are referenced
        // but not declared in SoyDoc.
        dataKeys.remove(param.name());
      } else if (ipi.paramKeyToCalleesMultimap.containsKey(param.name()) ||
                 ipi.mayHaveIndirectParamsInExternalCalls ||
                 ipi.mayHaveIndirectParamsInExternalDelCalls) {
        // Good: Declared in SoyDoc and either (a) used in a call that passes all data or (b) used
        // in an external call or delcall that passes all data, which may need the param (we can't
        // verify).
      } else {
        // Bad: Declared in SoyDoc but not referenced in template.
        unusedParams.add(param.name());
      }
    }

    List<String> undeclaredDataKeys = Lists.newArrayList();
    if (dataKeys.size() > 0) {
      // Bad: Referenced in template but not declared in SoyDoc.
      undeclaredDataKeys.addAll(dataKeys);
      Collections.sort(undeclaredDataKeys);
    }

    if (undeclaredDataKeys.size() > 0) {
      throw SoySyntaxExceptionUtils.createWithNode(
          "Found references to data keys that are not declared in SoyDoc: " + undeclaredDataKeys,
          node);
    }
    if (unusedParams.size() > 0 && ! (node instanceof TemplateDelegateNode)) {
      // Note: The reason we allow delegate templates to declare unused params (in the if-condition
      // above) is that other implementations of the same delegate may need to use those params.
      throw SoySyntaxExceptionUtils.createWithNode(
          "Found params declared in SoyDoc but not used in template: " + unusedParams, node);
    }
  }

  @Override protected void visitCallNode(CallNode node) {

    if (node.isPassingAllData()) {
      // Nothing to do here, because we now use FindIndirectParamsVisitor to find all the
      // transitive callees that we pass all data to (and also to find out whether there are any
      // external transitive callees).
    } else {
      // Not passing all data.
      visitExprHolderHelper(node);
    }

    visitChildren(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ExprHolderNode) {
      visitExprHolderHelper((ExprHolderNode) node);
    }

    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  /**
   * Helper for visiting a node that holds one or more expressions. For each expression, collects
   * data keys referenced.
   * @param exprHolder The node holding the expressions to be visited.
   */
  private void visitExprHolderHelper(ExprHolderNode exprHolder) {
    for (ExprUnion exprUnion : exprHolder.getAllExprUnions()) {
      getDataKeysInExprVisitor.exec(exprUnion.getExpr());
    }
  }

  /**
   * Helper for travering an expression tree and locating all the data keys (excluding local vars
   * and injected data keys) referenced in the expression.
   *
   * <p> {@link #exec} may be called on any expression. Any data keys referenced in the expression
   * (excluding local vars and injected data keys) will be added to the {@code dataKeys} set passed
   * in to the constructor. There is no return value.
   */
  private static class GetDataKeysInExprVisitor extends AbstractExprNodeVisitor<Void> {
    /** The set used to collect the data keys found. */
    private final Set<String> dataKeys;

    /**
     * @param dataKeys The set used to collect the data keys found.
     */
    public GetDataKeysInExprVisitor(Set<String> dataKeys) {
      this.dataKeys = dataKeys;
    }

    // ------ Implementations for specific nodes. ------

    @Override protected void visitVarRefNode(VarRefNode node) {

      // If not referencing injected or local var data, add the first key to the set of data keys
      // referenced. (If it's an undeclared variable reference, then we assume it's a param
      // for now.)
      if (node.isPossibleParam()) {
        dataKeys.add(node.getName());
      }
    }

    // ------ Fallback implementation. ------

    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }
  }
}
