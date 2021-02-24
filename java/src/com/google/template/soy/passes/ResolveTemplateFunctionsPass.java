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

import static com.google.template.soy.base.SourceLocation.UNKNOWN;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.CallableExpr.ParamsStyle;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.ProtoImportType;
import com.google.template.soy.types.SoyType;

/**
 * Resolves function calls to template names inside of the dynamic names of HTML open tags, where
 * such calls are allowed.
 */
@RunAfter({
  ImportsPass.class,
  ResolveDottedImportsPass.class, // So that all names are VarRefs.
})
@RunBefore({ResolveTemplateNamesPass.class})
final class ResolveTemplateFunctionsPass implements CompilerFilePass {

  ResolveTemplateFunctionsPass() {}

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.allNodesOfType(file, HtmlOpenTagNode.class)
        .filter(tag -> !tag.getTagName().isStatic())
        .flatMap(
            tag ->
                SoyTreeUtils.allNodesOfType(
                    tag.getTagName().getDynamicTagName(), FunctionNode.class))
        .filter(
            fct ->
                !fct.hasStaticName()
                    && (fct.getParamsStyle() == ParamsStyle.NONE
                        || fct.getParamsStyle() == ParamsStyle.NAMED))
        .filter(fct -> fct.getNameExpr().getKind() == Kind.VAR_REF_NODE)
        .collect(toList()) // Guard against concurrent modification.
        .forEach(ResolveTemplateFunctionsPass::convertToBind);
  }

  private static void convertToBind(FunctionNode fct) {
    ExprNode replacementExpr;
    VarRefNode varRefNode = (VarRefNode) fct.getNameExpr();
    if (varRefNode.hasType() && varRefNode.getType() instanceof ProtoImportType) {
      return;
    }
    if (varRefNode.hasType() && varRefNode.getType().getKind() == SoyType.Kind.TEMPLATE_TYPE) {
      // If the function is a template symbol modify AST like:
      // {tmp(...)} -> {template(tmp).bind(...)}
      replacementExpr = TemplateLiteralNode.forVarRef(varRefNode);
    } else {
      // Otherwise modify AST like:
      // {$tmp(...)} -> {$tmp.bind(...)}
      replacementExpr = varRefNode;
    }

    if (fct.numParams() > 0) {
      // Move original function's parameters into a record() literal.
      RecordLiteralNode record =
          new RecordLiteralNode(
              Identifier.create("record", fct.getSourceLocation()),
              fct.getParamNames(),
              fct.getSourceLocation());
      record.addChildren(fct.getChildren());

      // Bind and replace.
      replacementExpr =
          MethodCallNode.newWithPositionalArgs(
              replacementExpr,
              ImmutableList.of(record),
              Identifier.create("bind", UNKNOWN),
              UNKNOWN,
              false);
    }

    fct.getParent().replaceChild(fct, replacementExpr);
  }
}
