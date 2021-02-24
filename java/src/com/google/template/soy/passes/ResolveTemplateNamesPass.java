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
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.SoyType;
import javax.annotation.Nullable;

/** Resolves template names in calls, checking against template names & imports. */
@RunAfter({
  ImportsPass.class,
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

  private static final SoyErrorKind UNIMPORTED_TEMPLATE_CALL =
      SoyErrorKind.of("Template must be imported. See go/soy-external-calls.");

  private static final ImmutableSet<String> TEMPLATE_IMPORT_EXEMPTIONS_BY_NAMESPACE =
      ImmutableSet.of(
          );

  private final ErrorReporter errorReporter;

  // Whether to require external templates to be imported (rather than referenced via fqn or aliased
  // namespaces).
  private final boolean requireTemplateImports;

  // Whether the current file should be exempted from template imports (only applies if
  // requireTemplateImports is enabled).
  private boolean isFileExemptedFromTemplateImports = false;

  public ResolveTemplateNamesPass(ErrorReporter errorReporter, boolean requireTemplateImports) {
    this.errorReporter = errorReporter;
    this.requireTemplateImports = requireTemplateImports;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      isFileExemptedFromTemplateImports = isFileExemptedFromTemplateImports(file);
      visitFile(file);
    }
    isFileExemptedFromTemplateImports = false;
    return Result.CONTINUE;
  }

  private void visitFile(SoyFileNode file) {
    // Change all callee expr of CallBasicNode to TemplateLiteralNode.
    SoyTreeUtils.allNodesOfType(file, CallBasicNode.class)
        .forEach(ResolveTemplateNamesPass::importedVarRefToTemplateLiteral);

    // Change all varrefs of type TEMPLATE_TYPE to TemplateLiteralNode.
    SoyTreeUtils.allNodesOfType(file, VarRefNode.class)
        .filter(n -> n.getParent().getKind() != Kind.TEMPLATE_LITERAL_NODE)
        .forEach(
            v -> {
              TemplateLiteralNode converted =
                  varRefToLiteral(v, v.getSourceLocation(), /* isSynthetic= */ false);
              if (converted != null) {
                v.getParent().replaceChild(v, converted);
              }
            });

    if (requireTemplateImports && !isFileExemptedFromTemplateImports) {
      SoyTreeUtils.allNodesOfType(file, TemplateLiteralNode.class)
          .filter(TemplateLiteralNode::isGlobalName)
          .forEach(
              n ->
                  errorReporter.report(
                      n.getChild(0).getSourceLocation(), UNIMPORTED_TEMPLATE_CALL));
    }

    // Resolve all unresolved TemplateLiteralNodes. Remove this along with template FQN support.
    SoyTreeUtils.allNodesOfType(file, TemplateLiteralNode.class)
        .filter(n -> !n.isResolved())
        .forEach(TemplateLiteralNode::resolveTemplateName);

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
      return TemplateLiteralNode.forVarRef(
          varRef.copy(new CopyState()), sourceLocation, isSynthetic);
    }
    return null;
  }

  private static boolean isFileExemptedFromTemplateImports(SoyFileNode file) {
    return TEMPLATE_IMPORT_EXEMPTIONS_BY_NAMESPACE.contains(file.getNamespace())
        // Temporary recaptcha hack. They've got these private soy files that we can't access so we
        // can't easily know the namespaces to exempt.
        // TODO(user): Remove this after Jesse migrates them with his superpowers.
        || file.getFilePath().toString().contains("recaptcha/");
  }
}
