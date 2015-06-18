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

package com.google.template.soy.jbcsrc.api;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SanitizedContent.ContentKind;

/**
 * The result of template compilation.
 */
public final class CompiledTemplates {
  private final ImmutableMap<String, CompiledTemplate.Factory> templateNameToFactory;

  public CompiledTemplates(ImmutableMap<String, CompiledTemplate.Factory> templateFactories) {
    this.templateNameToFactory = templateFactories;
  }

  /** Returns the strict content type of the template. */
  public Optional<ContentKind> getTemplateContentKind(String name) {
    TemplateMetadata meta = getTemplateMetadata(name);
    String contentKind = meta.contentKind();
    if (contentKind.isEmpty()) {
      return Optional.absent();
    }
    return Optional.of(ContentKind.valueOf(contentKind));
  }

  /**
   * Returns a factory for the given fully qualified template name.
   */
  public CompiledTemplate.Factory getTemplateFactory(String name) {
    checkNotNull(name);
    CompiledTemplate.Factory factory = templateNameToFactory.get(name);
    if (factory == null) {
      throw new IllegalArgumentException("No template was compiled for: " + name);
    }
    return factory;
  }
  
  private TemplateMetadata getTemplateMetadata(String name) {
    // TODO(lukes): cache this in a map?
    // Each template factory is an inner class of its corresponding template file and the annotation
    // is always on the template
    return getTemplateFactory(name)
        .getClass()
        .getDeclaringClass()
        .getAnnotation(TemplateMetadata.class);
  }

}
