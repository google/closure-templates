/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A registry of information about every compiled template.
 */
final class CompiledTemplateRegistry {
  private final ImmutableBiMap<String, CompiledTemplateMetadata> templateNameToGeneratedClassName;
  private final ImmutableMap<String, ContentKind> deltemplateNameToContentKind;

  CompiledTemplateRegistry(TemplateRegistry registry) {
    Map<String, ContentKind> deltemplateNameToContentKind = new HashMap<>();
    ImmutableBiMap.Builder<String, CompiledTemplateMetadata> builder = ImmutableBiMap.builder();
    for (TemplateNode template : registry.getAllTemplates()) {
      builder.put(template.getTemplateName(), 
          CompiledTemplateMetadata.create(template.getTemplateName(), template));
      if (template instanceof TemplateDelegateNode && template.getContentKind() != null) {
        // all delegates are guaranteed to have the same content kind by the checkdelegatesvisitor
        deltemplateNameToContentKind.put(
            ((TemplateDelegateNode) template).getDelTemplateName(),
            template.getContentKind());
      }
    }
    this.templateNameToGeneratedClassName = builder.build();
    this.deltemplateNameToContentKind = ImmutableMap.copyOf(deltemplateNameToContentKind);
  }

  /**
   * Returns information about the generated class for the given fully qualified template name.
   */
  CompiledTemplateMetadata getTemplateInfo(String templateName) {
    return templateNameToGeneratedClassName.get(templateName);
  }

  /**
   * Returns the {@link ContentKind} (if any) of a deltemplate.
   */
  @Nullable ContentKind getDelTemplateContentKind(String delTemplateName) {
    return deltemplateNameToContentKind.get(delTemplateName);
  }
}
