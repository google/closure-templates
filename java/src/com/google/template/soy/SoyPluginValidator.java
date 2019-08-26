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

package com.google.template.soy;

import com.google.template.soy.error.SoyCompilationException;
import com.google.template.soy.error.SoyErrors;

/** Executable that validates SoySourceFunctions. */
public final class SoyPluginValidator extends AbstractSoyCompiler {

  SoyPluginValidator(PluginLoader loader, SoyInputCache cache) {
    super(loader, cache);
  }

  SoyPluginValidator() {}

  public static void main(final String[] args) {
    new SoyPluginValidator().runMain(args);
  }

  @Override
  boolean requireSources() {
    return false;
  }

  @Override
  String formatCompilationException(SoyCompilationException sce) {
    return SoyErrors.formatErrorsMessageOnly(sce.getErrors());
  }

  @Override
  protected void compile(SoyFileSet.Builder sfsBuilder) {
    sfsBuilder.build().validateUserPlugins();
  }
}
