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

import static com.google.template.soy.base.SourceLocation.Point.UNKNOWN_POINT;
import static com.google.template.soy.base.SourceLocation.UNKNOWN;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode.Quotes;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.AttrParam;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Enforces rules on the usage of @attribute parameters within a template. Rewrites templates for
 * implicit @attribute usage.
 */
@RunAfter({ResolveNamesPass.class, ResolveTemplateParamTypesPass.class})
@RunBefore({
  ResolveExpressionTypesPass.class,
  SoyElementCompositionPass.class,
  AutoescaperPass.class // since it inserts print directives
})
final class ElementAttributePass implements CompilerFileSetPass {

  private static final SoyErrorKind ATTRIBUTE_USED_OUTSIDE_OF_TAG =
      SoyErrorKind.of("Attributes may not be referenced explicitly.");

  private final ErrorReporter errorReporter;
  private final PluginResolver pluginResolver;

  ElementAttributePass(ErrorReporter errorReporter, PluginResolver pluginResolver) {
    this.errorReporter = errorReporter;
    this.pluginResolver = pluginResolver;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      run(file, idGenerator);
    }
    return Result.CONTINUE;
  }

  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.getAllNodesOfType(file, TemplateNode.class)
        .forEach(t -> processTemplate(t, nodeIdGen::genId));
  }

  private <T extends Node> void processTemplate(TemplateNode templateNode, Supplier<Integer> id) {
    List<AttrParam> attrs =
        templateNode.getAllParams().stream()
            .filter(p -> p instanceof AttrParam)
            .map(AttrParam.class::cast)
            .collect(Collectors.toList());

    if (attrs.isEmpty()) {
      return;
    }

    ImmutableList<VarRefNode> refs = SoyTreeUtils.getAllNodesOfType(templateNode, VarRefNode.class);
    Optional<HtmlOpenTagNode> elmOpen = getElementOpen(templateNode);

    for (AttrParam attr : attrs) {
      List<VarRefNode> attrRefs =
          refs.stream().filter(ref -> ref.getDefnDecl().equals(attr)).collect(Collectors.toList());

      if (attrRefs.isEmpty()) {
        if (!elmOpen.isPresent()) {
          // A previous pass will have reported an error.
          continue;
        }
        // If no refs then a simple print in the root element is the implied behavior.
        // <... {if not isNull($attr)}attr="{$attr}"{/if} ...>
        ExprNode attrExpr = new VarRefNode(attr.name(), UNKNOWN, attr);
        HtmlAttributeNode attrNode = new HtmlAttributeNode(id.get(), UNKNOWN, UNKNOWN_POINT);
        attrNode.addChild(new RawTextNode(id.get(), attr.getAttrName(), UNKNOWN));
        HtmlAttributeValueNode valueNode =
            new HtmlAttributeValueNode(id.get(), UNKNOWN, Quotes.DOUBLE);
        valueNode.addChild(
            new PrintNode(
                id.get(), UNKNOWN, true, attrExpr, ImmutableList.of(), ErrorReporter.exploding()));
        attrNode.addChild(valueNode);

        StandaloneNode nodeToAdd = attrNode;
        if (!attr.isRequired()) {
          NotOpNode not = new NotOpNode(UNKNOWN, UNKNOWN);
          FunctionNode isNull =
              FunctionNode.newPositional(
                  Identifier.create("isNull", UNKNOWN),
                  (SoySourceFunction) pluginResolver.lookupSoyFunction("isNull", 1, UNKNOWN),
                  UNKNOWN);
          isNull.addChild(new ExprRootNode(new VarRefNode(attr.name(), UNKNOWN, attr)));
          not.addChild(isNull);
          IfNode ifNode = new IfNode(id.get(), UNKNOWN);
          IfCondNode ifCondNode = new IfCondNode(id.get(), UNKNOWN, UNKNOWN, "if", not);
          ifCondNode.addChild(attrNode);
          ifNode.addChild(ifCondNode);
          nodeToAdd = ifNode;
        }

        elmOpen.get().addChild(nodeToAdd);
      } else {
        // No standard var refs are allowed.
        for (VarRefNode attrRef : attrRefs) {
          errorReporter.report(attrRef.getSourceLocation(), ATTRIBUTE_USED_OUTSIDE_OF_TAG);
        }
      }
    }
  }

  private static Optional<HtmlOpenTagNode> getElementOpen(TemplateNode node) {
    // TODO(user): Dedupe logic with SoyElementPass?
    return node.getChildren().stream()
        .filter(n -> n.getKind() == Kind.HTML_OPEN_TAG_NODE)
        .map(HtmlOpenTagNode.class::cast)
        .findFirst();
  }
}
