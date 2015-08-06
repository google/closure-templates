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

package com.google.template.soy.jbcsrc.shared;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SanitizedContent.ContentKind;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The result of template compilation.
 */
public final class CompiledTemplates {
  private final ImmutableSet<String> templateNames;
  private final ClassLoader loader;
  private final ConcurrentHashMap<String, TemplateData> templateNameToFactory = 
      new ConcurrentHashMap<>();

  public CompiledTemplates(ImmutableSet<String> templateNames, ClassLoader loader) {
    this.templateNames = checkNotNull(templateNames);
    this.loader = checkNotNull(loader);
  }

  /** Returns the strict content type of the template. */
  public Optional<ContentKind> getTemplateContentKind(String name) {
    return getTemplateData(name).kind;
  }

  /**
   * Returns a factory for the given fully qualified template name.
   */
  public CompiledTemplate.Factory getTemplateFactory(String name) {
    return getTemplateData(name).factory;
  }

  private TemplateData getTemplateData(String name) {
    checkNotNull(name);
    TemplateData template = templateNameToFactory.get(name);
    if (template == null) {
      if (templateNames.contains(name)) {
        template = new TemplateData(loadFactory(name, loader));
        templateNameToFactory.putIfAbsent(name, template);
      } else {
        throw new IllegalArgumentException("No template was compiled for: " + name);
      }
    }
    return template;
  }

  private static CompiledTemplate.Factory loadFactory(
      String name,
      ClassLoader loader) {
    // We construct the factories via reflection to bridge the gap between generated and
    // non-generated code.  However, each factory only needs to be constructed once so the
    // reflective cost isn't paid on a per render basis.
    CompiledTemplate.Factory factory;
    try {
      String factoryName = Names.javaClassNameFromSoyTemplateName(name) + "$Factory";
      Class<? extends CompiledTemplate.Factory> factoryClass =
          Class.forName(factoryName, true /* run clinit */, loader)
              .asSubclass(CompiledTemplate.Factory.class);
      factory = factoryClass.newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
      // this should be impossible since our factories are public with a default constructor.
      // TODO(lukes): failures of bytecode verification will propagate as Errors, we should
      // consider catching them here to add information about our generated types. (e.g. add the
      // class trace and a pointer on how to file a soy bug)
      throw new AssertionError(e);
    }
    return factory;
  }

  private static class TemplateData {
    final CompiledTemplate.Factory factory;
    final Optional<ContentKind> kind;

    TemplateData(CompiledTemplate.Factory factory) {
      this.factory = factory;
      // We pull the content kind off the templatemetadata eagerly since the parsing+reflection each
      // time is expensive.
      this.kind = getContentKind(factory);
    }
  }

  private static Optional<ContentKind> getContentKind(CompiledTemplate.Factory factory) {
    Optional<ContentKind> kind;
    String contentKind = 
        factory.getClass().getDeclaringClass().getAnnotation(TemplateMetadata.class).contentKind();
    if (contentKind.isEmpty()) {
      kind = Optional.absent();
    } else {
      kind = Optional.of(ContentKind.valueOf(contentKind));
    }
    return kind;
  }
}
