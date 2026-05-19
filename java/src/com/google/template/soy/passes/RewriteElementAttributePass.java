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
import static com.google.template.soy.base.SourceLocation.UNKNOWN;
import static com.google.template.soy.error.ErrorReporter.exploding;

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
import com.google.template.soy.compilermetrics.Impression;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractParentExprNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
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
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.AttrParam;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SanitizedType.AttributesType;
import com.google.template.soy.types.SanitizedType.StyleType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.UnionType;
import com.google.template.soy.types.UnknownType;
import com.google.template.soy.types.ast.NamedTypeNode;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Enforces rules on the usage of @attribute parameters within a template that must run after type
 * resolution. Rewrites templates for implicit @attribute usage.
 */
@RunAfter({
  ResolveNamesPass.class, // Needs full template names resolved.
  ResolveExpressionTypesPass.class,
  SoyElementPass.class // Uses HtmlElementMetadataP
})
@RunBefore({
  FinalizeTemplateRegistryPass.class, // Mutates template metadata
  SoyElementCompositionPass.class,
  AutoescaperPass.class // since it inserts print directives
})
final class RewriteElementAttributePass implements CompilerFileSetPass {

  private static final SoyErrorKind BAD_ATTRIBUTE_TYPE =
      SoyErrorKind.of(
          "Attributes must be of type string or a sanitized type.",
          Impression.ERROR_ELEMENT_ATTRIBUTE_PASS_BAD_ATTRIBUTE_TYPE);

  private static final SoyErrorKind ATTRIBUTE_STAR_AND_EXPLICIT =
      SoyErrorKind.of(
          "Cannot specify a param named ''{0}'' along with ''attribute *''.",
          Impression.ERROR_ELEMENT_ATTRIBUTE_PASS_ATTRIBUTE_STAR_AND_EXPLICIT);

  private static final SoyErrorKind ATTRIBUTE_PARAM_NOT_ALLOWED =
      SoyErrorKind.of(
          "Attribute ''{0}'' can only be present on root elements of html<?> templates.",
          Impression.ERROR_ELEMENT_ATTRIBUTE_PASS_ATTRIBUTE_PARAM_NOT_ALLOWED);

  private static final SoyErrorKind EXTRA_ROOT_ELEMENT_ATTRIBUTES_TYPE =
      SoyErrorKind.of(
          "Param ''{0}'' must be optional and of type 'attributes'.",
          Impression.ERROR_ELEMENT_ATTRIBUTE_PASS_EXTRA_ROOT_ELEMENT_ATTRIBUTES_TYPE);

  private final ErrorReporter errorReporter;
  private final boolean desugarIdomPasses;

  RewriteElementAttributePass(ErrorReporter errorReporter, boolean desugarIdomPasses) {
    this.errorReporter = errorReporter;
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
    printNode.getExpr().setType(node.getType());
    ifCondNode.addChild(printNode);
    return ifNode;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      run(file, idGenerator);
    }

    return Result.CONTINUE;
  }

  private void run(SoyFileNode file, IdGenerator nodeIdGen) {
    checkAttributeTypes(file);

    // Rewrite all @attribute values in root elements.
    file.getTemplates().stream()
        .filter(
            t ->
                t.getTemplateContentKind() instanceof ElementContentKind
                    && t.getHtmlElementMetadata() != null)
        .forEach(
            t -> {
              if (t instanceof TemplateDelegateNode) {
                return;
              }
              processTemplate(t, nodeIdGen::genId);
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
  }

  private void checkAttributeTypes(SoyFileNode file) {
    file.getTemplates().stream()
        .flatMap(t -> t.getHeaderParams().stream())
        .filter(AttrParam.class::isInstance)
        .map(AttrParam.class::cast)
        .forEach(
            attr -> {
              SoyType type = SoyTypes.excludeNullish(attr.type());
              if (!(type instanceof SanitizedType || type instanceof StringType)
                  || SanitizedType.HtmlType.getInstance().isAssignableFromStrict(type)) {
                errorReporter.report(attr.getSourceLocation(), BAD_ATTRIBUTE_TYPE);
              }
            });
  }

  private void processTemplate(TemplateNode templateNode, Supplier<Integer> id) {
    ImmutableMap<String, AttrParam> attrs =
        templateNode.getAllParams().stream()
            .filter(AttrParam.class::isInstance)
            .map(AttrParam.class::cast)
            .collect(toImmutableMap(AttrParam::getAttrName, Function.identity()));

    HtmlOpenTagNode openTagNode = getElementOpen(templateNode);
    if (openTagNode == null) {
      return;
    }
    SourceLocation unknown = templateNode.getSourceLocation().clearRange();

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
                return;
              }

              if (!attrs.containsKey(attrName)) {
                return;
              }

              AttrParam attr = attrs.get(attrName);
              if (!TemplateType.Parameter.isValidAttrName(attrName)) {
                errorReporter.report(
                    attr.getSourceLocation(), MoreCallValidationsPass.BAD_ATTRIBUTE_NAME);
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
    keyParam.setType(SoyTypes.unionWithUndefined(StringType.getInstance()));
    templateNode.addParam(keyParam);

    if (desugarIdomPasses && openTagNode.getTagName().isStatic()) {
      VarRefNode keyParamRef = new VarRefNode("$" + keyParam.name(), UNKNOWN, keyParam);

      // This produces the following code:
      // {if $ssk}ssk="{$ssk + xid('some-template-root')}"{/if}
      IfNode ifNode = new IfNode(id.get(), unknown);
      IfCondNode ifCondNode =
          new IfCondNode(id.get(), unknown, unknown, "if", buildNotNull(keyParamRef));
      ifCondNode.getExpr().setType(BoolType.getInstance());
      ifNode.addChild(ifCondNode);
      HtmlAttributeNode htmlAttributeNode =
          new HtmlAttributeNode(id.get(), UNKNOWN, SourceLocation.Point.UNKNOWN_POINT);
      htmlAttributeNode.addChild(
          new RawTextNode(id.get(), TemplateType.KEY_HIDDEN_ATTRIBUTE_NAME, UNKNOWN));
      HtmlAttributeValueNode valueNode =
          new HtmlAttributeValueNode(id.get(), UNKNOWN, Quotes.SINGLE);
      String tplName =
          templateNode.getHtmlElementMetadata().getFinalCallee().isEmpty()
              ? templateNode.getTemplateName()
              : templateNode.getHtmlElementMetadata().getFinalCallee();
      FunctionNode wrappedFn =
          FunctionNode.newPositional(
              Identifier.create(BuiltinFunction.SOY_SERVER_KEY.getName(), UNKNOWN),
              BuiltinFunction.SOY_SERVER_KEY,
              UNKNOWN);
      wrappedFn.setType(StringType.getInstance());
      ExprNode result;
      if (openTagNode.getKeyNode() == null) {
        FunctionNode funcNode =
            FunctionNode.newPositional(
                Identifier.create(BuiltinFunction.XID.getName(), UNKNOWN),
                BuiltinFunction.XID,
                UNKNOWN);
        funcNode.addChild(new StringNode(tplName + "-root", QuoteStyle.SINGLE, UNKNOWN));
        funcNode.setType(StringType.getInstance());
        funcNode.setAllowedParamTypes(ImmutableList.of(StringType.getInstance()));
        wrappedFn.addChild(funcNode);
        result = Operator.PLUS.createNode(UNKNOWN, UNKNOWN, wrappedFn, keyParamRef);
        ((AbstractParentExprNode) result).setType(StringType.getInstance());
      } else {
        wrappedFn.addChild(openTagNode.getKeyNode().getExpr().getRoot().copy(new CopyState()));
        result = wrappedFn;
      }
      wrappedFn.setAllowedParamTypes(ImmutableList.of(UnknownType.getInstance()));
      PrintNode printNode =
          new PrintNode(id.get(), unknown, true, result, ImmutableList.of(), exploding());
      printNode.getExpr().setType(wrappedFn.getType());
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
        if (!SoyTypes.excludeNullish(param.type())
                .isEffectivelyEqual(SanitizedType.AttributesType.getInstance())
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
      attrsParam.setType(SoyTypes.unionWithUndefined(SanitizedType.AttributesType.getInstance()));
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
      printNode.getExpr().setType(extraAttributesRef.getType());
      htmlAttributeNode.addChild(printNode);
      ifCondNode.addChild(htmlAttributeNode);

      openTagNode.addChild(ifNode);
    }
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
    staticValuePrint.getExpr().setType(attrExpr.getType());

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
            .isAssignableFromStrict(SoyTypes.excludeNullish(attr.type()));
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
        new VarRefNode(letContentNode.getVarRefName(), UNKNOWN, letContentNode.getVar());
    func.addChild(new VarRefNode("$" + attr.name(), unknown, attr));
    func.addChild(letRef);
    func.setType(AttributesType.getInstance());
    func.setIsVarArgs(true);
    func.setAllowedParamTypes(
        ImmutableList.of(
            StringType.getInstance(),
            UnionType.of(StringType.getInstance(), StyleType.getInstance())));

    PrintNode printNode =
        new PrintNode(id.get(), unknown, true, func, ImmutableList.of(), errorReporter);
    printNode.getExpr().setType(func.getType());

    HtmlAttributeNode newAttrNode =
        new HtmlAttributeNode(id.get(), attrNode.getSourceLocation(), null);
    newAttrNode.addChild(printNode);
    return newAttrNode;
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
      if (child instanceof HtmlAttributeValueNode valueNode) {
        for (StandaloneNode node : valueNode.getChildren()) {
          to.addChild(node.copy(new CopyState()));
        }
      } else {
        to.addChild(child.copy(new CopyState()));
      }
    }
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
    if (!(node.getChild(0) instanceof PrintNode printNode)) {
      return null;
    }
    if (!(printNode.getExpr().getRoot() instanceof FunctionNode func)) {
      return null;
    }
    if (func.hasStaticName()
        && func.getStaticFunctionName().equals("buildAttr")
        && func.getChild(0) instanceof StringNode stringNode) {
      return "@" + stringNode.getValue();
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
