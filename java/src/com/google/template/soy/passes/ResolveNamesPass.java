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

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.passes.LocalVariablesNodeVisitor.ExprVisitor;
import com.google.template.soy.passes.LocalVariablesNodeVisitor.LocalVariables;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.defn.UndeclaredVar;

/**
 * Visitor which resolves all variable and parameter references to point to the corresponding
 * declaration object.
 */
public final class ResolveNamesPass implements CompilerFilePass {

  private static final SoyErrorKind GLOBAL_MATCHES_VARIABLE =
      SoyErrorKind.of(
          "Found global reference aliasing a local variable ''{0}'', did you mean ''${0}''?");

  private static final SoyErrorKind UKNOWN_VARIABLE =
      SoyErrorKind.of("Unknown variable.{0}", StyleAllowance.NO_PUNCTUATION);

  private final ErrorReporter errorReporter;

  public ResolveNamesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    new LocalVariablesNodeVisitor(new Visitor()).exec(file);
  }

  private final class Visitor extends LocalVariablesNodeVisitor.NodeVisitor {

    private final ResolveNamesExprVisitor exprVisitor = new ResolveNamesExprVisitor();

    @Override
    protected ExprVisitor getExprVisitor() {
      return exprVisitor;
    }

    @Override
    protected ErrorReporter getErrorReporter() {
      return errorReporter;
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Expr visitor.

  /**
   * Visitor which resolves all variable and parameter references in expressions to point to the
   * corresponding declaration object.
   */
  private final class ResolveNamesExprVisitor extends LocalVariablesNodeVisitor.ExprVisitor {

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
      LocalVariables localVariables = getLocalVariables();
      VarDefn varDefn = localVariables.lookup("$" + globalName);
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
      LocalVariables localVariables = getLocalVariables();
      VarDefn varDefn = localVariables.lookup(varRef.getName());
      if (varDefn == null) {
        errorReporter.report(
            varRef.getSourceLocation(),
            UKNOWN_VARIABLE,
            SoyErrors.getDidYouMeanMessage(localVariables.allVariablesInScope(), varRef.getName()));
        varDefn = new UndeclaredVar(varRef.getName(), varRef.getSourceLocation());
      }
      varRef.setDefn(varDefn);
    }
  }
}
