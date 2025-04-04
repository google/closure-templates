/*
 * Copyright 2024 Google Inc.
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.types.SanitizedType.AttributesType;

/**
 * Inserts calls to {@code $$flushPendingLoggingAttributes()} at the end of root elements in element
 * style templates.
 */
@RunAfter(AutoescaperPass.class)
@RunBefore(DesugarHtmlNodesPass.class)
final class AddFlushPendingLoggingAttributesPass implements CompilerFilePass {

  AddFlushPendingLoggingAttributesPass() {}

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    // we instrument an open tag node if
    // 1. it is a direct child of a VeLogNode
    for (var velogNode : SoyTreeUtils.getAllNodesOfType(file, VeLogNode.class)) {
      var openTag = velogNode.getOpenTagNode();
      if (openTag != null) {
        instrumentNode(nodeIdGen, openTag);
      }
    }
    // 2. it is the root of a {template}, {let}, or {param} with a well-formed single root element.
    Streams.<RenderUnitNode>concat(
            file.getTemplates().stream().filter(t -> t.getContentKind().isHtml()),
            SoyTreeUtils.allNodesOfType(file, LetContentNode.class)
                .filter(l -> !l.isImplicitContentKind() && l.getContentKind().isHtml()),
            SoyTreeUtils.allNodesOfType(file, CallParamContentNode.class)
                .filter(l -> !l.isImplicitContentKind() && l.getContentKind().isHtml()))
        .forEach(
            block -> {
              var contentTags =
                  block.getChildren().stream()
                      .filter(n -> !SoyElementPass.ALLOWED_CHILD_NODES.contains(n.getKind()))
                      .collect(toImmutableList());
              if (contentTags.isEmpty() || !(contentTags.get(0) instanceof HtmlOpenTagNode)) {
                return;
              }

              HtmlOpenTagNode openTag = (HtmlOpenTagNode) contentTags.get(0);
              if ((openTag.isSelfClosing() && contentTags.size() == 1)
                  || (openTag.getTaggedPairs().size() == 1
                      && openTag.getTaggedPairs().get(0).equals(Iterables.getLast(contentTags)))) {
                instrumentNode(nodeIdGen, openTag);
              }
            });
  }

  /**
   * Adds the {@code $$flushPendingLoggingAttributes()} call to the end of the open tag node.
   *
   * <p>By placing them at the end, we ensure that they will simply be ignored by browesers.
   *
   * <p>See:
   * https://html.spec.whatwg.org/multipage/parsing.html#attribute-name-state:parse-error-duplicate-attribute
   * which states that duplicate attributes are ignored.
   *
   * <p>This is not ideal behavior but aligns with the behavior of the javascript implementations
   * which will also ignore the duplicate attributes (though they will throw in debug builds).
   *
   * <p>TODO: b/383661457 - throw an error if duplicates get printed. This will require tracking the
   * full set of attributes that are printed for an element that has this call added.
   */
  private void instrumentNode(IdGenerator nodeIdGen, HtmlOpenTagNode openTag) {
    var functionCall =
        FunctionNode.newPositional(
            Identifier.create(
                BuiltinFunction.FLUSH_PENDING_LOGGING_ATTRIBUTES.name(), SourceLocation.UNKNOWN),
            BuiltinFunction.FLUSH_PENDING_LOGGING_ATTRIBUTES,
            SourceLocation.UNKNOWN);
    functionCall.addChild(getTagIsAnchorNode(openTag));
    functionCall.setType(AttributesType.getInstance());
    var printNode =
        new PrintNode(
            nodeIdGen.genId(),
            SourceLocation.UNKNOWN,
            /* isImplicit= */ true,
            functionCall,
            /* attributes= */ ImmutableList.of(),
            ErrorReporter.exploding());
    printNode.setHtmlContext(HtmlContext.HTML_TAG);
    var attributeNode =
        new HtmlAttributeNode(
            nodeIdGen.genId(),
            SourceLocation.UNKNOWN,
            /* equalsSignLocation= */ null,
            /* isSoyAttr= */ false);
    attributeNode.addChild(printNode);
    openTag.addChild(attributeNode);
  }

  private static ExprNode getTagIsAnchorNode(HtmlOpenTagNode openTag) {
    var tagName = openTag.getTagName();
    return new BooleanNode(
        // If the tag is dynamic we assume it isn't an anchor
        tagName.isStatic() && tagName.getStaticTagNameAsLowerCase().equals("a"),
        tagName.getTagLocation());
  }
}
