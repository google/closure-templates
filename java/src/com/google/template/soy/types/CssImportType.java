/*
 * Copyright 2023 Google Inc.
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
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceFilePath;

/** Representing imported classes from a CSS file. */
@AutoValue
public abstract class CssImportType extends ImportType {

  public static CssImportType create(
      SourceFilePath path, ImmutableMap<String, String> shortClassMap) {
    return new AutoValue_CssImportType(path, shortClassMap);
  }

  public abstract SourceFilePath getPath();

  /**
   * Maps short class names to full class names, as defined in the CssRegistry.
   *
   * <p>A {@code classes} object can be imported from a CSS file and used to refer to CSS classes by
   * their short name (e.g. {@code classes.shortName}). This map is used to recover the full class
   * name.
   *
   * <p>TODO b/289431307 - Link to CSS symbols documentation.
   */
  public abstract ImmutableMap<String, String> getShortClassMap();

  @Override
  public final String toString() {
    return CssModuleImportType.CLASSES + " from " + getPath().path();
  }

  @Override
  public Kind getKind() {
    return Kind.CSS_TYPE;
  }

  @Override
  public ImmutableCollection<String> getNestedSymbolNames() {
    return getShortClassMap().keySet();
  }
}
