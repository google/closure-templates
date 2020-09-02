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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.passes.CompilerFileSetPass.Result;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TagName;

/**
 * Rewrites {@code <{legacyTagName($tag)}>} to {@code <{$tag}>} and disallows all other print nodes
 * that name HTML tags.
 */
@RunAfter(ResolveExpressionTypesPass.class)
final class SoyElementCompositionPass implements CompilerFileSetPass {

  private static final SoyErrorKind UNIMPLEMENTED_FEATURES =
      SoyErrorKind.of(
          "Soy Element Composition does not support HTML attributes or slotted content.");

  private final ErrorReporter errorReporter;

  SoyElementCompositionPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
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
        if (tagNode.numChildren() > 1) {
          errorReporter.report(tagNode.getSourceLocation(), UNIMPLEMENTED_FEATURES);
        }
        for (HtmlTagNode closeTag : tagNode.getTaggedPairs()) {
          if (SoyTreeUtils.nextSibling(tagNode) != closeTag) {
            errorReporter.report(tagNode.getSourceLocation(), UNIMPLEMENTED_FEATURES);
          }
          closeTag.getParent().removeChild(closeTag);
        }
        tagNode
            .getParent()
            .replaceChild(
                tagNode,
                new CallBasicNode(
                    nodeIdGen.genId(),
                    SourceLocation.UNKNOWN,
                    SourceLocation.UNKNOWN,
                    printNode.getExpr().getRoot().copy(new CopyState()),
                    ImmutableList.of(),
                    false,
                    errorReporter));
      }
    }
  }
}
