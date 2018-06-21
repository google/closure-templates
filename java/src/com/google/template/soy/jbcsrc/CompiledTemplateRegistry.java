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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/** A registry of information about every compiled template. */
final class CompiledTemplateRegistry {
  private final ImmutableBiMap<String, CompiledTemplateMetadata> templateNameToMetadata;
  private final ImmutableBiMap<String, CompiledTemplateMetadata> classNameToMetadata;
  private final ImmutableMap<String, Optional<SanitizedContentKind>> deltemplateNameToContentKind;
  private final ImmutableSet<String> delegateTemplateNames;

  CompiledTemplateRegistry(TemplateRegistry registry) {
    Map<String, Optional<SanitizedContentKind>> deltemplateNameToContentKind = new HashMap<>();
    ImmutableBiMap.Builder<String, CompiledTemplateMetadata> templateToMetadata =
        ImmutableBiMap.builder();
    ImmutableBiMap.Builder<String, CompiledTemplateMetadata> classToMetadata =
        ImmutableBiMap.builder();
    ImmutableSet.Builder<String> delegateTemplateNames = ImmutableSet.builder();
    for (TemplateNode template : registry.getAllTemplates()) {
      CompiledTemplateMetadata metadata =
          CompiledTemplateMetadata.create(template.getTemplateName(), template);
      templateToMetadata.put(template.getTemplateName(), metadata);
      classToMetadata.put(metadata.typeInfo().className(), metadata);
      if (template instanceof TemplateDelegateNode) {
        delegateTemplateNames.add(template.getTemplateName());
        // all delegates are guaranteed to have the same content kind by the
        // checkdelegatesvisitor
        deltemplateNameToContentKind.put(
            ((TemplateDelegateNode) template).getDelTemplateName(),
            Optional.fromNullable(template.getContentKind()));
      }
    }
    this.templateNameToMetadata = templateToMetadata.build();
    this.classNameToMetadata = classToMetadata.build();
    this.deltemplateNameToContentKind = ImmutableMap.copyOf(deltemplateNameToContentKind);
    this.delegateTemplateNames = delegateTemplateNames.build();
  }

  ImmutableSet<String> getTemplateNames() {
    return templateNameToMetadata.keySet();
  }

  /** Returns the names of all delegate template implementations. */
  ImmutableSet<String> getDelegateTemplateNames() {
    return delegateTemplateNames;
  }

  /** Returns information about the generated class for the given fully qualified template name. */
  CompiledTemplateMetadata getTemplateInfoByTemplateName(String templateName) {
    return templateNameToMetadata.get(templateName);
  }

  /** Returns information about the generated class for the given fully qualified template name. */
  CompiledTemplateMetadata getTemplateInfoByClassName(String templateName) {
    return classNameToMetadata.get(templateName);
  }

  /**
   * Returns the {@link SanitizedContentKind} (if any) of a deltemplate.
   *
   * @throws IllegalArgumentException if it is unknown because there are no implementations of the
   *     delegate available at compile time.
   */
  @Nullable
  SanitizedContentKind getDelTemplateContentKind(String delTemplateName) {
    return deltemplateNameToContentKind.get(delTemplateName).orNull();
  }

  boolean hasDelTemplateDefinition(String delTemplateName) {
    return deltemplateNameToContentKind.containsKey(delTemplateName);
  }
}
