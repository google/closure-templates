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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Template registry for a single soy file. This holds metadata for all the templates in the file
 * (NOT its dependencies).
 */
public final class TemplatesPerFile {

  private final String filePath;
  private final ImmutableMap<String, TemplateMetadata> templateNamesToValues;

  private TemplatesPerFile(
      String filePath, ImmutableMap<String, TemplateMetadata> templateNamesToValues) {
    this.filePath = filePath;
    this.templateNamesToValues = templateNamesToValues;
  }

  /** The file path for this registry. */
  String getFilePath() {
    return filePath;
  }

  /** Returns metadata for all the templates in this file. */
  ImmutableList<TemplateMetadata> getAllTemplates() {
    return ImmutableList.copyOf(templateNamesToValues.values());
  }

  /** Gets metadata for the template with the given name. */
  TemplateMetadata getTemplateMetadata(String templateName) {
    return checkNotNull(
        templateNamesToValues.get(checkNotNull(templateName)),
        "couldn't find metadata for %s in %s",
        templateName,
        templateNamesToValues);
  }

  /** Creates a new builder. */
  public static Builder builder(String filePath) {
    return new Builder(filePath);
  }

  /** Builder for TemplatesPerFile */
  public static class Builder {

    private final String filePath;
    private final Map<String, TemplateMetadata> templateNamesToValues;

    private Builder(String filePath) {
      this.filePath = filePath;
      this.templateNamesToValues = new LinkedHashMap<>();
    }

    public Builder addAllTemplates(List<TemplateMetadata> templateList) {
      templateList.forEach(this::addTemplate);
      return this;
    }

    public Builder addTemplate(TemplateMetadata template) {
      templateNamesToValues.put(template.getTemplateName(), template);
      return this;
    }

    public TemplatesPerFile build() {
      return new TemplatesPerFile(filePath, ImmutableMap.copyOf(templateNamesToValues));
    }
  }
}
