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
package com.google.template.soy.types;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceFilePath;

/** Representing an imported template module/file type, i.e. "import * as p from 'p.soy'; */
@AutoValue
public abstract class TemplateModuleImportType extends ImportType {

  public static TemplateModuleImportType create(
      String namespace, SourceFilePath path, ImmutableSet<String> templateNames) {
    return new AutoValue_TemplateModuleImportType(namespace, path, templateNames);
  }

  public abstract String getNamespace();

  public abstract SourceFilePath getPath();

  public abstract ImmutableSet<String> getTemplateNames();

  @Override
  public final String toString() {
    return getPath().path();
  }

  @Override
  public Kind getKind() {
    return Kind.TEMPLATE_MODULE;
  }

  @Override
  public ImmutableCollection<String> getNestedSymbolNames() {
    return getTemplateNames();
  }
}
