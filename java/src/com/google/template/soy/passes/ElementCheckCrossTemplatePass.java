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
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import java.util.Optional;

/** Validates the contents included in the root tag of an element template. */
@RunAfter(ResolveExpressionTypesCrossTemplatePass.class)
public final class ElementCheckCrossTemplatePass implements CompilerFileSetPass {

  private static final SoyErrorKind BAD_CONTENT_IN_ROOT_ELM =
      SoyErrorKind.of("Only attributes and allowed inside the root element.");

  private final ErrorReporter errorReporter;

  ElementCheckCrossTemplatePass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      SoyTreeUtils.getAllNodesOfType(file, TemplateNode.class).stream()
          .filter(t -> t.getTemplateContentKind() instanceof TemplateContentKind.ElementContentKind)
          .forEach(t -> processTemplate(t));
    }
    return Result.CONTINUE;
  }

  private void processTemplate(TemplateNode template) {
    Optional<HtmlOpenTagNode> elmOpen = ElementAttributePass.getElementOpen(template);
    if (!elmOpen.isPresent()) {
      return;
    }

    HtmlOpenTagNode openTagNode = elmOpen.get();
    openTagNode.getChildren().stream()
        .filter(p -> p.getKind() == Kind.HTML_ATTRIBUTE_NODE)
        .map(HtmlAttributeNode.class::cast)
        .filter(attr -> attr.getStaticKey() == null)
        .forEach(
            attr -> {
              if (!filterNonAttribute(attr)) {
                errorReporter.report(attr.getSourceLocation(), BAD_CONTENT_IN_ROOT_ELM);
              }
            });
  }

  private boolean filterNonAttribute(HtmlAttributeNode attr) {
    return SoyElementCompositionPass.isOkToPutInElement(attr);
  }
}
