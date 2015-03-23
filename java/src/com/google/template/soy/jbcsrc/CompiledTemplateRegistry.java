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
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateRegistry;

import java.util.Map;

/**
 * A registry of information about every compiled template.
 */
final class CompiledTemplateRegistry {
  private final ImmutableBiMap<String, CompiledTemplateMetadata> templateNameToGeneratedClassName;

  CompiledTemplateRegistry(TemplateRegistry registry) {
    ImmutableBiMap.Builder<String, CompiledTemplateMetadata> builder = ImmutableBiMap.builder();
    // TODO(lukes): this only deals with basic templates, add deltemplate support
    for (Map.Entry<String, TemplateBasicNode> entry : registry.getBasicTemplatesMap().entrySet()) {
      builder.put(entry.getKey(), 
          CompiledTemplateMetadata.create(entry.getKey(), entry.getValue()));
    }
    this.templateNameToGeneratedClassName = builder.build();
  }

  /**
   * Returns information about the generated class for the given fully qualified template name.
   */
  CompiledTemplateMetadata getTemplateInfo(String templateName) {
    return templateNameToGeneratedClassName.get(templateName);
  }
}
