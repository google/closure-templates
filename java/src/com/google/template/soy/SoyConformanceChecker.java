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

package com.google.template.soy;

import java.io.IOException;

/** Executable that enforces soy conformance rules against a set of soy source files. */
public final class SoyConformanceChecker extends AbstractSoyCompiler {
  SoyConformanceChecker(ClassLoader loader) {
    super(loader);
  }

  SoyConformanceChecker() {}

  public static void main(final String[] args) throws IOException {
    new SoyConformanceChecker().runMain(args);
  }

  @Override
  void validateFlags() {
    if (conformanceConfigs.isEmpty()) {
      exitWithError("Must set --conformanceConfig");
    }
  }

  @Override
  protected void compile(SoyFileSet.Builder sfsBuilder) {
    sfsBuilder.setAllowExternalCalls(false).build().parseCheck();
  }
}
