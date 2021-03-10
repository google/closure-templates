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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.TemplateContentKind.ElementContentKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basicfunctions.ConcatAttributeValuesFunction;
import com.google.template.soy.basicfunctions.ConcatCssValuesFunction;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode.Quotes;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.AttrParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.ast.NamedTypeNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Enforces rules on the usage of @attribute parameters within a template. Rewrites templates for
 * implicit @attribute usage.
 */
@RunAfter({
  ResolveNamesPass.class, // Needs full template names resolved.
  ResolveTemplateParamTypesPass.class,
  SoyElementPass.class // Uses HtmlElementMetadataP
})
@RunBefore({
  FinalizeTemplateRegistryPass.class, // Mutates template metadata
  SoyElementCompositionPass.class,
  AutoescaperPass.class // since it inserts print directives
})
final class ElementAttributePass implements CompilerFileSetPass {

  private static final SoyErrorKind DELTEMPLATE_USING_ELEMENT_CONTENT_KIND =
      SoyErrorKind.of("Deltemplates cannot set kind=\"html<...>\".");

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
      SoyErrorKind.of("Attributes must be of type string or a sanitized type.");

  private static final SoyErrorKind ROOT_TAG_KIND_MISMATCH =
      SoyErrorKind.of("Expected root tag to be {0}.");

  private static final SoyErrorKind DELEGATE_KIND_MISMATCH =
      SoyErrorKind.of("Expected the called template to have root tag {0}, found {1}.");

  private static final SoySourceFunction concatCssFunction = new ConcatCssValuesFunction();

  private static final SoySourceFunction concatAttributesFunction =
      new ConcatAttributeValuesFunction();

  private final ErrorReporter errorReporter;
  private final PluginResolver pluginResolver;
  private final Supplier<TemplateRegistry> templateRegistryFromDeps;

  ElementAttributePass(
      ErrorReporter errorReporter,
      PluginResolver pluginResolver,
      Supplier<TemplateRegistry> templateRegistryFromDeps) {
    this.errorReporter = errorReporter;
    this.pluginResolver = pluginResolver;
    this.templateRegistryFromDeps = templateRegistryFromDeps;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    // Collect all elements in this compilation first. This cache will be used to look up elements,
    // followed by the secondary cache libRegistry, which includes all elements from deps.
    ImmutableMap<String, TemplateNode> allElementsThisCompile =
        sourceFiles.stream()
            .flatMap(fn -> fn.getTemplates().stream())
            .filter(
                t ->
                    !(t instanceof TemplateDelegateNode)
                        && t.getTemplateContentKind() instanceof ElementContentKind
                        && t.getHtmlElementMetadata() != null)
            .collect(toImmutableMap(TemplateNode::getTemplateName, t -> t));

    for (SoyFileNode file : sourceFiles) {
      run(file, allElementsThisCompile, idGenerator);
    }

    checkRootElementTagNames(allElementsThisCompile.values());

    return Result.CONTINUE;
  }

  private void run(
      SoyFileNode file,
      ImmutableMap<String, TemplateNode> allElementsThisCompile,
      IdGenerator nodeIdGen) {
    checkAttributeTypes(file);

    ImmutableSet.Builder<TemplateNode> delegatingElementsWithAllAttrs = ImmutableSet.builder();

    // Rewrite all @attribute values in root elements.
    file.getTemplates().stream()
        .filter(
            t ->
                t.getTemplateContentKind() instanceof ElementContentKind
                    && t.getHtmlElementMetadata() != null)
        .forEach(
            t -> {
              if (t instanceof TemplateDelegateNode) {
                errorReporter.report(
                    t.getOpenTagLocation(), DELTEMPLATE_USING_ELEMENT_CONTENT_KIND);
                return;
              }
              processTemplate(t, nodeIdGen::genId, delegatingElementsWithAllAttrs::add);
            });

    // All other @attributes (outside of root elements) are illegal.
    file.getTemplates().stream()
        .filter(t -> t.getHtmlElementMetadata() != null && getDelegateCall(t).isEmpty())
        .forEach(
            t ->
                SoyTreeUtils.allNodesOfType(t, HtmlAttributeNode.class)
                    .map(HtmlAttributeNode.class::cast)
                    .filter(HtmlAttributeNode::isSoyAttr)
                    .forEach(
                        attr ->
                            errorReporter.report(
                                attr.getSourceLocation(),
                                ATTRIBUTE_PARAM_NOT_ALLOWED,
                                attr.getStaticKey())));

    updateReservedAttributesForDelegateCalls(
        delegatingElementsWithAllAttrs.build(), allElementsThisCompile);
  }

  private <T extends Node> void checkAttributeTypes(SoyFileNode file) {
    file.getTemplates().stream()
        .flatMap(t -> t.getHeaderParams().stream())
        .filter(p -> p instanceof AttrParam)
        .map(AttrParam.class::cast)
        .forEach(
            attr -> {
              SoyType type = SoyTypes.removeNull(attr.type());
              if (!(type instanceof SanitizedType || type instanceof StringType)
                  || SanitizedType.HtmlType.getInstance().isAssignableFromStrict(type)) {
                errorReporter.report(attr.getSourceLocation(), BAD_ATTRIBUTE_TYPE);
              }
            });
  }

  private void processTemplate(
      TemplateNode templateNode,
      Supplier<Integer> id,
      Consumer<TemplateNode> delegatingElementsWithAllAttrs) {
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
    String delegateTemplateName = getDelegateCall(templateNode);
    boolean iAmAnElementCallingAnElement = !delegateTemplateName.isEmpty();
    SoySourceFunction isNonnull =
        (SoySourceFunction)
            pluginResolver.lookupSoyFunction("isNonnull", 1, SourceLocation.UNKNOWN);
    ImmutableSet.Builder<String> foundNormalAttr = ImmutableSet.builder();
    SoyTreeUtils.allNodesOfType(openTagNode, HtmlAttributeNode.class)
        .filter(attr -> attr.getStaticKey() != null)
        .forEach(
            attrNode -> {
              String attrKey = attrNode.getStaticKey();
              if (attrKey.equals(AddDebugAttributesPass.DATA_DEBUG_SOY)) {
                return;
              }
              // Remove the @ at the beginning of the attribute.
              boolean isSoyAttr = attrNode.isSoyAttr();
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
              unseenParams.remove(attr);
              VarRefNode attrExpr = new VarRefNode("$" + attr.name(), unknown, attr);

              final StandaloneNode replacementNode;
              if (attrNode.hasValue() && attr.isRequired()) {
                errorReporter.report(
                    attrNode.getSourceLocation(), ATTRIBUTE_NOT_REQUIRED, attr.getAttrName());
              }

              if (!attrNode.hasValue() && iAmAnElementCallingAnElement && !isSoyAttr) {
                // Pass through and handle in SoyElementCompositionPass since we cannot encode
                // null/absent in an HtmlAttributeNode.
                return;
              }

              // Creates a new HTML attribute containing the original name with the @ chopped off
              HtmlAttributeNode newAttrNode =
                  new HtmlAttributeNode(id.get(), unknown, UNKNOWN_POINT);
              newAttrNode.addChild(((RawTextNode) attrNode.getChild(0)).substring(id.get(), 1));

              HtmlAttributeValueNode valueNode =
                  new HtmlAttributeValueNode(id.get(), unknown, Quotes.DOUBLE);
              newAttrNode.addChild(valueNode);

              if (attrNode.getConcatenationDelimiter() == null && attr.isRequired()) {
                // No concatenation, required param. incoming param is always non-null so use it.
                // Generates: id="{$id}"
                PrintNode printNode =
                    new PrintNode(
                        id.get(), unknown, true, attrExpr, ImmutableList.of(), errorReporter);
                printNode.getExpr().setType(attrExpr.getType());
                valueNode.addChild(printNode);
                replacementNode = newAttrNode;
              } else if (attrNode.getConcatenationDelimiter() == null && attrNode.hasValue()) {
                // No concatenation, with a default value. Use if/else to use either the default or
                // override. Generates: id="{isNonnull($id) ? $id : $idDefault}"
                IfNode ifNode = SoyTreeUtils.buildPrintIfNotNull(attrExpr, id, isNonnull);
                valueNode.addChild(ifNode);
                IfElseNode ifElseNode = new IfElseNode(id.get(), unknown, unknown);
                ifNode.addChild(ifElseNode);
                copyChildren(attrNode, ifElseNode);
                replacementNode = newAttrNode;
              } else {
                // In these cases, we need to conditionally suppress the entire attribute. Either
                // a concatenating attribute, or an attribute without a default.
                final VarRefNode outputValueExpr;
                if (attrNode.getConcatenationDelimiter() != null && attrNode.hasValue()) {
                  // Concatenating attribute, with a default. Concatenate the default and incoming
                  // values together.
                  outputValueExpr =
                      concatAttributeValues(
                          openTagNode,
                          attrNode,
                          attrExpr,
                          SanitizedType.StyleType.getInstance()
                              .isAssignableFromStrict(SoyTypes.removeNull(attr.type())),
                          id,
                          unknown);
                } else {
                  // No default, use incoming parameter for the attribute value. Generates
                  // id="{$id}"
                  outputValueExpr = attrExpr;
                }
                PrintNode printNode =
                    new PrintNode(
                        id.get(),
                        unknown,
                        true,
                        outputValueExpr,
                        ImmutableList.of(),
                        errorReporter);
                printNode.getExpr().setType(outputValueExpr.getType());
                valueNode.addChild(printNode);
                // In the event that the attribute value is an empty string/null, we should not emit
                // the attribute at all. If the attribute is concatenating, check falsiness so that
                // empty string will suppress the attribute; e.g. if there are multiple conditional
                // classes that all are suppressed, suppress the entire attribute. Otherwise, check
                // for null so that empty string could be passed in. Generates:
                // {if $concatAttributeValues}class="{$concatAttributeValues}"{/if}
                IfNode ifNode = new IfNode(id.get(), unknown);
                IfCondNode ifCondNode =
                    new IfCondNode(
                        id.get(),
                        unknown,
                        unknown,
                        "if",
                        attrNode.getConcatenationDelimiter() == null
                            ? SoyTreeUtils.buildNotNull(outputValueExpr, isNonnull)
                            : outputValueExpr.copy(new CopyState()));
                ifCondNode.getExpr().setType(BoolType.getInstance());
                ifCondNode.addChild(newAttrNode);
                ifNode.addChild(ifCondNode);
                replacementNode = ifNode;
              }

              attrNode.getParent().replaceChild(attrNode, replacementNode);
            });
    /*
     * This will generate the following code:
     *
     * <pre>
     * {template .foo}
     *   {@param soyInternalAttributes:attributes}
     *   <div {$soyInternalAttributes}></div>
     * {/template}
     * </pre>
     */
    if (templateNode.getAllowExtraAttributes()) {
      templateNode.setReservedAttributes(foundNormalAttr.build());
      if (iAmAnElementCallingAnElement) {
        delegatingElementsWithAllAttrs.accept(templateNode);
      }

      TemplateParam attrsParam =
          new TemplateParam(
              TemplateType.ATTRIBUTES_HIDDEN_PARAM_NAME,
              SourceLocation.UNKNOWN,
              SourceLocation.UNKNOWN,
              NamedTypeNode.create(
                  SourceLocation.UNKNOWN, TemplateType.ATTRIBUTES_HIDDEN_PARAM_NAME),
              /* isInjected= */ false,
              /* isImplicit= */ true,
              /* optional= */ true,
              /* desc= */ "Created by ElementAttributePass.",
              /* defaultValue= */ null);
      VarRefNode extraAttributesRef =
          new VarRefNode("$" + attrsParam.name(), SourceLocation.UNKNOWN, attrsParam);
      templateNode.addParam(attrsParam);
      attrsParam.setType(SoyTypes.makeNullable(SanitizedType.AttributesType.getInstance()));

      // This requires a different handling than SoyTreeUtils.printIfNotNull because we need to
      // put an HTMLAttributeNode inside it so that we can concatenate using a whitespace.
      IfNode ifNode = new IfNode(id.get(), unknown);
      IfCondNode ifCondNode =
          new IfCondNode(
              id.get(),
              unknown,
              unknown,
              "if",
              SoyTreeUtils.buildNotNull(extraAttributesRef, isNonnull));
      ifCondNode.getExpr().setType(BoolType.getInstance());
      ifNode.addChild(ifCondNode);
      HtmlAttributeNode htmlAttributeNode = new HtmlAttributeNode(id.get(), unknown, null);
      PrintNode printNode =
          new PrintNode(
              id.get(), unknown, true, extraAttributesRef, ImmutableList.of(), exploding());
      printNode.getExpr().setType(extraAttributesRef.getType());
      htmlAttributeNode.addChild(printNode);
      ifCondNode.addChild(htmlAttributeNode);

      openTagNode.addChild(ifNode);
    }

    warnUnusedAttributes(unseenParams);
  }

  /**
   * Adds {let} commands before the open tag to perform attribute concatenation and returns a
   * variable reference to the merged value. In order to properly concatentate defaults and incoming
   * attributes, we need to first put all of the default into a {let} and then pass them into a soy
   * function called concatAttributeValues. This will possibly omit the delimiter if one of the
   * values is nullish. Generates
   *
   * <pre>
   *   {let $__letContent_}class1 class2{/let}
   *   {let $__letValue_: $$concatAttributeValues($class, $letContent) /}
   * </pre>
   *
   * @param openTagNode The tag containing the attribute. {let}s will be inserted right before it
   * @param attrNode The Attribute to perform concatenation
   * @param attrExpr VarRefNode of incoming attribute
   * @param isCss Whether the attribute is a CSS attribute
   * @param id Id generator
   * @param location SourceLocation to add to all synthetic nodes
   * @return VarRefNode to the concatenated attribute value
   */
  private static VarRefNode concatAttributeValues(
      HtmlOpenTagNode openTagNode,
      HtmlAttributeNode attrNode,
      VarRefNode attrExpr,
      boolean isCss,
      Supplier<Integer> id,
      SourceLocation location) {
    LetContentNode letContentNode =
        LetContentNode.forVariable(
            id.get(),
            location,
            "$__internal_soy_letContent_" + id.get(),
            location,
            isCss ? SanitizedContentKind.CSS : SanitizedContentKind.TEXT);
    openTagNode
        .getParent()
        .addChild(openTagNode.getParent().getChildIndex(openTagNode), letContentNode);
    copyChildren(attrNode, letContentNode);
    VarRefNode letRef =
        new VarRefNode(
            letContentNode.getVarRefName(), SourceLocation.UNKNOWN, letContentNode.getVar());
    SoySourceFunction soyFn = isCss ? concatCssFunction : concatAttributesFunction;
    FunctionNode fn =
        FunctionNode.newPositional(
            Identifier.create("$$concatAttributeValues", location), soyFn, location);
    fn.setType(isCss ? SanitizedType.StyleType.getInstance() : StringType.getInstance());
    fn.addChild(attrExpr);
    fn.addChild(letRef);
    if (!isCss) {
      fn.addChild(
          new StringNode(attrNode.getConcatenationDelimiter(), QuoteStyle.SINGLE, location));
    }
    if (isCss) {
      fn.setAllowedParamTypes(
          ImmutableList.of(
              SoyTypes.makeNullable(SanitizedType.StyleType.getInstance()),
              SoyTypes.makeNullable(SanitizedType.StyleType.getInstance())));
    } else {
      fn.setAllowedParamTypes(
          ImmutableList.of(
              SoyTypes.makeNullable(StringType.getInstance()),
              SoyTypes.makeNullable(StringType.getInstance()),
              StringType.getInstance()));
    }
    LetValueNode letValueNode =
        new LetValueNode(id.get(), location, "$__internal_soy_letValue_" + id.get(), location, fn);
    letValueNode.getVar().setType(fn.getType());
    letValueNode.getExpr().setType(fn.getType());
    letContentNode
        .getParent()
        .addChild(letContentNode.getParent().getChildIndex(letContentNode) + 1, letValueNode);
    return new VarRefNode(
        letValueNode.getVarRefName(), SourceLocation.UNKNOWN, letValueNode.getVar());
  }

  private void updateReservedAttributesForDelegateCalls(
      ImmutableSet<TemplateNode> templates,
      ImmutableMap<String, TemplateNode> localTemplateLookup) {

    Map<String, String> templateFqnCall =
        templates.stream()
            .collect(toMap(TemplateNode::getTemplateName, ElementAttributePass::getDelegateCall));

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
              templateRegistryFromDeps
                  .get()
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

  private void checkRootElementTagNames(ImmutableCollection<TemplateNode> elements) {
    for (TemplateNode node : elements) {
      ElementContentKind contentKind = (ElementContentKind) node.getTemplateContentKind();
      String expectedTagName = contentKind.getTagName();
      // html<?> matches anything
      if (expectedTagName.isEmpty()) {
        continue;
      }

      String tag = node.getHtmlElementMetadata().getTag();
      if (!"?".equals(tag) && !expectedTagName.equals(tag)) {
        TagName tagName = getElementOpen(node).get().getTagName();
        if (tagName.isStatic()) {
          errorReporter.report(tagName.getTagLocation(), ROOT_TAG_KIND_MISMATCH, expectedTagName);
        } else {
          errorReporter.report(
              tagName.getTagLocation(), DELEGATE_KIND_MISMATCH, expectedTagName, tag);
        }
      }
    }
  }

  /**
   * Returns the FQN template name of the template to which this element delegates, or null if this
   * template does not delegate.
   */
  private static String getDelegateCall(TemplateNode templateNode) {
    return templateNode.getHtmlElementMetadata().getDelegateElement();
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
    SoyTreeUtils.allNodesOfType(templateNode, VarRefNode.class)
        .filter(ref -> attrs.contains(ref.getDefnDecl()))
        .forEach(
            attrRef ->
                errorReporter.report(attrRef.getSourceLocation(), ATTRIBUTE_USED_OUTSIDE_OF_TAG));
  }

  private void warnUnusedAttributes(Iterable<AttrParam> unseenParams) {
    unseenParams.forEach(
        attrParam -> errorReporter.warn(attrParam.getSourceLocation(), UNUSED_ATTRIBUTE));
  }

  static Optional<HtmlOpenTagNode> getElementOpen(TemplateNode node) {
    return SoyTreeUtils.allNodesOfType(node, HtmlOpenTagNode.class)
        .filter(HtmlOpenTagNode::isElementRoot)
        .findFirst();
  }
}
