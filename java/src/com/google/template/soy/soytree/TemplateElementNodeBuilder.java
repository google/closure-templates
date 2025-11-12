/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.compilermetrics.Impression;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import java.util.List;

/** Builder for TemplateElementNode. */
public final class TemplateElementNodeBuilder
    extends TemplateNodeBuilder<TemplateElementNodeBuilder> {

  static final ImmutableSet<String> BANNED_ATTRIBUTE_NAMES =
      ImmutableSet.of("autoescape", "stricthtml", "visibility");

  private static final SoyErrorKind BANNED_ATTRIBUTE_NAMES_ERROR =
      SoyErrorKind.of(
          "Attribute ''{0}'' is not allowed on Soy elements.",
          Impression.ERROR_TEMPLATE_ELEMENT_NODE_BUILDER_ATTRIBUTE_NOT_ALLOWED);

  private static final SoyErrorKind INVALID_ELEMENT_KIND =
      SoyErrorKind.of(
          "Soy element kind must be html or html<...>.",
          Impression.ERROR_TEMPLATE_ELEMENT_NODE_BUILDER_INVALID_KIND);

  /**
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   */
  public TemplateElementNodeBuilder(
      SoyFileHeaderInfo soyFileHeaderInfo, ErrorReporter errorReporter) {
    super(soyFileHeaderInfo, errorReporter);
  }

  @CanIgnoreReturnValue
  @Override
  public TemplateElementNodeBuilder setCommandValues(
      Identifier templateName, List<CommandTagAttribute> attrs) {
    this.cmdText = templateName.identifier() + " " + Joiner.on(' ').join(attrs);
    setCommonCommandValues(attrs);

    for (CommandTagAttribute attribute : attrs) {
      Identifier name = attribute.getName();
      switch (name.identifier()) {
        case "kind":
          if (!getContentKind().getSanitizedContentKind().isHtml()) {
            errorReporter.report(attribute.getValueLocation(), INVALID_ELEMENT_KIND);
          }
          break;
        default:
          if (BANNED_ATTRIBUTE_NAMES.contains(name.identifier())) {
            this.errorReporter.report(
                name.location(), BANNED_ATTRIBUTE_NAMES_ERROR, name.identifier());

          } else if (!COMMON_ATTRIBUTE_NAMES.contains(name.identifier())) {
            errorReporter.report(
                name.location(),
                CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY,
                name.identifier(),
                "element",
                ImmutableList.builder()
                    .addAll(
                        COMMON_ATTRIBUTE_NAMES.stream()
                            .filter(n -> !BANNED_ATTRIBUTE_NAMES.contains(n))
                            .collect(ImmutableList.toImmutableList()))
                    .build());
          }
      }
    }

    setTemplateNames(templateName, soyFileHeaderInfo.getNamespace());
    return this;
  }

  @Override
  public TemplateElementNode build() {
    Preconditions.checkState(id != null && cmdText != null);
    validateBuild();
    return new TemplateElementNode(this, soyFileHeaderInfo);
  }

  @Override
  protected TemplateElementNodeBuilder self() {
    return this;
  }
}
