/*
 * Copyright 2025 Google Inc.
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

import com.google.auto.value.AutoValue;
import com.google.template.soy.base.internal.SoyFileKind;

/** Simple tuple of un an-evaluated compilation unit containing information about dependencies. */
@AutoValue
public abstract class CompilationUnitAndKind {
  public static CompilationUnitAndKind create(
      SoyFileKind fileKind, CompilationUnit compilationUnit) {
    // sanity check
    checkArgument(
        fileKind != SoyFileKind.SRC, "compilation units should only represent dependencies");
    return new AutoValue_CompilationUnitAndKind(fileKind, compilationUnit);
  }

  abstract SoyFileKind fileKind();

  abstract CompilationUnit compilationUnit();
}
