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
import com.google.template.soy.parsepasses.contextautoesc.ContextualAutoescaper;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.AttrParam;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import java.util.Optional;
import java.util.stream.Stream;

/** Validates the contents included in the root tag of an element template. */
@RunAfter(ResolveExpressionTypesCrossTemplatePass.class)
public final class ElementCheckCrossTemplatePass implements CompilerFileSetPass {

  private static final SoyErrorKind BAD_CONTENT_IN_ROOT_ELM =
      SoyErrorKind.of("Only attributes are allowed inside the root element.");

  private static final SoyErrorKind WRONG_ATTRIBUTE_TYPE =
      SoyErrorKind.of("Expected type of attribute to be {0}.");

  private final ErrorReporter errorReporter;

  ElementCheckCrossTemplatePass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    sourceFiles.stream()
        .flatMap(file -> SoyTreeUtils.allNodesOfType(file, TemplateNode.class))
        .filter(t -> t.getTemplateContentKind() instanceof TemplateContentKind.ElementContentKind)
        .forEach(this::processTemplate);
    return Result.CONTINUE;
  }

  private void validateAttributeTypes(HtmlOpenTagNode node, Stream<AttrParam> attributes) {
    attributes.forEach(
        attr -> {
          Optional<SoyType> maybeType =
              ContextualAutoescaper.getRequiredTypeFromAttributeName(attr.getAttrName(), node);
          if (!maybeType.isPresent()) {
            return;
          }
          SoyType type = maybeType.get();
          if (!type.isAssignableFromStrict(SoyTypes.removeNull(attr.type()))) {
            errorReporter.report(attr.getSourceLocation(), WRONG_ATTRIBUTE_TYPE, type.toString());
          }
        });
  }

  private void processTemplate(TemplateNode template) {
    Optional<HtmlOpenTagNode> elmOpen = ElementAttributePass.getElementOpen(template);
    if (!elmOpen.isPresent()) {
      return;
    }

    HtmlOpenTagNode openTagNode = elmOpen.get();
    validateAttributeTypes(
        openTagNode,
        template.getHeaderParams().stream()
            .filter(p -> p instanceof AttrParam)
            .map(AttrParam.class::cast));
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
