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
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
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

  private final ErrorReporter errorReporter;

  public ResolveTemplateNamesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      visitFile(file);
    }
    for (SoyFileNode file : sourceFiles) {
      updateTemplateLiteralsStaticCallProperty(file);
    }
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
              TemplateLiteralNode converted = varRefToLiteral(v, v.getSourceLocation());
              if (converted != null) {
                v.getParent().replaceChild(v, converted);
              }
            });

    // Resolve all unresolved TemplateLiteralNodes.
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
    TemplateLiteralNode converted = varRefToLiteral(templateExpr, templateExpr.getSourceLocation());
    if (converted != null) {
      callNode.setCalleeExpr(new ExprRootNode(converted));
    }
  }

  /**
   * If {@code expr} is a VAR_REF and its type is TEMPLATE_TYPE then create and return a new
   * equivalent TemplateLiteralNode, otherwise null.
   */
  @Nullable
  private static TemplateLiteralNode varRefToLiteral(ExprNode expr, SourceLocation sourceLocation) {
    if (expr.getKind() != Kind.VAR_REF_NODE) {
      return null;
    }
    VarRefNode varRef = (VarRefNode) expr;
    if (varRef.hasType() && expr.getType().getKind() == SoyType.Kind.TEMPLATE_TYPE) {
      return TemplateLiteralNode.forVarRef(varRef.copy(new CopyState()), sourceLocation);
    }
    return null;
  }

  static void updateTemplateLiteralsStaticCallProperty(SoyNode root) {
    SoyTreeUtils.visitExprNodesWithHolder(
        root,
        TemplateLiteralNode.class,
        (exprHolder, templateLiteralNode) -> {
          boolean staticCall = false;
          if (exprHolder instanceof CallNode) {
            ExprNode parent = templateLiteralNode.getParent();
            if (parent.getKind() != Kind.METHOD_CALL_NODE) {
              staticCall = true;
            }
          }
          templateLiteralNode.setStaticCall(staticCall);
        });
  }
}
