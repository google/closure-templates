/*
 * Copyright 2018 Google Inc.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/** Names of the templates in each file (a lightweight template registry, without the metadata). */
@AutoValue
public abstract class TemplateNameRegistry {

  public static TemplateNameRegistry create(
      ImmutableMap<String, TemplatesPerFile> filePathsToTemplates) {
    return new AutoValue_TemplateNameRegistry(filePathsToTemplates);
  }

  abstract ImmutableMap<String, TemplatesPerFile> filePathsToTemplates();

  public boolean hasFile(String filePath) {
    return filePathsToTemplates().containsKey(filePath);
  }

  public TemplatesPerFile getTemplatesForFile(String filePath) {
    checkState(
        hasFile(filePath), "TemplateNameRegistry does not contain info for file: %s", filePath);
    return filePathsToTemplates().get(filePath);
  }

  public ImmutableSet<String> allFiles() {
    return filePathsToTemplates().keySet();
  }
}
