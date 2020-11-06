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
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.VarRefNode;
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
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.AttrParam;
import com.google.template.soy.types.SanitizedType.TrustedResourceUriType;
import com.google.template.soy.types.SanitizedType.UriType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.TemplateType.Parameter;
import java.util.HashSet;
import java.util.Map;
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
      SoyErrorKind.of("In an element call commands must be contained within an attribute value.");

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
        new CallParamContentNode(
            nodeIdGen.genId(),
            location,
            SourceLocation.UNKNOWN,
            Identifier.create(TemplateType.ATTRIBUTES_HIDDEN_PARAM, SourceLocation.UNKNOWN),
            new CommandTagAttribute(
                Identifier.create("kind", SourceLocation.UNKNOWN),
                QuoteStyle.SINGLE,
                "attributes",
                SourceLocation.UNKNOWN,
                SourceLocation.UNKNOWN),
            errorReporter);
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
              if (c.getKind() == SoyNode.Kind.HTML_ATTRIBUTE_NODE
                  && ((HtmlAttributeNode) c).getStaticKey() != null) {
                CallParamNode param =
                    consumeAttribute(
                        (HtmlAttributeNode) c,
                        nodeIdGen,
                        seenAttr,
                        parameterMap,
                        attrs,
                        attributesNode);
                if (param != null) {
                  call.addChild(param);
                }
              } else {
                errorReporter.report(c.getSourceLocation(), ILLEGAL_CHILD);
              }
            });
    if (attributesNode.numChildren() > 0) {
      call.addChild(attributesNode);
    }
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

  @Nullable
  private CallParamNode consumeAttribute(
      HtmlAttributeNode attr,
      IdGenerator nodeIdGen,
      Set<String> seenAttr,
      Map<String, SoyType> parameterMap,
      Map<String, AttrParam> attrs,
      CallParamContentNode attributesNode) {
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
      attributesNode.addChild(attr.copy(new CopyState()));
      return null;
    }
    if (isSoyAttr) {
      ExprNode val = new VarRefNode(paramName, unknown, attrs.get(paramName));
      return new CallParamValueNode(
          nodeIdGen.genId(), attr.getSourceLocation(), Identifier.create(paramName, unknown), val);
    } else {
      StandaloneNode value = attr.getChild(1);
      if (value.getKind() != Kind.HTML_ATTRIBUTE_VALUE_NODE) {
        return null;
      }
      HtmlAttributeValueNode attrValue = (HtmlAttributeValueNode) value;
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
