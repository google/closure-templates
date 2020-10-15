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
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.basetree.CopyState;
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
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.AttrParam;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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

  private static final SoyErrorKind ATTRIBUTE_AS_PARAM =
      SoyErrorKind.of("Attribute ''{0}'' should be set as a param.");

  private static final SoyErrorKind ATTRIBUTE_PARAM_NOT_ALLOWED =
      SoyErrorKind.of(
          "Attribute ''{0}'' can only be present on root elements of html<?> templates.");

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
    SoyTreeUtils.getAllNodesOfType(file, TemplateNode.class).stream()
        .filter(t -> t.getTemplateContentKind() instanceof TemplateContentKind.ElementContentKind)
        .forEach(t -> processTemplate(t, nodeIdGen::genId));
    SoyTreeUtils.getAllNodesOfType(file, HtmlAttributeNode.class).stream()
        .map(HtmlAttributeNode.class::cast)
        .filter(attr -> attr.getValueStrategy() != HtmlAttributeNode.ValueStrategy.NONE)
        .forEach(
            attr ->
                errorReporter.report(
                    attr.getSourceLocation(), ATTRIBUTE_PARAM_NOT_ALLOWED, attr.getStaticKey()));
  }

  private NotOpNode checkNotNull(AttrParam attr) {
    NotOpNode not = new NotOpNode(UNKNOWN, UNKNOWN);
    FunctionNode isNull =
        FunctionNode.newPositional(
            Identifier.create("isNull", UNKNOWN),
            (SoySourceFunction) pluginResolver.lookupSoyFunction("isNull", 1, UNKNOWN),
            UNKNOWN);
    isNull.addChild(new ExprRootNode(new VarRefNode(attr.name(), UNKNOWN, attr)));
    not.addChild(isNull);
    return not;
  }

  private <T extends Node> void processTemplate(TemplateNode templateNode, Supplier<Integer> id) {
    Map<String, AttrParam> attrs =
        templateNode.getAllParams().stream()
            .filter(p -> p instanceof AttrParam)
            .map(AttrParam.class::cast)
            .collect(Collectors.toMap(AttrParam::getAttrName, Function.identity()));

    ImmutableList<VarRefNode> refs = SoyTreeUtils.getAllNodesOfType(templateNode, VarRefNode.class);
    Optional<HtmlOpenTagNode> elmOpen = getElementOpen(templateNode);
    Set<String> explicitAttributes = new HashSet<>();
    if (!elmOpen.isPresent()) {
      return;
    }

    HtmlOpenTagNode openTagNode = elmOpen.get();
    openTagNode.getChildren().stream()
        .filter(
            p ->
                p instanceof HtmlAttributeNode
                    && ((HtmlAttributeNode) p).getValueStrategy()
                        != HtmlAttributeNode.ValueStrategy.NONE)
        .map(HtmlAttributeNode.class::cast)
        .forEach(
            node -> {
              // Remove the @ at the beginning of the attribute.
              String staticKey = node.getStaticKey().substring(1);
              if (!attrs.containsKey(node.getStaticKey().substring(1))) {
                errorReporter.report(node.getSourceLocation(), ATTRIBUTE_AS_PARAM, staticKey);
                return;
              }
              AttrParam attr = attrs.get(staticKey);
              explicitAttributes.add(staticKey);
              // Creates an HTML attribute containing the original name with the @ chopped off
              HtmlAttributeNode attrNode = new HtmlAttributeNode(id.get(), UNKNOWN, UNKNOWN_POINT);
              attrNode.addChild(((RawTextNode) node.getChild(0)).substring(id.get(), 1));

              // Creates a conditional around the parameter name
              // This should look like class="{if $foo}{$foo}"
              VarRefNode attrExpr = new VarRefNode(attr.name(), UNKNOWN, attr);
              HtmlAttributeValueNode valueNode =
                  new HtmlAttributeValueNode(id.get(), UNKNOWN, Quotes.DOUBLE);
              attrNode.addChild(valueNode);
              // We do not check required here since all += and = are implicitly optional.
              IfNode ifNode = new IfNode(id.get(), UNKNOWN);
              valueNode.addChild(ifNode);
              IfCondNode ifCondNode =
                  new IfCondNode(id.get(), UNKNOWN, UNKNOWN, "if", checkNotNull(attr));
              ifNode.addChild(ifCondNode);

              PrintNode printNode =
                  new PrintNode(
                      id.get(),
                      UNKNOWN,
                      true,
                      attrExpr,
                      ImmutableList.of(),
                      ErrorReporter.exploding());
              ifCondNode.addChild(printNode);

              if (node.getValueStrategy() == HtmlAttributeNode.ValueStrategy.DEFAULT) {
                // In the default case, we append an {else}...{/if} for the default case.
                IfElseNode ifElseNode = new IfElseNode(id.get(), UNKNOWN, UNKNOWN);
                ifNode.addChild(ifElseNode);
                for (int i = 1; i < node.getChildren().size(); i++) {
                  if (node.getChild(i) instanceof HtmlAttributeValueNode) {
                    HtmlAttributeValueNode childValue = (HtmlAttributeValueNode) node.getChild(i);
                    for (StandaloneNode child : childValue.getChildren()) {
                      ifElseNode.addChild(child.copy(new CopyState()));
                    }
                  } else {
                    ifElseNode.addChild(node.getChild(i).copy(new CopyState()));
                  }
                }
              } else {
                // In the concat case, we add a space and then the default.
                ifCondNode.addChild(new RawTextNode(id.get(), " ", UNKNOWN));
                for (int i = 1; i < node.getChildren().size(); i++) {
                  if (node.getChild(i) instanceof HtmlAttributeValueNode) {
                    HtmlAttributeValueNode childValue = (HtmlAttributeValueNode) node.getChild(i);
                    for (StandaloneNode child : childValue.getChildren()) {
                      valueNode.addChild(child.copy(new CopyState()));
                    }
                  } else {
                    valueNode.addChild(node.getChild(i).copy(new CopyState()));
                  }
                }
              }
              node.getParent().replaceChild(node, attrNode);
            });

    for (AttrParam attr : attrs.values()) {
      if (explicitAttributes.contains(attr.name())) {
        continue;
      }
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
          IfNode ifNode = new IfNode(id.get(), UNKNOWN);
          IfCondNode ifCondNode =
              new IfCondNode(id.get(), UNKNOWN, UNKNOWN, "if", checkNotNull(attr));
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
