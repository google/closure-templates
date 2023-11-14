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
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.types.SoyType.Kind;

/** Wrapper class around Kind.TOGGLE_TYPE that inherits from SoyType. */
@AutoValue
public abstract class ToggleImportType extends ImportType {

  // Keep track of toggle name and toggle file path (needed in jssrc)
  public static ToggleImportType create(String name, SourceFilePath path) {
    return new AutoValue_ToggleImportType(name, path);
  }

  public abstract String getName();

  public abstract SourceFilePath getPath();

  @Override
  public final String toString() {
    return getName() + " from " + getPath().path();
  }

  @Override
  public Kind getKind() {
    return Kind.TOGGLE_TYPE;
  }
}
