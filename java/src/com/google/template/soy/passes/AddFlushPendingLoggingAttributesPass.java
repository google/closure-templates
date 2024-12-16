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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
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
    // 2. it is the direct child of a template node that is an 'element'
    for (var template : file.getTemplates()) {
      var metadata = template.getHtmlElementMetadata();
      if (metadata == null || !metadata.getIsHtmlElement()) {
        continue;
      }
      // If we are an element then there is either a single root element at the top level, or under
      // a velog command, or there is a delegating call either way we are just looking for an
      // HtmlOpenTageNode one or two levels deep.
      var openTag =
          template.getChildren().stream()
              .filter(node -> node instanceof HtmlOpenTagNode)
              .findFirst();
      if (openTag.isPresent()) {
        instrumentNode(nodeIdGen, (HtmlOpenTagNode) openTag.get());
      }
    }
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
}
