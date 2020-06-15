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
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.TemplateType;

/** A registry of information about every compiled template. */
final class CompiledTemplateRegistry {
  private final ImmutableMap<String, CompiledTemplateMetadata> templateNameToMetadata;
  private final ImmutableMap<String, CompiledTemplateMetadata> classNameToMetadata;
  private final ImmutableSet<String> delegateTemplateNames;

  CompiledTemplateRegistry(TemplateRegistry registry) {
    ImmutableMap.Builder<String, CompiledTemplateMetadata> templateToMetadata =
        ImmutableBiMap.builder();
    ImmutableMap.Builder<String, CompiledTemplateMetadata> classToMetadata =
        ImmutableBiMap.builder();
    ImmutableSet.Builder<String> delegateTemplateNames = ImmutableSet.builder();
    for (TemplateMetadata template : registry.getAllTemplates()) {
      CompiledTemplateMetadata metadata =
          CompiledTemplateMetadata.create(template.getTemplateName(), template.getSoyFileKind());
      templateToMetadata.put(template.getTemplateName(), metadata);
      classToMetadata.put(metadata.typeInfo().className(), metadata);
      if (template.getTemplateKind() == TemplateType.TemplateKind.DELTEMPLATE) {
        delegateTemplateNames.add(template.getTemplateName());
      }
    }
    this.templateNameToMetadata = templateToMetadata.build();
    this.classNameToMetadata = classToMetadata.build();
    this.delegateTemplateNames = delegateTemplateNames.build();
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
  CompiledTemplateMetadata getTemplateInfoByClassName(String className) {
    return classNameToMetadata.get(className);
  }
}
