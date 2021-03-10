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

/** Representing an imported template. */
@AutoValue
public abstract class TemplateImportType extends ImportType {

  public static TemplateImportType create(String fqn) {
    return new AutoValue_TemplateImportType(fqn);
  }

  public static TemplateImportType create(TemplateModuleImportType moduleType, String fieldName) {
    return create(moduleType.getNamespace() + "." + fieldName);
  }

  /** This is not available at instantiation time since it depends on expression type resolution. */
  private TemplateType basicTemplateType;

  public abstract String getName();

  public TemplateType getBasicTemplateType() {
    return basicTemplateType;
  }

  public void setBasicTemplateType(TemplateType basicTemplateType) {
    this.basicTemplateType = basicTemplateType;
  }

  @Override
  public final String toString() {
    return getName();
  }

  @Override
  public Kind getKind() {
    return Kind.TEMPLATE_TYPE;
  }
}
