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
import com.google.template.soy.passes.CompilerFileSetPass.Result;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CommandTagAttribute;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TagName;

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
      if (tagNode.getTagName().isTemplateCall()) {
        Preconditions.checkState(tagNode.numChildren() == 1);
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
}
