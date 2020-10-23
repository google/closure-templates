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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.template.soy.base.SourceLocation.Point.UNKNOWN_POINT;
import static com.google.template.soy.error.ErrorReporter.exploding;
import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeNode.ValueStrategy;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode.Quotes;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.ImportsContext.ImportsTemplateRegistry;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.AttrParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SanitizedType.TrustedResourceUriType;
import com.google.template.soy.types.SanitizedType.UriType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.ast.NamedTypeNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

  private static final SoyErrorKind UNUSED_ATTRIBUTE =
      SoyErrorKind.of("Declared @attribute unused in template element.");

  private static final SoyErrorKind ATTRIBUTE_USED_OUTSIDE_OF_TAG =
      SoyErrorKind.of("Attributes may not be referenced explicitly.");

  private static final SoyErrorKind UNRECOGNIZED_ATTRIBUTE =
      SoyErrorKind.of(
          "''{0}'' is not a declared @attribute of the template.{1}",
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind PLAIN_ATTRIBUTE =
      SoyErrorKind.of("HTML attribute masks Soy attribute. Did you mean ''{0}''?");

  private static final SoyErrorKind ATTRIBUTE_NOT_REQUIRED =
      SoyErrorKind.of("@attribute ''{0}'' must be set as optional to be used here.");

  private static final SoyErrorKind ATTRIBUTE_PARAM_NOT_ALLOWED =
      SoyErrorKind.of(
          "Attribute ''{0}'' can only be present on root elements of html<?> templates.");

  private static final SoyErrorKind BAD_ATTRIBUTE_TYPE =
      SoyErrorKind.of("Attributes must be of type string, trusted_resource_uri, or uri.");

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
    checkAttributeTypes(file);

    Set<TemplateNode> delegatingElementsWithAllAttrs = new HashSet<>();
    Map<String, TemplateNode> allTemplatesThisCompile = new HashMap<>();

    // Rewrite all @attribute values in root elements.
    SoyTreeUtils.getAllNodesOfType(file, TemplateNode.class).stream()
        .filter(t -> t.getTemplateContentKind() instanceof TemplateContentKind.ElementContentKind)
        .forEach(
            t -> {
              allTemplatesThisCompile.put(t.getTemplateName(), t);
              processTemplate(t, nodeIdGen::genId, delegatingElementsWithAllAttrs);
            });

    // All other @attributes (outside of root elements) are illegal.
    SoyTreeUtils.getAllNodesOfType(file, HtmlAttributeNode.class).stream()
        .map(HtmlAttributeNode.class::cast)
        .filter(attr -> attr.getValueStrategy() != HtmlAttributeNode.ValueStrategy.NONE)
        .forEach(
            attr ->
                errorReporter.report(
                    attr.getSourceLocation(), ATTRIBUTE_PARAM_NOT_ALLOWED, attr.getStaticKey()));

    if (!delegatingElementsWithAllAttrs.isEmpty()) {
      updateReservedAttributesForDelegateCalls(
          delegatingElementsWithAllAttrs, allTemplatesThisCompile, file.getTemplateRegistry());
    }
  }

  private static final ImmutableSet<SoyType> ALLOWED_ATTR_TYPES =
      ImmutableSet.of(
          StringType.getInstance(), UriType.getInstance(), TrustedResourceUriType.getInstance());

  private <T extends Node> void checkAttributeTypes(SoyFileNode file) {
    SoyTreeUtils.getAllNodesOfType(file, TemplateNode.class).stream()
        .flatMap(t -> t.getHeaderParams().stream())
        .filter(p -> p instanceof AttrParam)
        .map(AttrParam.class::cast)
        .forEach(
            attr -> {
              SoyType type = SoyTypes.removeNull(attr.type());
              if (!ALLOWED_ATTR_TYPES.contains(type)) {
                errorReporter.report(attr.getSourceLocation(), BAD_ATTRIBUTE_TYPE);
              }
            });
  }

  private NotOpNode buildNotNull(VarDefn defn) {
    SourceLocation unknown = defn.nameLocation().clearRange();
    NotOpNode not = new NotOpNode(unknown, unknown);
    FunctionNode isNull =
        FunctionNode.newPositional(
            Identifier.create("isNull", unknown),
            (SoySourceFunction) pluginResolver.lookupSoyFunction("isNull", 1, unknown),
            unknown);
    isNull.addChild(new ExprRootNode(new VarRefNode(defn.name(), unknown, defn)));
    not.addChild(isNull);
    return not;
  }

  private IfNode buildPrintIfNotNull(VarDefn defn, Supplier<Integer> id) {
    SourceLocation unknown = defn.nameLocation().clearRange();
    IfNode ifNode = new IfNode(id.get(), unknown);
    IfCondNode ifCondNode = new IfCondNode(id.get(), unknown, unknown, "if", buildNotNull(defn));
    ifNode.addChild(ifCondNode);
    PrintNode printNode =
        new PrintNode(
            id.get(),
            unknown,
            true,
            new VarRefNode(defn.name(), unknown, defn),
            ImmutableList.of(),
            exploding());
    ifCondNode.addChild(printNode);
    return ifNode;
  }

  private void processTemplate(
      TemplateNode templateNode,
      Supplier<Integer> id,
      Set<TemplateNode> delegatingElementsWithAllAttrs) {
    ImmutableMap<String, AttrParam> attrs =
        templateNode.getAllParams().stream()
            .filter(p -> p instanceof AttrParam)
            .map(AttrParam.class::cast)
            .collect(toImmutableMap(AttrParam::getAttrName, Function.identity()));

    Set<AttrParam> unseenParams = new HashSet<>(attrs.values());
    checkAttributeRefs(templateNode, unseenParams);

    Optional<HtmlOpenTagNode> elmOpen = getElementOpen(templateNode);
    if (!elmOpen.isPresent()) {
      return;
    }

    SourceLocation unknown = templateNode.getSourceLocation().clearRange();

    HtmlOpenTagNode openTagNode = elmOpen.get();
    String delegateTemplateName = getElementCall(openTagNode);
    boolean iAmAnElementCallingAnElement = delegateTemplateName != null;
    ImmutableSet.Builder<String> foundNormalAttr = ImmutableSet.builder();

    openTagNode.getChildren().stream()
        .filter(p -> p.getKind() == Kind.HTML_ATTRIBUTE_NODE)
        .map(HtmlAttributeNode.class::cast)
        .filter(attr -> attr.getStaticKey() != null)
        .forEach(
            attrNode -> {
              String attrKey = attrNode.getStaticKey();
              // Remove the @ at the beginning of the attribute.
              boolean isSoyAttr = attrKey.startsWith("@");
              String attrName = isSoyAttr ? attrKey.substring(1) : attrKey;

              if (!isSoyAttr) {
                foundNormalAttr.add(attrName);

                // e.g. Not allowed to write aria-label= if @aria-label is in scope.
                if (attrs.containsKey(attrName)) {
                  errorReporter.report(
                      attrNode.getSourceLocation(), PLAIN_ATTRIBUTE, "@" + attrName);
                }
                return;
              }

              if (!attrs.containsKey(attrName)) {
                String didYouMeanMessage = SoyErrors.getDidYouMeanMessage(attrs.keySet(), attrName);
                errorReporter.report(
                    attrNode.getSourceLocation(),
                    UNRECOGNIZED_ATTRIBUTE,
                    attrName,
                    didYouMeanMessage);
                return;
              }

              AttrParam attr = attrs.get(attrName);
              VarRefNode attrExpr = new VarRefNode(attr.name(), unknown, attr);
              unseenParams.remove(attr);

              StandaloneNode replacementNode;

              if (attrNode.getValueStrategy() == ValueStrategy.DEFAULT
                  || attrNode.getValueStrategy() == ValueStrategy.CONCAT) {
                if (attr.isRequired()) {
                  errorReporter.report(
                      attrNode.getSourceLocation(), ATTRIBUTE_NOT_REQUIRED, attr.getAttrName());
                }

                // Creates an HTML attribute containing the original name with the @ chopped off
                HtmlAttributeNode newAttrNode =
                    new HtmlAttributeNode(id.get(), unknown, UNKNOWN_POINT);
                newAttrNode.addChild(((RawTextNode) attrNode.getChild(0)).substring(id.get(), 1));

                // Creates a conditional around the parameter name
                // This should look like class="{if not isNull($foo)}{$foo}{/if}"
                HtmlAttributeValueNode valueNode =
                    new HtmlAttributeValueNode(id.get(), unknown, Quotes.DOUBLE);
                newAttrNode.addChild(valueNode);
                // We do not check required here since all += and = are implicitly optional.
                IfNode ifNode = buildPrintIfNotNull(attr, id);
                valueNode.addChild(ifNode);

                if (attrNode.getValueStrategy() == ValueStrategy.DEFAULT) {
                  // In the default case, we append an {else}...{/if} for the default case.
                  IfElseNode ifElseNode = new IfElseNode(id.get(), unknown, unknown);
                  ifNode.addChild(ifElseNode);
                  copyChildren(attrNode, ifElseNode);
                } else {
                  // In the concat case, we add a space and then the default.
                  ifNode.getChild(0).addChild(new RawTextNode(id.get(), " ", unknown));
                  copyChildren(attrNode, valueNode);
                }

                replacementNode = newAttrNode;
              } else if (!attrNode.hasValue()) {
                if (iAmAnElementCallingAnElement) {
                  // Pass through and handle in SoyElementCompositionPass since we cannot encode
                  // null/absent in an HtmlAttributeNode.
                  return;
                }

                // <... @attr ...> rewrite as: {if not isNull($attr)}attr="{$attr}"{/if}
                HtmlAttributeNode newAttrNode =
                    new HtmlAttributeNode(id.get(), unknown, UNKNOWN_POINT);
                newAttrNode.addChild(new RawTextNode(id.get(), attr.getAttrName(), unknown));
                HtmlAttributeValueNode valueNode =
                    new HtmlAttributeValueNode(id.get(), unknown, Quotes.DOUBLE);
                valueNode.addChild(
                    new PrintNode(
                        id.get(), unknown, true, attrExpr, ImmutableList.of(), exploding()));
                newAttrNode.addChild(valueNode);

                replacementNode = newAttrNode;
                if (!attr.isRequired()) {
                  IfNode ifNode = new IfNode(id.get(), unknown);
                  IfCondNode ifCondNode =
                      new IfCondNode(id.get(), unknown, unknown, "if", buildNotNull(attr));
                  ifCondNode.addChild(newAttrNode);
                  ifNode.addChild(ifCondNode);
                  replacementNode = ifNode;
                }
              } else {
                throw new IllegalArgumentException(
                    "Unexpected attribute " + attrNode.toSourceString());
              }

              attrNode.getParent().replaceChild(attrNode, replacementNode);
            });

    if (templateNode.getAllowExtraAttributes()) {
      templateNode.setReservedAttributes(foundNormalAttr.build());
      if (iAmAnElementCallingAnElement) {
        delegatingElementsWithAllAttrs.add(templateNode);
      }

      TemplateParam attrsParam =
          new TemplateParam(
              TemplateType.ATTRIBUTES_HIDDEN_PARAM,
              SourceLocation.UNKNOWN,
              SourceLocation.UNKNOWN,
              NamedTypeNode.create(SourceLocation.UNKNOWN, TemplateType.ATTRIBUTES_HIDDEN_PARAM),
              /* isInjected= */ false,
              /* isImplicit= */ true,
              /* optional= */ true,
              /* desc= */ "Created by ElementAttributePass.",
              /* defaultValue= */ null);
      templateNode.addParam(attrsParam);
      attrsParam.setType(SanitizedType.AttributesType.getInstance());
      // To add a whitespace between the tag and the print node. This has no effect in Incremental
      // DOM.
      openTagNode.addChild(new RawTextNode(id.get(), " ", unknown));
      IfNode ifNode = buildPrintIfNotNull(attrsParam, id);
      openTagNode.addChild(ifNode);
    }

    warnUnusedAttributes(unseenParams);
  }

  private void updateReservedAttributesForDelegateCalls(
      Set<TemplateNode> templates,
      Map<String, TemplateNode> localTemplateLookup,
      ImportsTemplateRegistry templateRegistry) {

    Map<String, String> templateFqnCall =
        templates.stream()
            .collect(toMap(TemplateNode::getTemplateName, ElementAttributePass::getElementCall));

    // Simple topological sort.
    while (!templateFqnCall.isEmpty()) {
      List<Map.Entry<String, String>> leaves =
          templateFqnCall.entrySet().stream()
              .filter(e -> !templateFqnCall.containsKey(e.getValue()))
              .collect(Collectors.toList());
      if (leaves.isEmpty()) {
        throw new IllegalArgumentException("Cyclical graph: " + templateFqnCall);
      }
      for (Map.Entry<String, String> leaf : leaves) {
        TemplateNode caller = localTemplateLookup.get(leaf.getKey());
        TemplateNode callee = localTemplateLookup.get(leaf.getValue());
        ImmutableSet<String> reservedAttr;
        if (callee != null) {
          reservedAttr = callee.getReservedAttributes();
        } else {
          reservedAttr =
              templateRegistry
                  .getBasicTemplateOrElement(leaf.getValue())
                  .getTemplateType()
                  .getReservedAttributes();
        }
        caller.setReservedAttributes(
            ImmutableSet.<String>builder()
                .addAll(caller.getReservedAttributes())
                .addAll(reservedAttr)
                .build());
        templateFqnCall.remove(leaf.getKey());
      }
    }
  }

  private static String getElementCall(TemplateNode templateNode) {
    return getElementCall(getElementOpen(templateNode).get());
  }

  private static String getElementCall(HtmlOpenTagNode openTag) {
    // The normal TagName.isTemplateCall() doesn't work this early in the pass manager.
    TagName tagName = openTag.getTagName();
    if (tagName.isStatic()) {
      return null;
    }
    PrintNode printNode = tagName.getDynamicTagName();
    ExprNode exprNode = printNode.getExpr().getRoot();
    if (!(exprNode.getKind() == ExprNode.Kind.METHOD_CALL_NODE
        && ((MethodCallNode) exprNode).getMethodName().identifier().equals("bind"))) {
      return null;
    }

    MethodCallNode bind = (MethodCallNode) exprNode;
    if (bind.getChild(0).getKind() != ExprNode.Kind.TEMPLATE_LITERAL_NODE) {
      return null;
    }

    return ((TemplateLiteralNode) bind.getChild(0)).getResolvedName();
  }

  private static void copyChildren(HtmlAttributeNode from, ParentSoyNode<StandaloneNode> to) {
    Iterator<StandaloneNode> i = from.getChildren().iterator();
    i.next(); // skip the attribute name
    while (i.hasNext()) {
      StandaloneNode child = i.next();
      if (child instanceof HtmlAttributeValueNode) {
        for (StandaloneNode node : ((HtmlAttributeValueNode) child).getChildren()) {
          to.addChild(node.copy(new CopyState()));
        }
      } else {
        to.addChild(child.copy(new CopyState()));
      }
    }
  }

  private void checkAttributeRefs(TemplateNode templateNode, Set<AttrParam> attrs) {
    // No standard var refs to @attribute params are allowed.
    SoyTreeUtils.getAllNodesOfType(templateNode, VarRefNode.class).stream()
        .filter(ref -> attrs.contains(ref.getDefnDecl()))
        .forEach(
            attrRef ->
                errorReporter.report(attrRef.getSourceLocation(), ATTRIBUTE_USED_OUTSIDE_OF_TAG));
  }

  private void warnUnusedAttributes(Iterable<AttrParam> unseenParams) {
    Streams.stream(unseenParams)
        .forEach(attrParam -> errorReporter.warn(attrParam.getSourceLocation(), UNUSED_ATTRIBUTE));
  }

  static Optional<HtmlOpenTagNode> getElementOpen(TemplateNode node) {
    // TODO(user): Dedupe logic with SoyElementPass?
    return node.getChildren().stream()
        .filter(n -> n.getKind() == Kind.HTML_OPEN_TAG_NODE)
        .map(HtmlOpenTagNode.class::cast)
        .findFirst();
  }
}
