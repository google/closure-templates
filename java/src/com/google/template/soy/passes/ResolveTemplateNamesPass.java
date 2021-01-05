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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.TemplateNodeBuilder;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TemplateImportType;
import javax.annotation.Nullable;

/** Resolves template names in calls, checking against template names & imports. */
@RunAfter({
  ResolveTemplateImportsPass.class,
  ResolvePluginsPass.class, // Needs TEMPLATE function resolved.
  ResolveNamesPass.class, // Needs VarRef.defn defined.
  ResolveDottedImportsPass.class, // Needs dotted template imports to be inlined.
})
@RunBefore({
  SoyElementPass.class, // Needs {@link CallBasicNode#getCalleeName} to be resolved.
})
public final class ResolveTemplateNamesPass implements CompilerFileSetPass {

  private static final SoyErrorKind DATA_ATTRIBUTE_ONLY_ALLOWED_ON_STATIC_CALLS =
      SoyErrorKind.of("The `data` attribute is only allowed on static calls.");

  private static final SoyErrorKind INVALID_TEMPLATE_FUNCT_PARAM =
      SoyErrorKind.of(
          "The argument to the template() function must be a local, imported, or global template"
              + " name.");

  private final ErrorReporter errorReporter;

  public ResolveTemplateNamesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      visitFile(file);
    }

    return Result.CONTINUE;
  }

  private void visitFile(SoyFileNode file) {
    // Change all callee expr of CallBasicNode to TemplateLiteralNode.
    SoyTreeUtils.allNodesOfType(file, CallBasicNode.class)
        .forEach(ResolveTemplateNamesPass::importedVarRefToTemplateLiteral);

    // Change all function() calls to TemplateLiteralNode.
    SoyTreeUtils.allFunctionInvocations(file, BuiltinFunction.TEMPLATE)
        .forEach(node -> resolveTemplateFunction(node, file.getHeaderInfo()));

    // Change all varrefs of type TEMPLATE_TYPE to TemplateLiteralNode.
    SoyTreeUtils.allNodesOfType(file, VarRefNode.class)
        .forEach(
            v -> {
              TemplateLiteralNode converted =
                  varRefToLiteral(v, v.getSourceLocation(), /* isSynthetic= */ false);
              if (converted != null) {
                v.getParent().replaceChild(v, converted);
              }
            });

    // Resolve all unresolved TemplateLiteralNodes. Remove this along with template FQN support.
    SoyTreeUtils.allNodesOfType(file, TemplateLiteralNode.class)
        .filter(n -> !n.isResolved())
        .forEach(node -> resolveTemplateName(node, file.getHeaderInfo()));

    // Validate CallBasicNode data="expr". This previously happened in the CallBasicNode
    // constructor but now must happen after Visitor runs.
    SoyTreeUtils.allNodesOfType(file, CallBasicNode.class)
        .filter(callNode -> callNode.isPassingData() && !callNode.isStaticCall())
        .forEach(
            callNode ->
                errorReporter.report(
                    callNode.getOpenTagLocation(), DATA_ATTRIBUTE_ONLY_ALLOWED_ON_STATIC_CALLS));
  }

  private static void importedVarRefToTemplateLiteral(CallBasicNode callNode) {
    ExprNode templateExpr = callNode.getCalleeExpr().getRoot();
    TemplateLiteralNode converted =
        varRefToLiteral(templateExpr, templateExpr.getSourceLocation(), /* isSynthetic= */ true);
    if (converted != null) {
      callNode.setCalleeExpr(new ExprRootNode(converted));
    }
  }

  private void resolveTemplateFunction(FunctionNode functionNode, SoyFileHeaderInfo header) {
    if (functionNode.numChildren() != 1) {
      // Error reported elsewhere.
      return;
    }

    ExprNode param = functionNode.getParams().get(0);
    TemplateLiteralNode converted =
        varRefToLiteral(param, functionNode.getSourceLocation(), /* isSynthetic= */ false);

    if (converted != null) {
      functionNode.getParent().replaceChild(functionNode, converted);
    } else if (param.getKind() == Kind.GLOBAL_NODE) {
      // Remove this branch along with template FQN support.
      Identifier unresolved = ((GlobalNode) param).getIdentifier();
      Identifier fqn = resolveTemplateName(((GlobalNode) param).getIdentifier(), header);
      TemplateLiteralNode template =
          new TemplateLiteralNode(unresolved, param.getSourceLocation(), false);
      template.resolveTemplateName(fqn);
      functionNode.getParent().replaceChild(functionNode, template);
    } else {
      errorReporter.report(param.getSourceLocation(), INVALID_TEMPLATE_FUNCT_PARAM);
    }
  }

  /**
   * If {@code expr} is a VAR_REF and its type is TEMPLATE_TYPE then create and return a new
   * equivalent TemplateLiteralNode, otherwise null.
   */
  @Nullable
  private static TemplateLiteralNode varRefToLiteral(
      ExprNode expr, SourceLocation sourceLocation, boolean isSynthetic) {
    if (expr.getKind() != Kind.VAR_REF_NODE) {
      return null;
    }
    VarRefNode varRef = (VarRefNode) expr;
    if (varRef.hasType() && expr.getType().getKind() == SoyType.Kind.TEMPLATE_TYPE) {
      return new TemplateLiteralNode(
          Identifier.create(varRef.getName(), varRef.getSourceLocation()),
          sourceLocation,
          isSynthetic,
          (TemplateImportType) expr.getType());
    }
    return null;
  }

  private static Identifier resolveTemplateName(
      Identifier unresolvedIdent, SoyFileHeaderInfo header) {

    switch (unresolvedIdent.type()) {
      case SINGLE_IDENT:
      case DOT_IDENT:
        // Case 1: ".foo" and "foo" Source callee name is partial.
        return Identifier.create(
            TemplateNodeBuilder.combineNsAndName(
                header.getNamespace(), unresolvedIdent.identifier()),
            unresolvedIdent.identifier(),
            unresolvedIdent.location());
      case DOTTED_IDENT:
        // Case 2: "foo.bar.baz" Source callee name is a proper dotted ident, which might start with
        // an alias.
        return header.resolveAlias(unresolvedIdent);
    }
    throw new AssertionError(unresolvedIdent.type());
  }

  /** Attempts to resolve a template name, checking against aliases & imports. */
  private static void resolveTemplateName(
      TemplateLiteralNode templateLiteralNode, SoyFileHeaderInfo header) {
    templateLiteralNode.resolveTemplateName(
        resolveTemplateName(templateLiteralNode.getIdentifier(), header));
  }
}
