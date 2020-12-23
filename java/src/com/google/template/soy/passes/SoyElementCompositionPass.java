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
import static java.util.stream.Collectors.toCollection;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.parsepasses.contextautoesc.ContextualAutoescaper;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CommandTagAttribute;
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
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.AttrParam;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SanitizedType.AttributesType;
import com.google.template.soy.types.SanitizedType.StyleType;
import com.google.template.soy.types.SanitizedType.TrustedResourceUriType;
import com.google.template.soy.types.SanitizedType.UriType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.TemplateType.Parameter;
import com.google.template.soy.types.UnionType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

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

  private static final SoyErrorKind SKIP_NODE_NOT_ALLOWED =
      SoyErrorKind.of("Skip nodes are not allowed on this template call.");

  private final ErrorReporter errorReporter;
  private final ImmutableList<? extends SoyPrintDirective> printDirectives;

  SoyElementCompositionPass(
      ErrorReporter errorReporter, ImmutableList<? extends SoyPrintDirective> printDirectives) {
    this.errorReporter = errorReporter;
    this.printDirectives = printDirectives;
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
        process(template, tagNode, nodeIdGen, file.getTemplateRegistry());
      }
    }
  }

  private void process(
      TemplateNode template,
      HtmlTagNode tagNode,
      IdGenerator nodeIdGen,
      TemplateRegistry registry) {
    TagName name = tagNode.getTagName();
    if (name.isStatic()) {
      return;
    }
    PrintNode printNode = name.getDynamicTagName();
    if (!tagNode.getTagName().isTemplateCall()) {
      return;
    }

    if (tagNode instanceof HtmlOpenTagNode) {
      ContextualAutoescaper.annotateAndRewriteHtmlTag(
          (HtmlOpenTagNode) tagNode, registry, nodeIdGen, errorReporter, printDirectives);
    }

    Preconditions.checkState(tagNode.getTaggedPairs().size() <= 1);
    SourceLocation unknown = template.getSourceLocation().clearRange();
    Map<String, AttrParam> attrs =
        template.getAllParams().stream()
            .filter(p -> p instanceof AttrParam)
            .map(AttrParam.class::cast)
            .collect(Collectors.toMap(AttrParam::name, Function.identity()));

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
    TemplateType templateType = (TemplateType) call.getCalleeExpr().getRoot().getType();
    CallParamContentNode attributesNode =
        templateType.getAllowExtraAttributes()
            ? new CallParamContentNode(
                nodeIdGen.genId(),
                location,
                UNKNOWN,
                Identifier.create(TemplateType.ATTRIBUTES_HIDDEN_PARAM, UNKNOWN),
                new CommandTagAttribute(
                    Identifier.create("kind", UNKNOWN),
                    QuoteStyle.SINGLE,
                    "attributes",
                    UNKNOWN,
                    UNKNOWN),
                errorReporter)
            : null;
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
      call.addChild(attributesNode);
    }
  }

  private void maybeConsumeAttribute(
      StandaloneNode c,
      CallBasicNode call,
      IdGenerator nodeIdGen,
      Set<String> seenAttr,
      Map<String, SoyType> parameterMap,
      Map<String, AttrParam> attrs,
      @Nullable CallParamContentNode attributesNode,
      Optional<ExprNode> conditional) {
    if (c.getKind() == SoyNode.Kind.HTML_ATTRIBUTE_NODE) {
      HtmlAttributeNode attrNode = (HtmlAttributeNode) c;
      if (attrNode.getStaticKey() != null) {
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
          param.setOriginalName(attrNode.getStaticKey());
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
            && ((PrintNode) attrNode.getChild(0)).getExpr().getType()
                == AttributesType.getInstance())
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

  @Nullable
  private CallParamNode consumeAttribute(
      HtmlAttributeNode attr,
      IdGenerator nodeIdGen,
      Set<String> seenAttr,
      Map<String, SoyType> parameterMap,
      Map<String, AttrParam> attrs,
      @Nullable CallParamContentNode attributesNode,
      CallBasicNode call,
      Optional<ExprNode> condition) {
    SourceLocation unknown = attr.getSourceLocation().clearRange();

    String attrName = attr.getStaticKey();
    boolean isSoyAttr = attrName.startsWith("@");
    if (isSoyAttr) {
      attrName = attrName.substring(1);
    }

    if (!seenAttr.add(attrName)) {
      errorReporter.report(attr.getChild(0).getSourceLocation(), DUPLICATE_ATTRIBUTE);
      return null;
    }

    String paramName = Parameter.attrToParamName(attrName);
    if (!parameterMap.containsKey(paramName)) {
      // attributesNode can't be null, bad attrs caught in ResolveExpressionTypesCrossTemplatePass
      maybePrintAttribute(attributesNode, condition, nodeIdGen, attr);
      return null;
    }
    if (isSoyAttr) {
      ExprNode val = new VarRefNode("$" + paramName, unknown, attrs.get(paramName));
      if (condition.isPresent()) {
        return new CallParamValueNode(
            nodeIdGen.genId(),
            attr.getSourceLocation(),
            Identifier.create(paramName, unknown),
            Operator.CONDITIONAL.createNode(
                unknown, unknown, condition.get(), val, new NullNode(unknown)));
      } else {
        return new CallParamValueNode(
            nodeIdGen.genId(),
            attr.getSourceLocation(),
            Identifier.create(paramName, unknown),
            val);
      }
    }
    StandaloneNode value = attr.getChild(1);
    if (value.getKind() != Kind.HTML_ATTRIBUTE_VALUE_NODE) {
      return null;
    }
    HtmlAttributeValueNode attrValue = (HtmlAttributeValueNode) value;
    if (!condition.isPresent()) {
      CallParamContentNode contentNode =
          new CallParamContentNode(
              nodeIdGen.genId(),
              attr.getSourceLocation(),
              unknown,
              Identifier.create(paramName, unknown),
              // TODO(tomnguyen) Verify that @attribute is only string or uri or string|uri.
              new CommandTagAttribute(
                  Identifier.create("kind", unknown),
                  QuoteStyle.SINGLE,
                  getKind(parameterMap.get(paramName)),
                  unknown,
                  unknown),
              errorReporter);
      for (StandaloneNode node : attrValue.getChildren()) {
        contentNode.addChild(node.copy(new CopyState()));
      }
      return contentNode;
    }

    /*
     * Otherwise, construct a {let} for each attribute and pass them as values in the map
     *
     * <pre>
     *   {let $__internal_call_someAttr_0 kind="text"}{if $cond}...{/if}{/let}
     *   {call foo}
     *     {param someAttr: $cond ? $__internal_call_someAttr_0 : null /}
     *   {/call}
     * </pre>
     */
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
    call.getParent().addChild(call.getParent().getChildIndex(call), letContentNode);
    IfNode ifNode = new IfNode(nodeIdGen.genId(), unknown);
    letContentNode.addChild(ifNode);
    IfCondNode ifCondNode =
        new IfCondNode(
            nodeIdGen.genId(), unknown, unknown, "if", condition.get().copy(new CopyState()));
    ifNode.addChild(ifCondNode);
    ifCondNode.getExpr().setType(condition.get().getType());
    for (StandaloneNode node : attrValue.getChildren()) {
      ifCondNode.addChild(node.copy(new CopyState()));
    }
    VarRefNode varRef =
        new VarRefNode("$" + letContentNode.getVar().name(), unknown, letContentNode.getVar());
    ConditionalOpNode op =
        (ConditionalOpNode)
            Operator.CONDITIONAL.createNode(
                unknown, unknown, condition.get(), varRef, new NullNode(unknown));
    op.setType(UnionType.of(NullType.getInstance(), varRef.getType()));
    return new CallParamValueNode(
        nodeIdGen.genId(), unknown, Identifier.create(paramName, unknown), op);
  }

  private static String getKind(SoyType attrType) {
    attrType = SoyTypes.removeNull(attrType);
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
}
