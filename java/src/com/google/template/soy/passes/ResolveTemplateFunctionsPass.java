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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.exprtree.ExprNode.CallableExpr.ParamsStyle;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.ImportsContext.ImportsTemplateRegistry;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves function calls to template names inside of the dynamic names of HTML open tags, where
 * such calls are allowed.
 */
@RunAfter(ResolveTemplateImportsPass.class)
@RunBefore({ResolvePluginsPass.class, ResolveTemplateNamesPass.class})
final class ResolveTemplateFunctionsPass implements CompilerFilePass {

  ResolveTemplateFunctionsPass() {}

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    ImportsTemplateRegistry templateRegistry = file.getTemplateRegistry();
    // TODO(b/170213185): Come up with a unified vision for template resolution.
    Set<String> localTemplateNames =
        file.getTemplates().stream()
            .map(TemplateNode::getLocalTemplateSymbol)
            .collect(Collectors.toSet());

    for (HtmlOpenTagNode tag :
        SoyTreeUtils.getAllMatchingNodesOfType(
            file, HtmlOpenTagNode.class, tag -> !tag.getTagName().isStatic())) {

      for (FunctionNode fct :
          SoyTreeUtils.getAllMatchingNodesOfType(
              tag.getTagName().getDynamicTagName(),
              FunctionNode.class,
              fct ->
                  fct.getParamsStyle() == ParamsStyle.NONE
                      || fct.getParamsStyle() == ParamsStyle.NAMED)) {
        if (templateRegistry.getImportedSymbols().contains(fct.getFunctionName())) {
          convertToBind(fct, fct.getIdentifier(), fct.getFunctionNameLocation());
        } else if (localTemplateNames.contains(fct.getFunctionName())) {
          // Special case allowing local template .foo to be called as foo() -- without leading dot.
          convertToBind(
              fct,
              Identifier.create("." + fct.getStaticFunctionName(), fct.getFunctionNameLocation()),
              fct.getFunctionNameLocation());
        }
      }
    }
  }

  private static void convertToBind(
      FunctionNode fct, Identifier templateLiteralId, SourceLocation location) {
    // Move original function's parameters into a record() literal.
    RecordLiteralNode record =
        new RecordLiteralNode(
            Identifier.create("record", fct.getSourceLocation()),
            fct.getParamNames(),
            fct.getSourceLocation());
    record.addChildren(fct.getChildren());

    // Create a template(foo) literal from function node foo()
    TemplateLiteralNode templateLiteral =
        new TemplateLiteralNode(templateLiteralId, location, true);

    // Bind and replace.
    MethodCallNode bind =
        MethodCallNode.newWithPositionalArgs(
            templateLiteral,
            ImmutableList.of(record),
            Identifier.create("bind", UNKNOWN),
            UNKNOWN,
            false);
    fct.getParent().replaceChild(fct, bind);
  }
}
