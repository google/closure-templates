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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CommandTagAttribute;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SanitizedType.TrustedResourceUriType;
import com.google.template.soy.types.SanitizedType.UriType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.TemplateType.Parameter;
import com.google.template.soy.types.UnionType;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Rewrites element calls with attributes and slots as regular calls.
 *
 * <p>Rewrites {@code <{legacyTagName($tag)}>} to {@code <{$tag}>} and disallows all other print
 * nodes that name HTML tags.
 */
@RunBefore(AutoescaperPass.class /* Creates trusted_resource_uri params. */)
@RunAfter(ResolveExpressionTypesPass.class)
final class SoyElementCompositionPass implements CompilerFileSetPass {

  private static final SoyErrorKind ILLEGAL_CHILD =
      SoyErrorKind.of("Only HTML attributes are allowed as children of this template call.");

  private static final SoyErrorKind DUPLICATE_ATTRIBUTE =
      SoyErrorKind.of("Attribute specified multiple times.");

  private final ErrorReporter errorReporter;

  SoyElementCompositionPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
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
    for (TemplateNode template : SoyTreeUtils.getAllNodesOfType(file, TemplateNode.class)) {
      for (HtmlTagNode tagNode : SoyTreeUtils.getAllNodesOfType(template, HtmlTagNode.class)) {
        process(template, tagNode, nodeIdGen);
      }
      // These parameters are effectively no longer used at runtime.
      template.removeAttributeParams();
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
    Optional<TemplateParam> attributesParam =
        template.getParams().stream()
            .filter(p -> p.name().equals(TemplateType.ATTRIBUTES_HIDDEN_PARAM))
            .findFirst();
    TemplateType templateType = (TemplateType) call.getCalleeExpr().getRoot().getType();
    ImmutableMap.Builder<ExprNode, ExprNode> attributesBuilder = new ImmutableMap.Builder<>();
    if (!tagNode.getTaggedPairs().isEmpty()) {
      HtmlTagNode closeTag = tagNode.getTaggedPairs().get(0);
      SoyNode next = SoyTreeUtils.nextSibling(tagNode);
      while (next != closeTag) {
        next = consumeSlot(call, next, nodeIdGen);
      }
      closeTag.getParent().removeChild(closeTag);
    }
    call.getCalleeExpr().setType(printNode.getExpr().getType());
    call.setHtmlContext(HtmlContext.HTML_PCDATA);
    tagNode.getParent().replaceChild(tagNode, call);

    Map<String, SoyType> parameterMap = templateType.getParameterMap();

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
                  processChildrenOfHtmlTagNode(
                      child,
                      nodeIdGen,
                      seenAttr,
                      attributesParam,
                      parameterMap,
                      attributesBuilder,
                      call,
                      Optional.of(
                          new VarRefNode(
                              letValueNode.getVarName(), unknown, letValueNode.getVar())));
                }
              } else {
                processChildrenOfHtmlTagNode(
                    c,
                    nodeIdGen,
                    seenAttr,
                    attributesParam,
                    parameterMap,
                    attributesBuilder,
                    call,
                    Optional.empty());
              }
            });
    ImmutableMap<ExprNode, ExprNode> attributes = attributesBuilder.build();
    MapLiteralNode map =
        new MapLiteralNode(Identifier.create("map", location), attributes, location);
    map.setType(MapType.of(StringType.getInstance(), StringType.getInstance()));
    CallParamValueNode attributesValueNode =
        new CallParamValueNode(
            nodeIdGen.genId(),
            location,
            Identifier.create(TemplateType.ATTRIBUTES_HIDDEN_PARAM, SourceLocation.UNKNOWN),
            map);
    call.addChild(attributesValueNode);
  }

  private SoyNode consumeSlot(CallBasicNode callNode, SoyNode startNode, IdGenerator nodeIdGen) {
    SourceLocation unknown = startNode.getSourceLocation().clearRange();
    HtmlOpenTagNode nextOpenTag = (HtmlOpenTagNode) startNode;
    HtmlAttributeNode attributeNode = (HtmlAttributeNode) nextOpenTag.getChild(1);
    HtmlTagNode closeTag = nextOpenTag.getTaggedPairs().get(0);
    CallParamContentNode callParamContent =
        new CallParamContentNode(
            nodeIdGen.genId(),
            startNode.getSourceLocation(),
            unknown,
            Identifier.create(attributeNode.getStaticKey(), unknown),
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
    return retNode;
  }

  private void processChildrenOfHtmlTagNode(
      StandaloneNode node,
      IdGenerator nodeIdGen,
      Set<String> seenAttr,
      Optional<TemplateParam> attributesParam,
      Map<String, SoyType> parameterMap,
      ImmutableMap.Builder<ExprNode, ExprNode> attributesBuilder,
      CallBasicNode call,
      Optional<ExprNode> conditional) {
    if (node.getKind() == SoyNode.Kind.HTML_ATTRIBUTE_NODE
        && ((HtmlAttributeNode) node).getStaticKey() != null) {
      consumeAttribute(
          (HtmlAttributeNode) node,
          nodeIdGen,
          seenAttr,
          attributesParam,
          parameterMap,
          attributesBuilder,
          call,
          conditional);
    } else {
      errorReporter.report(node.getSourceLocation(), ILLEGAL_CHILD);
    }
  }

  private void consumeAttribute(
      HtmlAttributeNode attr,
      IdGenerator nodeIdGen,
      Set<String> seenAttr,
      Optional<TemplateParam> attributesParam,
      Map<String, SoyType> parameterMap,
      ImmutableMap.Builder<ExprNode, ExprNode> attributes,
      CallBasicNode callNode,
      Optional<ExprNode> condition) {
    SourceLocation unknown = attr.getSourceLocation().clearRange();

    String attrName = attr.getStaticKey();
    boolean isSoyAttr = attrName.startsWith("@");
    if (isSoyAttr) {
      attrName = attrName.substring(1);
    }

    if (!seenAttr.add(attrName)) {
      errorReporter.report(attr.getChild(0).getSourceLocation(), DUPLICATE_ATTRIBUTE);
      return;
    }

    String paramName = Parameter.attrToParamName(attrName);
    /**
     * If this is an existing Soy attribute, then just use it verbatim. The code generated will look
     * like:
     *
     * <pre>
     *   {call foo}
     *     {param soyInternalAttributes: map(
     *         someAttr: soyInternalAttributes['someAttr']
     *      ) /}
     *   {/call}
     * </pre>
     */
    if (isSoyAttr) {
      ItemAccessNode itemAccessNode =
          new ItemAccessNode(
              new VarRefNode(attributesParam.get().name(), unknown, attributesParam.get()),
              new StringNode(attrName, QuoteStyle.SINGLE, unknown),
              unknown,
              false);
      itemAccessNode.setType(parameterMap.get(paramName));
      attributes.put(new StringNode(attrName, QuoteStyle.SINGLE, unknown), itemAccessNode);
      return;
    }
    StandaloneNode value = attr.getChild(1);
    if (value.getKind() != Kind.HTML_ATTRIBUTE_VALUE_NODE) {
      return;
    }
    /**
     * Otherwise, construct a {let} for each attribute and pass them as values in the map
     *
     * <pre>
     *   {let $__internal_call_someAttr_0 kind="text"}...{/let}
     *   {call foo}
     *     {param soyInternalAttributes: map(
     *         someAttr: $__internal_call_someAttr_0,
     *      ) /}
     *   {/call}
     * </pre>
     */
    HtmlAttributeValueNode attrValue = (HtmlAttributeValueNode) value;
    LetContentNode letContentNode =
        LetContentNode.forVariable(
            nodeIdGen.genId(),
            unknown,
            "$__internal_call_" + paramName + nodeIdGen.genId(),
            unknown,
            parameterMap.containsKey(paramName)
                ? SanitizedContentKind.fromAttributeValue(getKind(parameterMap.get(paramName)))
                    .get()
                : SanitizedContentKind.TEXT);
    callNode.getParent().addChild(callNode.getParent().getChildIndex(callNode), letContentNode);
    VarRefNode varRef =
        new VarRefNode(letContentNode.getVar().name(), unknown, letContentNode.getVar());
    if (condition.isPresent()) {
      ConditionalOpNode op =
          (ConditionalOpNode)
              Operator.CONDITIONAL.createNode(
                  unknown, unknown, condition.get(), varRef, new NullNode(unknown));
      op.setType(UnionType.of(NullType.getInstance(), varRef.getType()));
      attributes.put(new StringNode(attrName, QuoteStyle.SINGLE, unknown), op);
    } else {
      attributes.put(new StringNode(attrName, QuoteStyle.SINGLE, unknown), varRef);
    }
    for (StandaloneNode node : attrValue.getChildren()) {
      letContentNode.addChild(node.copy(new CopyState()));
    }
  }

  private static String getKind(SoyType attrType) {
    attrType = SoyTypes.removeNull(attrType);
    if (TrustedResourceUriType.getInstance().isAssignableFromStrict(attrType)) {
      return "trusted_resource_uri";
    } else if (UriType.getInstance().isAssignableFromStrict(attrType)) {
      return "uri";
    } else {
      return "text";
    }
  }
}
