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
package com.google.template.soy.soytree;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Template registry for a single soy file. This holds metadata for all the templates in the file
 * (NOT its dependencies).
 */
public final class TemplatesPerFile {

  private final String filePath;
  private final ImmutableSet<TemplateName> templates;

  private TemplatesPerFile(String filePath, ImmutableSet<TemplateName> templates) {
    this.filePath = filePath;
    this.templates = templates;
  }

  /** The file path for this registry. */
  public String getFilePath() {
    return filePath;
  }

  /** Gets the short (unqualified) template names for all the templates in this file. */
  public ImmutableSet<String> getUnqualifiedTemplateNames() {
    return templates.stream().map(TemplateName::unqualifiedName).collect(toImmutableSet());
  }

  /** Whether this file has a template with the given unqualified name. */
  public boolean hasTemplateWithUnqualifiedName(String unqualifiedTemplateName) {
    return templates.stream()
        .anyMatch(name -> name.unqualifiedName().equals(unqualifiedTemplateName));
  }

  /**
   * Gets the full template name wrapper object for the given unqualified template name. Throws an
   * error if the template does not exist in this file.
   */
  public TemplateName getFullTemplateName(String unqualifiedTemplateName) {
    checkArgument(
        hasTemplateWithUnqualifiedName(unqualifiedTemplateName),
        "File: %s does not contain template name: %s",
        filePath,
        unqualifiedTemplateName);

    return templates.stream()
        .filter(k -> k.unqualifiedName().equals(unqualifiedTemplateName))
        .findFirst()
        .get();
  }

  /** Creates a new builder. */
  public static Builder builder(String filePath) {
    return new Builder(filePath);
  }

  /** Builder for TemplatesPerFile */
  public static class Builder {

    private final String filePath;
    private final Set<TemplateName> templates;

    private Builder(String filePath) {
      this.filePath = filePath;
      this.templates = new LinkedHashSet<>();
    }

    public Builder addAllTemplates(List<TemplateMetadata> templateList) {
      templateList.forEach(this::addTemplate);
      return this;
    }

    public Builder addTemplate(TemplateMetadata template) {
      addTemplate(template.getTemplateName());
      return this;
    }

    public Builder addTemplate(String fullTemplateName) {
      templates.add(TemplateName.create(fullTemplateName));
      return this;
    }

    public TemplatesPerFile build() {
      return new TemplatesPerFile(filePath, ImmutableSet.copyOf(templates));
    }
  }

  /**
   * Wrapper for a template name. Stores the fully qualified and unqualified versions of the name
   * (e.g. "my.namespace.foo" and "foo").
   */
  @AutoValue
  public abstract static class TemplateName {
    static TemplateName create(String fullyQualifiedName) {
      checkArgument(
          !Strings.isNullOrEmpty(fullyQualifiedName),
          "Template name cannot be null or empty %s",
          fullyQualifiedName);

      int startOfUnqualifiedName = fullyQualifiedName.lastIndexOf('.');
      String unqualifiedName = fullyQualifiedName.substring(startOfUnqualifiedName + 1);
      return new AutoValue_TemplatesPerFile_TemplateName(fullyQualifiedName, unqualifiedName);
    }

    public abstract String fullyQualifiedName();

    public abstract String unqualifiedName();
  }
}
