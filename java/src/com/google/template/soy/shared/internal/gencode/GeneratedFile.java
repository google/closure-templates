/*
 * Copyright 2019 Google Inc.
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
package com.google.template.soy.shared.internal.gencode;

import com.google.auto.value.AutoValue;

/** Wrapper for a generated file to write. Holds the file name and contents. */
@AutoValue
public abstract class GeneratedFile {
  public static GeneratedFile create(String fileName, String contents) {
    return new AutoValue_GeneratedFile(fileName, contents);
  }

  public abstract String fileName(); // File name (without path).

  public abstract String contents(); // File contents.
}
