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
import com.google.template.soy.basicfunctions.BasicFunctions;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode.Quotes;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.AttrParam;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.treebuilder.ExprNodes;
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
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Enforces rules on the usage of @attribute parameters within a template. Rewrites templates for
 * implicit @attribute usage.
 */
@RunAfter({
  ResolveNamesPass.class, // Needs full template names resolved.
  ResolveDeclaredTypesPass.class,
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

  private static final SoyErrorKind ATTRIBUTE_STAR_AND_EXPLICIT =
      SoyErrorKind.of("Cannot specify a param named ''{0}'' along with ''attribute *''.");
  private static final SoyErrorKind EXTRA_ROOT_ELEMENT_ATTRIBUTES_TYPE =
      SoyErrorKind.of("Param ''{0}'' must be optional and of type 'attributes'.");

  private final ErrorReporter errorReporter;
  private final Supplier<FileSetMetadata> templateRegistryFromDeps;
  private final boolean desugarIdomPasses;

  ElementAttributePass(
      ErrorReporter errorReporter,
      Supplier<FileSetMetadata> templateRegistryFromDeps,
      boolean desugarIdomPasses) {
    this.errorReporter = errorReporter;
    this.templateRegistryFromDeps = templateRegistryFromDeps;
    this.desugarIdomPasses = desugarIdomPasses;
  }

  private static ExprNode buildNotNull(ExprNode node) {
    SourceLocation unknown = node.getSourceLocation().clearRange();
    NotEqualOpNode ne = new NotEqualOpNode(unknown, unknown);
    ne.addChild(node.copy(new CopyState()));
    ne.addChild(new NullNode(unknown));
    ne.setType(BoolType.getInstance());
    return ne;
  }

  private static IfNode buildPrintIfNotNull(ExprNode node, Supplier<Integer> id) {
    SourceLocation unknown = node.getSourceLocation().clearRange();
    IfNode ifNode = new IfNode(id.get(), unknown);
    IfCondNode ifCondNode = new IfCondNode(id.get(), unknown, unknown, "if", buildNotNull(node));
    ifCondNode.getExpr().setType(BoolType.getInstance());
    ifNode.addChild(ifCondNode);
    PrintNode printNode =
        new PrintNode(id.get(), unknown, true, node, ImmutableList.of(), exploding());
    printNode.getExpr().setType(node.getAuthoredType());
    ifCondNode.addChild(printNode);
    return ifNode;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    // Collect all elements in this compilation first. This cache will be used to look up elements,
    // followed by the secondary cache libRegistry, which includes all elements from deps.
    ImmutableMap<String, TemplateNode> allAstElements =
        sourceFiles.stream()
            .flatMap(fn -> fn.getTemplates().stream())
            .filter(
                t ->
                    !(t instanceof TemplateDelegateNode)
                        && t.getTemplateContentKind() instanceof ElementContentKind
                        && t.getHtmlElementMetadata() != null)
            .collect(toImmutableMap(TemplateNode::getTemplateName, t -> t));

    for (SoyFileNode file : sourceFiles) {
      run(file, allAstElements, idGenerator);
    }

    checkRootElementTagNames(allAstElements.values());

    return Result.CONTINUE;
  }

  private void run(
      SoyFileNode file, ImmutableMap<String, TemplateNode> allAstElements, IdGenerator nodeIdGen) {
    checkAttributeTypes(file);

    ImmutableList.Builder<TemplateNode> delegatingElementsWithAllAttrs = ImmutableList.builder();

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
                    .filter(HtmlAttributeNode::isSoyAttr)
                    .forEach(
                        attr ->
                            errorReporter.report(
                                attr.getSourceLocation(),
                                ATTRIBUTE_PARAM_NOT_ALLOWED,
                                attr.getStaticKey())));

    updateReservedAttributesForDelegateCalls(
        delegatingElementsWithAllAttrs.build(), allAstElements);
  }

  private void checkAttributeTypes(SoyFileNode file) {
    file.getTemplates().stream()
        .flatMap(t -> t.getHeaderParams().stream())
        .filter(AttrParam.class::isInstance)
        .map(AttrParam.class::cast)
        .forEach(
            attr -> {
              SoyType type = SoyTypes.tryRemoveNullish(attr.type());
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
            .filter(AttrParam.class::isInstance)
            .map(AttrParam.class::cast)
            .collect(toImmutableMap(AttrParam::getAttrName, Function.identity()));

    Set<AttrParam> unseenParams = new HashSet<>(attrs.values());
    checkAttributeRefs(templateNode, unseenParams);

    HtmlOpenTagNode openTagNode = getElementOpen(templateNode);
    if (openTagNode == null) {
      return;
    }
    SourceLocation unknown = templateNode.getSourceLocation().clearRange();

    String delegateTemplateName = getDelegateCall(templateNode);
    boolean iAmAnElementCallingAnElement = !delegateTemplateName.isEmpty();
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
              if (!TemplateType.Parameter.isValidAttrName(attrName)) {
                errorReporter.report(
                    attr.getSourceLocation(), MoreCallValidationsPass.BAD_ATTRIBUTE_NAME);
              }
              unseenParams.remove(attr);

              if (attrNode.hasValue() && attr.isRequired()) {
                errorReporter.report(
                    attrNode.getSourceLocation(), ATTRIBUTE_NOT_REQUIRED, attr.getAttrName());
              }

              attrNode
                  .getParent()
                  .replaceChild(
                      attrNode,
                      !isConcatenatedAttribute(attrNode) || !attrNode.hasValue()
                          ? noConcatRewrite(attrNode, attr, id, unknown)
                          : concatRewrite(openTagNode, attrNode, attr, id, unknown));
            });
    // This param is added unconditionally because TemplateType expects it to be populated. However,
    // outside of IDOM this is unused.
    NamedTypeNode typeNode = NamedTypeNode.create(unknown, "string");
    typeNode.setResolvedType(StringType.getInstance());
    TemplateParam keyParam =
        new TemplateParam(
            TemplateType.KEY_HIDDEN_ATTRIBUTE_NAME,
            unknown,
            unknown,
            typeNode,
            /* isInjected= */ false,
            /* isImplicit= */ true,
            /* optional= */ true,
            /* desc= */ "Created by ElementAttributePass.",
            /* defaultValue= */ null);
    keyParam.setType(SoyTypes.makeUndefinable(StringType.getInstance()));
    templateNode.addParam(keyParam);

    if (desugarIdomPasses && openTagNode.getTagName().isStatic()) {
      VarRefNode keyParamRef =
          new VarRefNode("$" + keyParam.name(), SourceLocation.UNKNOWN, keyParam);

      // This produces the following code:
      // {if $ssk}ssk="{$ssk + xid('some-template-root')}"{/if}
      IfNode ifNode = new IfNode(id.get(), unknown);
      IfCondNode ifCondNode =
          new IfCondNode(id.get(), unknown, unknown, "if", buildNotNull(keyParamRef));
      ifCondNode.getExpr().setType(BoolType.getInstance());
      ifNode.addChild(ifCondNode);
      HtmlAttributeNode htmlAttributeNode =
          new HtmlAttributeNode(
              id.get(), SourceLocation.UNKNOWN, SourceLocation.Point.UNKNOWN_POINT);
      htmlAttributeNode.addChild(
          new RawTextNode(
              id.get(), TemplateType.KEY_HIDDEN_ATTRIBUTE_NAME, SourceLocation.UNKNOWN));
      HtmlAttributeValueNode valueNode =
          new HtmlAttributeValueNode(id.get(), SourceLocation.UNKNOWN, Quotes.SINGLE);
      String tplName =
          templateNode.getHtmlElementMetadata().getFinalCallee().isEmpty()
              ? templateNode.getTemplateName()
              : templateNode.getHtmlElementMetadata().getFinalCallee();
      FunctionNode wrappedFn =
          FunctionNode.newPositional(
              Identifier.create(BuiltinFunction.SOY_SERVER_KEY.getName(), SourceLocation.UNKNOWN),
              BuiltinFunction.SOY_SERVER_KEY,
              SourceLocation.UNKNOWN);
      wrappedFn.setType(StringType.getInstance());
      ExprNode result;
      if (openTagNode.getKeyNode() == null) {
        FunctionNode funcNode =
            FunctionNode.newPositional(
                Identifier.create(BuiltinFunction.XID.getName(), SourceLocation.UNKNOWN),
                BuiltinFunction.XID,
                SourceLocation.UNKNOWN);
        funcNode.addChild(
            new StringNode(tplName + "-root", QuoteStyle.SINGLE, SourceLocation.UNKNOWN));
        funcNode.setType(StringType.getInstance());
        wrappedFn.addChild(funcNode);
        result = ExprNodes.plus(wrappedFn, keyParamRef);
      } else {
        wrappedFn.addChild(openTagNode.getKeyNode().getExpr().getRoot().copy(new CopyState()));
        result = wrappedFn;
      }
      PrintNode printNode =
          new PrintNode(id.get(), unknown, true, result, ImmutableList.of(), exploding());
      printNode.getExpr().setType(wrappedFn.getAuthoredType());
      valueNode.addChild(printNode);
      htmlAttributeNode.addChild(valueNode);
      ifCondNode.addChild(htmlAttributeNode);

      openTagNode.addChild(ifNode);
      if (openTagNode.getKeyNode() != null) {
        openTagNode.removeChild(openTagNode.getKeyNode());
      }
    }
    for (TemplateHeaderVarDefn param : templateNode.getHeaderParams()) {
      if (param.name().equals(TemplateType.EXTRA_ROOT_ELEMENT_ATTRIBUTES)) {
        if (templateNode.getAllowExtraAttributes()) {
          errorReporter.report(
              param.getSourceLocation(),
              ATTRIBUTE_STAR_AND_EXPLICIT,
              TemplateType.EXTRA_ROOT_ELEMENT_ATTRIBUTES);
        }
        if (!SoyTypes.tryRemoveNullish(param.type())
                .equals(SanitizedType.AttributesType.getInstance())
            || param.isRequired()) {
          errorReporter.report(
              param.getSourceLocation(),
              EXTRA_ROOT_ELEMENT_ATTRIBUTES_TYPE,
              TemplateType.EXTRA_ROOT_ELEMENT_ATTRIBUTES);
        }
      }
    }

    if (templateNode.getAllowExtraAttributes()) {
      /*
       * This will generate the following code:
       *
       * <pre>
       * {template foo}
       *   {@param extraRootElementAttributes:attributes}
       *   <div {$extraRootElementAttributes}></div>
       * {/template}
       * </pre>
       */
      SourceLocation loc = templateNode.getAllowExtraAttributesLoc();
      typeNode = NamedTypeNode.create(loc, "attributes");
      typeNode.setResolvedType(SanitizedType.AttributesType.getInstance());
      TemplateParam attrsParam =
          new TemplateParam(
              TemplateType.EXTRA_ROOT_ELEMENT_ATTRIBUTES,
              loc,
              loc,
              typeNode,
              /* isInjected= */ false,
              /* isImplicit= */ true,
              /* optional= */ true,
              /* desc= */ "Created by ElementAttributePass.",
              /* defaultValue= */ null);
      VarRefNode extraAttributesRef = new VarRefNode("$" + attrsParam.name(), loc, attrsParam);
      templateNode.addParam(attrsParam);
      attrsParam.setType(SoyTypes.makeUndefinable(SanitizedType.AttributesType.getInstance()));
      // This requires a different handling than SoyTreeUtils.printIfNotNull because we need to
      // put an HTMLAttributeNode inside it so that we can concatenate using a whitespace.
      IfNode ifNode = new IfNode(id.get(), unknown);
      IfCondNode ifCondNode =
          new IfCondNode(id.get(), unknown, unknown, "if", buildNotNull(extraAttributesRef));
      ifCondNode.getExpr().setType(BoolType.getInstance());
      ifNode.addChild(ifCondNode);
      HtmlAttributeNode htmlAttributeNode = new HtmlAttributeNode(id.get(), unknown, null);
      PrintNode printNode =
          new PrintNode(
              id.get(), unknown, true, extraAttributesRef, ImmutableList.of(), exploding());
      printNode.getExpr().setType(extraAttributesRef.getAuthoredType());
      htmlAttributeNode.addChild(printNode);
      ifCondNode.addChild(htmlAttributeNode);

      openTagNode.addChild(ifNode);
      templateNode.setReservedAttributes(foundNormalAttr.build());
      if (iAmAnElementCallingAnElement) {
        delegatingElementsWithAllAttrs.accept(templateNode);
      }
    }
    warnUnusedAttributes(unseenParams);
  }

  private StandaloneNode noConcatRewrite(
      HtmlAttributeNode attrNode, AttrParam attr, Supplier<Integer> id, SourceLocation unknown) {

    // Creates a new HTML attribute containing the original name with the @ chopped off
    HtmlAttributeNode newAttrNode =
        new HtmlAttributeNode(id.get(), attrNode.getSourceLocation(), UNKNOWN_POINT);
    newAttrNode.addChild(((RawTextNode) attrNode.getChild(0)).substring(id.get(), 1));

    HtmlAttributeValueNode valueNode = new HtmlAttributeValueNode(id.get(), unknown, Quotes.DOUBLE);
    newAttrNode.addChild(valueNode);

    VarRefNode attrExpr = new VarRefNode("$" + attr.name(), unknown, attr);
    PrintNode staticValuePrint =
        new PrintNode(id.get(), unknown, true, attrExpr, ImmutableList.of(), errorReporter);
    staticValuePrint.getExpr().setType(attrExpr.getAuthoredType());

    if (attr.isRequired()) {
      // No concatenation, required param. incoming param is always non-null so use it.
      // Generates: id="{$id}"
      valueNode.addChild(staticValuePrint);
      return newAttrNode;
    } else if (attrNode.hasValue()) {
      // No concatenation, with a default value. Use if/else to use either the default or
      // override. Generates: id="{$id != null ? $id : $idDefault}"
      IfNode ifNode = buildPrintIfNotNull(attrExpr, id);
      valueNode.addChild(ifNode);
      IfElseNode ifElseNode = new IfElseNode(id.get(), unknown, unknown);
      ifNode.addChild(ifElseNode);
      copyChildren(attrNode, ifElseNode);
      return newAttrNode;
    } else {
      // Attribute without a default, generate: {if $id}id="$id"{/if}
      valueNode.addChild(staticValuePrint);
      IfNode ifNode = new IfNode(id.get(), unknown);
      IfCondNode ifCondNode =
          new IfCondNode(
              id.get(),
              unknown,
              unknown,
              "if",
              // Concatentaing attributes omit the entire attribute if the incoming param is empty.
              isConcatenatedAttribute(attrNode)
                  ? attrExpr.copy(new CopyState())
                  : buildNotNull(attrExpr));
      ifCondNode.getExpr().setType(BoolType.getInstance());
      ifCondNode.addChild(newAttrNode);
      ifNode.addChild(ifCondNode);
      return ifNode;
    }
  }

  private StandaloneNode concatRewrite(
      HtmlOpenTagNode openTagNode,
      HtmlAttributeNode attrNode,
      AttrParam attr,
      Supplier<Integer> id,
      SourceLocation unknown) {
    FunctionNode func =
        FunctionNode.newPositional(
            Identifier.create("buildAttr", attrNode.getSourceLocation()),
            BasicFunctions.BUILD_ATTR_FUNCTION,
            attrNode.getSourceLocation());
    func.addChild(
        new StringNode(
            attrNode.getStaticKey().substring(1), QuoteStyle.DOUBLE, unknown, /* isXid= */ false));
    boolean isCss =
        SanitizedType.StyleType.getInstance()
            .isAssignableFromStrict(SoyTypes.tryRemoveNullish(attr.type()));
    LetContentNode letContentNode =
        LetContentNode.forVariable(
            id.get(),
            unknown,
            "$__internal_soy_letContent_" + id.get(),
            unknown,
            isCss ? SanitizedContentKind.CSS : SanitizedContentKind.TEXT);
    openTagNode
        .getParent()
        .addChild(openTagNode.getParent().getChildIndex(openTagNode), letContentNode);
    copyChildren(attrNode, letContentNode);
    VarRefNode letRef =
        new VarRefNode(
            letContentNode.getVarRefName(), SourceLocation.UNKNOWN, letContentNode.getVar());
    func.addChild(new VarRefNode("$" + attr.name(), unknown, attr));
    func.addChild(letRef);

    PrintNode printNode =
        new PrintNode(id.get(), unknown, true, func, ImmutableList.of(), errorReporter);

    HtmlAttributeNode newAttrNode =
        new HtmlAttributeNode(id.get(), attrNode.getSourceLocation(), null);
    newAttrNode.addChild(printNode);
    return newAttrNode;
  }

  private void updateReservedAttributesForDelegateCalls(
      ImmutableList<TemplateNode> templates, ImmutableMap<String, TemplateNode> allAstElements) {

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
        TemplateNode callee = allAstElements.get(leaf.getValue());
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
        TemplateNode caller = allAstElements.get(leaf.getKey());
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
      if ("?".equals(tag) || expectedTagName.equals(tag)) {
        continue;
      }

      HtmlOpenTagNode openTag = getElementOpen(node);
      SourceLocation errorLoc = null;
      if (openTag != null) {
        errorLoc = openTag.getTagName().getTagLocation();
      } else if (node.getChildren().size() == 1
          && node.getChild(0).getKind() == Kind.CALL_BASIC_NODE) {
        errorLoc = ((CallBasicNode) node.getChild(0)).getOpenTagLocation();
      }

      if (errorLoc == null) {
        // Error caught in earlier pass
        continue;
      }

      if (openTag != null && openTag.getTagName().isStatic()) {
        errorReporter.report(errorLoc, ROOT_TAG_KIND_MISMATCH, expectedTagName);
      } else {
        errorReporter.report(errorLoc, DELEGATE_KIND_MISMATCH, expectedTagName, tag);
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

  static void copyChildren(HtmlAttributeNode from, ParentSoyNode<StandaloneNode> to) {
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

  @Nullable
  static HtmlOpenTagNode getElementOpen(TemplateNode node) {
    return SoyTreeUtils.allNodesOfType(node, HtmlOpenTagNode.class)
        .filter(HtmlOpenTagNode::isElementRoot)
        .findFirst()
        .orElse(null);
  }

  // If the attribute is a single call to the buildAttr() function, return the attribute key.
  @Nullable
  static String getMergingKey(HtmlAttributeNode node) {
    if (!(node.getChild(0) instanceof PrintNode)) {
      return null;
    }
    PrintNode printNode = (PrintNode) node.getChild(0);
    if (!(printNode.getExpr().getRoot() instanceof FunctionNode)) {
      return null;
    }
    FunctionNode func = (FunctionNode) printNode.getExpr().getRoot();
    if (func.hasStaticName()
        && func.getStaticFunctionName().equals("buildAttr")
        && func.getChild(0) instanceof StringNode) {
      return "@" + ((StringNode) func.getChild(0)).getValue();
    }
    return null;
  }

  @Nullable
  static String getStaticOrMergingKey(HtmlAttributeNode node) {
    return node.getStaticKey() != null ? node.getStaticKey() : getMergingKey(node);
  }

  private static final ImmutableSet<String> CONCATENATED_ATTRIBUTES =
      ImmutableSet.of("@class", "@style", "@jsdata", "@jsaction", "@jsmodel");

  private boolean isConcatenatedAttribute(HtmlAttributeNode node) {
    return getStaticOrMergingKey(node) != null
        && CONCATENATED_ATTRIBUTES.contains(getStaticOrMergingKey(node));
  }
}
