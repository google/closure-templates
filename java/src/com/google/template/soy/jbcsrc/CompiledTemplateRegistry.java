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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;

/** A registry of information about every compiled template. */
final class CompiledTemplateRegistry {
  private final LoadingCache<TemplateMetadata, CompiledTemplateMetadata> templateToMetadata;

  private final TemplateRegistry registry;

  CompiledTemplateRegistry(TemplateRegistry registry) {
    this.registry = registry;
    this.templateToMetadata =
        CacheBuilder.newBuilder()
            .<TemplateMetadata, CompiledTemplateMetadata>build(
                CacheLoader.from(CompiledTemplateRegistry::createMetadata));
  }

  static CompiledTemplateMetadata createMetadata(TemplateMetadata template) {
    return CompiledTemplateMetadata.create(template.getTemplateName(), template.getSoyFileKind());
  }

  /** Returns information about the generated class for the given fully qualified template name. */
  CompiledTemplateMetadata getBasicTemplateInfoByTemplateName(String templateName) {
    return templateToMetadata.getUnchecked(registry.getBasicTemplateOrElement(templateName));
  }

  /** Returns information about the generated class for the given template . */
  CompiledTemplateMetadata getTemplateInfo(TemplateNode template) {
    return templateToMetadata.getUnchecked(registry.getMetadata(template));
  }
}
