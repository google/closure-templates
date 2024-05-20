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
import static com.google.template.soy.base.SourceLocation.UNKNOWN;
import static java.util.stream.Collectors.toCollection;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basicfunctions.BasicFunctions;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.parsepasses.contextautoesc.ContextualAutoescaper;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CommandTagAttribute;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.KeyNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SkipNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.AttrParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SanitizedType.AttributesType;
import com.google.template.soy.types.SanitizedType.StyleType;
import com.google.template.soy.types.SanitizedType.TrustedResourceUriType;
import com.google.template.soy.types.SanitizedType.UriType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.TemplateType.Parameter;
import com.google.template.soy.types.UndefinedType;
import com.google.template.soy.types.UnionType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Rewrites element calls with attributes and slots as regular calls.
 *
 * <p>Rewrites {@code <{legacyTagName($tag)}>} to {@code <{$tag}>} and disallows all other print
 * nodes that name HTML tags.
 */
@RunAfter({ResolveExpressionTypesPass.class, FinalizeTemplateRegistryPass.class})
final class SoyElementCompositionPass implements CompilerFileSetPass {

  private static final SoyErrorKind ILLEGAL_CHILD =
      SoyErrorKind.of("Only HTML attributes are allowed as children of this template call.");

  private static final SoyErrorKind DUPLICATE_ATTRIBUTE =
      SoyErrorKind.of("Attribute specified multiple times.");

  private static final SoyErrorKind SKIP_NODE_NOT_ALLOWED =
      SoyErrorKind.of("Skip nodes are not allowed on this template call.");

  private static final SoyErrorKind EXTRA_ATTRIBUTES_NOT_ALLOWED =
      SoyErrorKind.of(
          "The called template does not specify '''{'attribute *'}''' so only "
              + "static HTML attributes are allowed.");

  private final ErrorReporter errorReporter;
  private final ImmutableList<? extends SoyPrintDirective> printDirectives;
  private final Supplier<FileSetMetadata> templateRegistryFull;
  private final boolean desugarIdomFeatures;

  SoyElementCompositionPass(
      ErrorReporter errorReporter,
      ImmutableList<? extends SoyPrintDirective> printDirectives,
      Supplier<FileSetMetadata> templateRegistryFull,
      boolean desugarIdomFeatures) {
    this.errorReporter = errorReporter;
    this.printDirectives = printDirectives;
    this.templateRegistryFull = templateRegistryFull;
    this.desugarIdomFeatures = desugarIdomFeatures;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    if (errorReporter.hasErrors()) {
      return Result.CONTINUE;
    }
    for (SoyFileNode file : sourceFiles) {
      run(file, idGenerator);
    }
    return Result.CONTINUE;
  }

  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode template : file.getTemplates()) {
      for (HtmlTagNode tagNode : SoyTreeUtils.getAllNodesOfType(template, HtmlTagNode.class)) {
        process(template, tagNode, nodeIdGen);
      }
    }
  }

  private void process(TemplateNode template, HtmlTagNode tagNode, IdGenerator nodeIdGen) {
    TagName name = tagNode.getTagName();
    if (name.isStatic()) {
      return;
    }
    PrintNode printNode = name.getDynamicTagName();
    if (!tagNode.getTagName().isTemplateCall()) {
      return;
    }

    Preconditions.checkState(tagNode.getTaggedPairs().size() <= 1);
    SourceLocation unknown = template.getSourceLocation().clearRange();
    ImmutableMap<String, AttrParam> attrs =
        template.getAllParams().stream()
            .filter(AttrParam.class::isInstance)
            .map(AttrParam.class::cast)
            .collect(toImmutableMap(AttrParam::name, Function.identity()));

    SourceLocation location = tagNode.getSourceLocation();
    if (!tagNode.getTaggedPairs().isEmpty()) {
      location = location.extend(tagNode.getTaggedPairs().get(0).getSourceLocation());
    }
    CallBasicNode call =
        new CallBasicNode(
            nodeIdGen.genId(),
            location,
            unknown,
            printNode.getExpr().getRoot().copy(new CopyState()),
            ImmutableList.of(),
            false,
            errorReporter);

    if (tagNode instanceof HtmlOpenTagNode) {
      HtmlOpenTagNode openTagNode = (HtmlOpenTagNode) tagNode;
      ContextualAutoescaper.annotateAndRewriteHtmlTag(
          openTagNode, templateRegistryFull.get(), nodeIdGen, errorReporter, printDirectives);
      // When element compositioning a template, check the following cases:
      // 1. A key is present (an element type is element compositioning) - The key must be passed
      // forward in addition to the key on the current element.
      // 2. The key is not null - must be passed forward
      // 3. Key is null. No need to pass along anyhting.
      if (desugarIdomFeatures) {
        Optional<TemplateParam> keyParam =
            template.getParams().stream()
                .filter(p -> p.name().equals(TemplateType.KEY_HIDDEN_ATTRIBUTE_NAME))
                .findFirst();
        ExprNode expr;
        if (openTagNode.getKeyNode() == null) {
          if (keyParam.isPresent()) {
            expr =
                new VarRefNode("$" + keyParam.get().name(), SourceLocation.UNKNOWN, keyParam.get());
          } else {
            expr = null;
          }
        } else {
          expr = DesugarStateNodesPass.extractKeyFunctionFromHtmlTag(openTagNode);
        }
        // This is an optional parameter so there is no need to pass a literal null.
        if (expr != null) {
          call.addChild(
              new CallParamValueNode(
                  nodeIdGen.genId(),
                  openTagNode.getSourceLocation(),
                  Identifier.create(
                      TemplateType.KEY_HIDDEN_ATTRIBUTE_NAME, openTagNode.getSourceLocation()),
                  expr));
        }
      }
    }

    TemplateType templateType = (TemplateType) call.getCalleeExpr().getRoot().getType();
    CallParamContentNode attributesNode =
        new CallParamContentNode(
            nodeIdGen.genId(),
            location,
            UNKNOWN,
            Identifier.create(TemplateType.EXTRA_ROOT_ELEMENT_ATTRIBUTES, UNKNOWN),
            new CommandTagAttribute(
                Identifier.create("kind", UNKNOWN),
                QuoteStyle.SINGLE,
                "attributes",
                UNKNOWN,
                UNKNOWN),
            errorReporter);
    if (!tagNode.getTaggedPairs().isEmpty()) {
      HtmlTagNode closeTag = tagNode.getTaggedPairs().get(0);
      List<String> params =
          templateType.getParameters().stream()
              .filter(p -> SoyTypes.transitivelyContainsKind(p.getType(), SoyType.Kind.HTML))
              .map(Parameter::getName)
              .collect(toCollection(ArrayList::new));
      StandaloneNode next = (StandaloneNode) SoyTreeUtils.nextSibling(tagNode);
      if (params.size() != 1
          || (next instanceof HtmlOpenTagNode && ((HtmlOpenTagNode) next).isSlot())) {
        while (next != closeTag) {
          next = consumeSlot(call, next, nodeIdGen);
          if (next == null) {
            return;
          }
        }
      } else if (params.size() == 1) {
        CallParamContentNode callParamContent =
            new CallParamContentNode(
                nodeIdGen.genId(),
                unknown,
                unknown,
                Identifier.create(params.get(0), unknown),
                new CommandTagAttribute(
                    Identifier.create("kind", unknown),
                    QuoteStyle.SINGLE,
                    "html",
                    unknown,
                    unknown),
                errorReporter);
        call.addChild(callParamContent);
        while (next != closeTag) {
          StandaloneNode sibling = (StandaloneNode) SoyTreeUtils.nextSibling(next);
          next.getParent().removeChild(next);
          callParamContent.addChild(next);
          next = sibling;
        }
      }
      closeTag.getParent().removeChild(closeTag);
    }
    call.getCalleeExpr().setType(printNode.getExpr().getType());
    call.setHtmlContext(HtmlContext.HTML_PCDATA);
    tagNode.getParent().replaceChild(tagNode, call);

    ImmutableMap<String, TemplateType.Parameter> parameterMap = templateType.getParameterMap();

    Set<String> seenAttr = new HashSet<>();
    tagNode.getChildren().stream()
        .skip(1) // skip the first print node with the function call
        .forEach(
            c -> {
              if (c.getKind() == SoyNode.Kind.IF_NODE) {
                IfNode ifNode = (IfNode) c;
                if (ifNode.numChildren() != 1) {
                  errorReporter.report(c.getSourceLocation(), ILLEGAL_CHILD);
                  return;
                }
                IfCondNode ifCond = (IfCondNode) ifNode.getChild(0);
                LetValueNode letValueNode =
                    new LetValueNode(
                        nodeIdGen.genId(),
                        unknown,
                        "$__internal_call_" + nodeIdGen.genId(),
                        unknown,
                        ifCond.getExpr().getRoot().copy(new CopyState()));
                letValueNode.getVar().setType(ifCond.getExpr().getRoot().getType());
                call.getParent().addChild(call.getParent().getChildIndex(call), letValueNode);
                for (StandaloneNode child : ifCond.getChildren()) {
                  VarRefNode ref =
                      new VarRefNode(letValueNode.getVarRefName(), unknown, letValueNode.getVar());
                  ref.setSubstituteType(letValueNode.getVar().type());
                  maybeConsumeAttribute(
                      child,
                      call,
                      nodeIdGen,
                      seenAttr,
                      parameterMap,
                      attrs,
                      attributesNode,
                      Optional.of(ref));
                }
              } else if (c instanceof KeyNode) {
                call.setKeyExpr(((KeyNode) c).getExpr().copy(new CopyState()));
              } else if (c instanceof SkipNode) {
                errorReporter.report(c.getSourceLocation(), SKIP_NODE_NOT_ALLOWED);
              } else {
                maybeConsumeAttribute(
                    c,
                    call,
                    nodeIdGen,
                    seenAttr,
                    parameterMap,
                    attrs,
                    attributesNode,
                    Optional.empty());
              }
            });

    if (attributesNode != null && attributesNode.numChildren() > 0) {
      if (templateType.getAllowExtraAttributesOrExplicit()) {
        call.addChild(attributesNode);
      } else {
        errorReporter.report(tagNode.getSourceLocation(), EXTRA_ATTRIBUTES_NOT_ALLOWED);
      }
    }

    ResolveTemplateNamesPass.updateTemplateLiteralsStaticCallProperty(call);
  }

  private void maybeConsumeAttribute(
      StandaloneNode c,
      CallBasicNode call,
      IdGenerator nodeIdGen,
      Set<String> seenAttr,
      Map<String, TemplateType.Parameter> parameterMap,
      Map<String, AttrParam> attrs,
      @Nullable CallParamContentNode attributesNode,
      Optional<ExprNode> conditional) {
    if (c.getKind() == SoyNode.Kind.HTML_ATTRIBUTE_NODE) {
      HtmlAttributeNode attrNode = (HtmlAttributeNode) c;
      if (ElementAttributePass.getStaticOrMergingKey(attrNode) != null) {
        CallParamNode param =
            consumeAttribute(
                attrNode,
                nodeIdGen,
                seenAttr,
                parameterMap,
                attrs,
                attributesNode,
                call,
                conditional);
        if (param != null) {
          param.setOriginalName(ElementAttributePass.getStaticOrMergingKey(attrNode));
          call.addChild(param);
        }
        return;
      } else if (attrNode.numChildren() == 1 && attributesNode != null) {
        if (isOkToPutInElement(attrNode)) {
          maybePrintAttribute(attributesNode, conditional, nodeIdGen, attrNode);
          return;
        }
      }
    }

    errorReporter.report(c.getSourceLocation(), ILLEGAL_CHILD);
  }

  private static void maybePrintAttribute(
      CallParamContentNode attributesNode,
      Optional<ExprNode> conditional,
      IdGenerator nodeIdGen,
      HtmlAttributeNode attrNode) {
    SourceLocation unknown = attributesNode.getSourceLocation().clearRange();
    if (conditional.isPresent()) {
      IfNode ifNode = new IfNode(nodeIdGen.genId(), unknown);
      ifNode.setHtmlContext(HtmlContext.HTML_TAG);
      IfCondNode ifCondNode =
          new IfCondNode(
              nodeIdGen.genId(), unknown, unknown, "if", conditional.get().copy(new CopyState()));
      ifNode.addChild(ifCondNode);
      ifCondNode.getExpr().setType(conditional.get().getType());
      ifCondNode.addChild(attrNode.copy(new CopyState()));
      attributesNode.addChild(ifNode);
    } else {
      attributesNode.addChild(attrNode.copy(new CopyState()));
    }
  }

  static boolean isOkToPutInElement(HtmlAttributeNode attrNode) {
    // Any print node or call node whose type/kind is 'attributes' may appear within the root
    // element HTML node.
    return (attrNode.getChild(0).getKind() == Kind.PRINT_NODE
            && SoyTypes.makeNullish(AttributesType.getInstance())
                .isAssignableFromStrict(((PrintNode) attrNode.getChild(0)).getExpr().getType()))
        || (attrNode.getChild(0).getKind() == Kind.CALL_BASIC_NODE
            && ((TemplateType) ((CallBasicNode) attrNode.getChild(0)).getCalleeExpr().getType())
                    .getContentKind()
                    .getSanitizedContentKind()
                == SanitizedContentKind.ATTRIBUTES);
  }

  private StandaloneNode consumeSlot(
      CallBasicNode callNode, SoyNode startNode, IdGenerator nodeIdGen) {
    SourceLocation unknown = startNode.getSourceLocation().clearRange();
    HtmlOpenTagNode nextOpenTag = (HtmlOpenTagNode) startNode;
    String paramName = ((HtmlAttributeNode) nextOpenTag.getChild(1)).getStaticContent();
    HtmlTagNode closeTag = nextOpenTag.getTaggedPairs().get(0);
    CallParamContentNode callParamContent =
        new CallParamContentNode(
            nodeIdGen.genId(),
            startNode.getSourceLocation(),
            unknown,
            Identifier.create(paramName, unknown),
            new CommandTagAttribute(
                Identifier.create("kind", unknown),
                QuoteStyle.SINGLE,
                "html",
                startNode.getSourceLocation().extend(closeTag.getSourceLocation()),
                unknown),
            errorReporter);
    callNode.addChild(callParamContent);
    SoyNode.StandaloneNode next = (SoyNode.StandaloneNode) SoyTreeUtils.nextSibling(nextOpenTag);
    while (next != closeTag) {
      SoyNode.StandaloneNode sibling = (SoyNode.StandaloneNode) SoyTreeUtils.nextSibling(next);
      next.getParent().removeChild(next);
      callParamContent.addChild(next);
      next = sibling;
    }
    nextOpenTag.getParent().removeChild(nextOpenTag);
    SoyNode retNode = SoyTreeUtils.nextSibling(closeTag);
    closeTag.getParent().removeChild(closeTag);
    return (StandaloneNode) retNode;
  }

  private static boolean hasLoggingFunction(HtmlAttributeNode node) {
    return SoyTreeUtils.allNodesOfType(node, FunctionNode.class)
        .anyMatch(
            fnNode -> !fnNode.isResolved() || fnNode.getSoyFunction() instanceof LoggingFunction);
  }

  @Nullable
  private CallParamNode consumeAttribute(
      HtmlAttributeNode attr,
      IdGenerator nodeIdGen,
      Set<String> seenAttr,
      Map<String, TemplateType.Parameter> parameterMap,
      Map<String, AttrParam> attrs,
      CallParamContentNode attributesNode,
      CallBasicNode call,
      Optional<ExprNode> condition) {
    SourceLocation unknown = attr.getSourceLocation().clearRange();

    String attrName = ElementAttributePass.getStaticOrMergingKey(attr);
    boolean isSoyAttr = attrName.startsWith("@");
    if (isSoyAttr) {
      attrName = attrName.substring(1);
    }

    if (!seenAttr.add(attrName)) {
      errorReporter.report(attr.getChild(0).getSourceLocation(), DUPLICATE_ATTRIBUTE);
      return null;
    }

    String paramName = attrName.contains("-") ? Parameter.attrToParamName(attrName) : attrName;
    if (!parameterMap.containsKey(paramName)) {
      // attributesNode can't be null, bad attrs caught in ResolveExpressionTypesCrossTemplatePass
      maybePrintAttribute(attributesNode, condition, nodeIdGen, attr);
      return null;
    }
    if (isSoyAttr) {
      ExprNode val = new VarRefNode("$" + paramName, unknown, attrs.get(paramName));
      Preconditions.checkState(
          !condition.isPresent(), "{if ...}@attr{/if} should not pass parser.");
      if (ElementAttributePass.getMergingKey(attr) == null) {
        return new CallParamValueNode(
            nodeIdGen.genId(),
            attr.getSourceLocation(),
            Identifier.create(paramName, attr.getChild(0).getSourceLocation()),
            val);
      } else {
        // Element that calls another element.
        //
        // ElementAttributePass rewrites:
        // @class="foo"
        // as
        // buildAttr('class', ['foo', param$foo])
        //
        // Now unpack it back to:
        // {param class: buildClassValue('foo', param$foo) /}
        return new CallParamValueNode(
            nodeIdGen.genId(),
            attr.getSourceLocation(),
            Identifier.create(paramName, unknown),
            getMergingValueExpr(attr));
      }
    }
    StandaloneNode value = attr.getChild(1);
    if (value.getKind() != Kind.HTML_ATTRIBUTE_VALUE_NODE) {
      return null;
    }
    HtmlAttributeValueNode attrValue = (HtmlAttributeValueNode) value;

    // Create a new CallParamContentNode and copy the HtmlAttributeValue children into it, if:
    // 1) there is no condition, or 2) the value is a logging function, which can't go into a
    // CallParamValueNode.
    if (!condition.isPresent() || hasLoggingFunction(attr)) {
      CopyState copyState = new CopyState();
      CallParamContentNode contentNode =
          new CallParamContentNode(
              nodeIdGen.genId(),
              attr.getSourceLocation(),
              unknown,
              Identifier.create(paramName, attr.getChild(0).getSourceLocation()),
              // TODO(tomnguyen) Verify that @attribute is only string or uri or string|uri.
              new CommandTagAttribute(
                  Identifier.create("kind", unknown),
                  QuoteStyle.SINGLE,
                  getKind(parameterMap.get(paramName).getType()),
                  unknown,
                  unknown),
              errorReporter);

      // The node to copy HtmlAttributeValue children into.
      ParentSoyNode<StandaloneNode> valueParent = contentNode;

      if (condition.isPresent()) {
        // There is a condition so this value is a logging function. Since we're using a content
        // node, when the condition evaluates to false, the content node will evaluate to empty
        // string. This means when the condition is false we'll get an empty attribute like
        // `data-ved=""`, since the callee elides the attribute only when passed null.
        //
        // We could fix this by using a sentinel value i.e.:
        // `{if $cond}{loggingFunction()}{else}__OMIT_SENTINEL__{/if}` -- but we'd need to check
        // for it it at every attribute. We could also possibly change the semantics of attribute
        // merging such that sending an empty string also elides the entire attribute.
        IfNode ifNode = new IfNode(nodeIdGen.genId(), attrValue.getSourceLocation());
        ifNode.setHtmlContext(HtmlContext.HTML_TAG);
        contentNode.addChild(ifNode);
        IfCondNode ifCondNode =
            new IfCondNode(
                nodeIdGen.genId(),
                attrValue.getSourceLocation(),
                attrValue.getSourceLocation(),
                "if",
                condition.get().copy(new CopyState()));
        ifNode.addChild(ifCondNode);
        ifCondNode.getExpr().setType(condition.get().getType());
        valueParent = ifCondNode;
      }
      for (StandaloneNode node : attrValue.getChildren()) {
        valueParent.addChild(node.copy(copyState));
      }
      return contentNode;
    }

    /*
     * Otherwise, construct a {let} for each attribute and pass them as values in the map. We need
     * to assign to a variable first so we can turn empty string into a null so the callee elides
     * the entire attribute. Note that we can't do this for logging functions since they won't
     * survive being placed in the let.
     *
     * {let $__internal_call_someAttr_0 kind="text"}{if $cond}...{/if}{/let}
     * {call foo}
     *   {param someAttr: $$emptyToUndefined($__internal_call_someAttr_0) /}
     * {/call}
     */
    LetContentNode letContentNode =
        LetContentNode.forVariable(
            nodeIdGen.genId(),
            unknown,
            "$__internal_call_" + paramName + nodeIdGen.genId(),
            unknown,
            parameterMap.containsKey(paramName)
                ? SanitizedContentKind.fromAttributeValue(
                        getKind(parameterMap.get(paramName).getType()))
                    .get()
                : SanitizedContentKind.TEXT);
    call.getParent().addChild(call.getParent().getChildIndex(call), letContentNode);
    IfNode ifNode = new IfNode(nodeIdGen.genId(), unknown);
    ifNode.setHtmlContext(HtmlContext.HTML_TAG);
    letContentNode.addChild(ifNode);
    IfCondNode ifCondNode =
        new IfCondNode(
            nodeIdGen.genId(), unknown, unknown, "if", condition.get().copy(new CopyState()));
    ifNode.addChild(ifCondNode);
    ifCondNode.getExpr().setType(condition.get().getType());
    CopyState copyState = new CopyState();
    for (StandaloneNode node : attrValue.getChildren()) {
      ifCondNode.addChild(node.copy(copyState));
    }
    VarRefNode varRef =
        new VarRefNode("$" + letContentNode.getVar().name(), unknown, letContentNode.getVar());
    return new CallParamValueNode(
        nodeIdGen.genId(),
        unknown,
        Identifier.create(paramName, unknown),
        emptyToUndefined(varRef));
  }

  private static ExprNode emptyToUndefined(ExprNode val) {
    var functionNode =
        FunctionNode.newPositional(
            Identifier.create(
                BuiltinFunction.EMPTY_TO_UNDEFINED.getName(), val.getSourceLocation()),
            BuiltinFunction.EMPTY_TO_UNDEFINED,
            val.getSourceLocation());
    functionNode.setType(UnionType.of(UndefinedType.getInstance(), val.getType()));
    functionNode.addChild(val);
    return functionNode;
  }

  private static String getKind(SoyType attrType) {
    attrType = SoyTypes.tryRemoveNullish(attrType);
    if (TrustedResourceUriType.getInstance().isAssignableFromStrict(attrType)) {
      return "trusted_resource_uri";
    } else if (UriType.getInstance().isAssignableFromStrict(attrType)) {
      return "uri";
    } else if (StyleType.getInstance().isAssignableFromStrict(attrType)) {
      return "css";
    } else {
      return "text";
    }
  }

  private FunctionNode getMergingValueExpr(HtmlAttributeNode attr) {
    PrintNode printNode = (PrintNode) attr.getChild(0);
    FunctionNode func = (FunctionNode) printNode.getExpr().getRoot();
    FunctionNode newFunc =
        ElementAttributePass.getMergingKey(attr).equals("@class")
            ? FunctionNode.newPositional(
                Identifier.create("buildClassValue", SourceLocation.UNKNOWN),
                BasicFunctions.BUILD_CLASS_VALUE_FUNCTION,
                SourceLocation.UNKNOWN)
            : ElementAttributePass.getMergingKey(attr).equals("@style")
                ? FunctionNode.newPositional(
                    Identifier.create("buildStyleValue", SourceLocation.UNKNOWN),
                    BasicFunctions.BUILD_STYLE_VALUE_FUNCTION,
                    SourceLocation.UNKNOWN)
                : FunctionNode.newPositional(
                    Identifier.create("buildAttrValue", SourceLocation.UNKNOWN),
                    BasicFunctions.BUILD_ATTR_VALUE_FUNCTION,
                    SourceLocation.UNKNOWN);
    ImmutableList.Builder<SoyType> allowedTypes = ImmutableList.builder();
    Iterator<ExprNode> i = func.getChildren().iterator();
    i.next(); // skip the attribute name
    while (i.hasNext()) {
      newFunc.addChild(i.next().copy(new CopyState()));
      allowedTypes.add(
          UnionType.of(
              StringType.getInstance(),
              StyleType.getInstance(),
              BoolType.getInstance(),
              NullType.getInstance(),
              UndefinedType.getInstance()));
    }
    newFunc.setType(
        ElementAttributePass.getMergingKey(attr).equals("@style")
            ? StyleType.getInstance()
            : StringType.getInstance());
    newFunc.setAllowedParamTypes(allowedTypes.build());
    return newFunc;
  }
}
