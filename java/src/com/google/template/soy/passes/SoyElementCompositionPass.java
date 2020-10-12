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
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CommandTagAttribute;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.defn.AttrParam;

/**
 * Rewrites {@code <{legacyTagName($tag)}>} to {@code <{$tag}>} and disallows all other print nodes
 * that name HTML tags.
 */
@RunAfter(ResolveExpressionTypesPass.class)
final class SoyElementCompositionPass implements CompilerFileSetPass {

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
    for (HtmlTagNode tagNode : SoyTreeUtils.getAllNodesOfType(file, HtmlTagNode.class)) {
      TagName name = tagNode.getTagName();
      if (name.isStatic()) {
        continue;
      }
      PrintNode printNode = name.getDynamicTagName();
      if (!tagNode.getTagName().isTemplateCall()) {
        continue;
      }
      Preconditions.checkState(tagNode.getTaggedPairs().size() <= 1);

      SourceLocation location = tagNode.getSourceLocation();
      if (!tagNode.getTaggedPairs().isEmpty()) {
        location = location.extend(tagNode.getTaggedPairs().get(0).getSourceLocation());
      }
      CallBasicNode call =
          new CallBasicNode(
              nodeIdGen.genId(),
              location,
              SourceLocation.UNKNOWN,
              printNode.getExpr().getRoot().copy(new CopyState()),
              ImmutableList.of(),
              false,
              errorReporter);

      tagNode.getChildren().stream()
          .filter(n -> n.getKind() == SoyNode.Kind.HTML_ATTRIBUTE_NODE)
          .map(HtmlAttributeNode.class::cast)
          .filter(attr -> attr.getStaticKey() != null)
          .forEach(attr -> consumeAttribute(call, attr, nodeIdGen));

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
    }
  }

  private SoyNode consumeSlot(CallBasicNode callNode, SoyNode startNode, IdGenerator nodeIdGen) {
    HtmlOpenTagNode nextOpenTag = (HtmlOpenTagNode) startNode;
    HtmlAttributeNode attributeNode = (HtmlAttributeNode) nextOpenTag.getChild(1);
    HtmlTagNode closeTag = nextOpenTag.getTaggedPairs().get(0);
    CallParamContentNode callParamContent =
        new CallParamContentNode(
            nodeIdGen.genId(),
            startNode.getSourceLocation(),
            SourceLocation.UNKNOWN,
            Identifier.create(attributeNode.getStaticKey(), SourceLocation.UNKNOWN),
            new CommandTagAttribute(
                Identifier.create("kind", SourceLocation.UNKNOWN),
                QuoteStyle.SINGLE,
                "html",
                startNode.getSourceLocation().extend(closeTag.getSourceLocation()),
                SourceLocation.UNKNOWN),
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

  private void consumeAttribute(
      CallBasicNode callNode, HtmlAttributeNode attr, IdGenerator nodeIdGen) {
    StandaloneNode value = attr.getChild(1);
    if (value.getKind() != Kind.HTML_ATTRIBUTE_VALUE_NODE) {
      return;
    }
    HtmlAttributeValueNode attrValue = (HtmlAttributeValueNode) value;
    ExprNode valueNode;
    if (attrValue.numChildren() == 0) {
      // TODO(user): Pass-through if same attribute is in this template scope.
      valueNode = new StringNode("", QuoteStyle.SINGLE, attrValue.getSourceLocation());
    } else {
      StandaloneNode onlyChild = attrValue.getChild(0);
      if (onlyChild.getKind() == Kind.RAW_TEXT_NODE) {
        valueNode =
            new StringNode(
                ((RawTextNode) onlyChild).getRawText(),
                QuoteStyle.SINGLE,
                attrValue.getSourceLocation());
      } else if (onlyChild.getKind() == Kind.PRINT_NODE) {
        valueNode = ((PrintNode) onlyChild).getExpr().getRoot();
      } else {
        throw new IllegalArgumentException("Unexpected attribute AST: " + attr);
      }
    }
    CallParamValueNode callParamContent =
        new CallParamValueNode(
            nodeIdGen.genId(),
            attr.getSourceLocation(),
            Identifier.create(
                AttrParam.attrToParamName(attr.getStaticKey()),
                attr.getChild(0).getSourceLocation()),
            valueNode);
    callNode.addChild(callParamContent);
  }
}
